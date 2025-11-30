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

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import java.util.Date;

public class Secret implements PcMeta {
    private Integer id;
    private String accessKey;
    private String secretKey;
    private String mail;
    private String phone;
    private String description;
    private String iam;
    private Date createTime;
    private Date updateTime;
    private Date accessTime;

    public Secret() {
        createTime = new Date();
        updateTime = createTime;
        accessTime = createTime;
    }

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
        return accessKey;
    }

    @JsonIgnore
    public int update(PcMeta other) {
        Secret o = (Secret) other;
        if (StringUtils.isNotBlank(o.getSecretKey())) {
            secretKey = o.getSecretKey();
        }
        if (StringUtils.isNotBlank(o.getMail())) {
            mail = o.getMail();
        }
        if (StringUtils.isNotBlank(o.getPhone())) {
            phone = o.getPhone();
        }
        if (StringUtils.isNotBlank(o.getDescription())) {
            description = o.getDescription();
        }
        if (StringUtils.isNotBlank(o.getIam())) {
            iam = o.getIam();
        }
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

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
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

    public String getIam() {
        return iam;
    }

    public void setIam(String iam) {
        this.iam = iam;
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
