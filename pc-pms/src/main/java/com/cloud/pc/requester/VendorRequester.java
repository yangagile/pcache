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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
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
}
