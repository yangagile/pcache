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

package com.cloud.pc.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BucketInfo {
    private String name;

    private String prefix;

    private int featureFlags;

    private int quotaQps;

    private int quotaConcurrency;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getFeatureFlags() {
        return featureFlags;
    }

    public void setFeatureFlags(int featureFlags) {
        this.featureFlags = featureFlags;
    }

    public int getQuotaQps() {
        return quotaQps;
    }

    public void setQuotaQps(int quotaQps) {
        this.quotaQps = quotaQps;
    }

    public int getQuotaConcurrency() {
        return quotaConcurrency;
    }

    public void setQuotaConcurrency(int quotaConcurrency) {
        this.quotaConcurrency = quotaConcurrency;
    }
}



