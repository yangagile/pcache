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
	"io"
	"os"
	"strconv"
	"strings"
	"sync"
	"testdisk/model"
	"testdisk/utils"
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
	enableTracker        bool
	pmsMgr               *PmsManager
	secretMgr            *SecretManager
}

type Option func(*PBucket)

func NewPBucket(ctx *context.Context, pmsUrl, bucket, ak, sk string, opts ...Option) (*PBucket, error) {
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
		enableTracker:        true,
		s3ClientCacheTimeSec: 300,
	}

	for _, opt := range opts {
		opt(pb)
	}

	var err error
	pb.secretMgr, err = NewSecretManager(ctx, ak, sk)
	if err != nil {
		log.WithError(err).WithField("ak", ak).Errorln("failed to init secretMgr")
		return nil, err
	}

	pb.pmsMgr, err = NewPmsManager(ctx, strings.Split(pmsUrl, ","), pb.secretMgr)
	if err != nil {
		log.WithError(err).WithField("pmsUrl", pmsUrl).Errorln("failed to init PmsManager")
		return nil, err
	}

	if pb.enablePCahche {
		pb.pcpCache = NewPcpCache(pb.pcpCacheTimeSec, pb.getPcpTable)
	}

	if utils.GetTracker(ctx) == nil && pb.enableTracker {
		*ctx = utils.WithTracker(ctx)
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
		log.WithError(err).WithField("iamUrl", pb.pmsMgr.GetPmsUrl()).
			Errorln("failed to get pcp table")
		return nil
	}
	return pcpTable
}

// 自定义超时时间
func WithPermissions(permission string) Option {
	return func(c *PBucket) {
		c.permissions = strings.Split(permission, ",")
	}
}

func WithGroupNumber(groupNumber int) Option {
	return func(c *PBucket) {
		c.groupNumber = groupNumber
	}
}

func WithBlockSize(blockSize int64) Option {
	return func(c *PBucket) {
		c.bolckSize = blockSize
	}
}

func WithPcpCacheTimeout(pcpCacheTimeSec int64) Option {
	return func(c *PBucket) {
		c.pcpCacheTimeSec = pcpCacheTimeSec
	}
}

func WithPCacheEnable(enablePCache bool) Option {
	return func(c *PBucket) {
		c.enablePCahche = enablePCache
	}
}

func (pb *PBucket) Put(ctx *context.Context, localFilePath, objectKey string) error {
	if utils.GetTracker(ctx) == nil && pb.enableTracker {
		*ctx = utils.WithTracker(ctx)
	}
	fileInfo, err := os.Stat(localFilePath)
	if err != nil {
		log.WithError(err).WithField("localFilePath", localFilePath).
			Errorln("failed to open file")
		return err
	}
	if pb.enablePCahche && fileInfo.Size() > pb.bolckSize {
		return pb.putWithPCache(ctx, localFilePath, objectKey, fileInfo.Size())
	} else {
		return pb.putFromLocal(ctx, localFilePath, objectKey)
	}
}

// 上传文件到 S3
func (pb *PBucket) putFromLocal(ctx *context.Context, filePath, objectKey string) error {
	// 打开本地文件
	file, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("failed to open:%v with err:%v", filePath, err)
	}
	defer file.Close()

	stsInfo := pb.router.GetStsInfo()
	input := &s3.PutObjectInput{
		Bucket: aws.String(stsInfo.BucketName),
		Key:    aws.String(objectKey),
		Body:   file,
	}

	_, err = pb.s3ClientCache.Get().PutObject(context.TODO(), input)
	if err != nil {
		return fmt.Errorf("failed to put:%v with err:%v", input, err)
	}

	return nil
}

func (pb *PBucket) Get(ctx *context.Context, objectKey, localFilePath string) error {
	if utils.GetTracker(ctx) == nil && pb.enableTracker {
		*ctx = utils.WithTracker(ctx)
	}
	if pb.enablePCahche {
		return pb.getWithPCache(ctx, objectKey, localFilePath)
	} else {
		return pb.getFromLocal(ctx, objectKey, localFilePath)
	}
}

// 从 S3 下载文件
func (pb *PBucket) getFromLocal(ctx *context.Context, objectKey, filePath string) error {
	input := &s3.GetObjectInput{
		Bucket: aws.String(pb.router.GetStsInfo().BucketName),
		Key:    aws.String(objectKey),
	}

	result, err := pb.s3ClientCache.Get().GetObject(context.TODO(), input)
	if err != nil {
		return fmt.Errorf("failed to get:%v with err:%v", input, err)
	}
	defer result.Body.Close()

	file, err := os.Create(filePath)
	if err != nil {
		return fmt.Errorf("failed to create local file:%v with err:%v", filePath, err)
	}
	defer file.Close()

	_, err = io.Copy(file, result.Body)
	if err != nil {
		return fmt.Errorf("failed to write file:%v with err:%v", file, err)
	}

	return nil
}

