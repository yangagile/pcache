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

import com.cloud.pc.entity.*;
import com.cloud.pc.model.PcPermission;
import com.cloud.pc.chash.PcpHashInfo;
import com.cloud.pc.model.PmsInfo;
import com.cloud.pc.model.routing.RoutingResult;
import com.cloud.pc.entity.StsCacheKey;
import com.cloud.pc.entity.StsCacheValue;
import com.cloud.pc.service.cache.PcpCache;
import com.cloud.pc.service.cache.StsCache;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.HttpUtils;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.SecretUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;

public class PmsMgrImpl implements PmsMgr{
    public static final String URL_PARAMS_JOIN = "?";
    private static Logger LOG = LoggerFactory.getLogger(PmsMgrImpl.class);
    PmsProbe pmsProbe;
    PcpCache pcpCache = new PcpCache();
    String ak;
    String sk;

    public PmsMgrImpl(String pmsMgr, String ak, String sk) {
        pmsProbe = new PmsProbe(pmsMgr, this);
        this.ak = ak;
        this.sk = sk;

        refreshPmsUrl();
    }

    private Map<String, String> getPmsHeader(long expirationMs, Map<String, Object> claims) {
        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "application/json;charset=UTF-8"); // 要求响应为 JSON
        headers.put("X-AK", ak);
        headers.put("X-TOKEN", SecretUtils.generateToken(ak, sk, expirationMs, claims));
        return headers;
    }

    private String getPmsUrl(String path) {
        return FileUtils.mergePath(pmsProbe.getPmsUrl(), path);
    }

    private void refreshPmsUrl() {
        new Thread(pmsProbe).start();
    }

    public List<PmsInfo> getPmsApi(String url) {
        StringBuilder path = new StringBuilder(FileUtils.mergePath(url,"/api/v1/pms/list"));
        try {
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(path.toString(),
                    "GET", getPmsHeader(30000, null), null, null);
            if (response.getStatusCode() == 200) {

                List<PmsInfo> pmsInfos = JsonUtils.parseList(response.getBody(), PmsInfo.class);
                LOG.info("got pms list:{} ", pmsInfos);
                return pmsInfos;
            } else {
                LOG.error("failed to get pms list! error:{}", response.getStatusCode());
            }
        } catch (IOException e) {
            LOG.error("exception to get pms list!", e);
        }
        return null;
    }

    public BucketInfo getBucketInfoApi(String bucket) {
        if (StringUtils.isBlank(bucket)) {
            LOG.error(" bucket can not be null!");
            return null;
        }
        StringBuilder path = new StringBuilder(getPmsUrl("api/v1/pb/"));
        path.append(bucket).append("/info");
        Map<String, String> params = new HashMap<>();

        try {
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(path.toString(),
                    "GET", getPmsHeader(30000, null), params, null);
            if (response.getStatusCode() == 200) {
                BucketInfo info = JsonUtils.fromJson(response.getBody(), BucketInfo.class);
                LOG.info("got bucket:{} info:{}", bucket, info);
                return info;
            } else {
                LOG.error("failed to get bucket:{} info! error:{}", bucket, response.getStatusCode());
            }
        } catch (IOException e) {
            LOG.error("exception to get bucket:{} info!", bucket, e);
        }
        return null;
    }

    public RoutingResult getVirtualBucketSTSApi(String bucket, String storedPath,
        List<PcPermission> permissions, int expirationInSeconds) {

        StsCacheKey key = new StsCacheKey(bucket, storedPath, permissions);
        StsCacheValue voCache = StsCache.getCache().getIfPresent(key);
        if (voCache != null) {
            if (voCache.getVo() != null && !voCache.isExpired()) {
                return voCache.getVo();
            }
        }
        RoutingResult vo = requestSTSApi(bucket, storedPath, permissions, expirationInSeconds);
        StsCache.getCache().put(key, new StsCacheValue(vo, expirationInSeconds));
        return vo;
    }

    private RoutingResult requestSTSApi(String bucket, String prefix,
        List<PcPermission> permissions, int expirationInSeconds) {
        LOG.info("request STS, bucket:{} prefix:{} permission:{} expirationInSeconds:{}",
                bucket, prefix, permissions, expirationInSeconds);
        if (CollectionUtils.isEmpty(permissions)) {
            LOG.error("permissions parameter cannot be empty.");
            return null;
        }

        StringBuilder path = new StringBuilder(getPmsUrl("api/v1/pb/"));
        path.append(bucket).append("/sts");
        Map<String, String> params = new HashMap<>();
        if (-1 != expirationInSeconds) {
            params.put("expirationInSeconds", Integer.toString(expirationInSeconds));
        }
        try {
            if (StringUtils.isNotBlank(prefix)) {
                prefix = URLEncoder.encode(prefix, "UTF-8");
            }
            params.put("path", StringUtils.defaultIfBlank(prefix, ""));
            for(PcPermission permission : permissions) {
                params.put("permissions", permission.name());
            }
            try {
                Map<String, Object> claims = new HashMap<>();
                claims.put("bucket", bucket);
                claims.put("path", prefix);
                claims.put("permissions", permissions);
                HttpUtils.HttpResponse response = HttpUtils.sendRequest(path.toString(),
                        "GET", getPmsHeader(30000, claims), params, null);
                if (response.getStatusCode() == 200) {
                    RoutingResult routingResult = JsonUtils.fromJson(response.getBody(), RoutingResult.class);
                    LOG.info("got STS:{}", routingResult);
                    return routingResult;
                } else {
                    LOG.error("failed to get STS! code:{} msg:{}" ,
                            response.getStatusCode(), response.getBody());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            LOG.error("failed to get STS! " , e);
        }
        return null;
    }

    public String getPcp(String key) {
        String pcp = pcpCache.get(key);
        if (pcp == null) {
            PcpHashInfo newPcpHashInfo = getPcpHashListApi(pcpCache.getChecksum());
            pcpCache.updateCache(newPcpHashInfo);
        }
        return pcpCache.get(key);
    }

    public PcpHashInfo getPcpHashListApi(String slotTableChecksum) {
        LOG.info("get lot table, checksume:{}", slotTableChecksum);
        String url = getPmsUrl("api/v1/pcp/hash");
        Map<String, String> params = new HashMap<>();
        params.put("checksum", slotTableChecksum);
        try {
            HttpUtils.HttpResponse response = HttpUtils.sendRequest(url,
                    "GET", getPmsHeader(30000, null), params, null);
            if (response.getStatusCode() == 200) {
                return JsonUtils.fromJson(response.getBody(), PcpHashInfo.class);
            } else {
                LOG.error("failed to get STS! error:{}" , response.getStatusCode());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
