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

import com.cloud.pc.model.S3ClientCacheKey;
import com.cloud.pc.model.StsInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class S3ClientCache {
    public final static Cache<S3ClientCacheKey, S3Client> s3SyncClientCache = buildS3SyncClientCache();

    protected static final boolean clientCacheEnabled = ComUtils.getProps(
            "pc.s3.client.cache.enabled", true, Boolean::valueOf);
    private static final Boolean checksumEnable = ComUtils.getProps(
            "pc.s3.client.checksum.enable", null, Boolean::valueOf);

    private static Cache<S3ClientCacheKey, S3Client> buildS3SyncClientCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(50)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .removalListener((RemovalListener<S3ClientCacheKey, S3Client>) ele -> {
                    S3Client client;
                    if ((client = ele.getValue()) != null) {
                        client.close();
                    }
                })
                .build();
    }

    private static S3Configuration buildS3Configuration(String storageType) {
        S3Configuration.Builder builder = S3Configuration.builder();
        switch (storageType) {
            case "BOS":
                return builder.checksumValidationEnabled(checksumEnable != null ? checksumEnable : true)
                        .chunkedEncodingEnabled(false).build();
            case "OSS":
                return builder.checksumValidationEnabled(checksumEnable != null ? checksumEnable : true)
                        .chunkedEncodingEnabled(false).pathStyleAccessEnabled(false).build();
            case "TOS":
                return builder.checksumValidationEnabled(checksumEnable != null ? checksumEnable : true)
                        .chunkedEncodingEnabled(true).build();
            case "S3":
                return builder.checksumValidationEnabled(checksumEnable != null ? checksumEnable : true)
                        .chunkedEncodingEnabled(true).build();
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

    private static S3Client newS3ClientInstance(StsInfo stsInfo) {
        return S3Client.builder()
                .serviceConfiguration(buildS3Configuration(stsInfo.getStorageType()))
                .endpointOverride(URI.create(stsInfo.getS3Endpoint()))
                .region(Region.of(stsInfo.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(buildAwsCredentials(stsInfo)))
                .build();
    }

    private static S3ClientCacheKey buildS3ClientSessionKey(StsInfo stsInfo) {
        return new S3ClientCacheKey(stsInfo.getAccessKey(),
                stsInfo.getAccessSecret(),
                stsInfo.getSecurityToken());
    }

    private static void closeS3Client(S3Client client) {
        if (clientCacheEnabled || client == null) {
            return;
        }
        try {
            client.close();
        } catch (Throwable e) {
        }
    }

    public static S3Client buildS3Client(StsInfo stsInfo, boolean untrackedLifecycleInstance) {
        if (untrackedLifecycleInstance || !clientCacheEnabled) {
            return newS3ClientInstance(stsInfo);
        }

        S3ClientCacheKey key = buildS3ClientSessionKey(stsInfo);
        try {
            return s3SyncClientCache.get(key, () -> newS3ClientInstance(stsInfo));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
