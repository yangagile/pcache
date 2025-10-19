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

package utils

import (
	"context"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	pclient "testdisk/model"
)

func NewS3ClientWithSTS(ctx context.Context, stsInfo *pclient.StsInfo) (*s3.Client, error) {
	creds := credentials.NewStaticCredentialsProvider(stsInfo.AccessKey,
		stsInfo.AccessSecret, stsInfo.SecurityToken)

	cfg, err := config.LoadDefaultConfig(ctx,
		config.WithCredentialsProvider(creds),
		config.WithRegion(stsInfo.Region))
	if err != nil {
		return nil, err
	}
	cfg.BaseEndpoint = aws.String(stsInfo.S3Endpoint)
	client := s3.NewFromConfig(cfg, func(o *s3.Options) {
		o.UsePathStyle = true
	})
	return client, nil
}

func HeadObject(s3Client *s3.Client, bucketName string, objectKey string) (*s3.HeadObjectOutput, error) {
	// 构造 HeadObject 请求
	input := &s3.HeadObjectInput{
		Bucket: aws.String(bucketName),
		Key:    aws.String(objectKey),
	}
	return s3Client.HeadObject(context.TODO(), input)
}
