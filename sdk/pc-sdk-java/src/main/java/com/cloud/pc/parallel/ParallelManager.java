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

package com.cloud.pc.parallel;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.*;

public class ParallelManager {
    private ExecutorService exeService;

    public ParallelManager() {
        initGetExecutorService();
    }
    public void initGetExecutorService() {
        if (exeService == null) {
            exeService = new ThreadPoolExecutor(8, 16, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(128), new DefaultThreadFactory("Parallel-Thread"));

        }
    }

    public void put(Runnable task) throws InterruptedException {
        exeService.submit(task);
    }

    public void close() {
        if (exeService != null) {
            exeService.shutdownNow();
        }
    }
}
