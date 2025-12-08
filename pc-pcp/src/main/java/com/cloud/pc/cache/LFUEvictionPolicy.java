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

package com.cloud.pc.cache;

import java.util.HashMap;
import java.util.Map;

public class LFUEvictionPolicy implements IEvictionPolicy {
    private Map<Integer, NodeList> freqs;
    private int minFreq;
    private final Object lock = new Object();

    public LFUEvictionPolicy() {
        freqs = new HashMap<>();
        minFreq = 0;
    }
    public void access(CacheNode node) {
        synchronized (lock) {
            remove(node);
            node.freq++;
            insert(node);
        }
    }
    public void insert(CacheNode node) {
        synchronized (lock) {
            freqs.putIfAbsent(node.freq, new NodeList());
            freqs.get(node.freq).add(node);
            if (minFreq > node.freq) {
                minFreq = node.freq;
            }
        }
    }
    public void remove(CacheNode node) {
        synchronized (lock) {
            NodeList nodeList = freqs.get(node.freq);
            nodeList.remove(node);
            if (nodeList.isEmpty()) {
                freqs.remove(node.freq);
                if (minFreq == node.freq) {
                    minFreq++;
                }
            }
        }
    }

    public String evict() {
        synchronized (lock) {
            NodeList nodeList = freqs.get(minFreq);
            while (nodeList == null && nodeList.isEmpty()) {
                minFreq++;
                nodeList = freqs.get(minFreq);
            }
            CacheNode node = nodeList.tail.pre;
            nodeList.remove(node);
            if (nodeList.isEmpty()) {
                freqs.remove(minFreq);
                minFreq++;
            }
            return node.blockPath;
        }
    }
    public void clear() {
        synchronized (lock) {
            freqs.clear();
            minFreq = 0;
        }
    }

    class NodeList {
        CacheNode head;
        CacheNode tail;
        public NodeList() {
            head = new CacheNode(null, new byte[0]);
            tail = new CacheNode(null, new byte[0]);
            head.next = tail;
            tail.pre = head;
        }
        public void remove(CacheNode node) {
            node.next.pre = node.pre;
            node.pre.next = node.next;
        }
        public void add(CacheNode node) {
            node.pre = head;
            node.next = head.next;
            head.next.pre = node;
            head.next = node;
        }
        public boolean isEmpty() {
            return head.next == tail;
        }
    }

}