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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class BlockCache {
    private final long capacity;
    private final AtomicLong size = new AtomicLong(0);
    private final IEvictionPolicy evictStrategy;
    private final ConcurrentHashMap<String, CacheNode> cache;
    private final ReentrantLock evictionLock = new ReentrantLock();
    private static volatile BlockCache instance;

    public static void init(long capacity, IEvictionPolicy strategy) {
        instance = new BlockCache(capacity, strategy);
    }

    public static BlockCache instance() {
        return instance;
    }

    private BlockCache(long capacity, IEvictionPolicy strategy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.evictStrategy = strategy;
        this.cache = new ConcurrentHashMap<>();
    }

    public CacheNode getBlock(String blockPath) {
        if (blockPath == null) {
            return null;
        }
        CacheNode node = cache.get(blockPath);
        if (node != null) {
            // ReentrantLock
            evictionLock.lock();
            try {
                evictStrategy.access(node);
            } finally {
                evictionLock.unlock();
            }
            return node;
        }
        return null;
    }

    // put block
    public boolean putBlock(String blockPath, byte[] blockData) {
        if (blockData == null || blockPath == null ){
            throw new IllegalArgumentException();
        }

        evictionLock.lock();
        try {
            //if it's full, evict blocks
            while (size.get() + blockData.length > capacity) {
                CacheNode evictNode = evictStrategy.evict();
                if (evictNode != null) {
                    cache.remove(evictNode.blockPath);
                    size.addAndGet(-evictNode.blockData.length);
                } else {
                    break;
                }
            }

            // add new
            CacheNode newNode = new CacheNode(blockPath, blockData.clone());
            CacheNode oldNode = cache.put(blockPath, newNode);

            if (oldNode != null) {
                // replace old
                evictStrategy.remove(oldNode);
                size.addAndGet(-oldNode.blockData.length);
            }

            evictStrategy.insert(newNode);
            size.addAndGet(blockData.length);
            return true;
        } finally {
            evictionLock.unlock();
        }
    }

    public boolean removeBlock(String blockPath) {
        if (blockPath == null) {
            return false;
        }
        CacheNode node = cache.remove(blockPath);
        size.addAndGet(-node.blockData.length);
        if (node != null) {
            evictionLock.lock();
            try {
                evictStrategy.remove(node);
            } finally {
                evictionLock.unlock();
            }
            return true;
        }
        return false;
    }

    public void clear() {
        evictionLock.lock();
        try {
            cache.clear();
            evictStrategy.clear();
            size.set(0);
        } finally {
            evictionLock.unlock();
        }
    }

    public Set<String> getCachedBlockPaths() {
        return new HashSet<>(cache.keySet());
    }

    public long size() {
        return size.get();
    }
}
