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

public class FileUtils {

    public static String getMD5Base64FromFile(InputStream inputStream, long contentLength, String hashType)
            throws NoSuchAlgorithmException,IOException {
        String value = null;
        if (inputStream instanceof FileInputStream) {
            FileInputStream fileInputStream = (FileInputStream) inputStream;
            MappedByteBuffer byteBuffer = fileInputStream.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    0, contentLength > 0 ? contentLength : fileInputStream.available());
            MessageDigest md5 = MessageDigest.getInstance(hashType);
            md5.update(byteBuffer);
            byte[] bytes = md5.digest();
            value = Base64.getEncoder().encodeToString(bytes);
            byteBuffer.clear();
        }
        return value;
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

    public static void mkParentDir(Path path) {
        Path parentPath = path.getParent();
        if (parentPath != null) {
            File parentDirectory = parentPath.toFile();
            if (parentDirectory != null && !parentDirectory.exists()) {
                parentDirectory.mkdirs();
            }
        }
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
