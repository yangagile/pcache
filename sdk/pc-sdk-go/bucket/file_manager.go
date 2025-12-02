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
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	FILE_TYPE_PUT = iota // 0
	FILE_TYPE_GET
)

type FileTask struct {
	ctx          *context.Context
	wg           *sync.WaitGroup
	s3Client     *s3.Client
	stsInfo      *StsInfo
	metadata     map[string]string
	bucket       string
	opsType      int
	localPath    string
	remoteKey    string
	size         int64
	blockSize    int64
	blockCount   int64
	uploadId     *string
	timeDuration int64
	err          error
}

func (f *FileTask) IsSingleFile() bool {
	return f.blockCount == 1
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

func (m *FileManager) PutFile(ctx context.Context, fileTask *FileTask) error {
	options := GetOptions(ctx)
	var uploadID *string = nil

	// add checksum to user meta
	if options.Checksum != "" {
		if strings.EqualFold(options.Checksum, "md5") {
			checksum, err := utils.GetMD5Base64FromFile(fileTask.localPath)
			if err != nil {
				log.WithError(err).WithField("localPath", fileTask.localPath).
					Errorln("failed to get MD5 checksum")
				return err
			}
			if fileTask.metadata == nil {
				fileTask.metadata = make(map[string]string)
			}
			fileTask.metadata["checksum_md5"] = checksum
		} else if strings.EqualFold(options.Checksum, "crc32") {
			checksum, err := utils.GetCRC32Base64FromFile(fileTask.localPath)
			if err != nil {
				log.WithError(err).WithField("localPath", fileTask.localPath).
					Errorln("failed to get CRC32 checksum")
				return err
			}
			if fileTask.metadata == nil {
				fileTask.metadata = make(map[string]string)
			}
			fileTask.metadata["checksum_crc32"] = checksum
		} else {
			err := fmt.Errorf("checksum algrithm %s is not support", options.Checksum)
			log.WithError(err).Errorln("failed to get checksum")
			return err
		}
	}

	// multipart for big file to create upload ID
	if !fileTask.IsSingleFile() {
		createResp, err := fileTask.s3Client.CreateMultipartUpload(ctx,
			&s3.CreateMultipartUploadInput{Bucket: aws.String(fileTask.stsInfo.BucketName),
				Key:      aws.String(fileTask.remoteKey),
				Metadata: fileTask.metadata,
			})
		if err != nil {
			log.WithError(err).WithField("bucket", fileTask.stsInfo.BucketName).
				WithField("key", fileTask.remoteKey).Errorln("failed to CreateMultipartUpload")
			return err
		}
		uploadID = createResp.UploadId
		fileTask.uploadId = uploadID
	}

	var wg sync.WaitGroup
	wg.Add(int(fileTask.blockCount))
	fileTask.wg = &wg
	fileTask.ctx = &ctx

	// add block list
	blockList := make([]*Block, fileTask.blockCount)
	for i := int64(0); i < fileTask.blockCount; i++ {
		offset := i * fileTask.blockSize
		remaining := fileTask.size - offset
		size := fileTask.blockSize
		if remaining < fileTask.blockSize {
			size = remaining
		}
		pcpHost := m.pb.getPcpHost(fileTask.remoteKey + strconv.FormatInt(i, 10))
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
		completedParts := make([]types.CompletedPart, fileTask.blockCount)
		for i := range int(fileTask.blockCount) {
			PartNumber := int32(blockList[i].BlockNumber + 1)
			completedParts[i] = types.CompletedPart{
				PartNumber: &PartNumber,
				ETag:       blockList[i].Etag,
			}
			stats.Update(blockList[i])
		}
		_, err := fileTask.s3Client.CompleteMultipartUpload(context.TODO(), &s3.CompleteMultipartUploadInput{
			Bucket:   aws.String(fileTask.stsInfo.BucketName),
			Key:      aws.String(fileTask.remoteKey),
			UploadId: uploadID,
			MultipartUpload: &types.CompletedMultipartUpload{
				Parts: completedParts,
			},
		})
		if err != nil {
			log.WithError(err).WithField("BucketName", fileTask.stsInfo.BucketName).
				WithField("key", fileTask.remoteKey).Errorln("failed to CompleteMultipartUpload")
			return err
		}
	} else {
		stats.Update(blockList[0])
	}
	if options.DebugMode {
		log.WithField("file", fileTask.localPath).WithField("bucket", m.pb.bucket).
			WithField("key", fileTask.remoteKey).WithField("stats", stats).
			Infoln("successfully put file")
	}
	return nil
}

func (m *FileManager) GetFile(ctx context.Context, fileTask *FileTask) error {
	options := GetOptions(ctx)

	var wg sync.WaitGroup
	wg.Add(int(fileTask.blockCount))
	fileTask.wg = &wg
	fileTask.ctx = &ctx

	// add blocks
	blockList := make([]*Block, fileTask.blockCount)
	for i := int64(0); i < fileTask.blockCount; i++ {
		offset := i * fileTask.blockSize
		remaining := fileTask.size - offset
		size := fileTask.blockSize
		if remaining < fileTask.blockSize {
			size = remaining
		}
		pcpHost := m.pb.getPcpHost(fileTask.remoteKey + strconv.FormatInt(i, 10))
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
		localPartFiles := make([]string, fileTask.blockCount)
		for i := 0; i < int(fileTask.blockCount); i++ {
			localPartFiles[i] = blockList[i].GetLocalPartPath()
			stats.Update(blockList[i])
		}
		err := utils.MergeFiles(localPartFiles, fileTask.localPath)
		if err != nil {
			log.WithError(err).WithField("key", fileTask.remoteKey).Errorln("failed to merge file")
			return err
		}
	} else {
		stats.Update(blockList[0])
	}

	// verify file with checksum
	if options.Checksum != "" {
		err := m.verifyFile(ctx, fileTask)
		if err != nil {
			log.WithError(err).WithField("file", fileTask.localPath).WithField("key", fileTask.remoteKey).
				Errorln("failed to verify file")
			return err
		}
	}
	if options.DebugMode {
		log.WithField("file", fileTask.localPath).WithField("bucket", fileTask.bucket).
			WithField("key", fileTask.remoteKey).WithField("stats", stats).
			Infoln("successfully get file")
	}
	return nil
}

func (m *FileManager) verifyFile(ctx context.Context, fileTask *FileTask) error {
	options := GetOptions(ctx)
	if fileTask.metadata == nil && len(fileTask.metadata) == 0 {
		resp, err := HeadObject(fileTask.s3Client, fileTask.stsInfo.BucketName, fileTask.remoteKey)
		if err != nil {
			log.WithError(err).WithField("key", fileTask.remoteKey).Errorln("failed to head object")
			return err
		}
		fileTask.metadata = resp.Metadata
	}
	if strings.EqualFold(options.Checksum, "md5") {
		checksum, err := utils.GetMD5Base64FromFile(fileTask.localPath)
		if err != nil {
			log.WithError(err).WithField("localPath", fileTask.localPath).
				Errorln("failed to get MD5 checksum")
			return err
		}
		if fileTask.metadata["checksum_md5"] != checksum {
			return fmt.Errorf("checksum MD5 mismatch, remote:%v local:%v",
				fileTask.metadata["checksum_md5"], checksum)
		}
	} else if strings.EqualFold(options.Checksum, "crc32") {
		checksum, err := utils.GetCRC32Base64FromFile(fileTask.localPath)
		if err != nil {
			log.WithError(err).WithField("localPath", fileTask.localPath).
				Errorln("failed to get CRC32 checksum")
			return err
		}
		if fileTask.metadata["checksum_crc32"] != checksum {
			return fmt.Errorf("checksum CRC32 mismatch, remote:%v local:%v",
				fileTask.metadata["checksum_crc32"], checksum)
		}
		fileTask.metadata["checksum_crc32"] = checksum
	} else {
		err := fmt.Errorf("checksum algrithm %s is not support", options.Checksum)
		log.WithError(err).Errorln("failed to get checksum")
		return err
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
	if task.opsType == FILE_TYPE_PUT {
		task.err = m.PutFile(ctx, task)
	} else if task.opsType == FILE_TYPE_GET {
		task.err = m.GetFile(ctx, task)
	}
	task.timeDuration = time.Now().UnixMilli() - startTime
	GetOptions(ctx).FileStats.Update(task)
}
