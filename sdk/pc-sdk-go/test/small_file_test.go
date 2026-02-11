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

func Test_PutGet_SmallFileFromPcp(t *testing.T) {
	// create pbucket
	ctx := bucket.WithOptions(context.Background())
	bucket.GetOptions(ctx).Checksum = "crc32"
	cfg := GetConfig()
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	// create test file
	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := utils.MergePath(cfg.Local.Root, fileName)
	defer os.Remove(localFilePath)

	// put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	stat := bucket.GetOptions(ctx).BlockStats
	if stat.GetPcpHitCount() != 1 {
		t.Fatalf("invalid PCP hit count, expect:1, but got:%d", stat.GetPcpHitCount())
	}

	// get file, will hit the memory cache
	ctx = bucket.WithOptions(context.Background())
	downloadPath := utils.MergePath(cfg.Local.Root, fileName)
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stat = bucket.GetOptions(ctx).BlockStats
	if stat.CountPcpMemory <= 0 {
		t.Fatalf("failed to get from PCP memeory")
	}
}

func Test_PutGet_SmallFileOption(t *testing.T) {
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	defer os.Remove(localFilePath)

	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	downloadPath := utils.MergePath(cfg.Local.Root, fileName)

	// put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	// tag as small file, make sure the size is less block size.
	// get file
	ctx = bucket.WithOptions(context.Background())
	bucket.GetOptions(ctx).IsSmallFile = true
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
}
