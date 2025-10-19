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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import com.cloud.pc.model.StsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Getter
@Setter
@ToString
public class GetTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GetTask.class);
    private final CountDownLatch latch;
    private S3Client s3Client;
    private String fileUrl;
    private String localFile;
    private StsInfo stsInfo;
    private Stats stats;
    private int blockSize;

    public GetTask(CountDownLatch latch, S3Client s3Client, StsInfo stsInfo,
                   String fileUrl, String localFile, int blockSize) {
        this.latch = latch;
        this.s3Client = s3Client;
        this.stsInfo = stsInfo;
        this.fileUrl = fileUrl;
        this.localFile = localFile;
        this.stats = new Stats();
    }

    @Override
    public void run() {
       try {
            boolean getLocal = true;
            if (fileUrl.startsWith("http")) {
                try {
                    getBlockFromPcp();
                    getLocal =false;
                    stats.addPcp();
                }  catch (Exception e) {
                    LOG.error("exception to get block from PCP:{}", fileUrl, e);
                }
            }
            if (getLocal) {
                getBlockFromLocal();
                stats.addLocal();
            }
        } catch (Exception e) {
           LOG.error("exception to get block from local, key{}", fileUrl, e);
           stats.addFail();
        } finally {
            latch.countDown();
        }
    }

    private void getBlockFromPcp() throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-STS",
                JsonUtils.toJson(stsInfo));
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
        } finally {
            connection.disconnect();
        }
        LOG.info("finished get file block: {}", fileUrl);
    }

    private void getBlockFromLocal() throws Exception{
        LOG.info("get block from load key:{} localfile:{} fullKey={}", fileUrl, localFile);
        PcPath pcPath = new PcPath(fileUrl);
        long pos = (pcPath.getNo()-1)* blockSize;
        String range = String.format("bytes=%d-%d",pos, pos + pcPath.getSize()-1);

        Path localPath = Paths.get(localFile);
        FileUtils.mkParentDir(localPath);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(stsInfo.getName())
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
}
