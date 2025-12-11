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

import org.junit.Assert;
import org.junit.Test;


public class BlockCacheTest {
    static void testCache(BlockCache cache) {
        // 添加3个块
        long dataSize = 0;
        cache.putBlock("block1", new byte[]{1, 2, 3});
        cache.putBlock("block2", new byte[]{4, 5, 6});
        cache.putBlock("block3", new byte[]{7, 8, 9});
        dataSize  = 9;

        // 访问块1，使其成为最近使用的
        cache.getBlock("block2");
        cache.getBlock("block2");
        cache.getBlock("block1");
        cache.getBlock("block3");

        // 添加第4个块，应该淘汰块2（最久未使用）
        cache.putBlock("block4", new byte[]{10, 11, 12});

        System.out.println("缓存中的块: " + cache.getCachedBlockPaths());
    }

    @Test
    public void test_BlockCache() throws Exception {
        System.out.println("-- LRU test --");
        BlockCache.init(9, new LRUEvictionPolicy());
        testCache(BlockCache.instance());
        // "block2" is the Least Recently Used one
        Assert.assertNull(BlockCache.instance().getBlock("block2"));

        System.out.println("--  LFU test-- ");
        BlockCache.init(9, new LFUEvictionPolicy());
        testCache(BlockCache.instance());
        // frequent of "block2" is 2, the "block1" should be evicted
        Assert.assertNull(BlockCache.instance().getBlock("block1"));
    }
}