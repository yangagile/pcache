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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class TestFileUtils {

    public static void createTestFile(Path path, long size, boolean overwrite,
                                      boolean fillWithZero) throws IOException {
        if (Files.exists(path) && path.toFile().length() == size && !overwrite) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        int bufferSize = 1024 * 1024;
        byte[] buffer = new byte[bufferSize];
        if (!fillWithZero) {
            new Random().nextBytes(buffer); // 预填充随机数据
        }
        try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(path))) {
            long remaining = size;
            while (remaining > 0) {
                int writeSize = (int) Math.min(buffer.length, remaining);
                bos.write(buffer, 0, writeSize);
                remaining -= writeSize;
            }
            bos.flush();
        }
    }

    public static void createTestFile(Path path, String content) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(path))) {
            bos.write(content.getBytes());
            bos.flush();
        }
    }


    public static boolean compareFiles(File file1, File file2) throws IOException {
        try (InputStream is1 = new BufferedInputStream(new FileInputStream(file1));
             InputStream is2 = new BufferedInputStream(new FileInputStream(file2))) {

            byte[] buffer1 = new byte[8192];  // 8KB缓冲区
            byte[] buffer2 = new byte[8192];
            int bytesRead1, bytesRead2;

            while (true) {
                bytesRead1 = is1.read(buffer1);
                bytesRead2 = is2.read(buffer2);

                if (bytesRead1 != bytesRead2) return false;
                if (bytesRead1 == -1) break;  // 同时到达文件尾
                for (int i = 0; i < bytesRead1; i++) {
                    if (buffer1[i] != buffer2[i]) return false;
                }
            }
            return true;
        }
    }

    public static boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return directory.delete();
        }
        return false;  // 目录不存在
    }

    public static String getCurrentMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        return stackTrace[2].getMethodName();
    }
}
