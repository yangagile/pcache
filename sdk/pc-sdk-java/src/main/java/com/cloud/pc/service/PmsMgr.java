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

package com.cloud.pc.service;

import com.cloud.pc.entity.BucketInfo;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.model.PmsInfo;
import com.cloud.pc.model.routing.RoutingResult;

import java.util.List;

public interface PmsMgr {
    public List<PmsInfo> getPmsApi(String url);

    BucketInfo getBucketInfoApi(String bucket);

    RoutingResult getVirtualBucketSTSApi(String bucket, String storedPath, List<PcPermission> permissions, int expirationInSeconds);

    public String getPcp(String key);
}

