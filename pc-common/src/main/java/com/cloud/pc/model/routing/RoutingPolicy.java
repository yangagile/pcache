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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutingPolicy {
    private RoutingType router;
    private List<Integer> bucketIds = new ArrayList<>();

    public RoutingPolicy() {}

    public RoutingPolicy(RoutingType router, List<Integer> bucketIds) {
        this.router = router;
        this.bucketIds = bucketIds;
    }

    public int route(String factor) {
        return router.route(bucketIds, factor);
    }

    public RoutingType getRouter() {
        return router;
    }

    public void setRouter(RoutingType router) {
        this.router = router;
    }

    public List<Integer> getBucketIds() {
        return bucketIds;
    }

    public void setBucketIds(List<Integer> bucketIds) {
        this.bucketIds = bucketIds;
    }
}
