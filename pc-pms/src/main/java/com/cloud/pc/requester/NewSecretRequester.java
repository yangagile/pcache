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

import com.cloud.pc.meta.Secret;
import com.cloud.pc.utils.SecretUtils;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;


@ApiModel(description = "New Secret")
public class NewSecretRequester {
    @ApiModelProperty(value = "AK(generate automatically if empty)", example = "", required = true)
    String accessKey;

    @ApiModelProperty(value = "", example = "")
    String iam;

    @ApiModelProperty(value = "mail", example = "", required = true)
    String mail;

    @ApiModelProperty(value = "phone", example = "", required = true)
    String phone;

    @ApiModelProperty(value = "description")
    String description;

    public Secret toSecret() {
        Secret secret = new Secret();
        secret.setAccessKey(accessKey);
        secret.setMail(mail);
        secret.setPhone(phone);
        secret.setIam(iam);
        secret.setDescription(description);
        secret.setSecretKey(SecretUtils.generatSecretKey());
        secret.setCreateTime(new Date());
        secret.setUpdateTime(secret.getCreateTime());
        secret.setAccessTime(secret.getCreateTime());
        return secret;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getIam() {
        return iam;
    }

    public void setIam(String iam) {
        this.iam = iam;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
