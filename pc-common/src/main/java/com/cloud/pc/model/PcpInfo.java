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

import com.cloud.pc.chash.HashValue;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
public class PcpInfo extends HashValue {
    Integer id;
    Long totalSize;
    Long usedSize;
    Long fileCount;
    Date createTime;
    Date updateTime;
    Float adjust;

    public PcpInfo() {
        super();
        this.totalSize = 0L;
        this.usedSize = 0L;
        this.adjust = 0.0f;
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    public PcpInfo(PcpPulseInfo pulseInfo) {
        super(pulseInfo.getHost());
        this.totalSize = pulseInfo.getTotalSize();
        this.usedSize = pulseInfo.getUsedSize();
        this.adjust = pulseInfo.getAdjust();
        this.updateTime = new Date();
    }
}
