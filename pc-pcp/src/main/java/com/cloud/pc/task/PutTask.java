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

import com.cloud.pc.cache.BlockCache;
import com.cloud.pc.model.PcPath;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.HttpHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import static com.cloud.pc.utils.HttpHelper.sendError;

public class PutTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PutTask.class);
    private ChannelHandlerContext ctx;
    private byte[] content;
    private S3Client s3Client;
    private StsInfo stsInfo;
    private PcPath pcPath;
    private String localFile;
    private String uploadId;
    private Map<String, String> userMetas;

    public PutTask(ChannelHandlerContext ctx, byte[] content, S3Client s3Client, StsInfo stsInfo,
                   String localFile, PcPath pcPath, String uploadId, Map<String, String> userMetas) {
        this.ctx = ctx;
        this.content = content;
        this.s3Client = s3Client;
        this.stsInfo = stsInfo;
        this.localFile = localFile;
        this.pcPath = pcPath;
        this.uploadId = uploadId;
        this.userMetas = userMetas;
    }

    @Override
    public void run() {
        int retryCount = 3;
        while (retryCount > 0) {
            try {
                String eTag;
                if (pcPath.isSingleFile()) {
                    eTag = uploadFullFile();
                    LOG.info("successfully to put key:{} size:{} retryCount:{} return etag:{}",
                            pcPath.getKey(), content.length, retryCount, eTag);
                } else {
                    eTag = uploadPart();
                    LOG.info("successfully to put key:{} number:{}/{} size{} uploadId:{} retryCount:{} return etag:{}",
                            pcPath.getKey(), pcPath.getNumber(), pcPath.getTotalNumber(), content.length,
                            uploadId, retryCount, eTag);
                }

                // 响应客户端 (切换回EventLoop线程)
                ctx.executor().execute(() -> {
                    HttpHelper.sendResponse(ctx, HttpResponseStatus.OK, eTag);
                });
                addCacheAndDisk();
                return;
            } catch (Exception e) {
                LOG.error("exception to put {} size{} retryCount:{}", pcPath, content.length, retryCount, e);
                retryCount--;
            }
        }
        ctx.executor().execute(() -> {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        });
    }

    private String uploadPart() {
        UploadPartRequest uploadRequest = UploadPartRequest.builder()
                .bucket(stsInfo.getBucketName())
                .key(pcPath.getKey())
                .uploadId(uploadId)
                .partNumber((int)pcPath.getNumber()+1)
                .build();

        UploadPartResponse response = s3Client.uploadPart(
                uploadRequest,
                RequestBody.fromBytes(content)
        );
        return response.eTag();
    }

    private String uploadFullFile() {
        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        PutObjectRequest putObjectRequest = builder.bucket(stsInfo.getBucketName())
                .contentLength((long)(content.length))
                .metadata(userMetas)
                .key(pcPath.getKey()).build();

        RequestBody requestBody = RequestBody.fromBytes(content);
        PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
        return response.eTag();
    }

    private void addCacheAndDisk() {
        try {
            // add to memory cache
            BlockCache.instance().putBlock(pcPath.toString(), content);

            // save to disk
            if (!FileUtils.mkParentDir(Paths.get(localFile))) {
                LOG.error("failed to create parent dir of {}", localFile);
                throw new RuntimeException("failed to create parent dir");
            }
            File outputFile = new File(localFile);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(content);
            }
        } catch (IOException e ) {
            LOG.error("exception to save to local! localFilePath:{} size:{}",
                    localFile, content.length, e);
        }
    }
}
