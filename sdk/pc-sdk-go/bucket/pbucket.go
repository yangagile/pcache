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
	"github.com/aws/aws-sdk-go-v2/service/s3/types"
	log "github.com/sirupsen/logrus"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/model"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
	"strconv"
	"sync"
)

type PBucket struct {
	bucket               string
	path                 string
	ak                   string
	sk                   string
	permissions          []string
	bolckSize            int64
	groupNumber          int
	s3ClientCache        *S3ClientManager
	s3ClientCacheTimeSec int64
	router               *model.Router
	pcpCache             *PcpCache
	pcpCacheTimeSec      int64
	enablePCahche        bool
	pmsMgr               *PmsManager
	secretMgr            *SecretManager
}

type Option func(*PBucket)

func NewPBucket(ctx context.Context, pmsUrl, bucket, ak, sk string, permissions []string) (*PBucket, error) {
	if pmsUrl == "" {
		return nil, fmt.Errorf("pclient: missing pmsUrl")
	}
	if bucket == "" {
		return nil, fmt.Errorf("pclient: missing bucket")
	}
	if ak == "" || sk == "" {
		return nil, fmt.Errorf("pclient: ak, sk required")
	}

	pb := &PBucket{
		bucket:               bucket,
		ak:                   ak,
		sk:                   sk,
		pcpCacheTimeSec:      60,
		bolckSize:            5 * 1024 * 1024,
		groupNumber:          6,
		path:                 "",
		enablePCahche:        true,
		s3ClientCacheTimeSec: 300,
		permissions:          permissions,
	}

	var err error
	pb.secretMgr, err = NewSecretManager(ctx, ak, sk)
	if err != nil {
		log.WithError(err).WithField("ak", ak).Errorln("failed to init secretMgr")
		return nil, err
	}

	pb.pmsMgr, err = NewPmsManager(ctx, pmsUrl, pb.secretMgr)
	if err != nil {
		log.WithError(err).WithField("pmsUrl", pmsUrl).Errorln("failed to init PmsManager")
		return nil, err
	}

	if pb.enablePCahche {
		pb.pcpCache = NewPcpCache(pb.pcpCacheTimeSec, pb.getPcpTable)
	}

	pb.s3ClientCache, err = NewS3ClientCache(pb.s3ClientCacheTimeSec*1000, pb.getS3Client)
	if err != nil {
		log.WithError(err).Errorln("failed to create s3 bucket cache")
		return nil, err
	}

	log.WithField("bucket info", pb.PrintInfo()).Infoln("create bucket done")
	return pb, nil
}

func (pb *PBucket) PrintInfo() string {
	return fmt.Sprintf("bucket: %s, path: %s", pb.bucket, pb.path)
}

func (pb *PBucket) getS3Client() (*s3.Client, error) {
	var err error

	pb.router, err = pb.pmsMgr.GetRoutingResult(pb.bucket, pb.path, pb.permissions)
	if err != nil {
		log.WithError(err).Errorln("failed to get routing result")
		return nil, err
	}
	s3Client, err := utils.NewS3ClientWithSTS(context.TODO(), pb.router.GetStsInfo())
	if err != nil {
		log.WithError(err).Errorln("failed to create S3 bucket")
		return nil, err
	}
	return s3Client, nil
}

func (pb *PBucket) getPcpTable(checksum string) *utils.PcpTable {
	pcpTable, err := pb.pmsMgr.GetPcpList(checksum)
	if err != nil {
		log.WithError(err).WithField("iamUrl", pb.pmsMgr.urlProve.GetUrl()).
			Errorln("failed to get pcp table")
		return nil
	}
	return pcpTable
}

func (pb *PBucket) EnablePCache(enablePCache bool) {
	pb.enablePCahche = enablePCache
}

func (pb *PBucket) Put(ctx context.Context, localFilePath, objectKey string) error {
	fileInfo, err := os.Stat(localFilePath)
	if err != nil {
		log.WithError(err).WithField("localFilePath", localFilePath).
			Errorln("failed to open file")
		return err
	}
	if fileInfo.Size() > pb.bolckSize {
		return pb.putMultiPart(ctx, localFilePath, objectKey, fileInfo.Size())
	} else {
		return pb.putSingleFile(ctx, localFilePath, objectKey, fileInfo.Size())
	}
}

func (pb *PBucket) Get(ctx context.Context, objectKey, localFilePath string) error {

	if pb.enablePCahche {
		return pb.getWithPCache(ctx, objectKey, localFilePath)
	} else {
		return pb.getSingleFile(ctx, objectKey, localFilePath)
	}
}

