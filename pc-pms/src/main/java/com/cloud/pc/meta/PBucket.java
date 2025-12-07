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

package com.cloud.pc.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

public class PBucket implements PcMeta {
    private Integer id;
    private String name;
    private String prefix = "";
    private Integer featureFlags = 0;
    private Long quotaCapacity = -1L;
    private Integer quotaBandwidth = -1;
    private Integer quotaQps = -1;
    private String policyTtl = "";
    private String policyPermission = "";
    private String policyRouting = "";
    private String description = "";
    private Date createTime = new Date();
    private Date updateTime = new Date();
    private Date accessTime = new Date();

    @JsonIgnore
    public long getLastUpdateTime() {
        if (updateTime != null) {
            return updateTime.getTime();
        } else {
            return 0;
        }
    }

    @JsonIgnore
    public String getKey() {
        return name;
    }

    @JsonIgnore
    public int update(PcMeta other) {
        return 1;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

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

    public Integer getFeatureFlags() {
        return featureFlags;
    }

    public void setFeatureFlags(Integer featureFlags) {
        this.featureFlags = featureFlags;
    }

    public Long getQuotaCapacity() {
        return quotaCapacity;
    }

    public void setQuotaCapacity(Long quotaCapacity) {
        this.quotaCapacity = quotaCapacity;
    }

    public Integer getQuotaBandwidth() {
        return quotaBandwidth;
    }

    public void setQuotaBandwidth(Integer quotaBandwidth) {
        this.quotaBandwidth = quotaBandwidth;
    }

    public Integer getQuotaQps() {
        return quotaQps;
    }

    public void setQuotaQps(Integer quotaQps) {
        this.quotaQps = quotaQps;
    }

    public String getPolicyTtl() {
        return policyTtl;
    }

    public void setPolicyTtl(String policyTtl) {
        this.policyTtl = policyTtl;
    }

    public String getPolicyPermission() {
        return policyPermission;
    }

    public void setPolicyPermission(String policyPermission) {
        this.policyPermission = policyPermission;
    }

    public String getPolicyRouting() {
        return policyRouting;
    }

    public void setPolicyRouting(String policyRouting) {
        this.policyRouting = policyRouting;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Date getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(Date accessTime) {
        this.accessTime = accessTime;
    }
}
