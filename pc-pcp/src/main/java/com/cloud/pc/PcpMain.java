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

import com.cloud.pc.cache.BlockCache;
import com.cloud.pc.cache.LRUEvictionPolicy;
import com.cloud.pc.config.Envs;
import com.cloud.pc.scanner.impl.DirectoryScannerImpl;
import com.cloud.pc.pulse.PulseTask;
import com.cloud.pc.stats.BlockCounter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PcpMain {

    public static void main(String[] args) {
        System.setProperty("LOG_HOME", Envs.logDir);
        System.setProperty("APP_NAME", "pcp");

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(10*1024*1024));
                            ch.pipeline().addLast(new FileServerHandler());
                        }
                    });

            ChannelFuture f = serverBootstrap.bind(Envs.port).sync();
            System.out.println("\n========================================");
            System.out.printf("🚀 PCP is running at port: %d \n", Envs.port);
            System.out.println("========================================");

            // init block Cache
            BlockCache.init(Envs.BlockCacheSize, new LRUEvictionPolicy());
            BlockCounter.instance().reset();

            // directory scanner
            DirectoryScannerImpl dataScanner = new DirectoryScannerImpl();
            scheduler.scheduleAtFixedRate(dataScanner, 0, 60, TimeUnit.SECONDS);

            // pulse
            scheduler.scheduleAtFixedRate(new PulseTask(dataScanner), 0, 60, TimeUnit.SECONDS);

            f.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            scheduler.shutdown();
        }
    }
}