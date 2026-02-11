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

	blockSize := int64(10 * 1024 * 1024)
	blockWorkerChanSize := 256
	blockWorkerThreadNumber := 32
	fileTaskThreadNumber := 32
	stsTls := int64(3600)
	pcpTls := int64(30)
	pb, err := bucket.NewPBucketWithOptions(ctx, cfg.Pms.Url, cfg.Bucket.Name, cfg.Bucket.Ak, cfg.Bucket.Sk,
		[]string{"PutObject,GetObject"},
		bucket.WithBlockSize(blockSize),
		bucket.WithBlockWorkerChanSize(blockWorkerChanSize),
		bucket.WithBlockWorkerThreadNumber(blockWorkerThreadNumber),
		bucket.WithFileTaskThreadNumber(fileTaskThreadNumber),
		bucket.WithStsTls(stsTls),
		bucket.WithPcpTls(pcpTls),
	)
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	if pb.GetBlockSize() != blockSize {
		t.Fatalf("invalid blockSize, expect:%d, but got:%d", blockSize, pb.GetBlockSize())
	}
	if pb.GetBlockWorkerChanSize() != blockWorkerChanSize {
		t.Fatalf("invalid blockWorkerChanSize, expect:%d, but got:%d",
			blockWorkerChanSize, pb.GetBlockWorkerChanSize())
	}
	if pb.GetBlockWorkerThreadNumber() != blockWorkerThreadNumber {
		t.Fatalf("invalid blockWorkerThreadNumber, expect:%d, but got:%d",
			blockWorkerThreadNumber, pb.GetBlockWorkerThreadNumber())
	}
	if pb.GetFileTaskThreadNumber() != fileTaskThreadNumber {
		t.Fatalf("invalid fileTaskThreadNumber, expect:%d, but got:%d",
			fileTaskThreadNumber, pb.GetFileTaskThreadNumber())
	}
	if pb.GetStsTls() != stsTls {
		t.Fatalf("invalid stsTls, expect:%d, but got:%d",
			stsTls, pb.GetStsTls())
	}
	if pb.GetPcpTls() != pcpTls {
		t.Fatalf("invalid pcpTls, expect:%d, but got:%d",
			pcpTls, pb.GetPcpTls())
	}
}

func Test_NewBucketWithOptions_invalidBlockSize(t *testing.T) {
	// for invalid block size
	ctx := bucket.WithOptions(context.Background())
	_, err := bucket.NewPBucketWithOptions(ctx, "http://127.0.0.1:8080", "bucket", "ak", "sk",
		[]string{"PutObject,GetObject"}, bucket.WithBlockSize(1*1024*1024),
	)
	if err == nil {
		t.Fatalf("should fail to create pb: %v", err)
	}
}
