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

package com.cloud.pc.service;

import com.cloud.pc.config.Envs;
import com.cloud.pc.model.PcpPulseInfo;
import com.cloud.pc.model.PmsInfo;
import com.cloud.pc.model.PmsPulseInfo;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.HttpUtils;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.NetUitls;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@EnableAsync
public class PmsService {
    private static final Logger LOG = LoggerFactory.getLogger(PmsService.class);
    volatile List<PmsInfo> pmsList = new ArrayList<>();

    @Autowired
    MetaService metaService;

    @Autowired
    PcpService pcpService;

    @Autowired
    private ApplicationContext context;

    @PostConstruct
    void init() {
        try {
            metaService.init();

            PmsInfo PmsInfo = new PmsInfo(Envs.httpHeader + NetUitls.getIp() + ":" + Envs.port + "/",
                    metaService.getVersion());
            pmsList.add(PmsInfo);
            if (StringUtils.isNotBlank(Envs.existingPmsUrls)) {
                PmsInfo existingInfo = new PmsInfo(Envs.existingPmsUrls, -1);
                existingInfo.setHost(Envs.existingPmsUrls);
                if(!sendPulseTo(existingInfo)) {
                    LOG.error("failed to get meta fomr existing pms url {}, exit application!", Envs.existingPmsUrls);
                    SpringApplication.exit(context, () -> 1);
                }
            }
        } catch (Exception e) {
            LOG.error("exception to init PMS", e);
            SpringApplication.exit(context, () -> 1);
        }
    }

    private static Map<String, String> getPmsHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8"); // 要求响应为 JSON
        headers.put("X-AK", Envs.ak);
        headers.put("X-TOKEN", Envs.sk);
        return headers;
    }

    private List<PmsInfo> copyPmsList() {
        Date now = new Date();
        List<PmsInfo> copyList = new ArrayList<>();
        for (int i = 0; i < pmsList.size(); i++) {
            if (i == 0 || now.getTime() - pmsList.get(i).getUpdateTime().getTime() < Envs.pmsLiveMaxTime * 1000) {
                copyList.add(pmsList.get(i).deepCopy());
            }
        }
        return copyList;
    }

    public List<PmsInfo> getPmsList() {
        return copyPmsList();
    }

    private int indexPms(List<PmsInfo> pmsInfos, PmsInfo pmsInfo) {
        for (int i = 0; i < pmsInfos.size(); i++) {
            if (pmsInfo.getHost().equals(pmsInfos.get(i).getHost())) {
                return i;
            }
        }
        return -1;
    }

    public void syncMetaFrom(PmsInfo pmsInfo) {
        String url = pmsInfo.getHost() + "api/v1/pms/meta";
        try {
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(url,
                    "GET", getPmsHeader(), null, null);
            if (response.getStatusCode() == 200) {
                metaService.syncMeta(response.getBody());
                LOG.info("load meta down from {}!", url);
            } else {
                LOG.error("failed to get meta from {}! error:{}", url, response.getStatusCode());
            }
        } catch (Exception e) {
            LOG.error("exception to get meta from {}", url, e);
        }
    }

    private boolean sendPulseTo(PmsInfo pmsInfo) {
        String url = pmsInfo.getHost() + "api/v1/pms/pms/pulse";
        PmsPulseInfo pulseInfo = new PmsPulseInfo();
        pmsList.get(0).setMetaVersion(metaService.getVersion());
        pulseInfo.setPmsList(pmsList);

        try {
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(url,
                    "POST", getPmsHeader(), null, JsonUtils.toJson(pulseInfo));
            if (response.getStatusCode() == 200) {
                LOG.info("pulse info:{}", pulseInfo);
                return true;
            } else {
                LOG.error("failed to send pulse info! error:{}", response.getStatusCode());
            }
        } catch (Exception e) {
            LOG.error("exception to send pulse to {}", url, e);
        }
        return false;
    }

    public void receivePulse(PmsPulseInfo pmsPulseInfo) {
        PmsInfo pmsInfo = pmsPulseInfo.getPmsList().get(0);
        boolean needUpdateMeta = false;
        List<PmsInfo> pmsInfos = copyPmsList();
        int i = indexPms(pmsInfos, pmsInfo);
        if (i < 0) {
            pmsInfos.add(pmsPulseInfo.getPmsList().get(0));
        } else {
            pmsInfos.get(i).setUpdateTime(new Date());
        }
        for (i = 1; i < pmsPulseInfo.getPmsList().size(); i++) {
            int index = indexPms(pmsInfos, pmsInfo);
            if (index < 0) {
                pmsInfos.add(pmsInfo);
            }
        }
        if (pmsInfo.getMetaVersion() > pmsInfos.get(0).getMetaVersion()) {
            needUpdateMeta = true;
        }
        pmsList = pmsInfos;

        if (needUpdateMeta) {
            syncMetaFrom(pmsInfo);
        }
    }

    @Async
    public void sendPcpPulseToOther(String host, PcpPulseInfo pulseInfo)  {
        try {
            String url = host + "api/v1/pms/pcp/pulse";
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(url,
                    "POST", getPmsHeader(), null, JsonUtils.toJson(pulseInfo));
            if (response.getStatusCode() == 200) {
                LOG.info("pulse info:{}", pulseInfo);
            } else {
                LOG.error("failed to send pulse info! error:{}", response.getStatusCode());
            }
        } catch (IOException e) {
            LOG.error("exception to send PCP pulse {} to {}", pulseInfo, host, e);
        }
    }

    public void pcpPulse(PcpPulseInfo pulseInfo) {
        pcpService.pulse(pulseInfo);
        // only transfer the pulse from PCP
        if (pulseInfo.getLevel() > 0) {
            for (int i = 1; i < pmsList.size(); i++) {
                pulseInfo.setLevel(pulseInfo.getLevel() - 1);
                sendPcpPulseToOther(pmsList.get(i).getHost(), pulseInfo);
            }
        }
    }

    @Scheduled(fixedRateString = "${pc.pms.pulse.interval.millis:10000}")
    public void sendPulse() {
        for (int i = 1; i < pmsList.size(); i++) {
            sendPulseTo(pmsList.get(i));
        }
    }
}
