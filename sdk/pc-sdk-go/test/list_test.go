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

package test

import (
	"context"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	log "github.com/sirupsen/logrus"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/bucket"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"strconv"
	"testing"
)

func Test_ListObjects(t *testing.T) {
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())

	// create pbucket
	pb, err := bucket.NewPBucketWithOptions(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,ListObject,DeleteObject"})
	if err != nil {
		t.Fatalf("failed to create Bucket: %v", err)
	}
	defer pb.Close()

	// create test file
	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	defer os.Remove(localFilePath)

	prefix := utils.MergePath(cfg.Bucket.Prefix, "test_list/")

	// first to delete all file in this prefix
	err = deletePrefix(pb, prefix)
	if err != nil {
		log.WithError(err).WithField("prefix", prefix).Errorln("failed to clean test prefix")
	}

	// put new batch of objects with same local file to prefix
	fileNumber := 10
	for i := 0; i < fileNumber; i++ {
		fileKey := prefix + fileName + strconv.Itoa(i)
		_, err = pb.PutObject(ctx, localFilePath, fileKey)
		if err != nil {
			t.Fatalf("failed to put file:%v with err:%v", fileName, err)
		}
	}
	defer deletePrefix(pb, prefix)

	// list all in this prefix
	listCnt := 0
	dataSize := int64(0)
	var continuationToken *string
	for {
		input := &s3.ListObjectsV2Input{
			Prefix:            aws.String(prefix),
			ContinuationToken: continuationToken, // 设置分页令牌
		}
		result, err := pb.ListObjectsV2(context.TODO(), input)
		if err != nil {
			log.WithError(err).WithField("prefix", prefix).Errorln("failed to list object")
			break
		}
		for _, object := range result.Contents {
			println("list key: " + *object.Key)
			listCnt++
			dataSize += *object.Size
		}
		if *result.IsTruncated && result.NextContinuationToken != nil {
			continuationToken = result.NextContinuationToken
		} else {
			break
		}
	}

	// verify count and size
	if listCnt != fileNumber {
		t.Fatalf("failed to list prefix: %v, expect file count:%v, but got:%v",
			prefix, fileNumber, listCnt)
	}
	if dataSize != fileSize*int64(fileNumber) {
		t.Fatalf("data size mismatch, expect data size:%v, but got:%v",
			fileSize*int64(fileNumber), dataSize)
	}
}

func deletePrefix(pb *bucket.PBucket, prefix string) error {
	var continuationToken *string
	for {
		input := &s3.ListObjectsV2Input{
			Prefix:            aws.String(prefix),
			ContinuationToken: continuationToken, // 设置分页令牌
		}
		result, err := pb.ListObjectsV2(context.TODO(), input)
		if err != nil {
			return err
		}
		for _, object := range result.Contents {
			_, err = pb.DeleteObject(context.TODO(), *object.Key)
			if err != nil {
				return err
			}
		}
		if *result.IsTruncated && result.NextContinuationToken != nil {
			continuationToken = result.NextContinuationToken
		} else {
			break
		}
	}
	return nil
}
