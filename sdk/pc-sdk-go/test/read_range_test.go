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
	"bytes"
	"context"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/bucket"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"path/filepath"
	"testing"
)

func Test_ReadRange_SmallFile(t *testing.T) {
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	fileName := utils.GetCurrentFunctionName()
	fileSize := 256

	content := make([]byte, fileSize)
	for i := range content {
		content[i] = byte(i)
	}
	localFilePath, err := utils.CreateTestFileWithContent(cfg.Local.Root, fileName, content)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)

	// put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	buf := make([]byte, 64)
	var readLen int64

	// get file
	ctx = bucket.WithOptions(context.Background())
	readLen, err = pb.GetObjectRange(ctx, fileKey, 0, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	if readLen != 64 {
		t.Fatalf("failed to get right data length")
	}
	if buf[0] != byte(0) && buf[63] != byte(63) {
		t.Fatalf("failed to get right data")
	}

	readLen, err = pb.GetObjectRange(ctx, fileKey, 128, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	if readLen != 64 {
		t.Fatalf("failed to get right data length")
	}
	if buf[0] != byte(128) && buf[63] != byte(128+63) {
		t.Fatalf("failed to get right data")
	}

	readLen, err = pb.GetObjectRange(ctx, fileKey, 250, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	if readLen != 6 {
		t.Fatalf("failed to get right data length")
	}
	if buf[0] != byte(250) {
		t.Fatalf("failed to get right data")
	}
}

func Test_ReadRange_BigFile(t *testing.T) {
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
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

	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.PutObject(ctx, uploadLocalPath, fileKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}

	dataSize := int64(5 * 1024 * 1024)
	offset := int64(1 * 1024 * 1024)
	buf := make([]byte, dataSize)
	var readLen int64
	// get file
	ctx = bucket.WithOptions(context.Background())

	readLen, err = pb.GetObjectRange(ctx, fileKey, offset, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	if readLen != dataSize {
		t.Fatalf("failed to get right data length")
	}

	originData, err := os.ReadFile(filepath.Join(cfg.Local.Root, fileName))
	if err != nil {
		t.Fatalf("failed to get read test file ")
	}

	if bytes.Equal(originData[offset:offset+readLen], buf) {
		t.Fatalf("failed to get right data")
	}
}
