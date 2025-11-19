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
import com.cloud.pc.utils.ComUtils;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.HttpUtils;
import com.cloud.pc.utils.JsonUtils;
import com.cloud.pc.utils.SecretUtils;
import com.cloud.pc.utils.UrlProbe;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.stream.Collectors;

public class PmsMgrImpl implements PmsMgr, UrlProbe.IUrlProbeFunction{
    public static final String URL_PARAMS_JOIN = "?";
    private static Logger LOG = LoggerFactory.getLogger(PmsMgrImpl.class);
    UrlProbe pmsProbe;
    PcpCache pcpCache = new PcpCache();
    String ak;
    String sk;
    public int retryTimes = ComUtils.getProps("pc.pms.retry.times",
            3, Integer::valueOf);

    public PmsMgrImpl(String pmsMgr, String ak, String sk) {
        this.ak = ak;
        this.sk = sk;
        pmsProbe = new UrlProbe(pmsMgr, this);
    }

    private Map<String, String> getPmsHeader(long expirationMs, Map<String, Object> claims) {
        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "application/json;charset=UTF-8"); // 要求响应为 JSON
        if (StringUtils.isNotBlank(ak)) {
            headers.put("X-AK", ak);
            if (StringUtils.isNotBlank(sk)) {
                headers.put("X-TOKEN", SecretUtils.generateToken(ak, sk, expirationMs, claims));
            }
        }
        return headers;
    }

    public BucketInfo getBucketInfoApi(String bucket) {
        for (int i = 0; i < retryTimes; i++) {
            String url = FileUtils.mergePath(pmsProbe.getUrl(), "api/v1/pb/");
            try {
                BucketInfo bucketInfo = getBucketInfoApiInternal(bucket, url);
                return bucketInfo;
            }  catch (Exception e) {
                LOG.error("exception to get bucket:{} info! retry {}/{} times", bucket, i, retryTimes, e);
                pmsProbe.reportFail(url);
            }
        }
        return null;
    }

    private BucketInfo getBucketInfoApiInternal(String bucket, String url) throws IOException {
        if (StringUtils.isBlank(bucket)) {
            LOG.error(" bucket can not be null!");
            return null;
        }
        StringBuilder path = new StringBuilder(url);
        path.append(bucket).append("/info");
        Map<String, String> params = new HashMap<>();

        HttpUtils.HttpResponse response = HttpUtils.sendRequest(path.toString(),
                "GET", getPmsHeader(30000, null), params, null);
        if (response.getStatusCode() == 200) {
            BucketInfo info = JsonUtils.fromJson(response.getBody(), BucketInfo.class);
            LOG.info("got bucket:{} info:{}", bucket, info);
            return info;
        } else {
            LOG.error("failed to get bucket:{} info! error:{}", bucket, response.getStatusCode());
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

    public RoutingResult requestSTSApi(String bucket, String prefix,
                                       List<PcPermission> permissions, int expirationInSeconds) {
        LOG.info("request STS, bucket:{} prefix:{} permission:{} expirationInSeconds:{}",
                bucket, prefix, permissions, expirationInSeconds);
        if (CollectionUtils.isEmpty(permissions)) {
            LOG.error("permissions parameter cannot be empty.");
            throw new InvalidParameterException("permissions parameter cannot be empty.");
        }

        if (StringUtils.isNotBlank(prefix)) {
            try {
                prefix = URLEncoder.encode(prefix, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error("failed to encode prefix with UTF-8.", e);
                throw new RuntimeException(e);
            }
        }

        for (int i = 0; i < retryTimes; i++) {
            String url = FileUtils.mergePath(pmsProbe.getUrl(), "api/v1/pb/");
            try {
                RoutingResult routingResult = requestSTSApiInternal(bucket, prefix,
                        permissions, expirationInSeconds, url);
                return routingResult;
            }  catch (Exception e) {
                LOG.error("exception to get STS of bucket:{}! retry {}/{} times", bucket, i, retryTimes, e);
                pmsProbe.reportFail(url);
            }
        }
        return null;
    }

    private RoutingResult requestSTSApiInternal(String bucket, String prefix,
        List<PcPermission> permissions, int expirationInSeconds, String url) throws IOException{
        StringBuilder path = new StringBuilder(url);
        path.append(bucket).append("/sts");
        Map<String, String> params = new HashMap<>();
        if (-1 != expirationInSeconds) {
            params.put("expirationInSeconds", Integer.toString(expirationInSeconds));
        }
        params.put("path", StringUtils.defaultIfBlank(prefix, ""));
        for (PcPermission permission : permissions) {
            params.put("permissions", permission.name());
        }

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
            LOG.error("failed to get STS! code:{} msg:{}",
                    response.getStatusCode(), response.getBody());
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
        LOG.info("get PCP hash list, checksume:{}", slotTableChecksum);
        for (int i = 0; i < retryTimes; i++) {
            String url = FileUtils.mergePath(pmsProbe.getUrl(), "api/v1/pcp/hash");
            try {
                PcpHashInfo pcpHashInfo = getPcpHashListApiInternal(slotTableChecksum, url);
                return pcpHashInfo;
            }  catch (Exception e) {
                LOG.error("exception to get PCP hash list! retry {}/{} times", i, retryTimes, e);
                pmsProbe.reportFail(url);
            }
        }
        return null;
    }

    public PcpHashInfo getPcpHashListApiInternal(String slotTableChecksum, String url) throws IOException{
        Map<String, String> params = new HashMap<>();
        params.put("checksum", slotTableChecksum);
        HttpUtils.HttpResponse response = HttpUtils.sendRequest(url,
                "GET", getPmsHeader(30000, null), params, null);
        if (response.getStatusCode() == 200) {
            return JsonUtils.fromJson(response.getBody(), PcpHashInfo.class);
        } else {
            LOG.error("failed to get STS! error:{}" , response.getStatusCode());
        }
        return null;
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

    public List<String> getUrlList(String url) {
        List<PmsInfo> pmsInfos = getPmsApi(url);

        if (pmsInfos == null || pmsInfos.isEmpty()) {
            return Collections.emptyList();
        }
        return pmsInfos.stream()
                .map(PmsInfo::getHost)
                .collect(Collectors.toList());
    }
}
