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
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	log "github.com/sirupsen/logrus"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"
	"unsafe"
)

type PBucket struct {
	bucket              string
	path                string
	ak                  string
	sk                  string
	permissions         []string
	blockSize           int64
	groupNumber         int
	stsTtlSec           int64          // sts duration time
	s3ClientMgrPtr      unsafe.Pointer // *S3ClientManager
	S3ClientMgrUpdating int32          // 1: S3ClientMgr is updating
	pcpMgrPtr           unsafe.Pointer // *PcpManager
	pcpMgrTtlSec        int64          // pcp Table duration time
	pcpMgrUpdating      int32          // 1: PcpManager is updating
	pcpMgrEnable        bool
	pmsMgr              *PmsManager
	secretMgr           *SecretManager
	workerChanSize      int
	blockWorker         *BlockWorker
	workerThreadNumber  int
	fileThreadNumber    int
}

func NewPBucket(ctx context.Context, pmsUrl, bucket, ak, sk string, permissions []string) (*PBucket, error) {
	// check parameters
	if pmsUrl == "" {
		return nil, fmt.Errorf("pclient: missing pmsUrl")
	}
	if bucket == "" {
		return nil, fmt.Errorf("pclient: missing bucket")
	}
	if ak == "" || sk == "" {
		return nil, fmt.Errorf("pclient: ak, sk required")
	}

	// init config variables
	pb := &PBucket{
		bucket:             bucket,
		ak:                 ak,
		sk:                 sk,
		pcpMgrTtlSec:       60,
		blockSize:          5 * 1024 * 1024,
		groupNumber:        6,
		path:               "",
		pcpMgrEnable:       true,
		stsTtlSec:          500,
		permissions:        permissions,
		workerChanSize:     128,
		workerThreadNumber: 8,
		fileThreadNumber:   8,
	}

	// init user manager
	var err error
	pb.secretMgr, err = NewSecretManager(ctx, ak, sk)
	if err != nil {
		log.WithError(err).WithField("ak", ak).Errorln("failed to init secretMgr")
		return nil, err
	}

	// init PMS manager
	pb.pmsMgr, err = NewPmsManager(ctx, pmsUrl, pb.secretMgr)
	if err != nil {
		log.WithError(err).WithField("pmsUrl", pmsUrl).Errorln("failed to init PMS manager")
		return nil, err
	}

	// init PCP manager
	if pb.pcpMgrEnable {
		pcpTable, err := pb.getPcpTable("")
		if err != nil {
			log.WithError(err).WithField("pmsUrl", pmsUrl).Errorln("failed to init PCP manager")
			return nil, err
		}
		newPcpMgr := NewPcpManager(time.Now().Unix()+pb.pcpMgrTtlSec*1000, pcpTable)
		atomic.StorePointer(&pb.pcpMgrPtr, unsafe.Pointer(newPcpMgr))
	}

	// init S3Client manager
	s3ClientMgr, err := pb.newS3ClientManager()
	if err != nil {
		log.WithError(err).WithField("pmsUrl", pmsUrl).Errorln("failed to init S3Client manager")
		return nil, err
	}
	atomic.StorePointer(&pb.s3ClientMgrPtr, unsafe.Pointer(s3ClientMgr))

	// init block put/get worker threads
	pb.blockWorker = NewBlockWorker(pb.workerThreadNumber, pb.workerChanSize)
	pb.blockWorker.Start()
	log.WithField("bucket info", pb.PrintInfo()).Infoln("create bucket done")
	return pb, nil
}

func (pb *PBucket) Close() {
	pb.blockWorker.Close()
}

func (pb *PBucket) PrintInfo() string {
	return fmt.Sprintf("bucket: %s, path: %s", pb.bucket, pb.path)
}

func (pb *PBucket) EnablePCache(pcpEnable bool) {
	pb.pcpMgrEnable = pcpEnable
}

