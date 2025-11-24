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

package com.cloud.pc.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PCPathTest {
    @Test
    public void Test_MultipartFile() {
        String strPath = "test-bucket/test/file.dat.1_10";
        PcPath path = new PcPath(strPath);
        assertFalse(path.isSingleFile());
        assertEquals("test-bucket", path.getBucket());
        assertEquals("test/file.dat", path.getKey());
        assertEquals(1, path.getNumber());
        assertEquals(10, path.getTotalNumber());

        PcPath path2 = new PcPath(path.getBucket(),  path.getKey(), path.getNumber(), path.getTotalNumber());
        assertEquals(strPath, path2.toString());
    }

    @Test
    public void Test_SingleFile() {
        PcPath path = new PcPath("/test-bucket/test/file.dat.0_1");
        assertTrue(path.isSingleFile());
        assertEquals("test-bucket", path.getBucket());
        assertEquals("test/file.dat", path.getKey());
        assertEquals(0, path.getNumber());
        assertEquals(1, path.getTotalNumber());
    }
}