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

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

public class FileUtils {

    public static String getMD5Base64FromFile(String fileName)
            throws NoSuchAlgorithmException,IOException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(fileName)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md5.update(buffer, 0, bytesRead);
            }
        }
        byte[] bytes = md5.digest();
        return  Base64.getEncoder().encodeToString(bytes);
    }

    public static String getCRC32Base64FromFile(String fileName)
            throws IOException {
        CRC32 crc32 = new CRC32();
        try (FileInputStream fis = new FileInputStream(fileName)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                crc32.update(buffer, 0, bytesRead);
            }
        }
        long crcValue = crc32.getValue();

        // Convert a long value to a 4-byte array (big-endian order)
        byte[] crcBytes = new byte[4];
        crcBytes[0] = (byte) ((crcValue >> 24) & 0xFF);
        crcBytes[1] = (byte) ((crcValue >> 16) & 0xFF);
        crcBytes[2] = (byte) ((crcValue >> 8) & 0xFF);
        crcBytes[3] = (byte) (crcValue & 0xFF);

        return Base64.getEncoder().encodeToString(crcBytes);
    }

    public static void mergeFiles(List<String> partPaths, Path targetPath) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(targetPath))) {
            for (String partPath : partPaths) {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(partPath)))) {
                    byte[] buffer = new byte[8192]; // 8KB缓冲区
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
            }
            for (String partPath : partPaths) {
                File file = new File(partPath);
                if (file != null && file.exists()) {
                    file.delete();
                }
            }
        }
    }

    public static boolean mkParentDir(Path path) {
        if (path == null || path.toString().isEmpty()) {
            return false;
        }
        Path parentPath = path.getParent();
        File parentDirectory = parentPath.toFile();
        if (parentDirectory.exists()) {
            return true;
        }
        if (parentDirectory != null) {
            return parentDirectory.mkdirs();
        }
        return false;
    }

    public static String formatDataSize(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Bytes cannot be negative");
        }
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        DecimalFormat df;
        if (size < 10) {
            df = new DecimalFormat("#.##");
        } else if (size < 100) {
            df = new DecimalFormat("#.#");
        } else {
            df = new DecimalFormat("#");
        }
        return df.format(size)  + units[unitIndex];
    }

    public static void dump2File(String file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public static String mergePath(String root, String sub) {
        String path = "";
        if (StringUtils.isNotBlank(root)) {
            path = root;
        }
        if (StringUtils.isNotBlank(path) && !path.endsWith("/")) {
            path += "/";
        }
        if (sub.startsWith("/")) {
            path += sub.substring(1);
        } else {
            path += sub;
        }
        return path;
    }
}
