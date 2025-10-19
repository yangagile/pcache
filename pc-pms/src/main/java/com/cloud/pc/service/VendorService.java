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

package com.cloud.pc.service;

import com.cloud.pc.meta.PcMeta;
import com.cloud.pc.meta.Vendor;
import com.cloud.pc.meta.VendorBucket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class VendorService  {
    @Autowired
    private MetaService metaService;

    public Vendor getVendorByNameAndRegion(String name, String region) {
        return metaService.getVendorByNameAndRegion(name, region);
    }

    public PcMeta addVendor(Vendor vendor) throws Exception {
        return metaService.addVendor(vendor);
    }

    public PcMeta addVendorBucket(VendorBucket vb) throws Exception {
        return metaService.addVendorBucket(vb);
    }

    public List<VendorBucket> listVendorBucket() throws Exception {
        return metaService.listVendorBucket();
    }
}