func (pb *PBucket) getWithPCache(ctx *context.Context, objectKey, filePath string) error {
	t := utils.GetTracker(ctx)
	if t != nil {
		t.StartPhase("head object")
	}

	resp, err := utils.HeadObject(pb.s3ClientCache.Get(), pb.router.GetStsInfo().BucketName, objectKey)
	if err != nil {
		log.WithError(err).WithField("objectKey", objectKey).Errorln("failed to head object")
		return err
	}
	if t != nil {
		t.EndPhase("head objectdone")
		t.StartPhase("get multipart")
	}

	if *resp.ContentLength == 0 {
		return fmt.Errorf("object size is 0")
	}

	fileSize := *resp.ContentLength
	chunkCount := (fileSize + pb.bolckSize - 1) / pb.bolckSize

	var wg sync.WaitGroup
	chBlock := make(chan *model.GetBlock, chunkCount)
	chErrors := make(chan error, 1)
	threadNumber := min(pb.groupNumber, int(chunkCount))

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

	blockList := make([]*model.GetBlock, chunkCount)
	go func() {
		defer close(chBlock)
		for part := int32(1); part <= int32(chunkCount); part++ {
			offset := int64(part-1) * pb.bolckSize
			remaining := fileSize - offset
			size := pb.bolckSize
			if remaining < pb.bolckSize {
				size = remaining
			}

			pcpUrl := pb.pcpCache.Get(objectKey + strconv.Itoa(int(part)))
			if len(pcpUrl) > 0 {
				pcpUrl = fmt.Sprintf("%s%s/%s.%08d_%d",
					pcpUrl,
					pb.bucket,
					objectKey,
					part,
					size)
			}

			partFile := fmt.Sprintf("%s.%08d_%d", filePath, part, size)
			blockList[part-1] = &model.GetBlock{
				Block: model.Block{
					File:         partFile,
					Key:          objectKey,
					PartNumber:   part,
					Offset:       offset,
					Size:         size,
					PcpHost:      pcpUrl,
					TimeDuration: 0,
					State:        model.STATE_FAIL,
				},
			}
			chBlock <- blockList[part-1]
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

	if t != nil {
		t.EndPhase("get multipart done")
		t.StartPhase("merge file")
	}

	stats := utils.NewStats()
	localPartFiles := make([]string, chunkCount)
	for i := 0; i < int(chunkCount); i++ {
		localPartFiles[i] = blockList[i].File
		stats.Update(&blockList[i].Block)
	}
	err = utils.MergeFiles(localPartFiles, filePath)
	if err != nil {
		log.WithError(err).WithField("objectKey", objectKey).Errorln("failed to head object")
		return err
	}
	if t != nil {
		t.EndPhase("merge file done")
	}

	stats.End(ctx)
	log.WithField("file", filePath).WithField("bucket", pb.bucket).WithField("key", objectKey).
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

func (pb *PBucket) putWithPCache(ctx *context.Context, filename, key string, fileSize int64) error {
	t := utils.GetTracker(ctx)
	if t != nil {
		t.StartPhase("create upload ID")
	}

	createResp, err := pb.s3ClientCache.Get().CreateMultipartUpload(context.TODO(), &s3.CreateMultipartUploadInput{
		Bucket: aws.String(pb.router.GetStsInfo().BucketName),
		Key:    aws.String(key),
	})
	if err != nil {
		log.WithError(err).WithField("bucket", pb.router.GetStsInfo().BucketName).WithField("key", key).
			Errorln("failed to CreateMultipartUpload")
		return err
	}
	if t != nil {
		t.EndPhase("create upload ID done")
		t.StartPhase("multipart put object")
	}
	uploadID := createResp.UploadId

	chunkCount := (fileSize + pb.bolckSize - 1) / pb.bolckSize
	var wg sync.WaitGroup
	ch := make(chan *model.PutBlock, chunkCount)
	chErrors := make(chan error, 1)
	blockList := make([]*model.PutBlock, chunkCount)

	go func() {
		defer close(ch)
		for part := int32(1); part <= int32(chunkCount); part++ {
			offset := int64(part-1) * pb.bolckSize
			remaining := fileSize - offset
			size := pb.bolckSize
			if remaining < pb.bolckSize {
				size = remaining
			}

			pcpUrl := pb.pcpCache.Get(key + strconv.Itoa(int(part)))
			if len(pcpUrl) > 0 {
				pcpUrl = fmt.Sprintf("%s%s/%s.%08d_%d",
					pcpUrl,
					pb.bucket,
					key,
					part,
					size)
			}

			blockList[part-1] = &model.PutBlock{
				Block: model.Block{
					File:         filename,
					Key:          key,
					PartNumber:   part,
					Offset:       offset,
					Size:         size,
					PcpHost:      pcpUrl,
					TimeDuration: 0,
					State:        model.STATE_FAIL,
				},
				UploadId: uploadID,
			}

			ch <- blockList[part-1]
		}
	}()

	threadNumber := min(pb.groupNumber, int(chunkCount))
	for i := 0; i < threadNumber; i++ {
		wg.Add(1)
		worker := PutWorker{
			wg:     &wg,
			client: pb,
			ch:     ch,
			chErr:  chErrors,
		}
		go worker.uploadWorker()
	}

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

	if t != nil {
		t.EndPhase("multipart put object done")
		t.StartPhase("complete multipart")
	}

	stats := utils.NewStats()
	completedParts := make([]types.CompletedPart, chunkCount)
	for i := range int(chunkCount) {
		completedParts[i] = types.CompletedPart{
			PartNumber: &blockList[i].PartNumber,
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
	if t != nil {
		t.StartPhase("complete multipart done")
	}

	stats.End(ctx)
	log.WithField("file", filename).WithField("bucket", pb.bucket).WithField("key", key).
		WithField("stats", stats).Infoln("successfully put file")

	return nil
}
