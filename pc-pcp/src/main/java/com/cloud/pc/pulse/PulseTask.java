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

package com.cloud.pc.pulse;

import com.cloud.pc.cache.BlockCache;
import com.cloud.pc.config.Envs;
import com.cloud.pc.model.PmsInfo;
import com.cloud.pc.scanner.DirectoryScanner;
import com.cloud.pc.model.PcpPulseInfo;
import com.cloud.pc.scanner.FileStat;
import com.cloud.pc.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PulseTask implements Runnable, UrlProbe.IUrlProbeFunction{
    private static final Logger LOG = LoggerFactory.getLogger(PulseTask.class);
    private DirectoryScanner directoryScanner;
    private UrlProbe urlProbe;

    public PulseTask(DirectoryScanner directoryScanner) {
        this.directoryScanner = directoryScanner;
        this.urlProbe = new UrlProbe(Envs.pmsUrl, this);
    }

    private static Map<String, String> getPmsHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8"); // 要求响应为 JSON
        headers.put("X-AK", Envs.ak);
        headers.put("X-TOKEN", SecretUtils.generateToken(Envs.ak, Envs.sk, 300*1000, null));
        return headers;
    }

    @Override
    public void run() {
        String url = "";
        try {
            FileStat stat = directoryScanner.getUsage();
            if (stat == null) {
                LOG.info("PCP is scanning");
                return;
            }
            url = FileUtils.mergePath(urlProbe.getUrl(), "api/v1/pms/pcp/pulse");
            PcpPulseInfo pulseInfo = new PcpPulseInfo();
            pulseInfo.setHost(Envs.httpHeader + NetworkUtils.getLocalIpAddress(Envs.netWorkInterfaceName)
                    + ":" + Envs.port + "/");
            pulseInfo.setTotalSize(Envs.availableSize);
            pulseInfo.setUsedSize(stat.getSize());
            pulseInfo.setFileCount(stat.getCount());
            pulseInfo.setLevel(1);
            String info = JsonUtils.toJson(pulseInfo);
            LOG.info("PCP pulse info:{} memoryCache:{}/{}", info,
                    BlockCache.instance().size(), Envs.BlockCacheSize);
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(url, "POST", getPmsHeader(),
                    null, info);
            if (response.getStatusCode() != 200) {
                LOG.error("failed to send pulse info! error:{}" , response.getStatusCode());
            }
        } catch (IOException e) {
            urlProbe.reportFail(url);
            LOG.error("failed to send pulse info with exception" , e);
        }
    }

    public List<PmsInfo> getPmsApi(String url) {
        StringBuilder path = new StringBuilder(FileUtils.mergePath(url,"/api/v1/pms/list"));
        try {
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(path.toString(),
                    "GET", getPmsHeader(), null, null);
            if (response.getStatusCode() == 200) {

                List<PmsInfo> pmsInfos = JsonUtils.parseList(response.getBody(), PmsInfo.class);
                LOG.info("got pms list:{} ", pmsInfos);
                return pmsInfos;
            } else {
                LOG.error("failed to get pms list! error:{}", response.getStatusCode());
            }
        } catch (IOException e) {
            LOG.error("exception to get pms list!", e);
        }
        return null;
    }

    public List<String> getUrlList(String url) {
        List<PmsInfo> pmsInfos = getPmsApi(url);

        if (pmsInfos == null || pmsInfos.isEmpty()) {
            return Collections.emptyList();
        }
        return pmsInfos.stream()
                .map(PmsInfo::getHost)
                .collect(Collectors.toList());
    }
}
