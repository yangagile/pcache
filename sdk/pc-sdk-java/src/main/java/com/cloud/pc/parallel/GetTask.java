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

package com.cloud.pc.parallel;

import com.cloud.pc.model.PcPath;
import com.cloud.pc.entity.Stats;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.S3Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import com.cloud.pc.model.StsInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class GetTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GetTask.class);
    private final CountDownLatch latch;
    private S3Client s3Client;
    private StsInfo stsInfo;
    private String pcpUrl;
    private PcPath pcPath;
    private String localFile;
    private long size;
    private int blockSize;
    private String eTag;
    private Stats stats;

    public GetTask(CountDownLatch latch, S3Client s3Client, StsInfo stsInfo,
                   String pcpUrl, PcPath pcPath, String localFile, long size, int blockSize) {
        this.latch = latch;
        this.s3Client = s3Client;
        this.stsInfo = stsInfo;
        this.pcpUrl = pcpUrl;
        this.pcPath = pcPath;
        this.localFile = localFile;
        this.size = size;
        this.blockSize = blockSize;
        this.stats = new Stats();
    }

    public Stats getStats() {
        return stats;
    }

    public String getETag() {
        return eTag;
    }


    @Override
    public void run() {
        try {
            boolean getLocal = true;
            if (StringUtils.isNotBlank(pcpUrl)) {
                try {
                    getBlockFromPcp();
                    getLocal =false;
                    stats.addPcp();
                }  catch (Exception e) {
                    LOG.error("exception to get block from PCP:{}", pcpUrl, e);
                }
            }
            if (getLocal) {
                if (pcPath.isSingleFile()) {
                    getFileFromLocal();
                } else {
                    getBlockFromLocal();
                }
                stats.addLocal();
            }
        } catch (Exception e) {
            LOG.error("exception to get block from local, key{}", pcpUrl, e);
            stats.addFail();
        } finally {
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    private void getBlockFromPcp() throws Exception {
        URL url = new URL(FileUtils.mergePath(pcpUrl, pcPath.toString()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-STS", JsonUtils.toJson(stsInfo));
        connection.setRequestProperty("X-DATA-SIZE", String.valueOf(size));
        connection.setRequestProperty("X-BLOCK-SIZE", String.valueOf(blockSize));
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(60000);

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            String cacheHit = connection.getHeaderField("X-CACHE-HIT");
            stats.addPcpCacheHit(Integer.parseInt(cacheHit));
            eTag = "cache";
        } finally {
            connection.disconnect();
        }
        LOG.info("finished get file block: {}", url);
    }

    private void getBlockFromLocal() throws Exception{
        LOG.info("get block {} to local file {}", pcPath.getKey() , localFile);
        //PcPath pcPath = new PcPath(fileUrl);
        long pos = (pcPath.getNumber()) * blockSize;
        String range = String.format("bytes=%d-%d",pos, pos + size -1);

        Path localPath = Paths.get(localFile);
        FileUtils.mkParentDir(localPath);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(stsInfo.getBucketName())
                .key(pcPath.getKey())
                .range(range)
                .build();

        GetObjectResponse res = s3Client.getObject(getObjectRequest,
                ResponseTransformer.toFile(localPath));

        if (!S3Utils.isGetObjectSuccessful(res)) {
            LOG.error("failed to get object！for invalid response {}", res);
            throw SdkClientException.create("invalid response");
        }
    }

    private void getFileFromLocal() throws Exception {
        LOG.info("get file {} to local file {} ", pcPath.getKey(), localFile);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().
                bucket(stsInfo.getBucketName()).key(pcPath.getKey()).build();
        GetObjectResponse res = s3Client.getObject(
                getObjectRequest, ResponseTransformer.toFile(new File(localFile)));
        if (!S3Utils.isGetObjectSuccessful(res)) {
            LOG.error("failed to get object！for invalid response {}", res);
            throw SdkClientException.create("invalid response");
        }
        eTag = res.eTag();
    }
}
