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
	"testing"
)

func Test_NewBucketWithOptions(t *testing.T) {
	cfg := GetConfig()
	ctx := bucket.WithOptions(context.Background())
	pb, err := bucket.NewPBucketWithOptions(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"},
		bucket.WithBlockSize(10*1024*1024),
		bucket.WithBlockWorkerChanSize(256),
		bucket.WithBlockWorkerThreadNumber(16),
		bucket.WithFileTaskThreadNumber(16),
		bucket.WithStsTls(3600),
		bucket.WithPcpTls(30),
	)
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	if pb.GetBlockSize() != 10*1024*1024 {
		t.Fatalf("pb block size should be 0*1024*1024")
	}

	// for invalid block size
	_, err = bucket.NewPBucketWithOptions(ctx, "http://127.0.0.1:8080", "bucket", "ak", "sk",
		[]string{"PutObject,GetObject"}, bucket.WithBlockSize(1*1024*1024),
	)
	if err == nil {
		t.Fatalf("should fail to create pb: %v", err)
	}
}
