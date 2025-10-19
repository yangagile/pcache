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

import com.cloud.pc.chash.ConsistentHash;
import com.cloud.pc.chash.HashValue;
import com.cloud.pc.config.Envs;
import com.cloud.pc.chash.PcpHashInfo;
import com.cloud.pc.model.PcpInfo;
import com.cloud.pc.model.PcpPulseInfo;
import com.cloud.pc.utils.OpsTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableScheduling
public class PcpService {
    private static final Logger LOG = LoggerFactory.getLogger(PcpService.class);

    long firstSize = 0;

    Map<String, PcpInfo> pcpMap = new ConcurrentHashMap<>();

    ConsistentHash pcpHash = new ConsistentHash();

    public List<PcpInfo> list() {
        return new ArrayList<>(pcpMap.values());
    }

    public synchronized void add(PcpInfo pcpInfo) {
        if (firstSize == 0L) {
            firstSize = pcpInfo.getTotalSize();
        }
        pcpInfo.setPriority(pcpInfo.getTotalSize()/firstSize + pcpInfo.getAdjust());
        pcpHash.addNode(pcpInfo);
        pcpMap.put(pcpInfo.getHost(), pcpInfo);
    }

    public synchronized int remove(PcpInfo pcpInfo) {
        LOG.info("{} remove PCP {}", OpsTrace.get(), pcpInfo);
        if (pcpMap.containsKey(pcpInfo.getHost())) {
            pcpMap.remove(pcpInfo.getHost());
            pcpHash.removeNode(pcpInfo);
            return 1;
        }
        return 0;
    }

    public int remove(String host) {
        PcpInfo pcpInfo = pcpMap.get(host);
        if (pcpInfo != null) {
            return remove(pcpInfo);
        }
        return 0;
    }

    public int pulse(PcpPulseInfo pulseInfo) {
        LOG.info("{} pulse info {}", OpsTrace.get(), pulseInfo);
        PcpInfo pcpInfo;
        if (pcpMap.containsKey(pulseInfo.getHost())) {
            pcpInfo = pcpMap.get(pulseInfo.getHost());
            pcpInfo.setTotalSize(pulseInfo.getTotalSize());
            pcpInfo.setUsedSize(pulseInfo.getUsedSize());
            pcpInfo.setFileCount(pulseInfo.getFileCount());
            pcpInfo.setUpdateTime(new Date());
        } else {
            pcpInfo = new PcpInfo(pulseInfo);
            add(pcpInfo);
        }
        return 1;
    }

    public PcpHashInfo getHashList(String checksum) {
        List<HashValue> pcpHashList = new ArrayList<>(pcpMap.values());
        PcpHashInfo pcpHashInfo = new PcpHashInfo();
        String newChecksum = PcpHashInfo.generateChecksum(pcpHashList);
        if (!newChecksum.equals(checksum)) {
            List<HashValue> values = new ArrayList<>(pcpHashList.size());
            for (HashValue value : pcpHashList) {
                values.add(new HashValue(value.getHost(), value.getPriority()));
            }
            pcpHashInfo.setPcpList(values);
        }
        pcpHashInfo.setChecksum(newChecksum);
        return pcpHashInfo;
    }

    @Scheduled(fixedRateString = "${pcp.check.interval.millis:300000}")
    void pcpCheck() {
        Date now = new Date();
        for (PcpInfo pcpInfo : pcpMap.values()) {
            if (now.getTime() - pcpInfo.getUpdateTime().getTime() > Envs.pcpLiveMaxTime * 1000) {
                remove(pcpInfo);
            }
        }
    }
}
