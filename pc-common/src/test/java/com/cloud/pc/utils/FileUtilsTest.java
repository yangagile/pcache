package com.cloud.pc.utils;

import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class FileUtilsTest {
    @Test
    public void Test_mkParentDir() {
        String tempDir = System.getProperty("java.io.tmpdir");
        File dirParent1 = new File(tempDir + "/test_parent_dir");
        File dirParent2 = new File(tempDir + "/test_parent_dir/test_dir_sub");
        File file = new File(tempDir + "/test_parent_dir/test_dir_sub/1");
        if (!dirParent1.exists()) {
            try {
                boolean ret = FileUtils.mkParentDir(file.toPath());
                assertTrue(ret);
                ret = FileUtils.mkParentDir(file.toPath());
                assertTrue(ret);
                assertTrue(dirParent2.exists());
                assertTrue(dirParent2.exists());
            } finally {
                dirParent1.delete();
            }
        }

        Path path = Paths.get("/no_this_dir_xxxxx/test/f1");
        boolean ret = FileUtils.mkParentDir(path);
        assertFalse(ret);
    }
}