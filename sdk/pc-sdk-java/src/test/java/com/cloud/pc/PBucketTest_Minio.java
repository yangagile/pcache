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

package com.cloud.pc;

import com.cloud.pc.entity.Stats;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.TestFileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;


public class PBucketTest_Minio {
    static String localRootPath = "/tmp/pcache/test/";
    static String bucketName = "pb-minio";
    static String prefix = "test/pcache/java/sdk/";
    static String pmsUrl = "http://127.0.0.1:8080";
    static String ak = "ak-pc-test";
    static String sk = "KWBTBJJZTmZWb1F00lK1psg+2RMvRApY5uSDt7u1wpg=";

    @BeforeClass
    public static void init() {
        String tempDir = System.getProperty("java.io.tmpdir");
        if (StringUtils.isNotBlank(tempDir)) {
            localRootPath = FileUtils.mergePath(tempDir, localRootPath);
        }
        File rootDir = new File(localRootPath);
        if (rootDir.exists() && rootDir.isDirectory()) {
            TestFileUtils.deleteDirectory(rootDir);
            System.out.println("Deleted directory " + localRootPath);
        }
    }

    @AfterClass
    public static void clean() {
        File rootDir = new File(localRootPath);
        if (rootDir.exists() && rootDir.isDirectory()) {
            TestFileUtils.deleteDirectory(rootDir);
            System.out.println("Directory and its contents have been deleted.");
        }
    }

    @Test
    public void test_putObject_getObject_small_local() throws Exception {
        // create temp file
        String fileName = TestFileUtils.getCurrentMethodName();
        Path localFilePath = Paths.get(localRootPath, fileName);
        String fileContent = "test putObject and getObject directly";
        TestFileUtils.createTestFile(localFilePath, fileContent);
        File testFile = localFilePath.toFile();
        Assert.assertEquals("invalid test file", fileContent.getBytes().length, testFile.length());

        // put file
        String fileKey = prefix + fileName;
        PBucket pbucket = new PBucket(pmsUrl, bucketName, ak, sk);
        pbucket.setEnablePCache(false);
        PutObjectResponse response = pbucket.putObject(fileKey, localFilePath.toString());
        Assert.assertFalse("failed to put", StringUtils.isBlank(response.eTag()));
        Stats stats = pbucket.getThreadTracer().get().getStats();
        Assert.assertEquals("should put From Local", 1, stats.getCntLocal());

        // get file
        pbucket.getThreadTracer().get().resetStats();
        Path newLocalFile = Paths.get(localRootPath, fileName + ".new");
        GetObjectResponse getResponse = pbucket.getObject(fileKey, newLocalFile.toString());
        Assert.assertFalse("failed to get",StringUtils.isBlank(getResponse.eTag()));
        pbucket.getThreadTracer().get().getStats();
        Assert.assertEquals("should get From Local", 1, stats.getCntLocal());

        // compare file
        Assert.assertTrue("files are different",
                TestFileUtils.compareFiles(localFilePath.toFile(), newLocalFile.toFile()));
    }

    @Test
    public void test_putObject_getObject_small_pcp() throws Exception {
        // create temp file
        String fileName = TestFileUtils.getCurrentMethodName();
        Path localFilePath = Paths.get(localRootPath, fileName);
        String fileContent = "test putObject and getObject directly";
        TestFileUtils.createTestFile(localFilePath, fileContent);
        File testFile = localFilePath.toFile();
        Assert.assertEquals("invalid test file", fileContent.getBytes().length, testFile.length());

        // put file
        String fileKey = prefix + fileName;
        PBucket pbucket = new PBucket(pmsUrl, bucketName, ak, sk);
        PutObjectResponse response = pbucket.putObject(fileKey, localFilePath.toString());
        Assert.assertFalse("failed to put", StringUtils.isBlank(response.eTag()));
        Stats stats = pbucket.getThreadTracer().get().getStats();
        Assert.assertEquals("should put From Local", 1, stats.getCntPcp());

        // get file
        pbucket.getThreadTracer().get().resetStats();
        Path newLocalFile = Paths.get(localRootPath, fileName + ".new");
        GetObjectResponse getResponse = pbucket.getObject(fileKey, newLocalFile.toString());
        Assert.assertFalse("failed to get",StringUtils.isBlank(getResponse.eTag()));
        stats = pbucket.getThreadTracer().get().getStats();
        Assert.assertTrue("should get From Pcp", stats.getPcpCacheHit() > 0);

        // compare file
        Assert.assertTrue("files are different",
                TestFileUtils.compareFiles(localFilePath.toFile(), newLocalFile.toFile()));
    }

