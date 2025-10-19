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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
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

    private BucketInfo bucketInfo;
    private long updateTime = 0L;
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
    public Boolean md5ContentEnable = ComUtils.getProps("pc.content.md5.enable",
            false, Boolean::valueOf);
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

    private String name;

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
        BucketInfo vbInfo = getBucketInfo();
        if (null == vbInfo) {
            LOG.error("failed to get info! bucket:{}", name);
            throw SdkClientException.create("invalid bucket");
        }
        Path fullKey = Paths.get(FileUtils.mergePath(vbInfo.getPrefix(), fileKey));
        if (fullKey.toString().length() > maxObjectKeyLength) {
            LOG.error("length of key:{} is more than {}", fullKey, maxObjectKeyLength);
            throw SdkClientException.create("key is too long");
        }
        RoutingResult routingResult = pmsMgr.getVirtualBucketSTSApi(vbInfo.getName(),fullKey.getParent().toString(),
                Collections.singletonList(PcPermission.PutObject), stsDurationSeconds);
        StsInfo stsInfo = routingResult.getSTS();
        if (null == stsInfo) {
            LOG.error("failed to get STS for bucket:{}", vbInfo.getName());
            throw SdkClientException.create("invalid STS");
        }
        PutObjectResponse response;
        try {
            S3Client s3Client = S3ClientCache.buildS3Client(stsInfo, false);
            stsInfo.setKey(fullKey.toString());
            if (enablePCache && file.length() > blockSize) {
                response = putObjectPCache(s3Client, stsInfo, fullKey.toString(), file);
            } else {
                response = putObjectNormal(s3Client, stsInfo, fullKey.toString(), file);
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

    private PutObjectResponse putObjectNormal(S3Client s3Client, StsInfo stsInfo, String fullKey,
                                              File file) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            LOG.error("input file:{} is not found!", file);
            throw SdkClientException.create("invalid input file", e);
        }
        if (md5ContentEnable) {
            try {
                String contentMd5 = FileUtils.getMD5Base64FromFile(inputStream, file.length(), "MD5");
                if (StringUtils.isNotBlank(contentMd5)) {
                    builder.contentMD5(contentMd5);
                }
            } catch (Exception e) {
                LOG.error("failed to get MD5 from file {}!", file, e);
                throw SdkClientException.create("failed to get MD5 from file " + file , e);
            }
        }
        PutObjectRequest putObjectRequest = builder.bucket(stsInfo.getName())
                .contentLength(file.length())
                .contentDisposition(S3Utils.getContentDispositionHeaderValue(
                        contentDispositionDefault, Paths.get(fullKey).getFileName().toString()))
                .key(fullKey.toString()).build();

        RequestBody requestBody = RequestBody.fromInputStream(inputStream, file.length());
        PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
        return response;
    }

    private PutObjectResponse putObjectPCache(S3Client s3Client, StsInfo stsInfo, String fullKey, File file) {
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
                    bucket(stsInfo.getName()).key(fullKey).build();
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
            String uploadId = response.uploadId();

            List<PutTask> taskInfoList = new ArrayList<>();
            for (int i = 0; i < blockNum ; i++) {

                partSize = Math.min(blockSize, leftSize);
                String host = pmsMgr.getPcp(fullKey + i);
                String fileUrl;
                if (host != null) {
                    fileUrl = String.format("%s%s/%s.%08d_%d",
                            host, name, fullKey, i+1, partSize);
                } else {
                    fileUrl = String.format("%s/%s.%08d_%d",
                            fullKey, name, i+1, partSize);
                }
                PutTask task = new PutTask(latch, s3Client, i+1, i * blockSize, partSize, stsInfo);
                task.setFileUrl(fileUrl);
                task.setLocalFile(file.getPath());
                task.setUploadId(uploadId);
                taskInfoList.add(task);
                parallelManager.put(task);
                leftSize -= partSize;
            }

            awaitTasks(latch);
            Stats stats = threadTracer.get().getStats();
            List<CompletedPart> completedParts = new ArrayList<>();
            for(PutTask taskInfo : taskInfoList) {
                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(taskInfo.getPartNumber())
                        .eTag(taskInfo.getETag())
                        .build();
                completedParts.add(completedPart);
                stats.add(taskInfo.getStats());
            }

            LOG.debug("finished all tasks, total number", file.length()/blockSize+1);
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(stsInfo.getName()).key(fullKey).uploadId(uploadId)
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
        StsInfo stsInfo = getSTSInfo(name, fileKey);
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
                response = getObjectPCache(s3Client, tempPath, stsInfo, stsInfo.getKey());
            } else {
                response =  getObjectNormal(s3Client, tempPath, stsInfo, stsInfo.getKey());
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

    private GetObjectResponse getObjectNormal(
            S3Client s3Client, Path localFilePath, StsInfo stsInfo, String fullKey) {

        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(stsInfo.getName()).key(fullKey).build();
        GetObjectResponse res = s3Client.getObject(getObjectRequest, ResponseTransformer.toFile(localFilePath));
        if (!S3Utils.isGetObjectSuccessful(res)) {
            LOG.error("failed to get object！for invalid response {}", res);
            throw SdkClientException.create("invalid response");
        }
        return res;
    }

    private GetObjectResponse getObjectPCache(
            S3Client s3Client, Path localFilePath, StsInfo stsInfo, String fullKey) {
        HeadObjectResponse headInfo;
        try {
            if (parallelManager == null) {
                parallelManager = new ParallelManager();
            }
            headInfo = S3Utils.headObject(s3Client, stsInfo.getName(), fullKey);
            long fileSize = headInfo.contentLength();
            LOG.info("file sie: {}", headInfo.contentLength());

            int blockNum = (int) Math.ceil((double) fileSize / blockSize);
            CountDownLatch latch = new CountDownLatch(blockNum);
            long partSize = 0;
            long leftSize = fileSize;
            List<String> localFilePaths = new ArrayList<>();
            List<GetTask> taskList = new ArrayList<>();
            for (int i = 1; i < blockNum+1; i++) {
                partSize = Math.min(blockSize,leftSize);
                leftSize -= partSize;
                String host = pmsMgr.getPcp(fullKey + i);
                String fileUrl;
                if (host != null) {
                    fileUrl = String.format("%stest-vb/%s.%08d_%d",
                            host, fullKey, i, partSize);
                } else {
                    fileUrl = String.format("test-vb/%s.%08d_%d",
                            fullKey, i, partSize);
                }
                String localFile = String.format("%s.%08d_%d", localFilePath, i, partSize);
                localFilePaths.add(localFile);

                GetTask taskInfo = new GetTask(latch, s3Client, stsInfo, fileUrl, localFile, blockSize);
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

    private StsInfo getSTSInfo(String vbName, String fileKey) {
        BucketInfo vbInfo = getBucketInfo();
        if (null == vbInfo) {
            LOG.error("failed to get info of bucket:{}", vbName);
            throw SdkClientException.create("invalid bucket");
        }
        Path fullKey = Paths.get(FileUtils.mergePath(vbInfo.getPrefix(), fileKey));
        String parentPath = fullKey.getParent() == null ? "" : fullKey.getParent().toString();
        RoutingResult routingResult = pmsMgr.getVirtualBucketSTSApi(vbInfo.getName(),
                parentPath, Collections.singletonList(PcPermission.GetObject),
                stsDurationSeconds);
        StsInfo stsInfo = routingResult.getSTS();
        stsInfo.setKey(fullKey.toString());
        return stsInfo;
    }
}
