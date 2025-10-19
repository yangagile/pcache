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

package com.cloud.pc.service;

import com.cloud.pc.model.PmsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PmsProbe implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PmsProbe.class);

    class PmsUrlStats {
        PmsUrlStats(String url, long responseTime, boolean active, String msg) {
            this.url = url;
            this.responseTime = responseTime;
            this.active = active;
            this.msg = msg;
        }
        String url;
        long responseTime;
        boolean active;
        String msg;

        public long getResponseTime() {
            return responseTime;
        }
    }

    volatile List<PmsUrlStats> pmsUrls = new ArrayList<>();
    PmsMgr pmsMgr;
    public PmsProbe(String pmsUrl, PmsMgr pmsMgr) {
        pmsUrls.add(new PmsUrlStats(pmsUrl, 0, true, "ok: init"));
        this.pmsMgr = pmsMgr;
    }

    public String getPmsUrl() {
        for (PmsUrlStats pmsUrlStats : pmsUrls) {
            if (pmsUrlStats.active) {
                return pmsUrlStats.url;
            }
        }
        return null;
    }

    @Override
    public void run() {
        try {
            List<PmsInfo> pmsInfos = pmsMgr.getPmsApi(getPmsUrl());
            if (pmsInfos == null || pmsInfos.size() < 1) {
                return;
            }
            List<PmsUrlStats> results = new ArrayList<>();
            for (PmsInfo pms : pmsInfos) {
                long startTime = System.currentTimeMillis();
                try {
                    pmsMgr.getPmsApi(getPmsUrl());;
                    results.add(new PmsUrlStats(pms.getHost(), System.currentTimeMillis() - startTime,
                            true, "ok: "));
                } catch (Exception e) {
                    results.add(new PmsUrlStats(pms.getHost(), System.currentTimeMillis() - startTime,
                            false, "error: " + e.getMessage()));
                }
            }
            results.sort(Comparator.comparingLong(PmsUrlStats::getResponseTime));
            pmsUrls = results;
        } catch (Exception e) {
            LOG.error("exception to probe PMS info!", e);
        }
    }
}
