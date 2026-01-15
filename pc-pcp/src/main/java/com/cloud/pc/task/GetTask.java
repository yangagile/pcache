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
import com.cloud.pc.cache.CacheNode;
import com.cloud.pc.model.PcPath;
import com.cloud.pc.model.PcpBlockStatus;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.stats.BlockCounter;
import com.cloud.pc.utils.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
    private long offset;

    public GetTask(ChannelHandlerContext ctx, FullHttpRequest request, S3Client s3Client, StsInfo stsInfo,
                   String localFile, PcPath pcPath, long size, long blockSize, long offset) {
        this.ctx = ctx;
        this.request = request;
        this.s3Client = s3Client;
        this.stsInfo = stsInfo;
        this.localFile = localFile;
        this.pcPath = pcPath;
        this.blockSize = blockSize;
        this.size = size;
        this.offset = offset;
    }
    @Override
    public void run() {
        HttpResponse response = new DefaultHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        // try from memory cache
        CacheNode block = BlockCache.instance().getBlock(pcPath.toString());
        if (block != null) {
            sendFromBuffer(block.blockData, PcpBlockStatus.HIT_MEMORY.getValue());
            BlockCounter.instance().hit(PcpBlockStatus.HIT_MEMORY);
            return;
        }

        // try from local disk
        File file = new File(localFile);
        if (file.exists()) {
            byte[] blockData = readFromLocal();
            if (blockData != null) {
                sendFromBuffer(blockData, PcpBlockStatus.HIT_DISK.getValue());
                BlockCounter.instance().hit(PcpBlockStatus.HIT_DISK);

                // add to memory cache
                BlockCache.instance().putBlock(pcPath.toString(), blockData);

                return;
            }
        }

        // download from remote and send
        byte[] blockData = downloadBlock();
        if (blockData != null) {
            sendFromBuffer(blockData, PcpBlockStatus.HIT_REMOTE.getValue());

            // add to memory cache
            BlockCache.instance().putBlock(pcPath.toString(), blockData);

            // save to local
            saveToLocal(blockData);
            BlockCounter.instance().hit(PcpBlockStatus.HIT_REMOTE);
            return;
        }

        // fail
        sendError(ctx, NOT_FOUND);
    }

    private void sendFromBuffer(byte[] blockData, int hitType) {
        LOG.debug("[sendFromBuffer] block={} size={} hitTpye={}", pcPath, blockData.length,hitType);

        final ByteBuf buf;
        final int contentLength;

        if (size == 0 || (offset == 0 && size == blockData.length)) {
            buf = Unpooled.wrappedBuffer(blockData);
            contentLength = blockData.length;
        } else {
            contentLength = (int)Math.min(size, blockData.length-offset);
            buf = Unpooled.wrappedBuffer(blockData, (int) offset, contentLength);
        }

        FullHttpResponse respose = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                buf);
        HttpUtil.setContentLength(respose, contentLength);
        respose.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        respose.headers().set("X-CACHE-HIT", hitType);
        respose.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.writeAndFlush(respose);
    }

    private byte[] readFromLocal() {
        LOG.debug("[readFromLocal] block={} file={}", pcPath, localFile);
        try {
            byte[] fileData = Files.readAllBytes(Paths.get(localFile));
            if (size !=0 && fileData.length != size) {
                LOG.error("[readFromLocal] failed to read block {} from local {} read size {} of {}",
                        pcPath, localFile, fileData.length, size);
                return null;
            }
            return fileData;
        } catch (IOException e) {
            LOG.error("[readFromLocal] exception to read block {} from local {}", pcPath, localFile, e);
        }
        return null;
    }

    private void saveToLocal(byte[] blockData) {
        LOG.debug("[saveToLocal] block={} file={} ", pcPath, localFile);
        FileUtils.mkParentDir(Paths.get(localFile));
        try (FileOutputStream fos = new FileOutputStream(localFile)) {
            fos.write(blockData);
        } catch (IOException e) {
            LOG.error("[saveToLocal] exception to save block {} to local file {}",
                    pcPath, localFile, e);
        }
    }

    private byte[] downloadBlock() {
        LOG.debug("[downloadBlock] block={}", pcPath);

        GetObjectRequest getObjectRequest;
        if (blockSize == 0) {
            getObjectRequest = GetObjectRequest.builder()
                    .bucket(stsInfo.getBucketName())
                    .key(pcPath.getKey())
                    .build();
        } else {
            long pos = (pcPath.getNumber() - 1) * blockSize;
            String range = String.format("bytes=%d-%d", pos, pos + blockSize - 1);
            getObjectRequest = GetObjectRequest.builder()
                    .bucket(stsInfo.getBucketName())
                    .key(pcPath.getKey())
                    .range(range)
                    .build();
        }
        ResponseInputStream<GetObjectResponse> res = s3Client.getObject(
                getObjectRequest, ResponseTransformer.toInputStream());
        if (!S3Utils.isGetObjectSuccessful(res)) {
            LOG.error("[downloadBlock] failed to download block {}！for invalid response {}", pcPath, res);
            return null;
        }
        try {
            GetObjectResponse response = res.response();
            long contentLength = response.contentLength();
            byte[] data = new byte[(int) contentLength];

            int bytesRead = res.read(data);
            if (bytesRead != contentLength) {
                LOG.error("[downloadBlock] failed to download block {}, bytesRead={} contentLength={}",
                        pcPath, bytesRead, contentLength);
                return null;
            }
            return data;

        } catch (IOException e) {
            LOG.error("[downloadBlock] exception to download block {}！", pcPath, e);
        }
        return null;
    }
}