// 从 S3 下载文件
func (pb *PBucket) getSingleFile(ctx context.Context, key, localFile string) error {
	pcpHost := ""
	if pb.enablePCahche && pb.pcpCache != nil {
		pcpHost = pb.pcpCache.Get(key)
	}
	blockInfo := &model.GetBlock{
		Block: model.Block{
			PcpHost:      pcpHost,
			Bucket:       pb.bucket,
			PartFile:     localFile,
			Key:          key,
			BlockNumber:  0,
			TotalNumber:  1,
			Size:         pb.bolckSize,
			BlockSize:    pb.bolckSize,
			TimeDuration: 0,
			State:        model.STATE_FAIL,
		},
	}
	worker := GetWorker{
		wg:     nil,
		client: pb,
		ch:     nil,
		chErr:  nil,
	}
	worker.GetBlock(blockInfo)

	stats := utils.GetStatCounter(ctx)
	if stats == nil {
		stats = utils.NewStats()
	}
	stats.Update(&blockInfo.Block)
	stats.End(ctx)

	log.WithField("local file", localFile).WithField("bucket", pb.bucket).WithField("key", key).
		WithField("stats", stats).Infoln("successfully get single file")
	return nil
}

func (pb *PBucket) getWithPCache(ctx context.Context, key, localFile string) error {
	resp, err := utils.HeadObject(pb.s3ClientCache.Get(), pb.router.GetStsInfo().BucketName, key)
	if err != nil {
		log.WithError(err).WithField("key", key).Errorln("failed to head object")
		return err
	}
	if *resp.ContentLength == 0 {
		return fmt.Errorf("object size is 0")
	}
	fileSize := *resp.ContentLength

	// use getSingleFile for small file
	if fileSize < pb.bolckSize {
		return pb.getSingleFile(ctx, key, localFile)
	}

	blockCount := (fileSize + pb.bolckSize - 1) / pb.bolckSize
	var wg sync.WaitGroup
	chBlock := make(chan *model.GetBlock, blockCount)
	chErrors := make(chan error, 1)
	threadNumber := min(pb.groupNumber, int(blockCount))

	for i := 0; i < threadNumber; i++ {
		wg.Add(1)
		worker := GetWorker{
			wg:     &wg,
			client: pb,
			ch:     chBlock,
			chErr:  chErrors,
		}
		go worker.getWorker()
	}

	blockList := make([]*model.GetBlock, blockCount)
	go func() {
		defer close(chBlock)
		for i := int64(0); i < blockCount; i++ {
			offset := i * pb.bolckSize
			remaining := fileSize - offset
			size := pb.bolckSize
			if remaining < pb.bolckSize {
				size = remaining
			}
			pcpHost := ""
			if pb.enablePCahche && pb.pcpCache != nil {
				pcpHost = pb.pcpCache.Get(key + strconv.FormatInt(i, 10))
			}
			blockList[i] = &model.GetBlock{
				Block: model.Block{
					PcpHost:      pcpHost,
					Bucket:       pb.bucket,
					PartFile:     fmt.Sprintf("%s.%d_%d", localFile, i, blockCount),
					Key:          key,
					BlockNumber:  i,
					TotalNumber:  blockCount,
					Size:         size,
					BlockSize:    pb.bolckSize,
					TimeDuration: 0,
					State:        model.STATE_FAIL,
				},
			}
			chBlock <- blockList[i]
		}
	}()

	wg.Wait()
	select {
	case err := <-chErrors:
		log.WithError(err).WithField("bucket", pb.router.GetStsInfo().BucketName).
			Errorln("failed to get block")
		return err
	default:
	}
	close(chErrors)

	stats := utils.GetStatCounter(ctx)
	if stats == nil {
		stats = utils.NewStats()
	}
	localPartFiles := make([]string, blockCount)
	for i := 0; i < int(blockCount); i++ {
		localPartFiles[i] = blockList[i].PartFile
		stats.Update(&blockList[i].Block)
	}
	err = utils.MergeFiles(localPartFiles, localFile)
	if err != nil {
		log.WithError(err).WithField("key", key).Errorln("failed to head object")
		return err
	}
	stats.End(ctx)
	log.WithField("file", localFile).WithField("bucket", pb.bucket).WithField("key", key).
		WithField("stats", stats).Infoln("successfully get file")
	return nil
}

func (pb *PBucket) abortMultipartPut(key string, uploadID *string) error {
	_, err := pb.s3ClientCache.Get().AbortMultipartUpload(context.TODO(), &s3.AbortMultipartUploadInput{
		Bucket:   aws.String(pb.router.GetStsInfo().BucketName),
		Key:      aws.String(key),
		UploadId: uploadID,
	})
	return err
}

