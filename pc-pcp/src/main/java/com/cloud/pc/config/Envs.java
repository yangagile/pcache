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

package com.cloud.pc.config;

import com.cloud.pc.utils.ComUtils;

public class Envs {
    // http and service
    public static String pmsUrl = ComUtils.getProps("pcp.pms.url",
            "http://127.0.0.1:8081/", String::valueOf);
    public static Integer port = ComUtils.getProps("server.port",
            8081, Integer::valueOf);
    public static String httpHeader = ComUtils.getProps("pms.http.header",
            "http://", String::valueOf);
    public static String netWorkInterfaceName = ComUtils.getProps("pcp.network.interface.name",
            "", String::valueOf);

    // auth
    public static String ak = ComUtils.getProps("pcp.ak",
            "pcp-admin", String::valueOf);
    public static String sk = ComUtils.getProps("pcp.sk",
            "5Nlx6ToTemI4gl5xvfr9ikGh5/Ou2vygvtdsgYYCESc=", String::valueOf);

    // data
    public static String dataDir = ComUtils.getProps("pcp.data.dir",
            "/var/data", String::valueOf);
    public static int defaultBlockSize = ComUtils.getProps("pcp.block.size",
            5 * 1024 * 1024, Integer::valueOf);
    public static Long availableSize = ComUtils.getProps("pcp.available.size",
            10*1024*1024*1024L, Long::valueOf);
    public static Long timeSpan = ComUtils.getProps("pcp.data.time.span",
            60*1000L, Long::valueOf);
    public static Long timeSpanDelete = ComUtils.getProps("pcp.data.time.span.delete",
            2*30*24*3600*1000L, Long::valueOf);

    // thread pool
    public static Integer corePoolSize = ComUtils.getProps("pcp.thread.pool.size",
            16, Integer::valueOf);
    public static Integer maximumPoolSize = ComUtils.getProps("pcp.thread.max.pool.size",
            32, Integer::valueOf);
    public static Integer linkedBlockingQueueSize = ComUtils.getProps("pcp.thread.queue.size",
            1024, Integer::valueOf);
    public static Long keepAliveTime = ComUtils.getProps("pcp.thread.keep.alive.time",
            60L, Long::valueOf);

    // block memory cache
    public static Integer BlockCacheSize = ComUtils.getProps("pcp.block.cache.size",
            512, Integer::valueOf);

    // log
    public static String logDir = ComUtils.getProps("pcp.log.dir",
            "./logs", String::valueOf);
}
