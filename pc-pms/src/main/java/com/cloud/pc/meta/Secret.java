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

import com.cloud.pc.requester.NewSecretRequester;
import com.cloud.pc.utils.SecretUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnore;

import java.util.Date;

@Getter
@Setter
@ToString
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
}
