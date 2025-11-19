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

import com.cloud.pc.model.PcpPulseInfo;
import com.cloud.pc.model.PmsInfo;
import com.cloud.pc.model.PmsPulseInfo;
import com.cloud.pc.service.MetaService;
import com.cloud.pc.service.PmsService;
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

@Api(tags = "PCache Meta Server（PMS）")
@RequestMapping("/api/v1/pms")
@RestController
public class PmsController {
    private static final Logger LOG = LoggerFactory.getLogger(PmsController.class);

    @Autowired
    PmsService pmsService;

    @Autowired
    MetaService metaService;

    @Autowired
    SecretService secretService;


    @ApiOperation(value = "PCP Pulse")
    @PostMapping(value = "/pcp/pulse", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> pulse(
            @RequestBody PcpPulseInfo pulseInfo,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String errorMsg;
        try {
            OpsTrace.set("pcp-pulse");
            LOG.info("{} info={} ak={} token={}", OpsTrace.get(), pulseInfo, ak, token);
            secretService.checkToken(ak, token, null);
            pmsService.pcpPulse(pulseInfo);
            LOG.info("{} success", OpsTrace.get() );
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            errorMsg = e.getMessage();
            LOG.error("{} exception to deal with PCP pulse", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(errorMsg);
    }

    @ApiOperation(value = "PMS Pulse")
    @PostMapping(value = "/pms/pulse", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> pulse(
            @RequestBody PmsPulseInfo pmsPulseInfo,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String errorMsg = "";
        try {
            OpsTrace.set("pms-pulse");
            LOG.info("{} receive pulse from {} ",  OpsTrace.get(), pmsPulseInfo);
            secretService.checkToken(ak, token, null);
            pmsService.receivePulse(pmsPulseInfo);
            LOG.info("{} success", OpsTrace.get());
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            errorMsg = e.getMessage();
            LOG.error("{} exception to deal with PMS pulse", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(errorMsg);
    }

    @ApiOperation(value = "Query MS List")
    @GetMapping(value = "/list")
    public ResponseEntity<?> getPmsList(
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        try {
            OpsTrace.set("list-pms");
            LOG.info("{} list PMS", OpsTrace.get());
            secretService.checkToken(ak, token, null);
            List<PmsInfo> retMap = pmsService.getPmsList();
            LOG.info("{} success, return {}", OpsTrace.get(), retMap);
            return ResponseEntity.ok(retMap);
        } catch (Exception e) {
            LOG.error("{} failed to list PMS!", OpsTrace.get(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @ApiOperation(value = "Get Meta")
    @GetMapping(value = "/meta")
    public ResponseEntity<?> dumpMeta(
            @RequestParam(value = "sync", defaultValue = "false") Boolean sync,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        OpsTrace.set("dump-meta");
        LOG.info("{} dump all meta", OpsTrace.get());
        String respMsg;
        try {
            secretService.checkToken(ak, token, null);
            String ret = metaService.dumpMeta(sync);
            LOG.info("{} success", OpsTrace.get());
            return ResponseEntity.ok(ret);
        } catch (Exception e) {
            LOG.error("{} failed to dump meta!", OpsTrace.get(), e);
            respMsg = e.getMessage();
        }
        LOG.warn("{} failed to dump meta with error:", OpsTrace.get(), respMsg);
        return ResponseEntity.internalServerError().body(respMsg);
    }

    @ApiOperation(value = "PCP Pulse")
    @GetMapping(value = "/leader/enable", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> enableLeader(
            @RequestParam(value = "enableWrite", defaultValue = "false") Boolean enableLeader,
            @RequestHeader(required = false, value = "X-AK") String ak,
            @RequestHeader(required = false, value = "X-TOKEN") String token) {
        String errorMsg;
        try {
            OpsTrace.set("enable-leader");
            LOG.info("{} enableLeader={} ak={} token={}", OpsTrace.get(), enableLeader, ak, token);
            secretService.checkToken(ak, token, null);
            pmsService.enableLeader(enableLeader);
            LOG.info("{} success", OpsTrace.get() );
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            errorMsg = e.getMessage();
            LOG.error("{} exception to deal with PCP pulse", OpsTrace.get(), e);
        }
        return ResponseEntity.internalServerError().body(errorMsg);
    }
}