func (pb *PBucket) SyncFolderToPrefix(ctx context.Context, folder, prefix string) error {
	options := GetOptions(ctx)
	fileMgr := NewFileManager(pb, pb.fileThreadNumber)
	err := filepath.Walk(folder, func(localPath string, info os.FileInfo, err error) error {
		if err != nil {
			log.Printf("failed to access folder %s: %v", localPath, err)
			return nil
		}

		// skip folder
		if info.IsDir() {
			return nil
		}

		// calculate relative key
		relPath, err := filepath.Rel(folder, localPath)
		if err != nil {
			log.Printf("failed to get reltive path %s: %v", localPath, err)
			return nil
		}

		// Convert to an S3-compatible path (replace Windows path separators with “/”).
		s3Key := filepath.ToSlash(relPath)
		if prefix != "" {
			s3Key = filepath.ToSlash(filepath.Join(prefix, s3Key))
		}

		// new file task
		blockCount := (info.Size() + pb.blockSize - 1) / pb.blockSize
		fileTask := &FileTask{
			s3Client:   pb.getS3Client(),
			stsInfo:    pb.getStsInfo(),
			Bucket:     pb.bucket,
			Type:       FILE_TYPE_PUT,
			LocalPath:  localPath,
			RemoteKey:  s3Key,
			Size:       info.Size(),
			BlockSize:  pb.blockSize,
			BlockCount: blockCount,
		}

		if options.DryRun {
			log.Printf("will put %s to %s%s", localPath, pb.bucket, s3Key)
		} else {
			fileMgr.AddTask(ctx, fileTask)
		}
		return nil
	})
	if !options.DryRun {
		fileMgr.Wait()
	}
	return err
}

func (pb *PBucket) SyncPrefixToFolder(ctx context.Context, prefix, folder string) error {
	options := GetOptions(ctx)
	fileMgr := NewFileManager(pb, pb.fileThreadNumber)
	var err error
	var continuationToken *string
	for {
		input := &s3.ListObjectsV2Input{
			Bucket:            aws.String(pb.getStsInfo().BucketName),
			Prefix:            aws.String(prefix),
			ContinuationToken: continuationToken, // 设置分页令牌
		}

		result, err := pb.getS3Client().ListObjectsV2(context.TODO(), input)
		if err != nil {
			log.WithError(err).WithField("prefix", prefix).Errorln("failed to list object")
			break
		}

		for _, object := range result.Contents {
			relKey, _ := filepath.Rel(prefix, *object.Key)
			localFile := utils.MergePath(folder, relKey)
			utils.EnsureParentDir(localFile)
			blockCount := (*object.Size + pb.blockSize - 1) / pb.blockSize
			fileTask := &FileTask{
				s3Client:   pb.getS3Client(),
				stsInfo:    pb.getStsInfo(),
				Bucket:     pb.bucket,
				Type:       FILE_TYPE_GET,
				LocalPath:  localFile,
				RemoteKey:  *object.Key,
				Size:       *object.Size,
				BlockSize:  pb.blockSize,
				BlockCount: blockCount,
			}
			if options.DryRun {
				log.Printf("will get %s from %s%s", localFile, pb.bucket, *object.Key)
			} else {
				fileMgr.AddTask(ctx, fileTask)
			}
		}

		if *result.IsTruncated && result.NextContinuationToken != nil {
			continuationToken = result.NextContinuationToken
		} else {
			break // exit loop
		}
	}
	if !options.DryRun {
		fileMgr.Wait()
	}
	return err
}

func (pb *PBucket) Put(ctx context.Context, localPath, objectKey string) error {
	fileInfo, err := os.Stat(localPath)
	if err != nil {
		log.WithError(err).WithField("localPath", localPath).
			Errorln("failed to open file")
		return err
	}
	blockCount := (fileInfo.Size() + pb.blockSize - 1) / pb.blockSize
	fileTask := &FileTask{
		s3Client:   pb.getS3Client(),
		stsInfo:    pb.getStsInfo(),
		Bucket:     pb.bucket,
		Type:       FILE_TYPE_PUT,
		LocalPath:  localPath,
		RemoteKey:  objectKey,
		Size:       fileInfo.Size(),
		BlockSize:  pb.blockSize,
		BlockCount: blockCount,
	}
	fileMgr := NewSingleFileManager(pb)
	fileMgr.PutFile(ctx, fileTask)
	return nil
}

func (pb *PBucket) Get(ctx context.Context, objectKey, localPath string) error {
	resp, err := HeadObject(pb.getS3Client(), pb.getStsInfo().BucketName, objectKey)
	if err != nil {
		log.WithError(err).WithField("key", objectKey).Errorln("failed to head object")
		return err
	}
	if *resp.ContentLength == 0 {
		return fmt.Errorf("object size is 0")
	}
	fileSize := *resp.ContentLength

	blockCount := (fileSize + pb.blockSize - 1) / pb.blockSize
	fileTask := &FileTask{
		s3Client:   pb.getS3Client(),
		stsInfo:    pb.getStsInfo(),
		Bucket:     pb.bucket,
		Type:       FILE_TYPE_GET,
		LocalPath:  localPath,
		RemoteKey:  objectKey,
		Size:       fileSize,
		BlockSize:  pb.blockSize,
		BlockCount: blockCount,
	}
	fileMgr := NewSingleFileManager(pb)
	fileMgr.GetFile(ctx, fileTask)

	return nil
}

