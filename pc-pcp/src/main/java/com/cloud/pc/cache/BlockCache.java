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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockCache {
    private final long capacity;
    private long  size;
    private final IEvictionPolicy evictStrategy;
    private final Map<String, CacheNode> cache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

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
        this.cache = new HashMap<>();
    }

    public CacheNode getBlock(String blockPath) {
        if (blockPath == null) {
            return null;
        }

        readLock.lock();
        try {
            CacheNode node = cache.get(blockPath);
            if (node != null) {
                evictStrategy.access(node);

                return node;
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

    // 放入块数据
    public boolean putBlock(String blockPath, byte[] blockData) {
        if (blockData == null) {
            throw new IllegalArgumentException("Block data cannot be null");
        }
        if (blockPath == null) {
            throw new IllegalArgumentException("Block path cannot be null");
        }

        writeLock.lock();
        try {
            // replace old
            CacheNode existingNode = cache.get(blockPath);
            if (existingNode != null) {
                CacheNode newNode = new CacheNode(blockPath, blockData);
                evictStrategy.insert(newNode);
                cache.put(blockPath, newNode);
                size += newNode.blockData.length;
                evictStrategy.remove(existingNode);
                size -= existingNode.blockData.length;

                return true;
            }

            // if it's full, evict blocks
            while (size >= capacity) {
                CacheNode evictNode = evictStrategy.evict();
                if (evictNode != null) {
                    cache.remove(evictNode.blockPath);
                    size -= evictNode.blockData.length;
                }
            }

            // add new
            CacheNode newNode = new CacheNode(blockPath, blockData.clone());
            cache.put(blockPath, newNode);
            evictStrategy.insert(newNode);
            size += newNode.blockData.length;

            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean removeBlock(String blockPath) {
        if (blockPath == null) {
            return false;
        }
        writeLock.lock();
        try {
            CacheNode node = cache.remove(blockPath);
            if (node != null) {
                size -= node.blockData.length;
                evictStrategy.remove(node);
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            cache.clear();
            evictStrategy.clear();
            size = 0;
        } finally {
            writeLock.unlock();
        }
    }

    public Set<String> getCachedBlockPaths() {
        readLock.lock();
        try {
            return new HashSet<>(cache.keySet());
        } finally {
            readLock.unlock();
        }
    }

    public long size() {
        readLock.lock();
        try {
            return size;
        } finally {
            readLock.unlock();
        }
    }
}
