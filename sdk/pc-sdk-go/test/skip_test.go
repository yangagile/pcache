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

func Test_SkipUnchanged(t *testing.T) {
	// create pbucket
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject,DeleteObject"})
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
	defer os.Remove(localFilePath)

	// delete the object if it's existing
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.DeleteObject(ctx, fileKey)
	if err != nil {
		t.Fatalf("failed to delete object key:%v with err:%v", fileKey, err)
	}

	// first put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	fstat := bucket.GetOptions(ctx).FileStats
	if fstat.CountOk != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// enable skip unchanged option
	bucket.GetOptions(ctx).SkipUnchanged = true

	// put same file again, check with file size
	bucket.GetOptions(ctx).FileStats.Reset()
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	fstat = bucket.GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// disable skip unchanged and put file again with checksum
	bucket.GetOptions(ctx).SkipUnchanged = false
	bucket.GetOptions(ctx).Checksum = "md5"
	// first put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	// enable skip unchanged and put agian
	bucket.GetOptions(ctx).SkipUnchanged = true
	bucket.GetOptions(ctx).FileStats.Reset()
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	fstat = bucket.GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// get file with checksum enabled
	ctx = bucket.WithOptions(context.Background())
	bucket.GetOptions(ctx).SkipUnchanged = true
	bucket.GetOptions(ctx).Checksum = "md5"

	_, err = pb.GetObject(ctx, fileKey, localFilePath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	fstat = bucket.GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// disable checksum, get again, will use size to check
	bucket.GetOptions(ctx).Checksum = ""
	bucket.GetOptions(ctx).FileStats.Reset()
	_, err = pb.GetObject(ctx, fileKey, localFilePath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	fstat = bucket.GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}
}

func Test__SkipExisting(t *testing.T) {
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

	// test skip existing key for put
	bucket.GetOptions(ctx).SkipExisting = true
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}
	fstats := bucket.GetOptions(ctx).FileStats
	if fstats.CountSkipExisting != 1 {
		t.Fatalf("failed to skip existing key")
	}

	// first get file
	ctx = bucket.WithOptions(context.Background())
	downloadPath := utils.MergePath(cfg.Local.Root, fileName)
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stat := bucket.GetOptions(ctx).BlockStats
	if stat.CountPcpMemory <= 0 {
		t.Fatalf("failed to get from PCP memeory")
	}

	// test skip to get if local file is existing
	ctx = bucket.WithOptions(context.Background())
	bucket.GetOptions(ctx).SkipExisting = true
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	fstats = bucket.GetOptions(ctx).FileStats
	if fstats.CountSkipExisting != 1 {
		t.Fatalf("failed to skip existing key")
	}
}
