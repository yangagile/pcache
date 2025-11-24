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

import com.cloud.pc.config.Envs;
import com.cloud.pc.model.PcPath;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.utils.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.cloud.pc.utils.HttpHelper.sendError;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

public class GetTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GetTask.class);

    private ChannelHandlerContext ctx;
    private FullHttpRequest request;
    private S3Client s3Client;
    private StsInfo stsInfo;
    private String localFile;
    private PcPath pcPath;
    private long size;
    private long blockSize;

    public GetTask(ChannelHandlerContext ctx, FullHttpRequest request, S3Client s3Client,
                   StsInfo stsInfo, String localFile, PcPath pcPath, long size, long blockSize) {
        this.ctx = ctx;
        this.request = request;
        this.s3Client = s3Client;
        this.stsInfo = stsInfo;
        this.localFile = localFile;
        this.pcPath = pcPath;
    }
    @Override
    public void run() {
        File file = new File(localFile);
        if (file.isDirectory()) {
            if (Envs.enableListDir) {
                if (localFile.endsWith("/")) {
                    HttpHelper.sendFileListing(ctx, file, localFile);
                } else {
                    HttpHelper.sendRedirect(ctx, localFile + '/');
                }
            } else {
                LOG.error("[request] list dir={} is not allowed", file);
                sendError(ctx, FORBIDDEN);
            }
        }
        HttpResponse response = new DefaultHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        if (!file.exists()) {
            getAndSend(response);
        } else {
            sendFromLocal();
        }
    }

    private void sendFromLocal() {
        LOG.info("[sendFromLocal] file={} ", localFile);
        try {
            byte[] fileData = Files.readAllBytes(Paths.get(localFile));

            ByteBuf buf = Unpooled.wrappedBuffer(fileData);
            FullHttpResponse respose = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    buf);
            HttpUtil.setContentLength(respose, fileData.length);
            respose.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            respose.headers().set("X-CACHE-HIT",1);
            respose.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

            ctx.writeAndFlush(respose);

        } catch (IOException e) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void getAndSend(HttpResponse response) {
        LOG.info("[getAndSend] localFile={} fullKey={}", stsInfo, localFile, pcPath);

        long pos = (pcPath.getNumber()-1)* blockSize;
        String range = String.format("bytes=%d-%d",pos, pos + size - 1);

        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, size);
        response.headers().set("X-CACHE-HIT",0);
        ctx.write(response);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(localFile);
        } catch (FileNotFoundException e) {
            LOG.error("[getAndSend] failed to open file {} for write", localFile, e);
            sendError(ctx, NOT_FOUND);
        }
        try {
            FileUtils.mkParentDir(Paths.get(localFile));
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(stsInfo.getBucketName())
                    .key(pcPath.getKey())
                    .range(range)
                    .build();

            ResponseInputStream<GetObjectResponse> res = s3Client.getObject(
                    getObjectRequest, ResponseTransformer.toInputStream());
            if (!S3Utils.isGetObjectSuccessful(res)) {
                LOG.error("[getAndSend] failed to get object！for invalid response {}", res);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
            byte[] read_buf = new byte[4096];
            int read_len;
            while ((read_len = res.read(read_buf)) > 0) {
                ctx.write(Unpooled.wrappedBuffer(read_buf, 0, read_len));
                fos.write(read_buf, 0, read_len);
            }
            ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            future.addListener(ChannelFutureListener.CLOSE);

        } catch (IOException e) {
            LOG.error("[getAndSend] failed to get object！", e);
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                LOG.info("exception to close file {}", localFile, e);
            }
        }
    }
}
