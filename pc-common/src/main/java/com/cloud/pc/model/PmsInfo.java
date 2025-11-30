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

package com.cloud.pc.model;

import java.util.Date;

public class PmsInfo {
    private String  host;
    private Long    metaVersion;
    private Boolean leader;
    private Date    updateTime;

    public PmsInfo() {
        this("", 0L, false, new Date());
    }

    public PmsInfo(String host, Long version) {
        this(host, version, false, new Date());
    }

    public PmsInfo(String host, long version, Boolean leader, Date updateTime) {
        this.host = host;
        this.metaVersion = version;
        this.leader = leader;
        this.updateTime = updateTime;
    }

    public PmsInfo deepCopy() {
        return new PmsInfo(this.host, this.metaVersion, this.leader, this.updateTime);
    }

    public void update(PmsInfo other) {
        if (other.updateTime.after(this.updateTime)) {
            this.metaVersion = other.metaVersion;
            this.leader = other.leader;
            this.updateTime = other.updateTime;
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Long getMetaVersion() {
        return metaVersion;
    }

    public void setMetaVersion(Long metaVersion) {
        this.metaVersion = metaVersion;
    }

    public Boolean getLeader() {
        return leader;
    }

    public void setLeader(Boolean leader) {
        this.leader = leader;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
