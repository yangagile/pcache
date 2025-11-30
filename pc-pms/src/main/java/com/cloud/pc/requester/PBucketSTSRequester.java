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

import javax.validation.constraints.*;
import java.util.List;

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

    public @Size(min = 8, max = 256, message = "AK must be between 8 and 256 characters") String getAk() {
        return ak;
    }

    public void setAk(@Size(min = 8, max = 256, message = "AK must be between 8 and 256 characters") String ak) {
        this.ak = ak;
    }

    public @Size(min = 4, max = 128, message = "Name must be between 4 and 128 characters") String getVbName() {
        return vbName;
    }

    public void setVbName(@Size(min = 4, max = 128, message = "Name must be between 4 and 128 characters") String vbName) {
        this.vbName = vbName;
    }

    public @Size(max = 1024, message = "Name must be between 4 and 1024 characters") String getPath() {
        return path;
    }

    public void setPath(@Size(max = 1024, message = "Name must be between 4 and 1024 characters") String path) {
        this.path = path;
    }

    public List<PcPermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<PcPermission> permissions) {
        this.permissions = permissions;
    }

    public @Min(value = 900, message = "expiration must be at least 900") @Max(value = 7200, message = "expiration must be at most 7200") Integer getExpirationInSeconds() {
        return expirationInSeconds;
    }

    public void setExpirationInSeconds(@Min(value = 900, message = "expiration must be at least 900") @Max(value = 7200, message = "expiration must be at most 7200") Integer expirationInSeconds) {
        this.expirationInSeconds = expirationInSeconds;
    }
}
