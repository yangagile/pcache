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

func Test_Delete(t *testing.T) {
	// create pbucket
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucket(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject,DeleteObject"})
	if err != nil {
		t.Fatalf("failed to create Bucket: %v", err)
	}
	defer pb.Close()

	// create test file
	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(cfg.Local.Root, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}

	// delete not existing object will return nil
	fileKey := utils.MergePath(cfg.Bucket.Prefix, fileName)
	_, err = pb.DeleteObject(ctx, fileKey+"no_this_key_wosldfsafa")
	if err != nil {
		t.Fatalf("failed to delete object key:%v with err:%v", fileKey, err)
	}

	// put object
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	// delete the object
	_, err = pb.DeleteObject(ctx, fileKey)
	if err != nil {
		t.Fatalf("failed to delete object key:%v with err:%v", fileKey, err)
	}

	// the object should not exist
	_, err = pb.HeadObject(ctx, fileKey)
	if err == nil {
		t.Fatalf("failed to delete object key:%v, the object is still existing", fileKey)
	}
}
