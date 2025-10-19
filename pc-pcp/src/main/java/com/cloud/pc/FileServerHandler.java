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

import com.cloud.pc.config.Envs;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.task.GetTask;
import com.cloud.pc.task.PutTask;
import com.cloud.pc.utils.*;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.concurrent.*;

import static com.cloud.pc.utils.HttpHelper.sendError;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class FileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(FileServerHandler.class);

    private static final ExecutorService fileExecutor =
            new ThreadPoolExecutor(Envs.corePoolSize, Envs.maximumPoolSize, Envs.keepAliveTime, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(Envs.linkedBlockingQueueSize),
                    new DefaultThreadFactory("File-Thread"));

    private void getFile(ChannelHandlerContext ctx,  FullHttpRequest request, String path, String uri) {
        GetTask getTask = new GetTask(ctx, request, path, uri);
        fileExecutor.submit(getTask);
    }

    private void putFile(ChannelHandlerContext ctx, FullHttpRequest request, String localFilePath, String fileKey) {
        byte[] fileContent = new byte[request.content().readableBytes()];
        request.content().readBytes(fileContent);
        long expectedLength = Integer.parseInt(request.headers().get("Content-Length"));
        if (fileContent.length != expectedLength) {
            LOG.error("expected length:{}, received length: {} for file {}",
                    expectedLength, fileContent.length, fileKey);
            throw new RuntimeException("invalid length");
        }

        String sts = request.headers().get("X-STS");
        StsInfo stsInfo = JsonUtils.fromJson(sts, StsInfo.class);
        String uploadId = request.headers().get("X-UPLOAD-ID");
        int partNumber = Integer.parseInt(request.headers().get("X-UPLOAD-NUMBER"));
        final S3Client s3Client = S3ClientCache.buildS3Client(stsInfo, false);
        PutTask putTask = new PutTask(ctx, fileContent, s3Client, stsInfo, localFilePath, uploadId, partNumber);
        fileExecutor.submit(putTask);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        LOG.info("new request from ip={} uri={}", ctx.channel().remoteAddress().toString(), request.uri());
        if (!request.decoderResult().isSuccess()) {
            LOG.error("[request]failed to decoder request！reason:{}", request.decoderResult().cause());
            sendError(ctx, BAD_REQUEST);
            return;
        }
        try {
            final String fileKey = request.uri();
            if (StringUtils.isBlank(fileKey)) {
                LOG.error("[request] uri={} is not allowed", fileKey);
                sendError(ctx, FORBIDDEN);
                return;
            }
            final String localFilePath = HttpHelper.sanitizeUri(fileKey);
            if (request.method() == GET) {
                getFile(ctx, request, localFilePath, fileKey);
            } else if (request.method() == POST) {
                putFile(ctx, request, localFilePath, fileKey);
            } else {
                LOG.error("[request]method{} is not allowed", request.method());
                sendError(ctx, METHOD_NOT_ALLOWED);
            }
        }catch (RejectedExecutionException e) {
            sendError(ctx, HttpResponseStatus.TOO_MANY_REQUESTS);
        } catch (Exception e) {
            LOG.error("exception for request {}", request, e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("exception caught from ip={}", ctx.channel().remoteAddress().toString(), cause);
        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
    }
}