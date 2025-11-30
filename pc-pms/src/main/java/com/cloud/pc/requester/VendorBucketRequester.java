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

import com.cloud.pc.meta.VendorBucket;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "VendorBucket信息")
public class VendorBucketRequester {
    @ApiModelProperty(value = "name of vendor bucket", example = "name", required = true)
    String name;

    @ApiModelProperty(value = "vendor", example = "", required = true)
    String vendor;

    @ApiModelProperty(value = "region", example = "", required = true)
    String region;

    @ApiModelProperty(value = "permission", example = "", required = true)
    String permission;

    @ApiModelProperty(value = "endpoint", example = "", required = true)
    String endpoint;

    @ApiModelProperty(value = "cdnEndpoint", example = "", required = true)
    String cdnEndpoint;

    @ApiModelProperty(value = "s3Endpoint", example = "")
    String s3Endpoint;

    @ApiModelProperty(value = "quota of Capacity(BYTE)", example = "-1")
    Long quotaCapacity;

    @ApiModelProperty(value = "quota of QPS(S)", example = "-1")
    Integer quotaQps;

    @ApiModelProperty(value = "quota of bandwidth(BYTE)", example = "-1")
    Integer quotaBandwidth;

    public VendorBucket toVendorBucket() {
        VendorBucket vb = new VendorBucket();
        vb.setName(name);
        vb.setVendor(vendor);
        vb.setRegion(region);
        vb.setPermission(permission);
        vb.setEndpoint(endpoint);
        vb.setCdnEndpoint(cdnEndpoint);
        vb.setQuotaCapacity(quotaCapacity);
        vb.setQuotaQps(quotaQps);
        vb.setQuotaBandwidth(quotaBandwidth);
        return vb;
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

    public String getS3Endpoint() {
        return s3Endpoint;
    }

    public void setS3Endpoint(String s3Endpoint) {
        this.s3Endpoint = s3Endpoint;
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
}
