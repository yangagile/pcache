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

import com.cloud.pc.data.DataOps;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.model.ApplyStsToken;
import com.cloud.pc.model.StsToken;
import com.cloud.pc.model.ClientCreationInfo;
import com.cloud.pc.iam.IamUtils;
import com.cloud.pc.utils.S3Utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component("ops-MINIO")
public class MinioOps implements DataOps {

    @Value("${minio.client.s3.connectionTimeout:5000}")
    int connectionTimeout;
    @Value("${minio.client.s3.socketTimeout:3000}")
    int socketTimeout;
    @Value("${minio.client.s3.connectionMaxIdleTime:60000}")
    int connectionMaxIdleTime;
    @Value("${minio.client.s3.maxConnections:64}")
    int maxConnections;
    @Value("${minio.client.s3.tcpKeepAlive:true}")
    boolean tcpKeepAlive;
    @Value("${minio.iam.role.policy:arn:minio:iam::minio:role/sts-policy}")
    String roleArn;

    private final ConcurrentMap<String, S3Client> s3Clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StsClient> stsClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CloudFrontClient> cdnFrontClients = new ConcurrentHashMap<>();

    private StsClient newStsClient(ClientCreationInfo clientCreationInfo) {
        ApacheHttpClient.Builder apacheHttpClientBuilder = ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofMillis(connectionTimeout))
                .socketTimeout(Duration.ofMillis(socketTimeout))
                .connectionMaxIdleTime(Duration.ofMillis(connectionMaxIdleTime))
                .tcpKeepAlive(tcpKeepAlive)
                .maxConnections(maxConnections);
        return S3Utils.createStsClient(clientCreationInfo, apacheHttpClientBuilder);
    }

    public StsToken applyToken(ApplyStsToken applyStsToken, ClientCreationInfo clientCreationInfo) {
        String stsClientId = "sts" + clientCreationInfo.getVendor() + clientCreationInfo.getRegion();
        StsClient stsClient = stsClients.get(stsClientId);
        if (stsClient == null) {
            stsClient = newStsClient(clientCreationInfo);
            stsClients.put(stsClientId, stsClient);
        }
        List<String> perminssions;
        perminssions = getPermissions(applyStsToken.getPermissions());
        AssumeRoleRequest assumeRequest = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .policy(IamUtils.concatStsAcl(applyStsToken.getRegion(), IamUtils.getSid(roleArn),
                        IamUtils.resourceAcl(applyStsToken.getName(), applyStsToken.getPath()), perminssions))
                .roleSessionName("pc_apply_sts")
                .durationSeconds(applyStsToken.getExpirationTimeInSeconds())
                .build();

        AssumeRoleResponse assumeResponse = stsClient.assumeRole(assumeRequest);
        Credentials stsCredentials = assumeResponse.credentials();

        StsToken stsTokenVo = new StsToken();
        stsTokenVo.setAccessKey(stsCredentials.accessKeyId());
        stsTokenVo.setAccessSecret(stsCredentials.secretAccessKey());
        stsTokenVo.setSecurityToken(stsCredentials.sessionToken());
        stsTokenVo.setExpiration(Date.from(stsCredentials.expiration()).getTime());
        return stsTokenVo;
    }

    public List<String> getPermissions(List<PcPermission> permissions) {
        List<String> permissionList = new ArrayList<>();
        for (PcPermission permission : permissions) {
            switch (permission) {
                case PutObject:
                    permissionList.add("PutObject");
                    continue;
                case GetObject:
                    permissionList.add("GetObject");
                    continue;

                case DeleteObject:
                    permissionList.add("DeleteObject");
                    continue;
                case ListObject:
                    permissionList.add("ListBucket");
            }
        }
        return permissionList;
    }
}
