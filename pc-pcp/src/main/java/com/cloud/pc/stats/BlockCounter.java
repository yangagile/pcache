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

package com.cloud.pc.stats;

import com.cloud.pc.model.PcpBlockStatus;

import java.util.concurrent.atomic.AtomicLong;

public class BlockCounter {
    private static volatile BlockCounter instance = new BlockCounter();

    private AtomicLong total = new AtomicLong(0);
    private AtomicLong error = new AtomicLong(0);
    private AtomicLong hitRemote = new AtomicLong(0);
    private AtomicLong hitDisk = new AtomicLong(0);
    private AtomicLong hitMemory = new AtomicLong(0);

    public static BlockCounter instance() {
        return instance;
    }

    public void hit(PcpBlockStatus status) {
        total.incrementAndGet();
        switch (status) {
            case ERROR:
                error.incrementAndGet();
                break;
            case HIT_REMOTE:
                hitRemote.incrementAndGet();
                break;
            case HIT_DISK:
                hitDisk.incrementAndGet();
                break;
            case HIT_MEMORY:
                hitMemory.incrementAndGet();
                break;
        }
    }

    public String toString() {
        return String.format("CacheStats: total:%d error:%d hit_remote:%d hit_disk:%d hit_memory:%d",
                total.get(), error.get(), hitRemote.get(), hitDisk.get(), hitMemory.get());
    }

    public void reset() {
        total = new AtomicLong(0);
        error = new AtomicLong(0);
        hitRemote = new AtomicLong(0);
        hitDisk = new AtomicLong(0);
        hitMemory = new AtomicLong(0);
    }
}
