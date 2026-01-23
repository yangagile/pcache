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

func Test_WriteLayerForSmallFileToPcp(t *testing.T) {
	// create pbucket
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucketWithOptions(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"}, bucket.WithWriteLayer(bucket.LAYER_MEMORY))
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
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)

	// put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	stat := bucket.GetOptions(ctx).BlockStats
	if stat.CountPcpDisk <= 0 {
		t.Fatalf("failed to put from PCP disk")
	}

	// get
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
