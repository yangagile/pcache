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

package com.cloud.pc.entity;

import com.cloud.pc.model.routing.RoutingResult;

public class StsCacheValue {
    private RoutingResult routingResult;

    private long expireAt;

    public StsCacheValue(RoutingResult rr, long secondExpireAt) {
        this.routingResult = rr;
        long expireAt = secondExpireAt * 80 / 100;
        this.expireAt = System.currentTimeMillis() + (expireAt * 1000L);
    }

    public RoutingResult getVo() {
        return routingResult;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireAt;
    }
}
