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

import com.cloud.pc.utils.ComUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class PcpHashInfo {
    private String checksum = "";
    private List<HashValue> pcpList;

    public static String generateChecksum(List<HashValue> values) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            for (HashValue info : values) {
                messageDigest.update(info.key().getBytes());
            }
            byte[] sha1Bytes = messageDigest.digest();
            return ComUtils.bytesToHex(sha1Bytes);
        } catch (NoSuchAlgorithmException e) {
            // ignore
        }
        return null;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public List<HashValue> getPcpList() {
        return pcpList;
    }

    public void setPcpList(List<HashValue> pcpList) {
        this.pcpList = pcpList;
    }
}
