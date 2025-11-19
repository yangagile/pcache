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
    // storage method and location of metadata.
    public static String dataLoader = ComUtils.getProps("pms.meta.loader",
            "file-loader", String::valueOf);

    public static String fileLoaderPath = ComUtils.getProps("pms.data.loader.file.path",
            "./meta/", String::valueOf);

    // The list of existing PMS addresses， seperated by ','. this value is empty for the first PMS.
    public static String existingPmsUrls = ComUtils.getProps("pms.existing.url",
            "", String::valueOf);

    // http
    public static Integer port = ComUtils.getProps("server.port", 8080, Integer::valueOf);
    public static String httpHeader = ComUtils.getProps("pms.http.header",
            "http://", String::valueOf);

    // auth
    public static Boolean enableToken = ComUtils.getProps("pms.enable.token",
            false, Boolean::valueOf);
    public static String ak = ComUtils.getProps("pms.ak",
            "", String::valueOf);
    public static String sk = ComUtils.getProps("pms.sk",
            "", String::valueOf);

    // STS
    public static Integer minStsDurationSec = ComUtils.getProps("pms.sts.duration.min.sec",
            900, Integer::valueOf);
    public static Integer defaultStsDurationSec = ComUtils.getProps("pms.sts.duration.default.sec",
            10800, Integer::valueOf);
    public static Integer maxStsDurationSec = ComUtils.getProps("pms.sts.duration.max.sec",
            43200, Integer::valueOf);

    // log
    public static String logDir = ComUtils.getProps("pms.log.dir",
            "./logs", String::valueOf);

    // PCP
    public static Long pcpLiveMaxTime = ComUtils.getProps("pms.pcp.max.live.time.sec",
            120L, Long::valueOf);

    public static Long pmsLiveMaxTime = ComUtils.getProps("pms.max.live.time.sec",
            120L, Long::valueOf);

    public static Boolean enableWrite = ComUtils.getProps("pms.enable.write",
            false, Boolean::valueOf);
}
