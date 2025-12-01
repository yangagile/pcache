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

public class CacheNode {
    final String blockPath;
    final byte[] blockData;
    final long timestamp;
    int freq;
    CacheNode pre, next; // 用于LRU链表

    CacheNode(String blockPath, byte[] blockData) {
        this.blockPath = blockPath;
        this.blockData = blockData;
        this.timestamp = System.currentTimeMillis();
        this.freq = 0;
    }
}
