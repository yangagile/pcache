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

import com.cloud.pc.entity.Stats;
import com.cloud.pc.model.PcPath;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.JsonUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Getter
@Setter
@ToString
public class PutTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PutTask.class);

    private final CountDownLatch latch;
    private S3Client s3Client;
    private StsInfo stsInfo;
    private String pcpUrl;
    private PcPath pcPath;
    private Map<String, String> userMetas;
    private String localFile;
    private long  size;
    private long  blockSize;
    private String uploadId;

    private String eTag;
    private Stats stats;

    public PutTask(CountDownLatch latch, S3Client s3Client, StsInfo stsInfo, String pcpUrl, PcPath pcPath,
                   Map<String, String> userMetas, String localFile, long size, long blockSize, String uploadId) {
        this.latch = latch;
        this.s3Client = s3Client;
        this.stsInfo = stsInfo;
        this.pcpUrl = pcpUrl;
        this.pcPath = pcPath;
        this.userMetas = userMetas;
        this.localFile = localFile;
        this.size = size;
        this.blockSize = blockSize;
        this.uploadId = uploadId;
        this.stats = new Stats();
    }

    private void putToPcp(byte[] buffer) throws Exception{
        HttpURLConnection connection;
        URL url = new URL(FileUtils.mergePath(pcpUrl, pcPath.toString()));
        connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("Content-Length", String.valueOf(size));
        connection.setRequestProperty("X-STS", JsonUtils.toJson(stsInfo));
        if (userMetas != null && !userMetas.isEmpty()) {
            connection.setRequestProperty("X-USER-META", JsonUtils.toJson(userMetas));
        }
        if (StringUtils.isNotBlank(uploadId)) {
            connection.setRequestProperty("X-UPLOAD-ID", uploadId);
        }

        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(buffer);
            outputStream.flush();

            // deal with the response from server
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    eTag = reader.readLine().trim();
                }
            } else {
                throw new RuntimeException("upload failed. Response Code: "+ responseCode);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void putLocal(byte[] buffer) throws Exception{
        if (pcPath.isSingleFile()) {
            PutObjectRequest.Builder builder = PutObjectRequest.builder();
            PutObjectRequest putObjectRequest = builder.bucket(stsInfo.getBucketName())
                    .contentLength(size)
                    .metadata(userMetas)
                    .key(pcPath.getKey()).build();

            RequestBody requestBody = RequestBody.fromBytes(buffer);
            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
            eTag = response.eTag();
        } else {
            UploadPartRequest uploadRequest = UploadPartRequest.builder()
                    .bucket(stsInfo.getBucketName())
                    .key(pcPath.getKey())
                    .uploadId(uploadId)
                    .partNumber((int)pcPath.getNumber()+1)
                    .build();

            UploadPartResponse response = s3Client.uploadPart(
                    uploadRequest,
                    RequestBody.fromBytes(buffer)
            );
            eTag = response.eTag();
        }
    }

    @Override
    public void run() {
        boolean putLocal = true;
        try {
            File file = new File(this.localFile);
            byte[] buffer;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                buffer = new byte[(int) size];
                raf.seek(pcPath.getNumber()*blockSize);
                raf.read(buffer);
            }

            if (StringUtils.isNotBlank(pcpUrl)) {
                try {
                    putToPcp(buffer);
                    putLocal = false;
                    stats.addPcp();
                } catch (Exception e) {
                    LOG.error("exception to put {} to {} from PCP {}", localFile, pcPath, pcpUrl, e);
                }
                LOG.info("finished put file {} to {} from PCP {}", localFile, pcPath, pcpUrl);
            }
            if (putLocal) {
                putLocal(buffer);
                stats.addLocal();
                LOG.info("finished put file {} to {} from local", localFile, pcPath.toString());
            }

        } catch (Exception e) {
            LOG.error("exception to put {} to {} ", localFile, pcPath, e);
            stats.addFail();
        }finally {
            if (latch != null) {
                latch.countDown();
            }
        }
    }
}
