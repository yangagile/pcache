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
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.utils.JsonUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

@Getter
@Setter
@ToString
public class PutTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PutTask.class);

    private final CountDownLatch latch;
    private S3Client s3Client;
    private int partNumber;
    private long  pos;
    private long  size;
    private String fileUrl;
    private String localFile;
    private String uploadId;
    private StsInfo stsInfo;
    private String eTag;
    private Stats stats;

    public PutTask(CountDownLatch latch, S3Client s3Client, int number, long pos, long size, StsInfo stsInfo) {
        this.latch = latch;
        this.s3Client = s3Client;
        this.partNumber = number;
        this.pos = pos;
        this.size = size;
        this.stsInfo = stsInfo;
        this.stats = new Stats();
    }

    private void putToPcp(byte[] buffer, long fileLength) throws Exception{
        HttpURLConnection connection;
        URL url = new URL(fileUrl);
        connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-STS", JsonUtils.toJson(stsInfo));
        connection.setRequestProperty("X-UPLOAD-ID", uploadId);
        connection.setRequestProperty("X-UPLOAD-NUMBER", Long.toString(partNumber));
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("Content-Length", String.valueOf(fileLength));

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
        UploadPartRequest uploadRequest = UploadPartRequest.builder()
                .bucket(stsInfo.getName())
                .key(stsInfo.getKey())
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        UploadPartResponse response = s3Client.uploadPart(
                uploadRequest,
                RequestBody.fromBytes(buffer)
        );
        eTag = response.eTag();
    }

    @Override
    public void run() {
        boolean putLocal = true;
        try {
            File file = new File(this.localFile);
            byte[] buffer;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                buffer = new byte[(int) size];
                raf.seek(pos);
                raf.read(buffer);
            }

            if (fileUrl.startsWith("http")) {
                try {
                    putToPcp(buffer, file.length());
                    putLocal = false;
                    stats.addPcp();
                } catch (Exception e) {
                    LOG.error("exception to put {} to {} with PCP ", this.localFile, fileUrl, e);
                }
            }
            if (putLocal) {
                putLocal(buffer);
                stats.addLocal();
            }
            LOG.info("finished put file {} pos:{} size:{} to {}", localFile, pos, size, fileUrl);
        } catch (Exception e) {
            LOG.error("exception to put {} to {} ", localFile, fileUrl, e);
            stats.addFail();
        }finally {
            latch.countDown();
        }
    }
}
