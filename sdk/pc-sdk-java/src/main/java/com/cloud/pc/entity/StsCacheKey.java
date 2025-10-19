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

import com.cloud.pc.model.PcPermission;
import com.google.common.base.Objects;

import java.util.List;


public class StsCacheKey {
    private String bucket;
    private String storedPath;
    private List<PcPermission> permissions;

    public StsCacheKey(String bucket, String storedPath, List<PcPermission> permissions) {
        this.bucket = bucket;
        this.storedPath = storedPath;
        this.permissions = permissions;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        StsCacheKey that = (StsCacheKey) object;
        return Objects.equal(bucket, that.bucket)
                && Objects.equal(storedPath, that.storedPath)
                && listEquals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(bucket, storedPath, permissions);
    }

    public static boolean listEquals(List<PcPermission> left, List<PcPermission> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (PcPermission p : left) {
            if (!right.contains(p)) {
                return false;
            }
        }
        return true;
    }
}
