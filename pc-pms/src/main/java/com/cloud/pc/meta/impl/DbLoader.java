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

package com.cloud.pc.meta.impl;

import com.cloud.pc.meta.*;
import com.cloud.pc.mapper.DbMapper;
import com.cloud.pc.utils.ComUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component("db-loader")
public class DbLoader implements MetaLoader {

    @Autowired
    DbMapper dbMapper;

    public void init(String content) throws IOException  {
    }

    public void sync(String content) throws IOException {
    }

    public List<PcMeta> load(Class type) throws IOException {
        if (type.getSimpleName().equals(PBucket.class.getSimpleName())) {
            return dbMapper.loadPBucket();
        } else if(type.getSimpleName().equals(Secret.class.getSimpleName())) {
            return dbMapper.loadSecret();
        } else if(type.getSimpleName().equals(Vendor.class.getSimpleName())) {
            return dbMapper.loadVendor();
        } else if(type.getSimpleName().equals(VendorBucket.class.getSimpleName())) {
            return dbMapper.loadVendorBucket();
        }
        return null;
    }

    public int add(Class type, PcMeta item) throws IOException {
        if (type.getSimpleName().equals(PBucket.class.getSimpleName())) {
            return dbMapper.addPBucket((PBucket)item);
        } else if(type.getSimpleName().equals(Secret.class.getSimpleName())) {
            return dbMapper.addSecret((Secret)item);
        } else if(type.getSimpleName().equals(Vendor.class.getSimpleName())) {
            return dbMapper.addVendor((Vendor)item);
        } else if(type.getSimpleName().equals(VendorBucket.class.getSimpleName())) {
            return dbMapper.addVendorBucket((VendorBucket)item);
        }
        return 0;
    }

    public int update(Class type, PcMeta item) throws IOException {
        if (type.getSimpleName().equals(PBucket.class.getSimpleName())) {
            return 0;
        } else if(type.getSimpleName().equals(Secret.class.getSimpleName())) {
            return dbMapper.updateSecret((Secret)item);
        } else if(type.getSimpleName().equals(Vendor.class.getSimpleName())) {
            return 0;
        } else if(type.getSimpleName().equals(VendorBucket.class.getSimpleName())) {
            return 0;
        }
        return 0;
    }

    public String getRootPath() {
        return null;
    }
}
