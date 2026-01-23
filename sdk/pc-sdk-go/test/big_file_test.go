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
	fileSize := int64(10 * 1024 * 1024) // 10MB
	uploadLocalPath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)

	// put to pbucket
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.PutObject(ctx, uploadLocalPath, fileKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}
	stats := bucket.GetOptions(ctx).BlockStats
	if stats.CountPcpDisk <= 0 {
		t.Fatalf("failed to use PCP")
	}

	// test get file from pcp
	downloadPath := utils.MergePath(cfg.Local.Root, fileName) + ".download"
	ctx = bucket.WithOptions(context.Background())
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stats = bucket.GetOptions(ctx).BlockStats
	if stats.CountPcpMemory <= 0 {
		t.Fatalf("failed to use PCP")
	}
}
