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

func Test_syncFolder(t *testing.T) {
	// create pbucket
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	bucket.GetOptions(ctx).DryRun = false
	bucket.GetOptions(ctx).DebugMode = false
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject,ListObject"})
	if err != nil {
		t.Fatalf("failed to new PBucket with err:%v", err)
	}
	defer pb.Close()

	// create folder
	folder := utils.MergePath(cfg.Local.Root, utils.GetCurrentFunctionName())
	fileSize := int64(1 * 1024) // 10MB
	fileNumber := 10
	fileMD5Map := make(map[string]string)
	for i := 0; i < fileNumber; i++ {
		filePath, err := utils.CreateTestFile(folder, fmt.Sprintf("f_%05d.dat", i), fileSize)
		if err != nil {
			t.Fatalf("failed to create local file:%v with err:%v", fmt.Sprintf("f_%05d.dat", i), err)
		}
		md5Value, err := utils.GetMD5Base64FromFile(filePath)
		if err != nil {
			t.Fatalf("failed to get md5 value for local file:%v with err:%v", filePath, err)
		}
		fileMD5Map[filePath] = md5Value
	}

	// sync to bucket from local
	prefix := utils.MergePath(cfg.Bucket.Prefix, utils.GetCurrentFunctionName())
	err = pb.SyncFolderToPrefix(ctx, folder, prefix)
	if err != nil {
		t.Fatalf("failed to sync folder:%v to prefix:%v", folder, prefix)
	}
	if bucket.GetOptions(ctx).FileStats.CountOk != int64(fileNumber) {
		t.Fatalf("failed to sync folder:%v to prefix:%v", folder, prefix)
	}

	//clean folder
	err = os.RemoveAll(folder)
	if err != nil {
		panic(fmt.Errorf("failed to delete test root path %v: %v", folder, err))
	}

	// sync back to local from Bucket
	ctx = bucket.WithOptions(context.Background())
	err = pb.SyncPrefixToFolder(ctx, prefix, folder)
	if err != nil {
		t.Fatalf("failed to sync prefix:%v to folder:%v ", prefix, folder)
	}
	defer os.RemoveAll(folder)

	if bucket.GetOptions(ctx).FileStats.CountOk != int64(fileNumber) {
		t.Fatalf("failed to sync prefix:%v to folder:%v ", prefix, folder)
	}
	// verify folder content
	for path, hash := range fileMD5Map {
		md5Value, err := utils.GetMD5Base64FromFile(path)
		if err != nil {
			t.Fatalf("failed to get md5 value for local file:%v with err:%v", path, err)
		}
		if hash != md5Value {
			t.Fatalf("failed to sync prefix:%v to folder:%v ", prefix, folder)
		}
	}
}
