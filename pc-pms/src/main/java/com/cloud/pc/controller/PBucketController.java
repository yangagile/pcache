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

import com.cloud.pc.config.Envs;
import com.cloud.pc.iam.IamPolicy;
import com.cloud.pc.iam.IamUtils;
import com.cloud.pc.meta.PBucket;
import com.cloud.pc.meta.PcMeta;
import com.cloud.pc.meta.Secret;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.model.routing.RoutingResult;
import com.cloud.pc.requester.PBucketRequester;
import com.cloud.pc.service.SecretService;
import com.cloud.pc.service.PBucketService;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.OpsTrace;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "Parallel Bucket (PB)")
@RequestMapping("/api/v1/pb")
@RestController
public class PBucketController {
    private static final Logger LOG = LoggerFactory.getLogger(PBucketController.class);

    @Autowired
    PBucketService pbService;

    @Autowired
    SecretService secretService;

    @ApiOperation(value = "Query PB Info")
    @GetMapping(value = "/{bucket}/info", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> getBucketInfo(
           @PathVariable("bucket") String bucket,
           @RequestHeader(required = false, value = "X-AK") String ak,
           @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String exception;
        try {
            OpsTrace.set("bucket-info");
            LOG.info("{} name={} ak={} token={}", OpsTrace.get(), bucket, ak, token);

            if (StringUtils.isBlank(bucket)) {
                LOG.error("{} bad bucket is empty", OpsTrace.get());
                return ResponseEntity.badRequest().body("name cannot be empty!");
            }
            secretService.checkToken(ak, token, null);
            PBucket pb = pbService.getPbInfo(bucket);
            if (pb == null) {
                LOG.error("{} failed to query bucket", OpsTrace.get());
                return ResponseEntity.noContent().build();
            }
            LOG.info("{} success to query bucket:{}", OpsTrace.get(), pb);
            return ResponseEntity.ok(pb);
        } catch (Exception e) {
            exception = e.getMessage();
            LOG.error("{} exception to query bucket", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(exception);
    }

    @ApiOperation(value = "Apply STS")
    @GetMapping(value = "/{bucket}/sts", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> applyBucketSts(
            @PathVariable(value = "bucket") String bucket,
            @RequestParam(value = "path", defaultValue = "") String path,
            @RequestParam(value = "permissions") List<PcPermission> permissions,
            @RequestParam(value = "expirationInSeconds", required = false, defaultValue = "1800") int expirationInSeconds,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String exception;
        try {
            OpsTrace.set("apply-sts");
            Secret secret = secretService.checkToken(ak, token, null);
            if (secret != null) {
                for (PcPermission permission : permissions) {
                    IamUtils.checkAction(JsonUtils.fromJson(secret.getIam(), IamPolicy.class),
                            "s3:" + permission.toString());
                }
            }
            LOG.info("{} bucket={} path={} permissions={} expirationTime={} token={}",
                    OpsTrace.get(), bucket, path, permissions, expirationInSeconds, token);

            if ( !pbService.checkStsExpirationTime(expirationInSeconds)) {
                LOG.error("{} invalid expirationInSeconds {}", OpsTrace.get(), expirationInSeconds);
                return ResponseEntity.badRequest().body("expirationTimeInSeconds must between " +
                        Envs.minStsDurationSec + " and " + Envs.maxStsDurationSec);
            }
            RoutingResult routingResult = pbService.applySts(bucket, path, permissions, expirationInSeconds);
            LOG.info("{} success bucket={} path={} permissions={} expirationTime={} routingResult={}",
                    OpsTrace.get(), bucket, path, permissions, expirationInSeconds, routingResult);
            return ResponseEntity.ok(routingResult);
        } catch (Exception e) {
            exception = e.getMessage();
            LOG.error("{} exception to apply sts token", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(exception);
    }

    @ApiOperation(value = "Add Parallel Bucket")
    @PostMapping(value = "/add", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> addParallelBucket(
            @RequestBody PBucketRequester pbRequester,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String exception;
        try {
            OpsTrace.set("add-bucket");
            LOG.info("{} requester={} ak={} token={}", OpsTrace.get(), pbRequester, ak, token);
            secretService.checkToken(ak, token, null);
            PcMeta pcMeta = pbService.addPBucket(pbRequester.toPBucket());
            LOG.info("{} successfully add bucket {}", OpsTrace.get(), pcMeta);
            return ResponseEntity.ok(pcMeta);
        } catch (Exception e) {
            LOG.error("{} exception to add bucket", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
