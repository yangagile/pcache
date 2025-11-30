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

import com.cloud.pc.meta.Vendor;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "Vendor Information")
public class VendorRequester {
    @ApiModelProperty(value = "vendor name", example = "name", required = true)
    String name;

    @ApiModelProperty(value = "region", example = "region", required = true)
    String region;

    @ApiModelProperty(value = "description", example = "description", required = true)
    String description;

    @ApiModelProperty(value = "AK", example = "", required = true)
    String accessKey;

    @ApiModelProperty(value = "SK", example = "", required = true)
    String accessSecret;

    @ApiModelProperty(value = "S3 endpoint", example = "")
    String s3Endpoint;

    @ApiModelProperty(value = "endpoint", example = "")
    String endpoint;

    @ApiModelProperty(value = "internal endpoint", example = "")
    String internalEndpoint;

    @ApiModelProperty(value = "STS endpoint", example = "")
    String stsEndpoint;

    @ApiModelProperty(value = "CDN endpoint", example = "")
    String cdnEndpoint;

    public Vendor toVendor() {
        Vendor vendor = new Vendor();
        vendor.setName(name);
        vendor.setRegion(region);
        vendor.setDescription(description);
        vendor.setAccessKey(accessKey);
        vendor.setAccessSecret(accessSecret);
        vendor.setS3Endpoint(s3Endpoint);
        vendor.setEndpoint(endpoint);
        vendor.setInternalEndpoint(internalEndpoint);
        vendor.setStsEndpoint(stsEndpoint);
        vendor.setCdnEndpoint(cdnEndpoint);
        return vendor;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getAccessSecret() {
        return accessSecret;
    }

    public void setAccessSecret(String accessSecret) {
        this.accessSecret = accessSecret;
    }

    public String getS3Endpoint() {
        return s3Endpoint;
    }

    public void setS3Endpoint(String s3Endpoint) {
        this.s3Endpoint = s3Endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getInternalEndpoint() {
        return internalEndpoint;
    }

    public void setInternalEndpoint(String internalEndpoint) {
        this.internalEndpoint = internalEndpoint;
    }

    public String getStsEndpoint() {
        return stsEndpoint;
    }

    public void setStsEndpoint(String stsEndpoint) {
        this.stsEndpoint = stsEndpoint;
    }

    public String getCdnEndpoint() {
        return cdnEndpoint;
    }

    public void setCdnEndpoint(String cdnEndpoint) {
        this.cdnEndpoint = cdnEndpoint;
    }
}
