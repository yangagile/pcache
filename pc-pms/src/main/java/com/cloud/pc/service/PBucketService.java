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
import com.cloud.pc.data.DataOps;
import com.cloud.pc.meta.PcMeta;
import com.cloud.pc.model.ClientCreationInfo;
import com.cloud.pc.meta.Vendor;
import com.cloud.pc.meta.VendorBucket;
import com.cloud.pc.meta.PBucket;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.utils.*;
import com.mysql.cj.util.StringUtils;
import com.cloud.pc.model.routing.RoutingPolicy;
import com.cloud.pc.model.routing.RoutingResult;
import com.cloud.pc.model.ApplyStsToken;
import com.cloud.pc.model.StsInfo;
import com.cloud.pc.model.StsToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PBucketService {
    private static final Logger LOG = LoggerFactory.getLogger(PBucketService.class);

    @Autowired
    VendorBucketService vbService;

    @Autowired
    VendorService vendorService;

    @Autowired
    private MetaService metaService;

    private Map<String, DataOps> dataOpsMap = new HashMap<>();

    @Autowired
    public void DataOpsInterface(Map<String, DataOps> dataOps) {
        this.dataOpsMap.putAll(dataOps);
    }


    public PBucket getPbInfo(String name) {
        return metaService.getPBucketInfo(name);
    }

    public static boolean checkStsExpirationTime(int expirationTimeInSeconds) {
        if (Envs.minStsDurationSec > expirationTimeInSeconds) {
            return false;
        }
        if (Envs.maxStsDurationSec < expirationTimeInSeconds) {
            return false;
        }
        return true;
    }

    public boolean checkPathWithPrefix(final String path, final String prefix) {
        if (path == null) {
            return false;
        }
        if (org.apache.commons.lang3.StringUtils.startsWith(path, "/")) {
            return false;
        }
        if (org.apache.commons.lang3.StringUtils.isBlank(prefix)) {
            return true;
        }
        if (org.apache.commons.lang3.StringUtils.startsWith(path, prefix)) {
            return true;
        }
        return false;
    }

    public RoutingResult applySts(String bucket, String path, List<PcPermission> permissions,
        int expirationInSeconds) {
        PBucket pb = metaService.getPBucketInfo(bucket);
        if (pb == null) {
            LOG.error("{} No failed to get bucket={}", OpsTrace.get(), bucket);
            throw new RuntimeException("No this pbucket:" + bucket);
        }
        if (!checkPathWithPrefix(path, pb.getPrefix())) {
            LOG.error("{} No permission to access bucket={} path={}", OpsTrace.get(), bucket, path);
            throw new RuntimeException("No permission for the path");
        }
        RoutingPolicy routingPolicy = JsonUtils.fromJson(pb.getPolicyRouting(), RoutingPolicy.class);
        if (routingPolicy == null) {
            LOG.error("{} failed to parse routing policy, bucket={} rolicyRouting={}",
                    OpsTrace.get(), bucket, pb.getPolicyRouting());
            throw new RuntimeException("Missing route policy configuration");
        }
        RoutingResult result = new RoutingResult();
        result.setRouter(routingPolicy.getRouter());

        List<StsInfo> stsResults = routingPolicy.getBucketIds()
            .stream()
            .map(vendorBucketId -> {
                VendorBucket vb = vbService.getVendorBucketById(vendorBucketId);
                ApplyStsToken stsToken = new ApplyStsToken();
                stsToken.setPermissions(permissions);
                stsToken.setPath(path);
                stsToken.setExpirationTimeInSeconds(expirationInSeconds);
                return applyStsInternal(vb, stsToken);
            })
            .collect(Collectors.toList());
        result.setStsInfos(stsResults);
        return result;
    }

    private StsInfo applyStsInternal(VendorBucket vb, ApplyStsToken applyStsToken) {
        Vendor vendor = vendorService.getVendorByNameAndRegion(vb.getVendor(), vb.getRegion());
        if (vendor == null) {
            LOG.error("{} failed to get vendor vb={}", OpsTrace.get(), vb);
            throw new RuntimeException("vendor do not exists vendorBucketId=" + vb.getId());
        }
        StsInfo stsResult = new StsInfo();
        stsResult.setBucketName(vb.getName());
        stsResult.setRegion(vb.getRegion());
        stsResult.setPath(applyStsToken.getPath());
        stsResult.setStorageType(vb.getVendor());
        if(StringUtils.isNullOrEmpty(vb.getEndpoint())) {
            stsResult.setEndpoint(vendor.getEndpoint());
        } else {
            stsResult.setEndpoint(vb.getEndpoint());
        }
        stsResult.setCdnEndpoint(vb.getCdnEndpoint());

        ClientCreationInfo clientCreationInfo = new ClientCreationInfo(
                vb.getVendor(), vb.getRegion(), vendor.getStsEndpoint(),
                vendor.getAccessKey(), vendor.getAccessSecret());
        DataOps dataOps =  dataOpsMap.get("ops-" + vendor.getName());
        applyStsToken.setName(vb.getName());
        applyStsToken.setRegion(vendor.getRegion());
        StsToken stsToken = dataOps.applyToken(applyStsToken, clientCreationInfo);
        stsResult.setAccessKey(stsToken.getAccessKey());
        stsResult.setAccessSecret(stsToken.getAccessSecret());
        stsResult.setSecurityToken(stsToken.getSecurityToken());
        stsResult.setExpiration(stsToken.getExpiration());

        return stsResult;
    }

    public PcMeta addPBucket(PBucket pb) throws Exception {
        return metaService.addPBucket(pb);
    }
}
