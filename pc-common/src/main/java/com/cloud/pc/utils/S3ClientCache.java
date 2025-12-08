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

package com.cloud.pc.utils;

import com.cloud.pc.model.StsInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class S3ClientCache {
    private static final Logger LOG = LoggerFactory.getLogger(UrlProbe.class);
    public static Cache<StsInfo, S3Client> s3SyncClientCache ;

    private static final String CONFIG_PREFIX = "pc.s3.client.";

    protected static final boolean CACHE_ENABLED = ComUtils.getProps(
            CONFIG_PREFIX + "cache.enabled", true, Boolean::valueOf);
    private static final int CACHE_MAX_SIZE = ComUtils.getProps(
            CONFIG_PREFIX + "cache.max.size", 50, Integer::valueOf);
    private static final int CACHE_EXPIRE_TIME_MINUTES = ComUtils.getProps(
            CONFIG_PREFIX + "cache.expire.time.minutes", 15, Integer::valueOf);

    private static final Boolean CONFIG_CHECKSUM_ENABLED = ComUtils.getProps(
            CONFIG_PREFIX + "config.checksum.enabled", true, Boolean::valueOf);

    // HTTP pool configuration
    private static final int HTTP_MAX_CONNECTIONS = ComUtils.getProps(
            CONFIG_PREFIX + "http.maxConnections", 100, Integer::valueOf);
    private static final int HTTP_CONNECTION_TIMEOUT_SECONDS = ComUtils.getProps(
            CONFIG_PREFIX + "http.connectionTimeout", 10, Integer::valueOf);
    private static final int SOCKET_TIMEOUT_SECONDS = ComUtils.getProps(
            CONFIG_PREFIX + "http.socketTimeout", 30, Integer::valueOf);

    private static Cache<StsInfo, S3Client> buildS3SyncClientCache() {
        if (!CACHE_ENABLED) {
            LOG.debug("S3 client cache is disabled");
            return null;
        }

        return CacheBuilder.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterAccess(CACHE_EXPIRE_TIME_MINUTES, TimeUnit.MINUTES)
                .removalListener((RemovalListener<StsInfo, S3Client>) ele -> {
                    S3Client client;
                    if ((client = ele.getValue()) != null) {
                        String cause = ele.getCause().name();
                        LOG.debug("remove S3 client from cache. cause: {}, key: {}",
                                cause, ele.getKey().getAccessKey());
                        try {
                            client.close();
                        } catch (Exception e) {
                            LOG.error("exception to close S3 client, key: {}",
                                    ele.getKey().getAccessKey(), e);
                        }
                    }
                })
                .build();
    }

    private static S3Configuration buildS3Configuration(String storageType) {
        S3Configuration.Builder builder = S3Configuration.builder();
        if (CONFIG_CHECKSUM_ENABLED != null) {
            builder.checksumValidationEnabled(CONFIG_CHECKSUM_ENABLED);
        }
        switch (storageType) {
            case "BOS":
                return builder.chunkedEncodingEnabled(false).build();
            case "OSS":
                return builder.chunkedEncodingEnabled(false)
                        .pathStyleAccessEnabled(false).build();
            case "TOS":
            case "S3":
            default:
                return builder.chunkedEncodingEnabled(true).build();
        }
    }

    private static AwsCredentials buildAwsCredentials(StsInfo stsInfo) {
        return new AwsSessionCredentials.Builder()
                .accessKeyId(stsInfo.getAccessKey())
                .secretAccessKey(stsInfo.getAccessSecret())
                .sessionToken(stsInfo.getSecurityToken())
                .build();
    }

    private static SdkHttpClient buildApacheHttpClient() {
        return ApacheHttpClient.builder()
                .maxConnections(HTTP_MAX_CONNECTIONS)
                .connectionTimeout(Duration.ofSeconds(HTTP_CONNECTION_TIMEOUT_SECONDS))
                .socketTimeout(Duration.ofSeconds(SOCKET_TIMEOUT_SECONDS))
                .connectionTimeToLive(Duration.ofMinutes(5)) // 连接TTL
                .build();
    }

    private static S3Client newS3ClientInstance(StsInfo stsInfo) {
        if (stsInfo == null) {
            throw new IllegalArgumentException("StsInfo cannot be null");
        }
        return S3Client.builder()
                .serviceConfiguration(buildS3Configuration(stsInfo.getStorageType()))
                .endpointOverride(URI.create(stsInfo.getEndpoint()))
                .region(Region.of(stsInfo.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(buildAwsCredentials(stsInfo)))
                .httpClient(buildApacheHttpClient()) // user http pool
                .build();
    }

    private static void closeS3Client(S3Client client) {
        if (CACHE_ENABLED || client == null) {
            return;
        }
        try {
            client.close();
        } catch (Throwable e) {
        }
    }

    public static S3Client buildS3Client(StsInfo stsInfo, boolean uncached) {
        if (uncached || !CACHE_ENABLED) {
            LOG.debug("creating uncached S3 client for key: {}", stsInfo.getAccessKey());
            return newS3ClientInstance(stsInfo);
        }

        try {
            if (s3SyncClientCache == null) {
                synchronized (S3ClientCache.class) {
                    if (s3SyncClientCache == null) {
                        s3SyncClientCache = buildS3SyncClientCache();
                    }
                }
            }
            return s3SyncClientCache.get(stsInfo, () -> newS3ClientInstance(stsInfo));
        } catch (Throwable e) {
            LOG.error("exception to get S3 client from cache for key: {}", stsInfo.getAccessKey(), e);
            return newS3ClientInstance(stsInfo);
        }
    }

    public static void invalidateCache() {
        if (s3SyncClientCache != null) {
            s3SyncClientCache.invalidateAll();
        }
    }
}
