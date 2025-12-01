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

public class LRUEvictionPolicy implements IEvictionPolicy {
    private CacheNode lruHead, lruTail;

    public LRUEvictionPolicy () {
        // 初始化LRU链表
        lruHead = new CacheNode(null, new byte[0]);
        lruTail = new CacheNode(null, new byte[0]);
        lruHead.next = lruTail;
        lruTail.pre = lruHead;
    }
    public void access(CacheNode node) {
        remove(node);
        insert(node);
    }

    public void insert(CacheNode node) {
        node.pre = lruHead;
        node.next = lruHead.next;
        lruHead.next.pre = node;
        lruHead.next = node;
    }
    public void remove(CacheNode node) {
        node.pre.next = node.next;
        node.next.pre = node.pre;
    }
    public String evict() {
        CacheNode lruNode = lruTail.pre;
        if (lruNode != lruHead) {
            String blockIdToEvict = lruNode.blockPath;
            remove(lruNode);
            return blockIdToEvict;
        }
        return null;
    }
    public void clear() {
        lruHead.next = lruTail;
        lruTail.pre = lruHead;
    }
}

