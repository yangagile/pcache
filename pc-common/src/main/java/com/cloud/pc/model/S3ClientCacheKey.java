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

import com.google.common.base.Objects;

public class S3ClientCacheKey {
    private String accessKey;

    private String secretAccessKey;

    private String sessionToken;

    public S3ClientCacheKey(String accessKey, String secretAccessKey, String sessionToken) {
        this.accessKey = accessKey;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        S3ClientCacheKey that = (S3ClientCacheKey) object;
        return Objects.equal(accessKey, that.accessKey) &&
                Objects.equal(secretAccessKey, that.secretAccessKey) &&
                Objects.equal(sessionToken, that.sessionToken);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accessKey, secretAccessKey, sessionToken);
    }
}
