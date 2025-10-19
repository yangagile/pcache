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

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.codehaus.jackson.annotate.JsonIgnore;

import java.util.Date;
@Getter
@Setter
@ToString
public class Vendor implements PcMeta {
    private static final String RANDOM_SALT = "2%YkW!@g52Gc19Ua";
    private Integer id;
    private String name;
    private String region;
    private String description;
    private String accessKey;
    private String accessSecret;
    private String s3Endpoint;
    private String endpoint;
    private String internalEndpoint;
    private String stsEndpoint;
    private String cdnEndpoint;

    private Date createTime;
    private Date updateTime;

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
        return name + region;
    }

    @JsonIgnore
    public int update(PcMeta other) {
        return 1;
    }
}
