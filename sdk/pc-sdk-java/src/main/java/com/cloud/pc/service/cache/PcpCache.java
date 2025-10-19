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

package com.cloud.pc.service.cache;

import com.cloud.pc.chash.ConsistentHash;
import com.cloud.pc.chash.HashValue;
import com.cloud.pc.chash.PcpHashInfo;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.cloud.pc.utils.ComUtils;

public class PcpCache {

    public int pcpCacheDurationMs = ComUtils.getProps(
            "pc.pcp.cache.duration.ms", 300*1000, Integer::valueOf);

    private ConsistentHash pcpHash = new ConsistentHash();

    private String checksum = "";

    private Map<String ,HashValue> pcpMap = new ConcurrentHashMap<>();

    private long updateTime = -1L;

    public String getChecksum() {
        return checksum;
    }

    public void updateCache(PcpHashInfo newPcpHashInfo) {
        Map<String ,HashValue> tmpMap = new ConcurrentHashMap<>();
        for (HashValue node : newPcpHashInfo.getPcpList()) {
            HashValue lastNode = pcpMap.get(node.key());
            if (lastNode == null) {
                pcpHash.addNode(node);
            } else {
                if (node.getPriority() != lastNode.getPriority()) {
                    pcpHash.removeNode(node);
                    pcpHash.addNode(node);
                }
            }
            tmpMap.put(node.key(), node);
            pcpMap.remove(node.key());
        }
        for (HashValue node : pcpMap.values()) {
            pcpHash.removeNode(node);
        }
        updateTime = System.currentTimeMillis();
        pcpMap = tmpMap;
    }

    public String get(String key) {
        if (System.currentTimeMillis()-updateTime > pcpCacheDurationMs) {
            return null;
        }
        return pcpHash.getNode(key);
    }
}
