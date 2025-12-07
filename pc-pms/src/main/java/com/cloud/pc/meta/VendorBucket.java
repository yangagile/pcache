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

public class VendorBucket implements PcMeta {
    private Integer id;
    private String name;
    private String vendor;
    private String region;
    private String permission;
    private String endpoint;
    private String cdnEndpoint;
    private Long quotaCapacity = -1L;
    private Integer quotaQps = -1;
    private Integer quotaBandwidth = -1;
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
        return id.toString();
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

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getCdnEndpoint() {
        return cdnEndpoint;
    }

    public void setCdnEndpoint(String cdnEndpoint) {
        this.cdnEndpoint = cdnEndpoint;
    }

    public Long getQuotaCapacity() {
        return quotaCapacity;
    }

    public void setQuotaCapacity(Long quotaCapacity) {
        this.quotaCapacity = quotaCapacity;
    }

    public Integer getQuotaQps() {
        return quotaQps;
    }

    public void setQuotaQps(Integer quotaQps) {
        this.quotaQps = quotaQps;
    }

    public Integer getQuotaBandwidth() {
        return quotaBandwidth;
    }

    public void setQuotaBandwidth(Integer quotaBandwidth) {
        this.quotaBandwidth = quotaBandwidth;
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
