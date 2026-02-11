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
	"github.com/yangagile/pcache/sdk/pc-sdk-go/bucket"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"testing"
)

func Test_PutGet_SmallFileWithoutPcp(t *testing.T) {
	// create pbucket
	ctx := bucket.WithOptions(context.Background())
	cfg := GetConfig()
	pb, err := bucket.NewPBucketWithOptions(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"}, bucket.WithPCacheEnable(false))
	if err != nil {
		t.Fatalf("failed to create Bucket: %v", err)
	}
	defer pb.Close()

	// create local file
	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	defer os.Remove(localFilePath)

	// put file to pbucket
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	stat := bucket.GetOptions(ctx).BlockStats
	if stat.CountLocal != 1 {
		t.Fatalf("invalid CountLocal for get, expect:1, but got:%d", stat.CountLocal)
	}

	// get file
	ctx = bucket.WithOptions(context.Background())
	downloadPath := utils.MergePath(cfg.Local.Root, fileName)
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	// verify file content
	if !utils.CompareFiles(localFilePath, downloadPath) {
		t.Fatalf("download file:%s is diff with origin file:%s", downloadPath, localFilePath)
	}

	// verify block stats
	stat = bucket.GetOptions(ctx).BlockStats
	if stat.CountLocal != 1 {
		t.Fatalf("invalid CountLocal for put, expect:1, but got:%d", stat.CountLocal)
	}
}
