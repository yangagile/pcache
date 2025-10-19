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

package com.cloud.pc.task;

import com.cloud.pc.model.StsInfo;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.HttpHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;

import static com.cloud.pc.utils.HttpHelper.sendError;

public class PutTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PutTask.class);
    ChannelHandlerContext ctx;
    byte[] content;
    S3Client s3Client;
    StsInfo stsInfo;
    String localFilePath;
    String uploadId;
    int partNumber;

    public PutTask(ChannelHandlerContext ctx, byte[] content, S3Client s3Client,
                   StsInfo stsInfo, String localFilePath, String uploadId, int partNumber) {
        this.ctx = ctx;
        this.content = content;
        this.s3Client = s3Client;
        this.stsInfo = stsInfo;
        this.localFilePath = localFilePath;
        this.uploadId = uploadId;
        this.partNumber = partNumber;
    }

    @Override
    public void run() {
        int retryCount = 3;
        while (retryCount > 0) {
            try {
                final String eTag = uploadPart(s3Client, stsInfo.getName(), stsInfo.getKey(), uploadId,
                        partNumber, content);
                LOG.info("successfully to put uploadId:{} key:{} number:{} size{} retryCount:{} return etag:{}",
                        uploadId, stsInfo.getKey(), partNumber, content.length, retryCount, eTag);

                // 响应客户端 (切换回EventLoop线程)
                ctx.executor().execute(() -> {
                    HttpHelper.sendResponse(ctx, HttpResponseStatus.OK, eTag);
                });
                saveToDisk(content, localFilePath); // 实际存储
                return;
            } catch (Exception e) {
                LOG.error("exception to put uploadId:{} key:{} number:{} size{} retryCount:{}",
                        uploadId, stsInfo.getKey(), partNumber, content.length, retryCount, e);
                retryCount--;
            }
        }
        ctx.executor().execute(() -> {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        });
    }

    private static String uploadPart(S3Client s3Client,
                                     String bucketName,
                                     String objectKey,
                                     String uploadId,
                                     int partNumber,
                                     byte[] partData) {
        UploadPartRequest uploadRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        UploadPartResponse response = s3Client.uploadPart(
                uploadRequest,
                RequestBody.fromBytes(partData)
        );
        return response.eTag();
    }

    private void saveToDisk(byte[] buffer, String localFilePath) {
        try {
            FileUtils.mkParentDir(Paths.get(localFilePath));
            File outputFile = new File(localFilePath);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(buffer);
            }
        } catch (IOException e ) {
            LOG.error("exception to save to local! localFilePath:{} size:{}",
                    localFilePath, buffer.length, e);
        }
    }
}
