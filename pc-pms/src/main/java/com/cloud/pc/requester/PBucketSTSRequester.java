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

import com.cloud.pc.model.PcPermission;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.*;
import java.util.List;

@Setter
@Getter
@ToString
@ApiModel(description = "Vendor信息")
public class PBucketSTSRequester {
    @Size(min = 8, max = 256, message = "AK must be between 8 and 256 characters")
    @ApiModelProperty(value = "AK", example = "ak", required = true)
    String ak;

    @Size(min = 4, max = 128, message = "Name must be between 4 and 128 characters")
    @ApiModelProperty(value = "VB name", example = "name", required = true)
    String vbName;

    @Size(max = 1024, message = "Name must be between 4 and 1024 characters")
    @ApiModelProperty(value = "path", example = "", required = false)
    String path;

    @ApiModelProperty(value = "permissions", example = "GetObject", required = false)
    List<PcPermission> permissions;

    @Min(value = 900, message = "expiration must be at least 900")
    @Max(value = 7200, message = "expiration must be at most 7200")
    @ApiModelProperty(value = "expiration", example = "1800", required = true)
    Integer expirationInSeconds;
}
