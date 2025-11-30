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

package com.cloud.pc.model.routing;

import com.cloud.pc.model.StsInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutingResult {
    private RoutingType router;
    private List<StsInfo> stsInfos = new ArrayList<>();

    @JsonIgnore
    public StsInfo getSTS() {
        if (!stsInfos.isEmpty()) {
            return stsInfos.get(0);
        }
        return null;
    }

    public void setRouter(RoutingType router) {
        this.router = router;
    }

    public RoutingType getRouter() {
        return router;
    }

    public List<StsInfo> getStsInfos() {
        return stsInfos;
    }

    public void setStsInfos(List<StsInfo> stsInfos) {
        this.stsInfos = stsInfos;
    }
}
