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

package com.cloud.pc.scanner.impl;

import com.cloud.pc.config.Envs;
import com.cloud.pc.scanner.DirectoryFilter;
import com.cloud.pc.scanner.DirectoryScanner;
import com.cloud.pc.scanner.FileStat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DirectoryScannerImpl implements Runnable, DirectoryScanner {
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryScannerImpl.class);
    private volatile DirectoryFilter lastFilter;
    @Override
    public void run() {
        try {
            DirectoryFilter filter = new DirectoryFilter(Envs.timeSpan, Envs.timeSpanDelete);
            Files.walkFileTree(Paths.get(Envs.dataDir), filter);
            LOG.info("scan {} done! {}", Envs.dataDir, filter);
            lastFilter = filter;
        } catch (IOException e) {
            LOG.error("failed to send pulse info with exception" , e);
        }
    }

    public FileStat getUsage() {
        if (lastFilter != null) {
            return lastFilter.getUsage();
        }
        return null;
    }
}
