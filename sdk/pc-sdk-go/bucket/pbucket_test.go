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

package bucket

import (
	"context"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"log"
	"os"
	"testing"
)

var testRootPrefix = "test/pclient/go/"
var testRootLocal = "~/tmp/test/pclient/go/"

var pmsUrl = "http://127.0.0.1:8080"
var ak = "unittest"
var sk = "3ewGHUIayI8cZ8qgAkoJ31gXvGqAzKmmsTLqMhTrhyM="
var bucket = "test-minio"

func Test_PutGet(t *testing.T) {
	ctx := context.Background()
	client, err := NewPBucket(&ctx, pmsUrl, bucket, ak, sk,
		WithPermissions("PutObject,GetObject"),
		WithPCacheEnable(false))
	if err != nil {
		log.Fatalf("failed to create bucket: %v", err)
	}
	fileName := "Test_PutGet.dat"
	fileSize := int64(1 * 10244)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName
	downloadPath := testRootLocal + fileName

	err = client.Put(&ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	err = client.Get(&ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
}
func Test_PutWithPCache(t *testing.T) {
	ctx := context.Background()
	pb, err := NewPBucket(&ctx, pmsUrl, bucket, ak, sk, WithPermissions("PutObject,GetObject"))
	if err != nil {
		t.Fatalf("failed to new PBucket with err:%v", err)
	}

	fileName := "Test_PutWithPCache.dat"
	fileSize := int64(10 * 1024 * 1024) // 10MB
	uploadLocalPath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	uploadKey := testRootPrefix + fileName

	err = pb.Put(&ctx, uploadLocalPath, uploadKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}
	tr := utils.GetTracker(&ctx)
	counterPcpLocal := tr.GetMetric(utils.StatsCounterPcpLocalKey).(int)
	if counterPcpLocal <= 0 {
		t.Fatalf("failed to use PCP to put")
	}

	downloadPath := testRootLocal + fileName + ".download"
	_, err = os.Stat(downloadPath)
	if err == nil {
		// 文件存在，删除它
		err := os.Remove(downloadPath)
		if err != nil {
			t.Fatalf("failed to get old file:%v with err:%v", downloadPath, err)
		}
	}

	ctx = context.Background()
	err = pb.Get(&ctx, uploadKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	tr = utils.GetTracker(&ctx)
	counterPcpCache := tr.GetMetric(utils.StatsCounterPcpCacheKey).(int)
	if counterPcpCache <= 0 {
		t.Fatalf("failed to get PCP Cache")
	}
}
