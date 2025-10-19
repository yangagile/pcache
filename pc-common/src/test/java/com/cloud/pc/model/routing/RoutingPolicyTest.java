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

import com.cloud.pc.utils.JsonUtils;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RoutingPolicyTest extends TestCase {
    @Test
    public void test_RoutingPolicy_json_save_load() throws Exception{
        List<Integer> bucketIds = new ArrayList<>();
        bucketIds.add(1);
        RoutingPolicy routingPolicy = new RoutingPolicy( new OneRouter(), bucketIds);
        String content = JsonUtils.toJson(routingPolicy);

        RoutingPolicy routingPolicyNew = JsonUtils.fromJson(content, RoutingPolicy.class);
        assertEquals(routingPolicyNew.getRouter().getClass(), routingPolicy.getRouter().getClass());
        assertEquals(routingPolicyNew.getBucketIds().get(0), routingPolicy.getBucketIds().get(0));
    }
}