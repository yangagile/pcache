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

package com.cloud.pc.chash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class ConsistentHash {
    private final int baseVirtualNodeCount = 150;
    private final SortedMap<Long, String> hashRing = new TreeMap<>();
    private final Map<String, List<Long>> nodeToHashes = new ConcurrentHashMap<>();
    private final HashFunction hashFunction = new MD5Hash();

    public void addNode(HashValue node) {
        if (nodeToHashes.containsKey(node.key())) return;

        int virtualNodeCount = (int) (baseVirtualNodeCount*(1+node.getPriority()));
        List<Long> virtualHashes = new ArrayList<>(virtualNodeCount);
        for (int i = 0; i < virtualNodeCount; i++) {
            String virtualNode = node + "#" + i;
            long hash = hashFunction.hash(virtualNode);
            virtualHashes.add(hash);
            hashRing.put(hash, node.key());
        }
        nodeToHashes.put(node.key(), virtualHashes);
    }

    public void removeNode(HashValue node) {
        List<Long> virtualHashes = nodeToHashes.remove(node.key());
        if (virtualHashes == null) return; // 节点不存在
        for (Long hash : virtualHashes) {
            hashRing.remove(hash);
        }
    }

    public String getNode(String key) {
        if (hashRing.isEmpty()) return null;
        long hash = hashFunction.hash(key);
        SortedMap<Long, String> tailMap = hashRing.tailMap(hash);
        Long targetHash = tailMap.isEmpty() ? hashRing.firstKey() : tailMap.firstKey();
        return hashRing.get(targetHash);
    }
}
