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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Setter
@Getter
@ToString
@ApiModel(description = "New Secret")
public class NewTokenRequester {
    @ApiModelProperty(value = "accessKey", example = "", required = true)
    String accessKey;

    @ApiModelProperty(value = "secretKey", example = "", required = true)
    String secretKey;

    @ApiModelProperty(value = "expirationMs", example = "300000")
    Long expirationMs;

    @ApiModelProperty(value = "phone", example = "", required = true)
    Map<String, Object> claims;

    @ApiModelProperty(value = "description")
    String description;
}
