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

package com.cloud.pc.chash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public  class MD5Hash implements HashFunction {
    private final MessageDigest md5;

    public MD5Hash() {
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
    }

    @Override
    public long hash(String key) {
        byte[] digest = md5.digest(key.getBytes());
        return ((long) (digest[0] & 0xFF) << 56)
                | ((long) (digest[1] & 0xFF) << 48)
                | ((long) (digest[2] & 0xFF) << 40)
                | ((long) (digest[3] & 0xFF) << 32)
                | ((long) (digest[4] & 0xFF) << 24)
                | ((long) (digest[5] & 0xFF) << 16)
                | ((long) (digest[6] & 0xFF) << 8)
                | (digest[7] & 0xFF);
    }
}