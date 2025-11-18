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

package com.cloud.pc.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/*
 *
 */
public class UrlProbe {
    private static final Logger LOG = LoggerFactory.getLogger(UrlProbe.class);
    public long probe_min_period_ms = ComUtils.getProps("pc.probe.min.period.ms",
            5*60*1000L, Long::valueOf);

    private volatile List<UrlStats> urlList = new ArrayList<>();
    private IUrlProbeFunction probeFunction;
    private final AtomicBoolean isRunning;
    private final AtomicLong lastProbeTime;

    public interface IUrlProbeFunction {
        List<String> getUrlList(String url);
    }

    class UrlStats {
        UrlStats(String url, long responseTime, boolean active) {
            this.url = url;
            this.responseTime = responseTime;
            this.active = new AtomicBoolean(active);
        }
        String url;
        long responseTime;
        AtomicBoolean active;

        public long getResponseTime() {
            return responseTime;
        }
    }

    public UrlProbe(String baseUrl, IUrlProbeFunction probeFunction) {
        List<String> urls = probeFunction.getUrlList(baseUrl);
        for (String url : urls) {
            urlList.add(new UrlStats(url, 0, true));
        }
        this.probeFunction = probeFunction;
        this.isRunning = new AtomicBoolean(false);
        this.lastProbeTime = new AtomicLong(0);
    }

    public String getUrl() {
        List<UrlStats> tmpList = urlList;
        for (UrlStats urlStats : tmpList) {
            if (urlStats.active.get()) {
                return urlStats.url;
            }
        }
        // if all are inactive, return the first
        for (UrlStats urlStats : tmpList) {
            return urlStats.url;
        }
        probe(false);
        return null;
    }

    public void reportFail(String url) {
        LOG.info("report fail url {}", url);
        boolean force = false;
        for (UrlStats urlStats : urlList) {
            if (url.startsWith(urlStats.url)) {
                if (urlStats.active.compareAndSet(true, false)) {
                    force = true;
                }
            }
        }
        probe(force);
    }

    public void probe(boolean force) {
        if (!force && (System.currentTimeMillis() - lastProbeTime.get() < probe_min_period_ms)) {
            return;
        }
        if (!isRunning.compareAndSet(false, true)) {
            LOG.info("test is already running");
            return;
        }
        Runnable runThread = () -> test();
        runThread.run();
    }

    private List<String> getUrlList(String url) {
        try {
            List<String> newUrlList = probeFunction.getUrlList(url);
            if (newUrlList != null && !newUrlList.isEmpty()) {
                return newUrlList;
            }
        } catch (Exception e) {
            LOG.error("exception to get URL list from {}", url, e);
        }
        return null;
    }

    private List<String> refreshUrlList() {
        List<String> newUrlList = null;
        for (UrlStats urlStats : urlList) {
            if (urlStats.active.get()) {
                newUrlList = getUrlList(urlStats.url);
                if (newUrlList != null) {
                    return newUrlList;
                }
            }
        }
        // try from the inactive URLs
        for (UrlStats urlStats : urlList) {
            newUrlList = getUrlList(urlStats.url);
            if (newUrlList != null) {
                return newUrlList;
            }
        }
        return newUrlList;
    }

    private void test() {
        try {
            List<String> newUrlList = refreshUrlList();
            if (newUrlList == null || newUrlList.isEmpty()) {
                LOG.error("failed to get new URL list!");
                return;
            }
            if (newUrlList.size() == 1) {
                return;
            }
            List<UrlStats> results = new ArrayList<>();
            for (String url : newUrlList) {
                long startTime = System.currentTimeMillis();
                try {
                    List<String> tmpList = probeFunction.getUrlList(url);
                    if (tmpList != null && !tmpList.isEmpty()) {
                        LOG.info("got one active url {}", url);
                        results.add(new UrlStats(url, System.currentTimeMillis() - startTime, true));
                    } else {
                        LOG.error("failed to get url list from url {}", url);
                    }
                } catch (Exception e) {
                    LOG.error("exception to probe url {}", url, e);
                }
            }
            if (!results.isEmpty()) {
                results.sort(Comparator.comparingLong(UrlStats::getResponseTime));
                urlList = results;
            }
        } catch (Exception e) {
            LOG.error("exception to probe URLs!", e);
        } finally {
            isRunning.set(false);
            lastProbeTime.set(System.currentTimeMillis());
        }
    }
}
