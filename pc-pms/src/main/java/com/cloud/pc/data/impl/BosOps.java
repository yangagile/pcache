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

package com.cloud.pc.data.impl;

import com.baidubce.BceClientConfiguration;
import com.baidubce.BceClientException;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.cdn.CdnClient;
import com.baidubce.services.sts.StsClient;
import com.baidubce.services.sts.model.GetSessionTokenRequest;
import com.baidubce.services.sts.model.GetSessionTokenResponse;
import com.cloud.pc.data.DataOps;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.model.ApplyStsToken;
import com.cloud.pc.model.StsToken;
import com.cloud.pc.model.ClientCreationInfo;
import com.cloud.pc.iam.IamUtils;
import com.cloud.pc.service.PBucketService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component("ops-BOS")
public class BosOps implements DataOps {
    private static final Logger LOG = LoggerFactory.getLogger(PBucketService.class);

    private final ConcurrentMap<String, BosClient> bosClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CdnClient> cdnClients = new ConcurrentHashMap<>();

    private StsClient createStsClient(ClientCreationInfo clientCreation) {
        BceClientConfiguration bosClientConfiguration = new BceClientConfiguration(new BceClientConfiguration());
        bosClientConfiguration.setCredentials(new DefaultBceCredentials(
                clientCreation.getAccessKey(), clientCreation.getAccessSecret()));
        bosClientConfiguration.setEndpoint(clientCreation.getEndpoint());
        return new StsClient(bosClientConfiguration);
    }

    public StsToken applyToken(ApplyStsToken applyStsToken, ClientCreationInfo clientCreationInfo) {
        com.baidubce.services.sts.StsClient stsClient = null;
        try {
            stsClient = createStsClient(clientCreationInfo);
            List<String> perminssions = getPermissions(applyStsToken.getPermissions());
            boolean hasList = applyStsToken.getPermissions().contains(PcPermission.ListObject);
            String acl = concatAcl(applyStsToken.getName(), IamUtils.resourceAcl(applyStsToken.getName(),
                    applyStsToken.getPath()), perminssions, hasList);
            GetSessionTokenResponse sessionTokenResponse = stsClient.getSessionToken(new GetSessionTokenRequest()
                    .withDurationSeconds(applyStsToken.getExpirationTimeInSeconds())
                    .withAcl(acl));
            StsToken stsTokenVo = new StsToken();
            stsTokenVo.setAccessKey(sessionTokenResponse.getAccessKeyId());
            stsTokenVo.setAccessSecret(sessionTokenResponse.getSecretAccessKey());
            stsTokenVo.setSecurityToken(sessionTokenResponse.getSessionToken());
            stsTokenVo.setExpiration(sessionTokenResponse.getExpiration().getTime());
            return stsTokenVo;
        } finally {
            if (stsClient != null) {
                try {
                    stsClient.shutdown();
                } catch (Exception e) {
                    LOG.error("Shutdown baidu BOS StsClient error", e);
                }
            }
        }
    }

    public List<String> getPermissions(List<PcPermission> permissions) {
        List<String> permissionList = new ArrayList<>();
        for (PcPermission permission : permissions) {
            switch (permission) {
                case PutObject:
                    permissionList.addAll(Arrays.asList("PutObject", "InitiateMultipartUpload",
                            "CompleteMultipartUpload", "AbortMultipartUpload", "ListMultipartUploads"));
                    continue;
                case GetObject:
                    permissionList.add("GetObject");
                    continue;

                case DeleteObject:
                    permissionList.add("DeleteObject");
                    continue;
                case ListObject:
                    permissionList.add("LIST");
            }
        }
        return permissionList;
    }

    private String concatAcl(String name, String resource, List<String> permissions, boolean hasList) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonNode = objectMapper.createObjectNode();

            ArrayNode accessControlList = objectMapper.createArrayNode();
            ObjectNode aclObject = objectMapper.createObjectNode();
            aclObject.put("service", "bce:bos");

            aclObject.put("region", "*");
            aclObject.put("effect", "Allow");

            ArrayNode resourceArray = objectMapper.createArrayNode();
            aclObject.put("resource", resourceArray);
            resourceArray.add(resource);

            if (hasList) {
                resourceArray.add(name);
            }
            ArrayNode permissionArray = objectMapper.createArrayNode();
            permissions.forEach(permission -> {
                permissionArray.add(permission);
            });
            aclObject.put("permission", permissionArray);
            accessControlList.add(aclObject);
            jsonNode.putArray("accessControlList").addAll(accessControlList);

            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
