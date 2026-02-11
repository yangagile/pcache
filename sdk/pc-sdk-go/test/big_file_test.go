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

func Test_PutWithPCache(t *testing.T) {
	ctx := bucket.WithOptions(context.Background())
	cfg := GetConfig()
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to new PBucket with err:%v", err)
	}
	defer pb.Close()

	// test put file to PCP
	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(11 * 1024 * 1024) // 11MB
	localFilePath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)
	blockNumber := (fileSize + pb.GetBlockSize() - 1) / pb.GetBlockSize()
	if blockNumber < 2 {
		t.Fatalf("block number must be greater than 1 for big file test!")
	}
	defer os.Remove(localFilePath)

	// put to pbucket
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}

	// verify put result
	stats := bucket.GetOptions(ctx).BlockStats
	if stats.CountTotal != blockNumber {
		t.Fatalf("invalid CountTotal, expect:%d, but got:%d", blockNumber, stats.CountTotal)
	}
	if stats.GetPcpHitCount() != blockNumber {
		t.Fatalf("invalid CountPcpDisk, expect:%d, but got:%d", blockNumber, stats.CountPcpDisk)
	}
	if stats.CountFail > 0 {
		t.Fatalf("invalid CountFail, expect:0, but got:%d", stats.CountFail)
	}

	// test get file from pcp
	downloadPath := utils.MergePath(cfg.Local.Root, fileName) + ".download"

	// create new ctx and get object
	ctx = bucket.WithOptions(context.Background())
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}

	// verify file content
	if !utils.CompareFiles(localFilePath, downloadPath) {
		t.Fatalf("download file:%s is diff with origin file:%s", downloadPath, localFilePath)
	}

	// verify block stats
	stats = bucket.GetOptions(ctx).BlockStats
	if stats.CountTotal != blockNumber {
		t.Fatalf("invalid CountTotal, expect:%d, but got:%d", blockNumber, stats.CountTotal)
	}
	if stats.CountFail > 0 {
		t.Fatalf("invalid CountFail, expect:0, but got:%d", stats.CountFail)
	}
	if stats.GetPcpHitCount() != blockNumber {
		t.Fatalf("invalid PCP hit count, expect:%d, but got:%d", blockNumber, stats.GetPcpHitCount())
	}
}
