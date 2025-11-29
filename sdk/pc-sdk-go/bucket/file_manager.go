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
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/aws-sdk-go-v2/service/s3/types"
	log "github.com/sirupsen/logrus"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"runtime"
	"strconv"
	"sync"
	"time"
)

const (
	FILE_TYPE_PUT = iota // 0
	FILE_TYPE_GET
)

type FileTask struct {
	Wg           *sync.WaitGroup
	s3Client     *s3.Client
	stsInfo      *StsInfo
	Bucket       string
	Type         int
	LocalPath    string
	RemoteKey    string
	Size         int64
	BlockSize    int64
	BlockCount   int64
	UploadId     *string
	TimeDuration int64
	Err          error
}

func (f *FileTask) IsSingleFile() bool {
	return f.BlockCount == 1
}

type FileManager struct {
	pb         *PBucket
	bucket     string
	concurrent int
	wg         sync.WaitGroup
	semaphore  chan struct{}
}

func NewFileManager(pb *PBucket, concurrent int) *FileManager {
	if concurrent <= 0 {
		concurrent = runtime.NumCPU() * 2
	}
	return &FileManager{
		pb:         pb,
		concurrent: concurrent,
		semaphore:  make(chan struct{}, concurrent),
	}
}

func NewSingleFileManager(pb *PBucket) *FileManager {
	return &FileManager{
		pb:         pb,
		concurrent: 1,
		semaphore:  nil,
	}
}

func (m *FileManager) AddTask(ctx context.Context, task *FileTask) {
	m.wg.Add(1)
	go m.processTask(ctx, task)
}

func (m *FileManager) Wait() {
	m.wg.Wait()
}

// 上传单个文件到 S3
func (m *FileManager) PutFile(ctx context.Context, fileTask *FileTask) error {
	options := GetOptions(ctx)
	var uploadID *string = nil

	// multipart for big file to create upload ID
	if !fileTask.IsSingleFile() {
		createResp, err := fileTask.s3Client.CreateMultipartUpload(ctx,
			&s3.CreateMultipartUploadInput{Bucket: aws.String(fileTask.stsInfo.BucketName),
				Key: aws.String(fileTask.RemoteKey),
			})
		if err != nil {
			log.WithError(err).WithField("bucket", fileTask.stsInfo.BucketName).
				WithField("key", fileTask.RemoteKey).Errorln("failed to CreateMultipartUpload")
			return err
		}
		uploadID = createResp.UploadId
		fileTask.UploadId = uploadID
	}

	var wg sync.WaitGroup
	wg.Add(int(fileTask.BlockCount))
	fileTask.Wg = &wg

	// add block list
	blockList := make([]*Block, fileTask.BlockCount)
	for i := int64(0); i < fileTask.BlockCount; i++ {
		offset := i * fileTask.BlockSize
		remaining := fileTask.Size - offset
		size := fileTask.BlockSize
		if remaining < fileTask.BlockSize {
			size = remaining
		}
		pcpHost := m.pb.getPcpHost(fileTask.RemoteKey + strconv.FormatInt(i, 10))
		blockList[i] = &Block{
			File:         fileTask,
			PcpHost:      pcpHost,
			BlockNumber:  i,
			Size:         size,
			TimeDuration: 0,
			State:        STATE_FAIL,
		}
		m.pb.blockWorker.Add(blockList[i])
	}

	wg.Wait()
	stats := options.BlockStats

	// completed for multipart upload
	if uploadID != nil {
		completedParts := make([]types.CompletedPart, fileTask.BlockCount)
		for i := range int(fileTask.BlockCount) {
			PartNumber := int32(blockList[i].BlockNumber + 1)
			completedParts[i] = types.CompletedPart{
				PartNumber: &PartNumber,
				ETag:       blockList[i].Etag,
			}
			stats.Update(blockList[i])
		}
		_, err := fileTask.s3Client.CompleteMultipartUpload(context.TODO(), &s3.CompleteMultipartUploadInput{
			Bucket:   aws.String(fileTask.stsInfo.BucketName),
			Key:      aws.String(fileTask.RemoteKey),
			UploadId: uploadID,
			MultipartUpload: &types.CompletedMultipartUpload{
				Parts: completedParts,
			},
		})
		if err != nil {
			log.WithError(err).WithField("BucketName", fileTask.stsInfo.BucketName).
				WithField("key", fileTask.RemoteKey).Errorln("failed to CompleteMultipartUpload")
			return err
		}
	} else {
		stats.Update(blockList[0])
	}
	if options.DebugMode {
		log.WithField("file", fileTask.LocalPath).WithField("bucket", m.pb.bucket).
			WithField("key", fileTask.RemoteKey).WithField("stats", stats).
			Infoln("successfully put file")
	}
	return nil
}

func (m *FileManager) GetFile(ctx context.Context, fileTask *FileTask) error {
	options := GetOptions(ctx)

	var wg sync.WaitGroup
	wg.Add(int(fileTask.BlockCount))
	fileTask.Wg = &wg

	// add blocks
	blockList := make([]*Block, fileTask.BlockCount)
	for i := int64(0); i < fileTask.BlockCount; i++ {
		offset := i * fileTask.BlockSize
		remaining := fileTask.Size - offset
		size := fileTask.BlockSize
		if remaining < fileTask.BlockSize {
			size = remaining
		}
		pcpHost := m.pb.getPcpHost(fileTask.RemoteKey + strconv.FormatInt(i, 10))
		blockList[i] = &Block{
			File:         fileTask,
			PcpHost:      pcpHost,
			BlockNumber:  i,
			Size:         size,
			TimeDuration: 0,
			State:        STATE_FAIL,
		}
		m.pb.blockWorker.Add(blockList[i])
	}

	wg.Wait()
	stats := options.BlockStats
	// merger blocks for big file
	if !fileTask.IsSingleFile() {
		localPartFiles := make([]string, fileTask.BlockCount)
		for i := 0; i < int(fileTask.BlockCount); i++ {
			localPartFiles[i] = blockList[i].GetLocalPartPath()
			stats.Update(blockList[i])
		}
		err := utils.MergeFiles(localPartFiles, fileTask.LocalPath)
		if err != nil {
			log.WithError(err).WithField("key", fileTask.RemoteKey).Errorln("failed to head object")
			return err
		}
	} else {
		stats.Update(blockList[0])
	}
	if options.DebugMode {
		log.WithField("file", fileTask.LocalPath).WithField("bucket", fileTask.Bucket).
			WithField("key", fileTask.RemoteKey).WithField("stats", stats).
			Infoln("successfully get file")
	}
	return nil
}

func (m *FileManager) processTask(ctx context.Context, task *FileTask) {
	defer m.wg.Done()

	// control concurrent number
	m.semaphore <- struct{}{}
	defer func() {
		<-m.semaphore
	}()
	startTime := time.Now().UnixMilli()
	if task.Type == FILE_TYPE_PUT {
		task.Err = m.PutFile(ctx, task)
	} else if task.Type == FILE_TYPE_GET {
		task.Err = m.GetFile(ctx, task)
	}
	task.TimeDuration = time.Now().UnixMilli() - startTime
	GetOptions(ctx).FileStats.Update(task)
}
