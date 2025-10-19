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

package com.cloud.pc.meta;

import com.cloud.pc.iam.IamPolicy;
import com.cloud.pc.utils.JsonUtils;
import junit.framework.TestCase;
import org.junit.Test;

public class IamPolicyTest extends TestCase {

    @Test
    public void test_createS3BucketPolicy() throws Exception {
        IamPolicy iamPolicy = IamPolicy.createS3BucketPolicy("test-minio");
        String jsonContent = JsonUtils.toJson(iamPolicy);
        System.out.println();
    }
}