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

import com.cloud.pc.model.PcpInfo;
import com.cloud.pc.chash.PcpHashInfo;
import com.cloud.pc.requester.PcpAddRequester;
import com.cloud.pc.service.PcpService;
import com.cloud.pc.service.SecretService;
import com.cloud.pc.utils.OpsTrace;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "Parallel Cache Point（PCP）")
@RequestMapping("/api/v1/pcp")
@RestController
public class PcpController {
    private static final Logger LOG = LoggerFactory.getLogger(PcpController.class);

    @Autowired
    PcpService pcpService;

    @Autowired
    SecretService secretService;

    @ApiOperation(value = "List PCP")
    @GetMapping(value = "/list", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> list(
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        try {
            OpsTrace.set("list-pcp");
            LOG.info("{} ak={} token={}", OpsTrace.get(), ak, token);
            secretService.checkToken(ak, token, null);
            List<PcpInfo> pcps = pcpService.list();
            LOG.info("{} return PCP list:{}", OpsTrace.get(), pcps);
            return ResponseEntity.ok(pcps);
        } catch (Exception e) {
            LOG.error("{} exception to list PCP", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }

    }

    @ApiOperation(value = "Add new PCP")
    @PostMapping(value = "/add", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> add(
            @RequestBody PcpAddRequester pcpAddRequester,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String errorMsg;
        try {
            OpsTrace.set("add-pcp");
            LOG.info("{} requester={} ak={} token={}", OpsTrace.get(), pcpAddRequester, ak, token);
            secretService.checkToken(ak, token, null);
            pcpService.add(pcpAddRequester.toPCPInfo());
            LOG.info("{} success to add new PCP {}", OpsTrace.get(), pcpAddRequester);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            errorMsg = e.getMessage();
            LOG.error("{} exception to add PCP", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(errorMsg);
    }

    @ApiOperation(value = "Remove a PCP")
    @PostMapping(value = "/remove", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> remove(
            @RequestBody String host,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String errorMsg;
        try {
            OpsTrace.set("remove-pcp");
            LOG.info("{} host={} ak={} token={}", OpsTrace.get(), host, ak, token);
            secretService.checkToken(ak, token, null);
            int cnt = pcpService.remove(host);
            if (cnt == 1) {
                LOG.info("{} success to remove a PCP", OpsTrace.get());
                return ResponseEntity.ok("OK");
            }
            errorMsg = "failed remove PCP return " + cnt;
            LOG.error("{} remove PCP return {}", OpsTrace.get(), cnt);
        } catch (Exception e) {
            errorMsg = e.getMessage();
            LOG.error("{} failed to remove a PCP with unknown error", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(errorMsg);
    }

    @ApiOperation(value = "Get Hash Table")
    @GetMapping(value = "/hash", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> hashTable(
            @RequestParam(value = "checksum", defaultValue = "") String checksum,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        try {
            OpsTrace.set("pcp-hash-table");
            LOG.info("{} checksum={} ak={} token={}", OpsTrace.get(), checksum, ak, token);
            secretService.checkToken(ak, token, null);
            PcpHashInfo slotTable = pcpService.getHashList(checksum);
            LOG.info("{} return PCP hash table:{}", OpsTrace.get(), slotTable);
            return ResponseEntity.ok(slotTable);
        } catch (Exception e) {
            LOG.error("{} failed to get PCP hash table with unknown error", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
