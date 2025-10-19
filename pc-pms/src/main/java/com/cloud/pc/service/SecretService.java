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
import com.cloud.pc.iam.IamPolicy;
import com.cloud.pc.meta.Secret;
import com.cloud.pc.iam.Statement;
import com.cloud.pc.requester.IamBucketRequester;
import com.cloud.pc.requester.NewSecretRequester;
import com.cloud.pc.requester.NewTokenRequester;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.SecretUtils;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SecretService {
    @Autowired
    private MetaService metaService;

    public Secret newSecret(NewSecretRequester userRequester) throws Exception{
        return metaService.addSecret(userRequester.toSecret());
    }

    public void newIamBucket(IamBucketRequester policyRequester) throws Exception{
        Secret secret = metaService.getSecret(policyRequester.getAccessKey());
        IamPolicy iamPolicy = new IamPolicy();
        if(StringUtils.isNotBlank(secret.getIam())) {
            iamPolicy = JsonUtils.fromJson(secret.getIam(), IamPolicy.class);
        }
        Statement statement = new Statement().
                withEffect(policyRequester.getEffect()).
                withActions(policyRequester.getPermissions()).
                withResources(policyRequester.getBucket());
        iamPolicy.addStatements(statement);
        secret.setIam(JsonUtils.toJson(iamPolicy));
        metaService.updateSecret(secret);

    }

    public Secret checkToken(String ak, String token, Map<String, Object> claims) {
        if (!Envs.enableToken) {
            return null;
        }
        Secret secret = metaService.getSecret(ak);
        Claims tokenClaims = SecretUtils.parseToken(token, secret.getSecretKey());
        if (claims != null) {
            for (String key : claims.keySet()) {
                if (!claims.get(key).toString().equals(tokenClaims.get(key).toString())) {
                    throw new RuntimeException("invalid token");
                }
            }
        }
        return secret;
    }

    public String getSK(String ak) {
        return metaService.getSecret(ak).getSecretKey();
    }

    public String newToken(NewTokenRequester tokenRequester) throws Exception{
        return SecretUtils.generateToken(tokenRequester.getAccessKey(), tokenRequester.getSecretKey(),
                tokenRequester.getExpirationMs(), tokenRequester.getClaims());
    }
}
