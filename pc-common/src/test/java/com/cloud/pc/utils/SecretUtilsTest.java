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

package com.cloud.pc.utils;

import io.jsonwebtoken.Claims;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SecretUtilsTest extends TestCase {
    @Test
    public void test_generateToken_parseToken() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "user@example.com");

        String ak = "user1";
        String sk = SecretUtils.generatSecretKey();
        long expirationMs = 300000;
        String token = SecretUtils.generateToken(ak, sk, expirationMs, null);

        Claims parseClaims = SecretUtils.parseToken(token, sk);
        Assert.assertEquals(parseClaims.get("email"), "user@example.com");
    }
}