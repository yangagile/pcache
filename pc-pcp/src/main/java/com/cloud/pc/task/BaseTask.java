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

import com.cloud.pc.model.PcPath;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.utils.HttpHelper;
import com.cloud.pc.utils.JsonUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.commons.lang3.StringUtils;

public class BaseTask implements Runnable {
    protected ChannelHandlerContext ctx;
    protected String method;
    protected StsInfo stsInfo;
    protected PcPath pcPath;
    protected String localFile;

    public BaseTask(ChannelHandlerContext ctx, FullHttpRequest request) {
        this.ctx = ctx;

        // method
        method = request.method().name();

        // parse URI
        final String fileKey = request.uri();
        if (StringUtils.isBlank(fileKey)) {
            throw new RuntimeException("invalid URI");
        }
        pcPath = new PcPath(fileKey);
        localFile = HttpHelper.sanitizeUri(fileKey);

        // STS
        String sts = request.headers().get("X-STS");
        if (StringUtils.isNotBlank(sts)) {
            stsInfo = JsonUtils.fromJson(sts, StsInfo.class);
        }
    }

    public void run() {};
}
