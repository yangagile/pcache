/*
 * Copyright (c) 2025 Yangagile. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloud.pc;

import com.cloud.pc.entity.*;
import com.cloud.pc.entity.Stats;
import com.cloud.pc.model.PcPath;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.model.routing.RoutingResult;
import com.cloud.pc.parallel.*;
import com.cloud.pc.service.PmsMgr;
import com.cloud.pc.service.PmsMgrImpl;
import com.cloud.pc.utils.ComUtils;
import com.cloud.pc.utils.FileUtils;


import com.cloud.pc.utils.S3ClientCache;
import com.cloud.pc.utils.S3Utils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;

@Getter
@Setter
@ToString
public class PBucket {
    private static final Logger LOG = LoggerFactory.getLogger(PBucket.class);
    private ThreadLocal<Tracer> threadTracer = ThreadLocal.withInitial(() -> new Tracer());

    private ParallelManager parallelManager = null;

    private PmsMgr pmsMgr = null;

    private long updateTime = 0L;

    private String name;

    private BucketInfo bucketInfo;

    public static long bucketInfoRefreshTimeMs = ComUtils.getProps("pc.bucket.refresh.time.ms",
            300*1000L, Long::valueOf);

    private boolean enablePCache = ComUtils.getProps(
            "pc.pcache.enable.default", true, Boolean::valueOf);

    public String pmsUrl = ComUtils.getProps(
            "pms.url", "", String::valueOf);

    public String ak = ComUtils.getProps("pc.ak",
            "", String::valueOf);

    public String sk = ComUtils.getProps("pc.sk",
            "", String::valueOf);

    // boject
    public int maxObjectKeyLength = ComUtils.getProps("pc.max.object.key.length",
            512, Integer::valueOf);
    public String contentDispositionDefault = ComUtils.getProps("pc.content.disposition.default",
            "attachment", String::valueOf);
    // add user metadata with object's checksum.
    public String checksumAlgorithm = ComUtils.getProps("pc.checksum.algorithm",
            "CRC32", String::valueOf);
    public int blockSize = ComUtils.getProps("pc.block.size",
            5*1024*1024, Integer::valueOf);
    public int blockGroupSize = ComUtils.getProps("pc.block.group.size",
            3, Integer::valueOf);

    // STS
    public int stsDurationSeconds = ComUtils.getProps("pc.sts.duration.seconds",
            1800, Integer::valueOf);

    // thread
    public int threadNumber = ComUtils.getProps("pc.thread.number",
            4, Integer::valueOf);

    // bucket info cache
    public int cacheBucketInfoSeconds = ComUtils.getProps("pc.cache.bucket.info.seconds",
            300, Integer::valueOf);


    public PBucket(String bucketName) {
        this.name = bucketName;
        init();
    }

    public PBucket(String pmsUrl, String bucketName, String ak, String sk) {
        this.name = bucketName;
        this.pmsUrl = pmsUrl;
        this.ak = ak;
        this.sk = sk;
        init();
    }

    private void init() {
        pmsMgr = new PmsMgrImpl(pmsUrl, ak, sk);
    }

    public BucketInfo getBucketInfo() {
        long timeNow = System.currentTimeMillis();
        if (bucketInfo == null && timeNow- updateTime > bucketInfoRefreshTimeMs) {
            BucketInfo info = pmsMgr.getBucketInfoApi(name);
            if (info == null) {
                LOG.error("failed to get BucketInfo through PMS API, bucket:{}", name);
            } else {
                bucketInfo = info;
            }
        }
        return bucketInfo;
    }

    public void close() {
        if (parallelManager != null) {
            parallelManager.close();
        }
    }

    private void awaitTasks(CountDownLatch latch) throws InterruptedException {
        latch.await();
    }

    public PutObjectResponse putObject(String fileKey, String localFile) {
        LOG.info("putObject bucket:{} key:{} localFile:{}" , name, fileKey, localFile);
        Stats stats = threadTracer.get().newStats();
        File file = new File(localFile);
        if (!file.exists()) {
            LOG.error("localFile:{} is not exists!", localFile);
            throw SdkClientException.create("invalid input file");
        }
        BucketInfo bucketInfo = getBucketInfo();
        if (null == bucketInfo) {
            LOG.error("failed to get info! bucket:{}", name);
            throw SdkClientException.create("invalid bucket");
        }
        Path fullKey = Paths.get(FileUtils.mergePath(bucketInfo.getPrefix(), fileKey));
        if (fullKey.toString().length() > maxObjectKeyLength) {
            LOG.error("length of key:{} is more than {}", fullKey, maxObjectKeyLength);
            throw SdkClientException.create("key is too long");
        }
        RoutingResult routingResult = pmsMgr.getVirtualBucketSTSApi(bucketInfo.getName(),bucketInfo.getPrefix(),
                Collections.singletonList(PcPermission.PutObject), stsDurationSeconds);
        StsInfo stsInfo = routingResult.getSTS();
        if (null == stsInfo) {
            LOG.error("failed to get STS for bucket:{}", bucketInfo.getName());
            throw SdkClientException.create("invalid STS");
        }
        PutObjectResponse response;
        try {
            Map<String, String> userMetas = new HashMap<>();
            if (checksumAlgorithm.equalsIgnoreCase("MD5")) {
                String checksum = FileUtils.getMD5Base64FromFile(localFile);
                userMetas.put("checksum-md5", checksum);
            } else if (checksumAlgorithm.equalsIgnoreCase("CRC32")) {
                String checksum = FileUtils.getCRC32Base64FromFile(localFile);
                userMetas.put("checksum-crc32", checksum);
            }
            S3Client s3Client = S3ClientCache.buildS3Client(stsInfo, false);
            if (file.length() > blockSize) {
                response = putMultipart(s3Client, stsInfo, fullKey.toString(), file, userMetas);
            } else {
                response = putObjectSingle(s3Client, stsInfo, fullKey.toString(), file, userMetas);
            }
            if (!S3Utils.isPutObjectSuccessful(response)) {
                LOG.error("failed to put object for invalid eTag!");
                throw SdkClientException.create("failed to put object");
            }
            stats.finish(file.length());
            LOG.info("successfully put file from {} to {}/{} {}", localFile, name, fileKey, stats);
            return response;

        } catch (Exception e) {
            LOG.error("put object:{} with exception!", fullKey, e);
            throw SdkClientException.create("failed to put object", e);
        }
    }

    private PutObjectResponse putObjectSingle(S3Client s3Client, StsInfo stsInfo, String fullKey,
                                              File file, Map<String, String> userMetas) {
        String host = null;
        if (enablePCache) {
            host = pmsMgr.getPcp(fullKey);
        }
        PcPath pcPath = new PcPath(name, fullKey, 0, 1);

        PutTask task = new PutTask(null, s3Client, stsInfo, host, pcPath,
                userMetas, file.toString(), file.length(), blockSize, null);
        task.run();
        threadTracer.get().getStats().add(task.getStats());
        PutObjectResponse response = PutObjectResponse.builder()
                .eTag(task.getETag())
                .build();

        return response;
    }

    private PutObjectResponse putMultipart(S3Client s3Client, StsInfo stsInfo, String fullKey, File file,
                                           Map<String, String> userMetas) {
        try {
            if (parallelManager == null) {
                parallelManager = new ParallelManager();
            }
            long blockNum = file.length()/blockSize;
            if (blockNum*blockSize < file.length()) {
                blockNum++;
            }
            CountDownLatch latch = new CountDownLatch((int)blockNum);
            long partSize = 0;
            long leftSize = file.length();

            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder().
                    bucket(stsInfo.getBucketName()).key(fullKey).metadata(userMetas).build();
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
            String uploadId = response.uploadId();

            List<PutTask> taskInfoList = new ArrayList<>();
            for (int i = 0; i < blockNum ; i++) {

                partSize = Math.min(blockSize, leftSize);
                String host = null;
                if (enablePCache) {
                    host = pmsMgr.getPcp(fullKey + i);
                }
                PcPath pcPath = new PcPath(name, fullKey, i, blockNum);

                PutTask task = new PutTask(latch, s3Client, stsInfo, host, pcPath,
                        null, file.toString(), partSize, blockSize, uploadId);
                taskInfoList.add(task);
                parallelManager.put(task);
                leftSize -= partSize;
            }
            awaitTasks(latch);
            Stats stats = threadTracer.get().getStats();
            List<CompletedPart> completedParts = new ArrayList<>();
            for(PutTask taskInfo : taskInfoList) {
                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber((int)taskInfo.getPcPath().getNumber()+1)
                        .eTag(taskInfo.getETag())
                        .build();
                completedParts.add(completedPart);
                stats.add(taskInfo.getStats());
            }

            LOG.debug("finished all tasks, total number", file.length()/blockSize+1);
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(stsInfo.getBucketName()).key(fullKey).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()).build();
            CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
            return S3Utils.convertToPutObjectResponse(completeResponse);
        } catch (Exception e) {
            LOG.error("failed to put object:{}", fullKey, e);
            throw SdkClientException.create("failed to get object, error: " + e.getMessage(), e);
        }
    }

    public GetObjectResponse getObject(String fileKey, String localFilePath) {
        Stats stats = threadTracer.get().newStats();
        BucketInfo bucketInfo = getBucketInfo();
        if (null == bucketInfo) {
            LOG.error("failed to get info of bucket:{}", name);
            throw SdkClientException.create("invalid bucket");
        }
        RoutingResult routingResult = pmsMgr.getVirtualBucketSTSApi(bucketInfo.getName(),
                bucketInfo.getPrefix(), Collections.singletonList(PcPermission.GetObject),
                stsDurationSeconds);
        StsInfo stsInfo = routingResult.getSTS();
        if (null == stsInfo) {
            LOG.error("failed to get STS for bukcet:{}", name);
            throw SdkClientException.create("invalid STS");
        }
        LOG.debug("successfully get STS, bukcet:{}, key:{}", name, fileKey);
        Path path = Paths.get(localFilePath);
        Path tempPath = path;
        if (Files.exists(path)) {
            tempPath = Paths.get(localFilePath + "." + System.nanoTime() + ".temp");
        }
        Path localDir = tempPath.getParent();
        if (localDir != null) {
            File parentDirectory = localDir.toFile();
            if (parentDirectory != null && !parentDirectory.exists()) {
                parentDirectory.mkdirs();
            }
        }
        try {
            GetObjectResponse response;
            S3Client s3Client = S3ClientCache.buildS3Client(stsInfo, false);
            if (enablePCache) {
                response = getMultipart(s3Client, tempPath, stsInfo, fileKey);
            } else {
                response =  getObjectSingle(s3Client, tempPath, stsInfo, fileKey);
            }
            stats.finish(new File(localFilePath).length());
            LOG.info("successfully get file from {}/{} to {} {}", name, fileKey, localFilePath, stats);
            return response;
        } catch (Exception e) {
            LOG.error("failed to get object:{}", fileKey);
            throw SdkClientException.create("failed to get object!", e);

        } finally {
            if (!tempPath.equals(path)) {
                try {
                    java.nio.file.Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
                    tempPath.toFile().delete();
                } catch (IOException e) {
                    LOG.error("failed to get object:{}", fileKey, e);
                    throw SdkClientException.create("failed to get object!", e);
                }
            }
        }
    }

    private GetObjectResponse getObjectSingle(S3Client s3Client, Path localFilePath,
                                              StsInfo stsInfo, String fullKey) {
        String host = null;
        if (enablePCache) {
            host =  pmsMgr.getPcp(fullKey);
        }

        PcPath pcPath = new PcPath(name, fullKey, 0, 1);
        GetTask taskInfo = new GetTask(null, s3Client, stsInfo, host, pcPath,
                localFilePath.toString(), localFilePath.toFile().length(), blockSize);
        taskInfo.run();
        threadTracer.get().getStats().add(taskInfo.getStats());
        return GetObjectResponse.builder().eTag(taskInfo.getETag()).build();
    }

    private GetObjectResponse getMultipart(S3Client s3Client, Path localFilePath, StsInfo stsInfo, String fullKey) {
        HeadObjectResponse headInfo;
        try {
            if (parallelManager == null) {
                parallelManager = new ParallelManager();
            }
            headInfo = S3Utils.headObject(s3Client, stsInfo.getBucketName(), fullKey);
            long fileSize = headInfo.contentLength();
            LOG.info("file size: {}", headInfo.contentLength());
            if (fileSize < blockSize) {
                return getObjectSingle(s3Client, localFilePath, stsInfo, fullKey);
            }
            int blockNum = (int) Math.ceil((double) fileSize / blockSize);
            CountDownLatch latch = new CountDownLatch(blockNum);
            long partSize = 0;
            long leftSize = fileSize;
            List<String> localFilePaths = new ArrayList<>();
            List<GetTask> taskList = new ArrayList<>();
            for (int i = 0; i < blockNum; i++) {
                partSize = Math.min(blockSize,leftSize);
                leftSize -= partSize;
                String host = null;
                if (enablePCache) {
                    host = pmsMgr.getPcp(fullKey + i);
                }
                String localFile = String.format("%s.%d_%d", localFilePath, i, blockNum);
                localFilePaths.add(localFile);

                PcPath pcPath = new PcPath(name, fullKey, i, blockNum);
                GetTask taskInfo = new GetTask(latch, s3Client, stsInfo, host, pcPath, localFile, partSize, blockSize);
                taskList.add(taskInfo);
                parallelManager.put(taskInfo);
            }
            awaitTasks(latch);

            FileUtils.mergeFiles(localFilePaths, localFilePath);

            Stats stats = threadTracer.get().getStats();
            for (GetTask task : taskList) {
                stats.add(task.getStats());
            }
        } catch (InterruptedException e) {
            LOG.error("failed to get object:{}", fullKey, e);
            throw SdkClientException.create("failed to get object, error: " + e.getMessage(), e);
        } catch (IOException e) {
            LOG.error("{} - failed to get object:{}", fullKey, e);
            throw SdkClientException.create("failed to get object, error: " + e.getMessage(), e);
        }
        return S3Utils.HeadObject2GetObjectResponse(headInfo);
    }
}
