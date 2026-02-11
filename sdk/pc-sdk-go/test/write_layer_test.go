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
	"fmt"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/bucket"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"testing"
)

func Test_WriteLayerForSmallFileToPcp(t *testing.T) {
	err := testWriteLayer(bucket.LAYER_MEMORY)
	if err != nil {
		t.Fatalf("failed to test WriteLayer:%v err:%v", bucket.LAYER_MEMORY, err)
	}

	err = testWriteLayer(bucket.LAYER_DISK)
	if err != nil {
		t.Fatalf("failed to test WriteLayer:%v err:%v", bucket.LAYER_MEMORY, err)
	}

	err = testWriteLayer(bucket.LAYER_REMOTE)
	if err != nil {
		t.Fatalf("failed to test WriteLayer:%v err:%v", bucket.LAYER_MEMORY, err)
	}
}

func testWriteLayer(layer int) error {
	// create pbucket
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucketWithOptions(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"}, bucket.WithWriteLayer(layer))
	if err != nil {
		return err
	}
	defer pb.Close()

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)
	if err != nil {
		return err
	}
	defer os.Remove(localFilePath)

	// put file
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		return err
	}
	stat := bucket.GetOptions(ctx).BlockStats

	if layer == bucket.LAYER_MEMORY && stat.CountPcpMemory != 1 {
		return fmt.Errorf("failed to put to PCP memory, expected 1, got %d", stat.CountPcpMemory)
	}
	if layer == bucket.LAYER_DISK && stat.CountPcpDisk != 1 {
		return fmt.Errorf("failed to put to PCP disk, expected 1, got %d", stat.CountPcpMemory)
	}
	if layer == bucket.LAYER_REMOTE && stat.CountPcpRemote != 1 {
		return fmt.Errorf("failed to put to PCP remote, expected 1, got %d", stat.CountPcpMemory)
	}

	// get file
	downloadPath := utils.MergePath(cfg.Local.Root, fileName)
	ctx = bucket.WithOptions(context.Background())
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		return err
	}

	stat = bucket.GetOptions(ctx).BlockStats
	if stat.CountPcpMemory != 1 {
		return fmt.Errorf("failed to get from PCP memeory, expected 1, got %d", stat.CountPcpMemory)
	}
	return nil
}
