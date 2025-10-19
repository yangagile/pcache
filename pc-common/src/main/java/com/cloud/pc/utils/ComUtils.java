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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.List;
import java.util.function.Function;

public class ComUtils {

    public static <T> T getProps(String key, T defaultVal, Function<String, T> convertFn) {
        String strValue = System.getenv(key);
        if (StringUtils.isBlank(strValue)) {
            strValue = System.getProperty(key);
        }
        return StringUtils.isNotBlank(strValue) ? convertFn.apply(strValue) : defaultVal;
    }

    public static <T> String checksumList(List<T> items) throws Exception{
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        for (T item : items) {
            messageDigest.update(item.toString().getBytes());
        }
        byte[] digest = messageDigest.digest();
        return bytesToHex(digest);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    public static String className(Class<?> clazz) {
        return clazz.getSimpleName().toLowerCase();
    }
}
