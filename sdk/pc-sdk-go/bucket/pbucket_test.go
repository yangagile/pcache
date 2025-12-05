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

package bucket

import (
	"context"
	"fmt"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"testing"
)

var testRootPrefix = "test/pcache/go/sdk/"
var testRootLocal = "~/tmp/pbucket_test/" // will use os.TempDir()

var pmsUrl = "http://127.0.0.1:8080"
var ak = "unittest"
var sk = "3ewGHUIayI8cZ8qgAkoJ31gXvGqAzKmmsTLqMhTrhyM="
var bucket = "pbucket-name"

func TestMain(m *testing.M) {
	setup()

	code := m.Run()

	teardown()

	os.Exit(code)
}

func setup() {
	testRootLocal = utils.MergePath(os.TempDir(), "pbucket_test")
	info, err := os.Stat(testRootLocal)
	if err == nil {
		if info.IsDir() {
			os.Remove(testRootLocal)
		} else {
			panic(fmt.Errorf("root %v is not valid dir", testRootLocal))
		}
	}
	err = os.MkdirAll(testRootLocal, 0755)
	if err != nil {
		panic(err)
	}
}

func teardown() {
	// clean temp files
	err := utils.CleanDirFromTempDir(testRootLocal)
	if err != nil {
		panic(fmt.Errorf("failed to delete test root path %v: %v", testRootLocal, err))
	}
}

func Test_PutGet_SmallFileFromLocal(t *testing.T) {
	ctx := WithOptions(context.Background())

	GetOptions(ctx).Checksum = "md5"
	pb, err := NewPBucketWithOptions(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"},
		WithPCacheEnable(false))
	if err != nil {
		t.Fatalf("failed to create Bucket: %v", err)
	}
	defer pb.Close()

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName
	downloadPath := testRootLocal + fileName

	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	stat := GetOptions(ctx).BlockStats
	if stat.CountLocal <= 0 {
		t.Fatalf("failed to put from local")
	}
	ctx = WithOptions(context.Background())
	GetOptions(ctx).Checksum = "md5"
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stat = GetOptions(ctx).BlockStats
	if stat.CountLocal <= 0 {
		t.Fatalf("failed to put from local")
	}
}

func Test_PutGet_SmallFileFromPcp(t *testing.T) {
	ctx := WithOptions(context.Background())
	GetOptions(ctx).Checksum = "crc32"
	pb, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName
	downloadPath := testRootLocal + fileName

	// put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	stat := GetOptions(ctx).BlockStats
	if stat.CountPcpDisk <= 0 {
		t.Fatalf("failed to put from PCP disk")
	}

	// test skip existing key for put
	GetOptions(ctx).SkipExisting = true
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}
	fstats := GetOptions(ctx).FileStats
	if fstats.CountSkipExisting != 1 {
		t.Fatalf("failed to skip existing key")
	}

	// get file
	ctx = WithOptions(context.Background())
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stat = GetOptions(ctx).BlockStats
	if stat.CountPcpMemory <= 0 {
		t.Fatalf("failed to get from PCP memeory")
	}

	// test skip to get if local file is existing
	ctx = WithOptions(context.Background())
	GetOptions(ctx).SkipExisting = true
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	fstats = GetOptions(ctx).FileStats
	if fstats.CountSkipExisting != 1 {
		t.Fatalf("failed to skip existing key")
	}
}

func Test_PutWithPCache(t *testing.T) {
	ctx := WithOptions(context.Background())
	pb, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to new PBucket with err:%v", err)
	}
	defer pb.Close()

	// test put file to PCP
	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(10 * 1024 * 1024) // 10MB
	uploadLocalPath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	uploadKey := testRootPrefix + fileName

	_, err = pb.PutObject(ctx, uploadLocalPath, uploadKey)
	if err != nil {
		t.Fatalf("failed to file:%v with err:%v", fileName, err)
	}
	stats := GetOptions(ctx).BlockStats
	if stats.CountPcpDisk <= 0 {
		t.Fatalf("failed to use PCP")
	}

	// test get file from pcp
	downloadPath := testRootLocal + fileName + ".download"
	ctx = WithOptions(context.Background())
	_, err = pb.GetObject(ctx, uploadKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	stats = GetOptions(ctx).BlockStats
	if stats.CountPcpMemory <= 0 {
		t.Fatalf("failed to use PCP")
	}
}

func Test_syncFolder(t *testing.T) {
	ctx := WithOptions(context.Background())
	GetOptions(ctx).DryRun = false
	GetOptions(ctx).DebugMode = false

	pb, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject,ListObject"})
	if err != nil {
		t.Fatalf("failed to new PBucket with err:%v", err)
	}
	defer pb.Close()

	folder := utils.MergePath(testRootLocal, utils.GetCurrentFunctionName())

	// clean source
	err = utils.CleanDirFromTempDir(folder)
	if err != nil {
		panic(fmt.Errorf("failed to delete test root path %v: %v", testRootLocal, err))
	}

	// create new
	fileSize := int64(1 * 1024) // 10MB
	fileNumber := 10
	for i := 0; i < fileNumber; i++ {
		_, err = utils.CreateTestFile(folder, fmt.Sprintf("f_%05d.dat", i), fileSize)
	}
	prefix := utils.MergePath(testRootPrefix, utils.GetCurrentFunctionName())

	// sync to Bucket from local
	err = pb.SyncFolderToPrefix(ctx, folder, prefix)
	if err != nil {
		t.Fatalf("failed to sync folder:%v to prefix:%v", folder, prefix)
	}
	if GetOptions(ctx).FileStats.CountOk != int64(fileNumber) {
		t.Fatalf("failed to sync folder:%v to prefix:%v", folder, prefix)
	}

	//clean source
	err = utils.CleanDirFromTempDir(testRootLocal)
	if err != nil {
		panic(fmt.Errorf("failed to delete test root path %v: %v", testRootLocal, err))
	}

	// sync back to local from Bucket
	ctx = WithOptions(context.Background())
	err = pb.SyncPrefixToFolder(ctx, prefix, folder)
	if err != nil {
		t.Fatalf("failed to sync prefix:%v to folder:%v ", prefix, folder)
	}
	if GetOptions(ctx).FileStats.CountOk != int64(fileNumber) {
		t.Fatalf("failed to sync prefix:%v to folder:%v ", prefix, folder)
	}
}

