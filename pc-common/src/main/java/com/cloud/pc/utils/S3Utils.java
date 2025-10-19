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

import com.cloud.pc.model.ClientCreationInfo;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sts.StsClient;

import java.net.URI;

public class S3Utils {

    public static boolean isGetObjectSuccessful(GetObjectResponse response) {
        if (response == null) {
            return false;
        }
        return response.sdkHttpResponse().isSuccessful();
    }

    public static boolean isGetObjectSuccessful(ResponseInputStream<GetObjectResponse> result) {
        if (result == null) {
            return false;
        }
        GetObjectResponse rs = result.response();
        if (rs != null) {
            return rs.sdkHttpResponse().isSuccessful();
        }
        return false;
    }

    public static boolean isPutObjectSuccessful(PutObjectResponse response) {
        if (response == null) {
            return false;
        }
        return StringUtils.isNotBlank(response.eTag());
    }

    public static PutObjectRequest copyPutObjectRequest(
            PutObjectRequest originalRequest, String bucketName, String key) {
        PutObjectRequest newPutRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(originalRequest.contentType())
                .contentLength(originalRequest.contentLength())
                .contentDisposition(originalRequest.contentDisposition())
                .build();
        return newPutRequest;
    }

    public static HeadObjectResponse headObject(S3Client s3Client, String bucketName, String objectKey) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        return s3Client.headObject(headObjectRequest);
    }

    public static GetObjectResponse HeadObject2GetObjectResponse(HeadObjectResponse headObjectResponse) {
        if (headObjectResponse == null) {
            return null;
        }
        return GetObjectResponse.builder()
                .acceptRanges(headObjectResponse.acceptRanges())
                .contentLength(headObjectResponse.contentLength())
                .eTag(headObjectResponse.eTag())
                .checksumCRC32(headObjectResponse.checksumCRC32())
                .contentDisposition(headObjectResponse.contentDisposition())
                .contentType(headObjectResponse.contentType())
                .expires(headObjectResponse.expires())
                .lastModified(headObjectResponse.lastModified())
                .contentType(headObjectResponse.contentType())
                .metadata(headObjectResponse.metadata())
                .build();
    }

    public static PutObjectResponse convertToPutObjectResponse(
            CompleteMultipartUploadResponse completeResponse
    ) {
        return PutObjectResponse.builder()
                .eTag(completeResponse.eTag())
                .versionId(completeResponse.versionId())
                .expiration(completeResponse.expiration())
                .serverSideEncryption(completeResponse.serverSideEncryption())
                .ssekmsKeyId(completeResponse.ssekmsKeyId())
                .bucketKeyEnabled(completeResponse.bucketKeyEnabled())
                .requestCharged(completeResponse.requestCharged())
                .build();
    }

    public static String getContentDispositionHeaderValue(String type, String fileName) {
        return type + "; filename*=UTF-8''" + fileName;
    }

    public static StsClient createStsClient(ClientCreationInfo clientCreationVO, ApacheHttpClient.Builder build) {
        return StsClient.builder().credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(clientCreationVO.getAccessKey(),
                        clientCreationVO.getAccessSecret())))
                .region(Region.of(clientCreationVO.getRegion()))
                .endpointOverride(URI.create(clientCreationVO.getEndpoint()))
                .httpClient(build.build())
                .build();
    }
}
