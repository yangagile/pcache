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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
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
}
