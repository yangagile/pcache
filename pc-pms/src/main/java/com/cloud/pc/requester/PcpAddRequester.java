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

import com.cloud.pc.model.PcpInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;

@ApiModel(description = "Vendor信息")
public class PcpAddRequester {
    @ApiModelProperty(value = "访问地址", example = "host", required = true)
    String host;

    @ApiModelProperty(value = "容量（单位KB）", example = "100000", required = true)
    Long totalSize;

    public PcpInfo toPCPInfo() {
        PcpInfo pcpInfo = new PcpInfo();
        pcpInfo.setHost(host);
        pcpInfo.setTotalSize(totalSize);
        pcpInfo.setUsedSize(0L);
        pcpInfo.setCreateTime(new Date());
        pcpInfo.setUpdateTime(new Date());
        return pcpInfo;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }
}