    @Test
    public void test_putObject_getObject_PCache() throws Exception {
        String fileName = TestFileUtils.getCurrentMethodName();
        int fileSize = 16*1024*1024;
        // put file with PCache
        Path localFilePath = Paths.get(localRootPath, fileName);
        TestFileUtils.createTestFile(localFilePath, fileSize, false, false);
        File testFile = localFilePath.toFile();
        Assert.assertEquals("invalid test file", fileSize, testFile.length());

        PBucket pbucket = new PBucket(pmsUrl, bucketName, ak, sk);
        pbucket.setEnablePCache(true);
        String fileKey = prefix + fileName;
        PutObjectResponse response = pbucket.putObject(fileKey, localFilePath.toString());
        Assert.assertFalse("failed to put", StringUtils.isBlank(response.eTag()));

        int blockNum = (int) Math.ceil((double) fileSize / pbucket.getBlockSize());;
        Stats stats = pbucket.getThreadTracer().get().getStats();
        if (pbucket.getPmsMgr().getPcp(fileKey) != null) {
            Assert.assertEquals("should put from PCP", blockNum, stats.getCntPcp());
        } else {
            Assert.assertEquals("should put From Local", blockNum, stats.getCntLocal());
        }
        pbucket.getThreadTracer().get().resetStats();


        // get file with PCache
        Path newLocalFile = Paths.get(localRootPath, fileName + ".new");
        File newFile  = newLocalFile.toFile();
        if (newFile.exists()) {
            newFile.delete();
        }
        Assert.assertFalse("file existing in local", newFile.exists());
        GetObjectResponse getResponse = pbucket.getObject(fileKey, newLocalFile.toString());
        Assert.assertFalse("failed to get", StringUtils.isBlank(getResponse.eTag()));

        Assert.assertTrue("files are different",
                TestFileUtils.compareFiles(localFilePath.toFile(), newLocalFile.toFile()));

        stats = pbucket.getThreadTracer().get().getStats();
        if (pbucket.getPmsMgr().getPcp(fileKey) != null) {
            Assert.assertEquals("should get From PCP", blockNum, stats.getCntPcp());
        } else {
            Assert.assertEquals("should get From Local", blockNum, stats.getCntLocal());
        }
        pbucket.getThreadTracer().get().resetStats();

        // get again with PCache, should get from cache of PCP
        if (newFile.exists()) {
            newFile.delete();
        }
        Assert.assertFalse("file existing in local", newFile.exists());
        getResponse = pbucket.getObject(fileKey, newLocalFile.toString());
        Assert.assertFalse("failed to get", StringUtils.isBlank(getResponse.eTag()));
        Assert.assertTrue("files are different",
                TestFileUtils.compareFiles(localFilePath.toFile(), newLocalFile.toFile()));

        stats = pbucket.getThreadTracer().get().getStats();
        if (pbucket.getPmsMgr().getPcp(fileKey) != null) {
            Assert.assertEquals("should get From PCP", blockNum, stats.getCntPcp());
        } else {
            Assert.assertEquals("should put From Local", blockNum, stats.getCntLocal());
        }
        pbucket.getThreadTracer().get().resetStats();
    }
}