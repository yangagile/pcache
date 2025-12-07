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
import com.cloud.pc.utils.*;
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
            Boolean leader = false;
            if (Envs.enableWrite || StringUtils.isBlank(Envs.existingPmsUrls)) {
                leader = true;
            }
            PmsInfo PmsInfo = new PmsInfo(Envs.httpHeader +
                    NetworkUtils.getLocalIpAddress(Envs.netWorkInterfaceName) + ":" + Envs.port + "/",
                    metaService.getVersion(), leader, new Date());
            pmsList.add(PmsInfo);
            if (StringUtils.isNotBlank(Envs.existingPmsUrls)) {
                PmsInfo existingInfo = new PmsInfo(Envs.existingPmsUrls, -1L);
                existingInfo.setHost(Envs.existingPmsUrls);
                PmsPulseInfo pulseInfo = new PmsPulseInfo();
                pulseInfo.setPmsList(pmsList);
                if(!sendPulseTo(existingInfo, pulseInfo)) {
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



    public void receivePulse(PmsPulseInfo pmsPulseInfo) {
        List<PmsInfo> pmsInfos = copyPmsList();
        for (int i = 0; i < pmsPulseInfo.getPmsList().size(); i++) {
            PmsInfo pmsInfo = pmsPulseInfo.getPmsList().get(i);
            int index = indexPms(pmsInfos, pmsInfo);
            // index = 0 is current node, ignore
            if (index < 0) {
                pmsInfos.add(pmsInfo);
            } else if (index > 0){
                pmsInfos.get(index).update(pmsInfo);
            }
        }
        pmsList = pmsInfos;

        if (pmsPulseInfo.getPmsList().get(0).getMetaVersion() > pmsInfos.get(0).getMetaVersion()) {
            syncMetaFrom(pmsPulseInfo.getPmsList().get(0));
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

    private boolean sendPulseTo(PmsInfo pmsInfo, PmsPulseInfo pulseInfo) {
        String url = pmsInfo.getHost() + "api/v1/pms/pms/pulse";
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

    @Scheduled(fixedRateString = "${pc.pms.pulse.interval.millis:10000}")
    public void sendPulse() {
        List<PmsInfo> pmsInfos = copyPmsList();
        pmsInfos.get(0).setMetaVersion(metaService.getVersion());
        pmsInfos.get(0).setUpdateTime(new Date());

        PmsPulseInfo pulseInfo = new PmsPulseInfo();
        pulseInfo.setPmsList(pmsInfos);

        for (int i = 1; i < pmsInfos.size(); i++) {
            sendPulseTo(pmsInfos.get(i), pulseInfo);
        }
    }

    public synchronized void enableLeader(Boolean enableLeader) {
        if (enableLeader == true) {
            if (pmsList.get(0).getLeader()) {
                LOG.info("{} already is leader", pmsList.get(0).getHost());
                return;
            }
            for (int i = 1; i < pmsList.size(); i++) {
                if (pmsList.get(i).getLeader()) {
                    throw new RuntimeException("leader existing " + pmsList.get(i).getHost());
                }
            }
        }
        pmsList.get(0).setLeader(enableLeader);
        pmsList.get(0).setUpdateTime(new Date());
    }

    public void checkLeader() {
        if (!pmsList.get(0).getLeader()) {
            throw new RuntimeException("can't write on NO leader node" + pmsList.get(0).getHost());
        }
    }
}
