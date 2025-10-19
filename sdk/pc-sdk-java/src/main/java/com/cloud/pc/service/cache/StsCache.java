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

package com.cloud.pc.service.cache;

import com.cloud.pc.entity.StsCacheKey;
import com.cloud.pc.entity.StsCacheValue;
import com.cloud.pc.utils.ComUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;

public class StsCache {
    // 是否启用 sts 缓存
    public static int cacheStsSeconds = ComUtils.getProps(
            "pc.cache.sts.seconds", 1200, Integer::valueOf);

    private final static Cache<StsCacheKey, StsCacheValue> stsCache = buildStsCache();

    public final static Cache<StsCacheKey, StsCacheValue> getCache() {
        return stsCache;
    }

    private static Cache<StsCacheKey, StsCacheValue> buildStsCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(cacheStsSeconds, TimeUnit.SECONDS)
                .build();
    }
}
