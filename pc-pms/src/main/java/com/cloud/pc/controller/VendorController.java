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

import com.cloud.pc.meta.PcMeta;
import com.cloud.pc.meta.VendorBucket;
import com.cloud.pc.requester.VendorBucketRequester;
import com.cloud.pc.requester.VendorRequester;
import com.cloud.pc.service.PmsService;
import com.cloud.pc.service.SecretService;
import com.cloud.pc.service.VendorService;
import com.cloud.pc.utils.OpsTrace;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "Vendor Manager")
@RequestMapping("/api/v1/vendor")
@RestController
public class VendorController {
    private static final Logger LOG = LoggerFactory.getLogger(VendorController.class);

    @Autowired
    VendorService vendorService;

    @Autowired
    SecretService secretService;

    @Autowired
    PmsService pmsService;

    @ApiOperation(value = "Add Vendor")
    @PostMapping(value = "/add", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> adVendor(
            @RequestBody VendorRequester vendorRequester,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        try {
            OpsTrace.set("add-vendor");
            LOG.info("{} requester={} ak={} token={}", OpsTrace.get(), vendorRequester, ak, token);
            secretService.checkToken(ak, token, null);
            pmsService.checkLeader();
            PcMeta pcMeta = vendorService.addVendor(vendorRequester.toVendor());
            LOG.info("{} successfully add vendor {}", OpsTrace.get(), pcMeta);
            return ResponseEntity.ok(pcMeta);
        } catch (Exception e) {
            LOG.error("{} exception to add vendor", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @ApiOperation(value = "Add Vendor Bucket")
    @PostMapping(value = "/bucket/add", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> adVendorBucket(
            @RequestBody VendorBucketRequester vbRequester,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        try {
            OpsTrace.set("add-vendor-bucket");
            LOG.info("{} requester={} ak={} token={}", OpsTrace.get(), vbRequester, ak, token);
            secretService.checkToken(ak, token, null);
            pmsService.checkLeader();
            PcMeta pcMeta = vendorService.addVendorBucket(vbRequester.toVendorBucket());
            LOG.info("{} successfully add vendor bucket {}", OpsTrace.get(), pcMeta);
            return ResponseEntity.ok(pcMeta);
        } catch (Exception e) {
            LOG.error("{} exception to add vendor bucket", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @ApiOperation(value = "List Vendor Bucket")
    @PostMapping(value = "/bucket/list", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> list(
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        try {
            OpsTrace.set("list-vb");
            LOG.info("{} ak={} token={}", OpsTrace.get(), ak, token);
            secretService.checkToken(ak, token, null);
            List<VendorBucket> buckets = vendorService.listVendorBucket();
            LOG.info("{} success to get vendor bucket list {}", OpsTrace.get(), buckets);
            return ResponseEntity.ok(buckets);
        } catch (Exception e) {
            LOG.error("{} failed to list VB, unknown error", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
