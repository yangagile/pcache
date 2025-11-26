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
	"fmt"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"testing"
)

var testRootPrefix = "test/pcache/go/sdk/"
var testRootLocal = "~/tmp/pbucket_test/" // will use os.TempDir()

var pmsUrl = "http://127.0.0.1:8080"
var ak = "unittest"
var sk = "3ewGHUIayI8cZ8qgAkoJ31gXvGqAzKmmsTLqMhTrhyM="
var bucket = "test-minio"

func TestMain(m *testing.M) {
	setup()

	code := m.Run()

	teardown()

	os.Exit(code)
}

func setup() {
	testRootLocal = utils.MergePath(os.TempDir(), "pbucket_test")
	info, err := os.Stat(testRootLocal)
	if err == nil {
		if info.IsDir() {
			os.Remove(testRootLocal)
		} else {
			panic(fmt.Errorf("root %v is not valid dir", testRootLocal))
		}
	}
	err = os.MkdirAll(testRootLocal, 0755)
	if err != nil {
		panic(err)
	}
}

func teardown() {
	// clean temp files
	os.Remove(testRootLocal)
}

func Test_PutGet_SmallFileFromLocal(t *testing.T) {
	ctx := utils.WithStatCounter(context.Background())

	bucket, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create bucket: %v", err)
	}

	// disable PCache
	bucket.EnablePCache(false)

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName
	downloadPath := testRootLocal + fileName

	err = bucket.Put(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	stat := utils.GetStatCounter(ctx)
	if stat.CountLocal <= 0 {
		t.Fatalf("failed to put from local")
	}
	ctx = utils.WithStatCounter(context.Background())
	err = bucket.Get(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stat = utils.GetStatCounter(ctx)
	if stat.CountLocal <= 0 {
		t.Fatalf("failed to put from local")
	}
}

func Test_PutGet_SmallFileFromPcp(t *testing.T) {
	ctx := utils.WithStatCounter(context.Background())

	bucket, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create bucket: %v", err)
	}

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName
	downloadPath := testRootLocal + fileName

	err = bucket.Put(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	stat := utils.GetStatCounter(ctx)
	if stat.CountPcpLocal <= 0 {
		t.Fatalf("failed to put from PCP")
	}
	ctx = utils.WithStatCounter(context.Background())
	err = bucket.Get(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stat = utils.GetStatCounter(ctx)
	if stat.CountPcpCache <= 0 {
		t.Fatalf("failed to get from PCP")
	}
}

func Test_PutWithPCache(t *testing.T) {
	ctx := utils.WithStatCounter(context.Background())
	pb, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to new PBucket with err:%v", err)
	}

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(10 * 1024 * 1024) // 10MB
	uploadLocalPath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	uploadKey := testRootPrefix + fileName

	err = pb.Put(ctx, uploadLocalPath, uploadKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}
	stats := utils.GetStatCounter(ctx)
	if stats.CountPcpLocal <= 0 {
		t.Fatalf("failed to use PCP")
	}

	downloadPath := testRootLocal + fileName + ".download"

	ctx = utils.WithStatCounter(context.Background())
	err = pb.Get(ctx, uploadKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stats = utils.GetStatCounter(ctx)
	if stats.CountPcpCache <= 0 {
		t.Fatalf("failed to use PCP")
	}
}