func (pb *PBucket) putSingleFile(ctx context.Context, localFile, key string, fileSize int64) error {
	pcpHost := ""
	if pb.enablePCahche && pb.pcpCache != nil {
		pcpHost = pb.pcpCache.Get(key)
	}
	blockInfo := &model.PutBlock{
		Block: model.Block{
			PcpHost:      pcpHost,
			Bucket:       pb.bucket,
			PartFile:     localFile,
			Key:          key,
			BlockNumber:  0,
			TotalNumber:  1,
			Size:         fileSize,
			BlockSize:    pb.bolckSize,
			TimeDuration: 0,
			State:        model.STATE_FAIL,
		},
		UploadId: nil,
	}
	worker := PutWorker{
		wg:     nil,
		client: pb,
		ch:     nil,
		chErr:  nil,
	}
	worker.PutBlock(blockInfo)

	stats := utils.GetStatCounter(ctx)
	if stats == nil {
		stats = utils.NewStats()
	}
	stats.Update(&blockInfo.Block)
	stats.End(ctx)

	log.WithField("local file", localFile).WithField("bucket", pb.bucket).WithField("key", key).
		WithField("stats", stats).Infoln("successfully put single file")
	return nil
}

func (pb *PBucket) putMultiPart(ctx context.Context, filename, key string, fileSize int64) error {
	createResp, err := pb.s3ClientCache.Get().CreateMultipartUpload(context.TODO(), &s3.CreateMultipartUploadInput{
		Bucket: aws.String(pb.router.GetStsInfo().BucketName),
		Key:    aws.String(key),
	})
	if err != nil {
		log.WithError(err).WithField("bucket", pb.router.GetStsInfo().BucketName).WithField("key", key).
			Errorln("failed to CreateMultipartUpload")
		return err
	}
	uploadID := createResp.UploadId

	blockCount := (fileSize + pb.bolckSize - 1) / pb.bolckSize
	var wg sync.WaitGroup
	chBlock := make(chan *model.PutBlock, blockCount)

	chErrors := make(chan error, 1)
	threadNumber := min(pb.groupNumber, int(blockCount))
	for i := 0; i < threadNumber; i++ {
		wg.Add(1)
		worker := PutWorker{
			wg:     &wg,
			client: pb,
			ch:     chBlock,
			chErr:  chErrors,
		}
		go worker.putWorker()
	}

	blockList := make([]*model.PutBlock, blockCount)

	go func() {
		defer close(chBlock)
		for i := int64(0); i < blockCount; i++ {
			offset := i * pb.bolckSize
			remaining := fileSize - offset
			size := pb.bolckSize
			if remaining < pb.bolckSize {
				size = remaining
			}
			pcpHost := ""
			if pb.enablePCahche && pb.pcpCache != nil {
				pcpHost = pb.pcpCache.Get(key + strconv.FormatInt(i, 10))
			}
			blockList[i] = &model.PutBlock{
				Block: model.Block{
					PcpHost:      pcpHost,
					Bucket:       pb.bucket,
					PartFile:     filename,
					Key:          key,
					BlockNumber:  i,
					TotalNumber:  blockCount,
					Size:         size,
					BlockSize:    pb.bolckSize,
					TimeDuration: 0,
					State:        model.STATE_FAIL,
				},
				UploadId: uploadID,
			}
			chBlock <- blockList[i]
		}
	}()

	wg.Wait()
	select {
	case err := <-chErrors:
		pb.abortMultipartPut(key, uploadID)
		log.WithError(err).WithField("bucket", pb.router.GetStsInfo().BucketName).WithField("key", key).
			Errorln("failed to put block")
		return err
	default:
	}
	close(chErrors)

	stats := utils.GetStatCounter(ctx)
	if stats == nil {
		stats = utils.NewStats()
	}
	completedParts := make([]types.CompletedPart, blockCount)
	for i := range int(blockCount) {
		PartNumber := int32(blockList[i].BlockNumber + 1)
		completedParts[i] = types.CompletedPart{
			PartNumber: &PartNumber,
			ETag:       blockList[i].Etag,
		}
		stats.Update(&blockList[i].Block)
	}

	_, err = pb.s3ClientCache.Get().CompleteMultipartUpload(context.TODO(), &s3.CompleteMultipartUploadInput{
		Bucket:   aws.String(pb.router.GetStsInfo().BucketName),
		Key:      aws.String(key),
		UploadId: uploadID,
		MultipartUpload: &types.CompletedMultipartUpload{
			Parts: completedParts,
		},
	})
	if err != nil {
		log.WithError(err).WithField("BucketName", pb.router.GetStsInfo().BucketName).
			WithField("key", key).Errorln("failed to CompleteMultipartUpload")
		return err
	}

	stats.End(ctx)
	log.WithField("file", filename).WithField("bucket", pb.bucket).WithField("key", key).
		WithField("stats", stats).Infoln("successfully put file")

	return nil
}
