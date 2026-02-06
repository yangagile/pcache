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
import com.cloud.pc.task.GetTask;
import com.cloud.pc.task.PutTask;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

import static com.cloud.pc.utils.HttpHelper.sendError;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class FileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(FileServerHandler.class);

    private static final ExecutorService fileExecutor =
            new ThreadPoolExecutor(Envs.corePoolSize, Envs.maximumPoolSize, Envs.keepAliveTime, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(Envs.linkedBlockingQueueSize),
                    new DefaultThreadFactory("File-Thread"));

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        LOG.debug("new request from ip={} uri={}", ctx.channel().remoteAddress().toString(), request.uri());
        if (!request.decoderResult().isSuccess()) {
            LOG.error("[request]failed to decoder request！reason:{}", request.decoderResult().cause());
            sendError(ctx, BAD_REQUEST);
            return;
        }
        try {
            // create task
            Runnable task;
            if (request.method() == GET) {
                task = new GetTask(ctx, request);
            } else if (request.method() == POST) {
                task = new PutTask(ctx, request);
            } else {
                LOG.error("[request]method{} is not allowed", request.method());
                return;
            }
            fileExecutor.submit(task);
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