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

package com.cloud.pc.controller;

import com.cloud.pc.iam.IamPolicy;
import com.cloud.pc.iam.IamUtils;
import com.cloud.pc.iam.Statement;
import com.cloud.pc.meta.Secret;
import com.cloud.pc.requester.IamBucketRequester;
import com.cloud.pc.requester.NewSecretRequester;
import com.cloud.pc.requester.NewTokenRequester;
import com.cloud.pc.service.PmsService;
import com.cloud.pc.service.SecretService;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.OpsTrace;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.swing.*;

@Api(tags = "Secret Manager")
@RequestMapping("/api/v1/secret")
@RestController
public class SecretController {
    private static final Logger LOG = LoggerFactory.getLogger(SecretController.class);

    @Autowired
    SecretService secretService;

    @Autowired
    PmsService pmsService;

    @ApiOperation(value = "Add Secret")
    @PostMapping(value = "/add", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> addSecret(
            @RequestBody NewSecretRequester newSecretRequester,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String exception;
        try {
            OpsTrace.set("add-secret");
            LOG.info("{} requester={} ak={} token={}", OpsTrace.get(), newSecretRequester, ak, token);
            secretService.checkToken(ak, token, null);
            pmsService.checkLeader();
            Secret secret = secretService.newSecret(newSecretRequester);
            LOG.info("{} success", OpsTrace.get());
            return ResponseEntity.ok(secret);
        } catch (Exception e) {
            exception = e.getMessage();
            LOG.error("{} failed to add user with unknown error", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(exception);
    }

    @ApiOperation(value = "Add Policy to a User")
    @PostMapping(value = "/iam/bucket", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> policy(
            @RequestBody IamBucketRequester iamPolicyRequester,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        try {
            OpsTrace.set("add-policy");
            LOG.info("{} requester={} ak={} token={}", OpsTrace.get(), iamPolicyRequester, ak, token);
            Secret secret = secretService.checkToken(ak, token, null);
            pmsService.checkLeader();
            if (secret != null) {
                IamUtils.checkAction(JsonUtils.fromJson(secret.getIam(), IamPolicy.class), "pms:admin");
            }
            secretService.newIamBucket(iamPolicyRequester);
            LOG.info("{} success", OpsTrace.get());
            return ResponseEntity.ok("ok");

        } catch (Exception e) {
            LOG.error("{} failed to add user with unknown error", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @ApiOperation(value = "Create Token")
    @PostMapping(value = "/token", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> generateToken(
            @RequestBody NewTokenRequester newTokenRequester) {
        try {
            OpsTrace.set("new-token");
            LOG.info("{} requester={} ak={} token={}", OpsTrace.get(), newTokenRequester);
            String newToken = secretService.newToken(newTokenRequester);
            LOG.info("{} success", OpsTrace.get());
            return ResponseEntity.ok(newToken);

        } catch (Exception e) {
            LOG.error("{} failed to generate new token with unknown error", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
