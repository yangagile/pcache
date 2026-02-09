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
import com.cloud.pc.model.CacheLayer;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.HttpHelper;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.S3ClientCache;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
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

public class PutTask extends BaseTask {
    private static final Logger LOG = LoggerFactory.getLogger(PutTask.class);

    public byte[] blockData;
    public Map<String, String> userMetas;
    public String uploadId;
    public CacheLayer cacheLayer;

    public PutTask(ChannelHandlerContext ctx, FullHttpRequest request) {
        super(ctx,request);

        blockData = new byte[request.content().readableBytes()];
        request.content().readBytes(blockData);
        long expectedLength = Integer.parseInt(request.headers().get("Content-Length"));
        if (blockData.length != expectedLength) {
            throw new RuntimeException("invalid content length");
        }
        String strUserMeta = request.headers().get("X-USER-META");
        if (StringUtils.isNotBlank(strUserMeta)) {
            userMetas = JsonUtils.fromJson(strUserMeta, Map.class);
        }
        uploadId = request.headers().get("X-UPLOAD-ID");
        String strWriteLayer = request.headers().get("X-WRITE-LAYER");
        if (StringUtils.isNotBlank(strWriteLayer)) {
            cacheLayer = new CacheLayer(Integer.parseInt(strWriteLayer));
        } else {
            cacheLayer = new CacheLayer(CacheLayer.ALL);
        }
    }

    @Override
    public void run() {
        int retryCount = 3;
        while (retryCount > 0) {
            try {
                BlockCache.instance().putBlock(pcPath.toString(), blockData);
                if (cacheLayer.maxLayer() == CacheLayer.MEMORY) {
                    ctx.executor().execute(() -> {
                        HttpHelper.sendResponse(ctx, HttpResponseStatus.OK, "memory");
                    });
                }

                // save to disk
                saveToDisk(localFile);
                if (cacheLayer.maxLayer() == CacheLayer.DISK) {
                    ctx.executor().execute(() -> {
                        HttpHelper.sendResponse(ctx, HttpResponseStatus.OK, "disk");
                    });
                }

                String eTag;
                if (pcPath.isSingleFile()) {
                    eTag = uploadFullFile();
                    LOG.debug("successfully to put key:{} size:{} retryCount:{} return etag:{}",
                            pcPath.getKey(), blockData.length, retryCount, eTag);
                } else {
                    eTag = uploadPart();
                    LOG.debug("successfully to put key:{} number:{}/{} size{} uploadId:{} retryCount:{} return etag:{}",
                            pcPath.getKey(), pcPath.getNumber(), pcPath.getTotalNumber(),
                            blockData.length, uploadId, retryCount, eTag);
                }

                // response to client and back to EventLoop thread
                if (cacheLayer.maxLayer() == CacheLayer.REMOTE) {
                    ctx.executor().execute(() -> {
                        HttpHelper.sendResponse(ctx, HttpResponseStatus.OK, eTag);
                    });
                }
                return;
            } catch (Exception e) {
                LOG.error("exception to put {} size{} retryCount:{}",
                        pcPath, blockData.length, retryCount, e);
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

        S3Client s3Client = S3ClientCache.buildS3Client(stsInfo, false);
        UploadPartResponse response = s3Client.uploadPart(
                uploadRequest,
                RequestBody.fromBytes(blockData)
        );
        return response.eTag();
    }

    private String uploadFullFile() {
        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        PutObjectRequest putObjectRequest = builder.bucket(stsInfo.getBucketName())
                .contentLength((long)(blockData.length))
                .metadata(userMetas)
                .key(pcPath.getKey()).build();

        S3Client s3Client = S3ClientCache.buildS3Client(stsInfo, false);
        RequestBody requestBody = RequestBody.fromBytes(blockData);
            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
        return response.eTag();
    }

    private void saveToDisk(String filePath) {
        try {
            // save to disk
            FileUtils.mkParentDir(Paths.get(filePath));
            File outputFile = new File(filePath);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(blockData);
            }
        } catch (IOException e ) {
            LOG.error("exception to save to local! localFilePath:{} size:{}", filePath, blockData.length, e);
        }
    }
}
