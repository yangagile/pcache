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

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SecretUtils {

    public static String generateToken(String ak, String sk, long expirationMs,
                                       Map<String, Object> claims) {
        if (claims == null) {
            claims = new HashMap<>();
        }
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(ak)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(Keys.hmacShaKeyFor(sk.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims parseToken(String token, String sk) throws JwtException{
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(sk.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static String generatSecretKey() {
        SecretKey hs256Key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        return Base64.getEncoder().encodeToString(hs256Key.getEncoded());
    }

}
