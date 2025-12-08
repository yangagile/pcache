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
    private final int capacity;
    private final IEvictionPolicy evictStrategy;
    private final Map<String, CacheNode> cache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    private static volatile BlockCache instance;

    public static void init(int capacity, IEvictionPolicy strategy) {
        if (instance == null) {
            synchronized (BlockCache.class) {
                if (instance == null) {
                    instance = new BlockCache(capacity, strategy);
                }
            }
        }
    }

    public static BlockCache instance() {
        return instance;
    }

    private BlockCache(int capacity, IEvictionPolicy strategy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.evictStrategy = strategy;
        this.cache = new HashMap<>(capacity);
    }

    public byte[] getBlock(String blockPath) {
        if (blockPath == null) {
            return null;
        }

        readLock.lock();
        try {
            CacheNode node = cache.get(blockPath);
            if (node != null) {
                evictStrategy.access(node);

                return node.blockData;
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
                evictStrategy.remove(existingNode);
                return true;
            }

            // if it's full, evict block
            if (cache.size() >= capacity) {
                String evictPath = evictStrategy.evict();
                if (evictPath != null) {
                    cache.remove(evictPath);
                }
            }

            // add new
            CacheNode newNode = new CacheNode(blockPath, blockData.clone());
            cache.put(blockPath, newNode);
            evictStrategy.insert(newNode);

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

    public int size() {
        readLock.lock();
        try {
            return cache.size();
        } finally {
            readLock.unlock();
        }
    }
}
