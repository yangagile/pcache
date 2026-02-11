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

func Test_ReadRange_SmallFile(t *testing.T) {
	cfg := GetConfig()

	// create pbucket
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	// create test file with special data
	fileName := utils.GetCurrentFunctionName()
	fileSize := 256
	content := make([]byte, fileSize)
	// set the value equal the index
	for i := range content {
		content[i] = byte(i)
	}
	localFilePath, err := utils.CreateTestFileWithContent(cfg.Local.Root, fileName, content)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	defer os.Remove(localFilePath)

	// put file
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	readBufSize := 64
	buf := make([]byte, readBufSize)
	var readLen int64

	// get a range of file
	ctx = bucket.WithOptions(context.Background())
	readLen, err = pb.GetObjectRange(ctx, fileKey, int64(fileSize), 0, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	// verify size with special range [0,64]
	if readLen != int64(readBufSize) {
		t.Fatalf("failed to get right data length")
	}
	// verify content
	for i := range readBufSize {
		if buf[i] != byte(i) {
			t.Fatalf("failed to get right data")
		}
	}

	// get another range [128,128+64]
	offset := int64(128)
	readLen, err = pb.GetObjectRange(ctx, fileKey, int64(fileSize), offset, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	if readLen != int64(readBufSize) {
		t.Fatalf("failed to get right data length")
	}
	for i := range readBufSize {
		if buf[i] != byte(int(offset)+i) {
			t.Fatalf("failed to get right data")
		}
	}

	// get end range out of the file size [250,250+64]
	offset = int64(250)
	readLen, err = pb.GetObjectRange(ctx, fileKey, int64(fileSize), offset, buf)
	realSize := min(int64(fileSize)-offset, int64(len(buf)))
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	if readLen != realSize {
		t.Fatalf("failed to get right data length")
	}
	for i := range int(realSize) {
		if buf[i] != byte(int(offset)+i) {
			t.Fatalf("failed to get right data")
		}
	}
}

func Test_ReadRange_BigFile(t *testing.T) {
	cfg := GetConfig()
	// create pbucket
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to new PBucket with err:%v", err)
	}
	defer pb.Close()

	// create test file with special data
	fileName := utils.GetCurrentFunctionName()
	fileSize := pb.GetBlockSize()*2 + 1024 // 3 blocks
	content := make([]byte, fileSize)
	for i := 0; i < len(content); i += 4 {
		dwordValue := uint32(i / 4)
		content[i] = byte(dwordValue)
		content[i+1] = byte(dwordValue >> 8)
		content[i+2] = byte(dwordValue >> 16)
		content[i+3] = byte(dwordValue >> 24)
	}
	localFilePath, err := utils.CreateTestFileWithContent(cfg.Local.Root, fileName, content)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	defer os.Remove(localFilePath)

	// put file
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}

	dataSize := int64(1024)
	buf := make([]byte, dataSize)

	// read [0, 1024]
	offset := int64(0)
	ctx = bucket.WithOptions(context.Background())
	readLen, err := pb.GetObjectRange(ctx, fileKey, fileSize, offset, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	// verify that access 1 block
	stats := bucket.GetOptions(ctx).BlockStats
	if stats.GetPcpHitCount() != 1 {
		t.Fatalf("invalid CountTotal, expect:1, but got:%d", stats.GetPcpHitCount())
	}
	// verify size
	if readLen != dataSize {
		t.Fatalf("failed to get right data length")
	}
	// verify content
	if !utils.BytesEqual(content[offset:offset+readLen], buf) {
		t.Fatalf("failed to get right data")
	}

	// get range [BlockSize-512, BlockSize+512], across the border of two blocks
	offset = pb.GetBlockSize() - 512
	ctx = bucket.WithOptions(context.Background())
	readLen, err = pb.GetObjectRange(ctx, fileKey, fileSize, offset, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	// verify that access 2 blocks
	stats = bucket.GetOptions(ctx).BlockStats
	if stats.GetPcpHitCount() != 2 {
		t.Fatalf("invalid CountTotal, expect:2, but got:%d", stats.GetPcpHitCount())
	}
	// verify size
	if readLen != dataSize {
		t.Fatalf("failed to get right data length")
	}
	// verify content
	if !utils.BytesEqual(content[offset:offset+readLen], buf) {
		t.Fatalf("failed to get right data")
	}

	// get range [fileSize-512, fileSize+512], across the end of file
	offset = fileSize - 512
	ctx = bucket.WithOptions(context.Background())
	readLen, err = pb.GetObjectRange(ctx, fileKey, fileSize, offset, buf)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	// verify that access 1 block
	stats = bucket.GetOptions(ctx).BlockStats
	if stats.GetPcpHitCount() != 1 {
		t.Fatalf("invalid CountTotal, expect:2, but got:%d", stats.GetPcpHitCount())
	}
	// verify size
	if readLen != 512 {
		t.Fatalf("failed to get right data length")
	}
	// verify content
	if !utils.BytesEqual(content[offset:offset+readLen], buf[0:readLen]) {
		t.Fatalf("failed to get right data")
	}
}
