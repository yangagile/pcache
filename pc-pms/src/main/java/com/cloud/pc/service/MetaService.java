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

import com.cloud.pc.config.Envs;
import com.cloud.pc.meta.*;

import com.cloud.pc.meta.PBucket;
import com.cloud.pc.meta.impl.FileLoader;
import com.cloud.pc.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class MetaService {
    private static final Logger LOG = LoggerFactory.getLogger(MetaService.class);

    @Value("${pc.iam.pms.iam.default:}")
    String defaultPmsIam;

    MetaTable bucketTable;
    MetaTable secretTable;
    MetaTable vendorTable;
    MetaTable vendorBucketTable;

    @JsonIgnore
    private Map<String, MetaLoader> loaderMap = new HashMap<>();

    @Autowired
    public void loaderInterface(Map<String, MetaLoader> loaders) throws IOException {
        this.loaderMap.putAll(loaders);
    }

    public void init() throws IOException {
        MetaLoader loader = loaderMap.get(Envs.dataLoader);
        if(loader == null) {
            LOG.error("failed to get loader {}!", Envs.dataLoader);
        }
        loader.init(Envs.fileLoaderPath);
        bucketTable = new MetaTable(PBucket.class, loader);
        secretTable = new MetaTable(Secret.class, loader);
        vendorTable = new MetaTable(Vendor.class, loader);
        vendorBucketTable = new MetaTable(VendorBucket.class, loader);
        try {
            loadMeta();
            // if the first PMS and secret table is empty, init secret table.
            if (StringUtils.isBlank(Envs.existingPmsUrls) && secretTable.isEmpty()) {
                initSecret();
            }
        } catch (Exception e) {
            LOG.error("failed to load meta!", Envs.dataLoader);
        }
    }

    public void initSecret() throws Exception{
        Secret secret = new Secret();
        secret.setAccessKey(Envs.ak);
        secret.setSecretKey(Envs.sk);
        secret.setIam(defaultPmsIam);
        secretTable.add(secret);
    }

    public String dumpMeta(Boolean sync) throws JsonProcessingException{
        if (sync) {
            String metaRoot = Envs.fileLoaderPath;
            try {
                bucketTable.dump2File(metaRoot);
                secretTable.dump2File(metaRoot);
                vendorTable.dump2File(metaRoot);
                vendorBucketTable.dump2File(metaRoot);
            } catch (IOException e) {
                LOG.error("failed to dump meta", e);
                return "Fail";
            }
            return "OK";

        } else {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode jsonObject = mapper.createObjectNode();

            jsonObject.put(bucketTable.getTableName(), JsonUtils.toJsonNode(bucketTable));
            jsonObject.put(secretTable.getTableName(), JsonUtils.toJsonNode(secretTable));
            jsonObject.put(vendorTable.getTableName(), JsonUtils.toJsonNode(vendorTable));
            jsonObject.put(vendorBucketTable.getTableName(), JsonUtils.toJsonNode(vendorBucketTable));
            return jsonObject.toPrettyString();
        }
    }

    public void loadMeta() throws Exception {
        bucketTable.updateAll();
        secretTable.updateAll();
        vendorTable.updateAll();
        vendorBucketTable.updateAll();
    }

    public void syncMeta(String content) throws Exception {
        MetaLoader loader = loaderMap.get(Envs.dataLoader);
        loader.sync(content);
        bucketTable.sync(loader);
        secretTable.sync(loader);
        vendorTable.sync(loader);
        vendorBucketTable.sync(loader);
    }

    public long getVersion() {
        long lastUpdateTime = 0;
        if (bucketTable.getLastUpdateTime() > lastUpdateTime) {
            lastUpdateTime = bucketTable.getLastUpdateTime();
        }
        if (secretTable.getLastUpdateTime() > lastUpdateTime) {
            lastUpdateTime = secretTable.getLastUpdateTime();
        }
        if (vendorTable.getLastUpdateTime() > lastUpdateTime) {
            lastUpdateTime = vendorTable.getLastUpdateTime();
        }
        if (vendorBucketTable.getLastUpdateTime() > lastUpdateTime) {
            lastUpdateTime = vendorBucketTable.getLastUpdateTime();
        }
        return lastUpdateTime;
    }

    /* PBucket */
    public PBucket getPBucketInfo(String name) {
        return (PBucket)bucketTable.get(name);
    }
    public PcMeta addPBucket(PBucket pb) throws Exception{
        return bucketTable.add(pb);
    }

    /* VendorBucket */
    public VendorBucket getVendorBucket(Integer id) {
        return (VendorBucket)vendorBucketTable.get(id.toString());
    }

    public PcMeta addVendor(Vendor vendor) throws Exception{
        return vendorTable.add(vendor);

    }
    public Vendor getVendorByNameAndRegion(String name, String region) {
        return (Vendor)vendorTable.get(name+region);
    }

    public PcMeta addVendorBucket(VendorBucket vb) throws Exception{
        return vendorBucketTable.add(vb);
    }

    public List<VendorBucket> listVendorBucket() throws Exception{
        return vendorBucketTable.getItems(VendorBucket.class);
    }

    /* Secret */
    public Secret getSecret(String name) {
        return (Secret)secretTable.get(name);
    }
    public Secret addSecret(Secret secret) throws Exception{
        secretTable.add(secret);
        return secret;
    }
    public int updateSecret(Secret secret) throws Exception{
        return secretTable.update(secret);
    }
}
