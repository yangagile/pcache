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

@Service
@EnableAsync
public class PmsService {
    private static final Logger LOG = LoggerFactory.getLogger(PmsService.class);
    List<PmsInfo> pmsList = new ArrayList<>();

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

    public List<PmsInfo> getPmsList() {
        return pmsList;
    }

    private int indexPms(PmsInfo pmsInfo) {
        for (int i = 0; i < pmsList.size(); i++) {
            if (pmsInfo.getHost().equals(pmsList.get(i).getHost())) {
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
        int i = indexPms(pmsInfo);
        if (i < 0) {
            pmsList.add(pmsPulseInfo.getPmsList().get(0));
        } else {
            pmsList.get(i).setUpdateTime(new Date());
        }
        if (pmsInfo.getMetaVersion() > pmsList.get(0).getMetaVersion()) {
            syncMetaFrom(pmsInfo);
        }
        for (i = 1; i<  pmsPulseInfo.getPmsList().size(); i++) {
            int index = indexPms(pmsInfo);
            if (index < 0) {
                pmsList.add(pmsInfo);
            }
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
        for (int i =1; i < pmsList.size(); i++) {
            sendPcpPulseToOther(pmsList.get(i).getHost(), pulseInfo);
        }
    }

    @Scheduled(fixedRateString = "${pc.pms.pulse.interval.millis:10000}")
    public void sendPulse() {
        Date now = new Date();
        pmsList.removeIf(pms -> now.getTime() - pms.getUpdateTime().getTime() > Envs.pmsLiveMaxTime * 1000);
        for (PmsInfo pms : pmsList) {
            sendPulseTo(pms);
        }
    }
}