func Test__Delete(t *testing.T) {
	ctx := WithOptions(context.Background())

	pb, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject,DeleteObject"})
	if err != nil {
		t.Fatalf("failed to create Bucket: %v", err)
	}
	defer pb.Close()

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName

	// delete not existing object will return nil
	_, err = pb.DeleteObject(ctx, fileKey+"no_this_key_wosldfsafa")
	if err != nil {
		t.Fatalf("failed to delete object key:%v with err:%v", fileKey, err)
	}

	// put a object
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

func Test__SkipUnchanged(t *testing.T) {
	ctx := WithOptions(context.Background())
	pb, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject,DeleteObject"})
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	// create test object
	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName

	// delete the object if it's existing
	_, err = pb.DeleteObject(ctx, fileKey)
	if err != nil {
		t.Fatalf("failed to delete object key:%v with err:%v", fileKey, err)
	}

	// first put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	fstat := GetOptions(ctx).FileStats
	if fstat.CountOk != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// enable skip unchanged optong
	GetOptions(ctx).SkipUnchanged = true

	// put same file again, check with file size
	GetOptions(ctx).FileStats.Reset()
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	fstat = GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// disable skip unchanged and put file again with checksum
	GetOptions(ctx).SkipUnchanged = false
	GetOptions(ctx).Checksum = "md5"
	// first put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	// enable skip unchanged and put agian
	GetOptions(ctx).SkipUnchanged = true
	GetOptions(ctx).FileStats.Reset()
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}
	fstat = GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// get file with checksum enabled
	ctx = WithOptions(context.Background())
	GetOptions(ctx).SkipUnchanged = true
	GetOptions(ctx).Checksum = "md5"

	_, err = pb.GetObject(ctx, fileKey, localFilePath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	fstat = GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}

	// disable checksum, get again, will use size to check
	GetOptions(ctx).Checksum = ""
	GetOptions(ctx).FileStats.Reset()
	_, err = pb.GetObject(ctx, fileKey, localFilePath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
	fstat = GetOptions(ctx).FileStats
	if fstat.CountSkipUnchanged != 1 {
		t.Fatalf("failed to put from PCP disk")
	}
}

func Test_PutGet_SmallFileOption(t *testing.T) {
	ctx := WithOptions(context.Background())
	pb, err := NewPBucket(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"})
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	fileName := utils.GetCurrentFunctionName()
	fileSize := int64(1024)
	localFilePath, err := utils.CreateTestFile(testRootLocal, fileName, fileSize)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", localFilePath, err)
	}
	fileKey := testRootPrefix + fileName
	downloadPath := testRootLocal + fileName

	// put file
	_, err = pb.PutObject(ctx, localFilePath, fileKey)
	if err != nil {
		t.Fatalf("failed to put file:%v with err:%v", fileName, err)
	}

	// tag as small file, make sure the size is less block size.
	// get file
	ctx = WithOptions(context.Background())
	GetOptions(ctx).IsSmallFile = true
	_, err = pb.GetObject(ctx, fileKey, downloadPath)
	if err != nil {
		t.Fatalf("failed to get file:%v with err:%v", fileName, err)
	}
}

func Test_NewBucketWithOptions(t *testing.T) {
	ctx := WithOptions(context.Background())
	pb, err := NewPBucketWithOptions(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"},
		WithBlockSize(10*1024*1024),
		WithBlockWorkerChanSize(256),
		WithBlockWorkerThreadNumber(16),
		WithFileTaskThreadNumber(16),
		WithStsTls(3600),
		WithPcpTls(30),
	)
	if err != nil {
		t.Fatalf("failed to create pb: %v", err)
	}
	defer pb.Close()

	if pb.blockSize != 10*1024*1024 {
		t.Fatalf("pb block size should be 0*1024*1024")
	}
	if pb.blockWorkerChanSize != 256 {
		t.Fatalf("pb blockWorkerChanSize should be 256")
	}
	if pb.blockWorkerThreadNumber != 16 {
		t.Fatalf("pb blockWorkerThreadNumber should be 16")
	}
	if pb.fileTaskThreadNumber != 16 {
		t.Fatalf("pb fileTaskThreadNumber should be 16")
	}
	if pb.stsTtlSec != 3600 {
		t.Fatalf("pb stsTtlSec should be 3600")
	}
	if pb.pcpTtlSec != 30 {
		t.Fatalf("pb pcpTtlSec should be 30")
	}

	// for invalid block size
	_, err = NewPBucketWithOptions(ctx, pmsUrl, bucket, ak, sk, []string{"PutObject,GetObject"},
		WithBlockSize(1*1024*1024),
	)
	if err == nil {
		t.Fatalf("should fail to create pb: %v", err)
	}
}