// below is private function
func (pb *PBucket) getPcpHost(key string) string {
	if !pb.pcpMgrEnable {
		return ""
	}
	pcpMgr := (*PcpManager)(atomic.LoadPointer(&pb.pcpMgrPtr))
	if pcpMgr.IsExpired() {
		// only the first thread init the pcpMgrPtr
		if atomic.CompareAndSwapInt32(&pb.pcpMgrUpdating, 0, 1) {
			defer atomic.StoreInt32(&pb.pcpMgrUpdating, 0) // 确保更新完成后释放锁
			pcpTable, err := pb.getPcpTable(pcpMgr.Checksum)
			if err != nil {
				// failed to update PCP list, record error and use the old
				log.WithError(err).Errorln("failed to reset PCP manager")
				return pcpMgr.get(key)
			}
			// no changes, just update the time
			if pcpTable.Checksum == pcpMgr.Checksum {
				pcpMgr.expiredTime = time.Now().Unix() + pb.pcpMgrTtlSec*1000
				return pcpMgr.get(key)
			}
			// update new PCP m
			newPcpMgr := NewPcpManager(time.Now().Unix()+pb.pcpMgrTtlSec*1000, pcpTable)
			atomic.StorePointer(&pb.pcpMgrPtr, unsafe.Pointer(newPcpMgr))
			return newPcpMgr.get(key)
		}
	}
	// later threads use the old.
	return pcpMgr.get(key)
}

func (pb *PBucket) newS3ClientManager() (*S3ClientManager, error) {
	// apply STS from PMS
	newRouter, err := pb.pmsMgr.GetRoutingResult(pb.bucket, pb.path, pb.permissions)
	if err != nil {
		log.WithError(err).Errorln("failed to get router!")
		return nil, err
	}

	// create S3Client
	s3Client, err := NewS3ClientWithSTS(context.TODO(), newRouter.GetStsInfo())
	if err != nil {
		log.WithError(err).Errorln("failed to create S3 bucket")
		return nil, err
	}

	// create S3 manager
	newS3ClientMgr := &S3ClientManager{
		s3Client:    s3Client,
		router:      newRouter,
		expiredTime: time.Now().Unix() + pb.stsTtlSec*1000,
	}
	return newS3ClientMgr, nil
}

func (pb *PBucket) getS3ClientMgr() *S3ClientManager {
	s3ClientMgr := (*S3ClientManager)(atomic.LoadPointer(&pb.s3ClientMgrPtr))
	if s3ClientMgr == nil || s3ClientMgr.IsExpired() {
		if atomic.CompareAndSwapInt32(&pb.S3ClientMgrUpdating, 0, 1) {
			defer atomic.StoreInt32(&pb.S3ClientMgrUpdating, 0)
			// the first thread reset the S3Client
			newS3ClientMgr, err := pb.newS3ClientManager()
			if err != nil {
				// failed to create new, just use the old one
				log.WithError(err).Errorln("failed to reset S3Client manager")
			} else {
				atomic.StorePointer(&pb.s3ClientMgrPtr, unsafe.Pointer(newS3ClientMgr))
			}
		}
	}
	return s3ClientMgr
}

func (pb *PBucket) getS3Client() *s3.Client {
	return pb.getS3ClientMgr().GetS3Client()
}

func (pb *PBucket) getStsInfo() *StsInfo {
	return pb.getS3ClientMgr().GetStsInfo()
}

func (pb *PBucket) getPcpTable(checksum string) (*utils.PcpTable, error) {
	pcpTable, err := pb.pmsMgr.GetPcpList(checksum)
	if err != nil {
		log.WithError(err).WithField("iamUrl", pb.pmsMgr.urlProve.GetUrl()).
			Errorln("failed to get pcp table")
		return nil, err
	}
	return pcpTable, nil
}

func (pb *PBucket) abortMultipartPut(key string, uploadID *string) error {
	_, err := pb.getS3Client().AbortMultipartUpload(context.TODO(), &s3.AbortMultipartUploadInput{
		Bucket:   aws.String(pb.getStsInfo().BucketName),
		Key:      aws.String(key),
		UploadId: uploadID,
	})
	return err
}
