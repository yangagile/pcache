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

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.cloud.pc.data.DataOps;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.model.ApplyStsToken;
import com.cloud.pc.model.StsToken;
import com.cloud.pc.model.ClientCreationInfo;
import com.cloud.pc.iam.IamUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component("ops-OSS")
public class OssOps implements DataOps {
    private static final Logger LOG = LoggerFactory.getLogger(OssOps.class);

    @Value("${aws.role.policy:acs:ram::1826535126281111:role/pc-fullrole}")
    String acsRole;

    private final ConcurrentMap<String, S3Client> s3Clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IAcsClient> stsClients = new ConcurrentHashMap<>();

    public IAcsClient createStsClient(ClientCreationInfo clientCreationInfo) {
        DefaultProfile.addEndpoint(clientCreationInfo.getRegion(), "Sts", clientCreationInfo.getEndpoint());
        IClientProfile profile = DefaultProfile.getProfile(clientCreationInfo.getRegion(),
                clientCreationInfo.getAccessKey(),
                clientCreationInfo.getAccessSecret());
        return new DefaultAcsClient(profile);
    }

    public StsToken applyToken(ApplyStsToken applyStsToken, ClientCreationInfo clientCreationInfo){
        try {
            List<String> perminssions = getPermissions(applyStsToken.getPermissions());
            List<String> resources = new LinkedList<>();
            resources.add(IamUtils.resourceAcl(applyStsToken.getName(), applyStsToken.getPath()));
            if (applyStsToken.getPermissions().contains(PcPermission.ListObject)) {
                resources.add(applyStsToken.getName());
            }
            com.aliyuncs.auth.sts.AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest();
            assumeRoleRequest.setSysMethod(MethodType.POST);
            assumeRoleRequest.setRoleSessionName("pc_apply_sts");
            assumeRoleRequest.setRoleArn(acsRole);
            assumeRoleRequest.setPolicy(concatAcl(resources, perminssions));
            assumeRoleRequest.setDurationSeconds((long) applyStsToken.getExpirationTimeInSeconds());
            String clientId = "sts"+ clientCreationInfo.getVendor() + clientCreationInfo.getRegion();
            IAcsClient aceClient = stsClients.get(clientId);
            if (aceClient == null) {
                aceClient = createStsClient(clientCreationInfo);
                stsClients.put(clientId, aceClient);
            }
            AssumeRoleResponse assumeRoleResponse = aceClient.getAcsResponse(assumeRoleRequest);
            StsToken stsTokenVo = new StsToken();
            stsTokenVo.setAccessKey(assumeRoleResponse.getCredentials().getAccessKeyId());
            stsTokenVo.setAccessSecret(assumeRoleResponse.getCredentials().getAccessKeySecret());
            stsTokenVo.setSecurityToken(assumeRoleResponse.getCredentials().getSecurityToken());
            stsTokenVo.setExpiration(DateUtils.parseDate(assumeRoleResponse.getCredentials().getExpiration(), "yyyy-MM-dd'T'HH:mm:ss'Z'").getTime());
            return stsTokenVo;
        } catch (ClientException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getPermissions(List<PcPermission> permissions) {
        List<String> permissionList = new ArrayList<>();
        for (PcPermission permission : permissions) {
            switch (permission) {
                case PutObject:
                    permissionList.addAll(Arrays.asList("PutObject", "InitiateMultipartUpload", "AbortMultipartUpload",
                            "CompleteMultipartUpload", "ListParts", "UploadPart", "UploadPartCopy"));
                    continue;
                case GetObject:
                    permissionList.add("GetObject");
                    continue;

                case DeleteObject:
                    permissionList.add("DeleteObject");
                    continue;
                case ListObject:
                    permissionList.addAll(Arrays.asList("ListObjects", "ListObjectsV2",
                            "ListMultipartUploads", "ListParts"));
            }
        }
        return permissionList;
    }

    private String concatAcl(List<String> resources, List<String> permissions) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonNode = objectMapper.createObjectNode();
            ArrayNode statementList = objectMapper.createArrayNode();
            ObjectNode aclObject = objectMapper.createObjectNode();
            aclObject.put("Effect", "Allow");
            aclObject.put("Region", "*");

            ArrayNode actionList = objectMapper.createArrayNode();
            permissions.forEach(permission -> {
                actionList.add("oss:" + permission);
            });
            aclObject.put("Action", actionList);

            ArrayNode resourceArray = objectMapper.createArrayNode();
            resources.stream().forEach(resource -> {
                resourceArray.add("acs:oss:*:*:" + resource);
            });
            aclObject.put("Resource", resourceArray);
            statementList.add(aclObject);
            jsonNode.putArray("Statement").addAll(statementList);
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
