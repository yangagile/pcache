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

package com.cloud.pc.requester;

import com.cloud.pc.meta.PBucket;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "PBucket Information")
public class PBucketRequester {
    @ApiModelProperty(value = "bucket name", required = true)
    String name;

    @ApiModelProperty(value = "prefix", example = "region")
    String prefix;

    @ApiModelProperty(value = "feature flags", example = "")
    Integer featureFlags;

    @ApiModelProperty(value = "quota of Capacity(BYTE)", example = "-1")
    Long quotaCapacity;

    @ApiModelProperty(value = "quota of QPS(S)", example = "-1")
    Integer quotaQps;

    @ApiModelProperty(value = "quota of bandwidth(BYTE)", example = "-1")
    Integer quotaBandwidth;

    @ApiModelProperty(value = "TTL", example = "")
    String policyTtl;

    @ApiModelProperty(value = "permission", example = "")
    String policyPermission;


    @ApiModelProperty(value = "route policy", example = "", required = true)
    String policyRouting;

    @ApiModelProperty(value = "description", example = "", required = true)
    String description;

    public PBucket toPBucket() {
        PBucket pb = new PBucket();
        pb.setName(name);
        pb.setPrefix(prefix);
        pb.setFeatureFlags(featureFlags);
        pb.setQuotaCapacity(quotaCapacity);
        pb.setQuotaQps(quotaQps);
        pb.setQuotaBandwidth(quotaBandwidth);
        pb.setPolicyTtl(policyTtl);
        pb.setPolicyPermission(policyPermission);
        pb.setPolicyRouting(policyRouting);
        pb.setDescription(description);
        return pb;
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
}
