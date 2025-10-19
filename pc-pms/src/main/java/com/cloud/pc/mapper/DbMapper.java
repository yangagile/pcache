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

package com.cloud.pc.mapper;

import com.cloud.pc.meta.PBucket;
import com.cloud.pc.meta.Secret;
import com.cloud.pc.meta.Vendor;
import com.cloud.pc.meta.VendorBucket;

import java.util.List;

public interface DbMapper<T> {
    List<PBucket> loadPBucket();
    int addPBucket(PBucket pb);

    List<Secret> loadSecret();
    int addSecret(Secret secrets);
    int updateSecret(Secret secrets);

    List<VendorBucket> loadVendorBucket();
    int addVendorBucket(VendorBucket vendorBucket);

    List<Vendor> loadVendor();
    int addVendor(Vendor vendor);
}
