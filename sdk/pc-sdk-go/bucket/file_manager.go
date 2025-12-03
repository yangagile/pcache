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
	"errors"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/aws-sdk-go-v2/service/s3/types"
	log "github.com/sirupsen/logrus"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"os"
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

const (
	FSTATE_FAIL = iota // 0
	FSTATE_OK          // 1
	FSTATE_OK_SKIP_EXIST
	FSTATE_OK_SKIP_UNCHANG // 2
)

type FileTask struct {
	Ctx            *context.Context
	Wg             *sync.WaitGroup
	S3Client       *s3.Client
	Sts            *StsInfo
	ETag           *string
	Metadata       map[string]string
	Bucket         string
	Type           int
	LocalFile      string
	LocalChecksum  string
	LocalSize      int64
	ObjectKey      string
	ObjectChecksum string
	ObjectSize     int64
	BlockSize      int64
	BlockCount     int64
	UploadId       *string
	DurationTime   int64
	Stats          int
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

func (m *FileManager) PutFile(ctx context.Context, fileTask *FileTask) error {
	startTime := time.Now().UnixMilli()
	defer m.updateFState(ctx, startTime, fileTask)

	options := GetOptions(ctx)
	var uploadID *string = nil
	if options.skipExisting {
		err := m.headObject(ctx, fileTask)
		if err != nil {
			fileTask.Stats = FSTATE_OK_SKIP_EXIST
			if options.DebugMode {
				log.WithField("key", fileTask.ObjectKey).Infoln("the remote key is existing")
			}
			return nil
		}
	}
	if options.skipUnchanged {
		same, err := m.diffFile2Object(ctx, fileTask)
		if err != nil {
			log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
				WithField("ObjectKey", fileTask.ObjectKey).
				Errorln("failed to diff local file and remote object")
			return err
		}
		if same {
			if options.DebugMode {
				log.WithField("LocalFile", fileTask.LocalFile).
					WithField("ObjectKey", fileTask.ObjectKey).
					Infoln("local file and remote object are same")
			}
			fileTask.Stats = FSTATE_OK_SKIP_UNCHANG
			return nil
		}
	}

	// add checksum to user meta
	if options.Checksum != "" {
		var err error
		if fileTask.LocalChecksum == "" {
			fileTask.LocalChecksum, err = m.getLocalFileChecksum(ctx, fileTask)
			if err != nil {
				log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
					Errorln("failed to get local file checksum")
				return err
			}
			if fileTask.Metadata == nil {
				fileTask.Metadata = make(map[string]string)
			}
			fileTask.Metadata["checksum_"+options.Checksum] = fileTask.LocalChecksum
		}
	}

	// multipart for big file to create upload ID
	if !fileTask.IsSingleFile() {
		createResp, err := fileTask.S3Client.CreateMultipartUpload(ctx,
			&s3.CreateMultipartUploadInput{Bucket: aws.String(fileTask.Sts.BucketName),
				Key:      aws.String(fileTask.ObjectKey),
				Metadata: fileTask.Metadata,
			})
		if err != nil {
			log.WithError(err).WithField("Bucket", fileTask.Sts.BucketName).
				WithField("key", fileTask.ObjectKey).Errorln("failed to CreateMultipartUpload")
			return err
		}
		uploadID = createResp.UploadId
		fileTask.UploadId = uploadID
	}

	var wg sync.WaitGroup
	wg.Add(int(fileTask.BlockCount))
	fileTask.Wg = &wg
	fileTask.Ctx = &ctx

	// add block list
	blockList := make([]*Block, fileTask.BlockCount)
	for i := int64(0); i < fileTask.BlockCount; i++ {
		offset := i * fileTask.BlockSize
		remaining := fileTask.LocalSize - offset
		size := fileTask.BlockSize
		if remaining < fileTask.BlockSize {
			size = remaining
		}
		pcpHost := m.pb.getPcpHost(fileTask.ObjectKey + strconv.FormatInt(i, 10))
		blockList[i] = &Block{
			File:         fileTask,
			PcpHost:      pcpHost,
			BlockNumber:  i,
			Size:         size,
			TimeDuration: 0,
			State:        BSTATE_FAIL,
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
		out, err := fileTask.S3Client.CompleteMultipartUpload(context.TODO(), &s3.CompleteMultipartUploadInput{
			Bucket:   aws.String(fileTask.Sts.BucketName),
			Key:      aws.String(fileTask.ObjectKey),
			UploadId: uploadID,
			MultipartUpload: &types.CompletedMultipartUpload{
				Parts: completedParts,
			},
		})
		if err != nil {
			log.WithError(err).WithField("BucketName", fileTask.Sts.BucketName).
				WithField("key", fileTask.ObjectKey).Errorln("failed to CompleteMultipartUpload")
			return err
		}
		fileTask.ETag = out.ETag
	} else {
		stats.Update(blockList[0])
	}
	if options.DebugMode {
		log.WithField("file", fileTask.LocalFile).WithField("Bucket", m.pb.bucket).
			WithField("key", fileTask.ObjectKey).WithField("Stats", stats).
			Infoln("successfully put file")
	}
	fileTask.Stats = FSTATE_OK
	return nil
}

func (m *FileManager) GetFile(ctx context.Context, fileTask *FileTask) error {
	startTime := time.Now().UnixMilli()
	defer m.updateFState(ctx, startTime, fileTask)
	options := GetOptions(ctx)
	if fileTask.BlockCount == 0 {
		err := m.headObject(ctx, fileTask)
		if err != nil {
			log.WithError(err).WithField("ObjectKey", fileTask.ObjectKey).
				Errorln("failed to head remote object")
			return err
		}
	}
	if options.skipExisting {
		_, err := os.Stat(fileTask.LocalFile)
		if err == nil {
			fileTask.Stats = FSTATE_OK_SKIP_EXIST
			if options.DebugMode {
				log.WithField("file", fileTask.LocalFile).Infoln("the local file is existing")
			}
			return nil
		}
	}
	if options.skipUnchanged {
		_, err := os.Stat(fileTask.LocalFile)
		// 如果错误为 nil，表示文件存在；如果错误为 os.ErrNotExist，表示文件不存在
		if err == nil {
			same, err := m.diffFile2Object(ctx, fileTask)
			if err != nil {
				log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
					WithField("ObjectKey", fileTask.ObjectKey).
					Errorln("failed to diff local file and remote object")
				return err
			}
			if same {
				if options.DebugMode {
					log.WithField("LocalFile", fileTask.LocalFile).
						WithField("ObjectKey", fileTask.ObjectKey).
						Infoln("local file and remote object are same")
				}
				fileTask.Stats = FSTATE_OK_SKIP_UNCHANG
				return nil
			}
		}
	}
	var wg sync.WaitGroup
	wg.Add(int(fileTask.BlockCount))
	fileTask.Wg = &wg
	fileTask.Ctx = &ctx

	// add blocks
	blockList := make([]*Block, fileTask.BlockCount)
	for i := int64(0); i < fileTask.BlockCount; i++ {
		offset := i * fileTask.BlockSize
		remaining := fileTask.LocalSize - offset
		size := fileTask.BlockSize
		if remaining < fileTask.BlockSize {
			size = remaining
		}
		pcpHost := m.pb.getPcpHost(fileTask.ObjectKey + strconv.FormatInt(i, 10))
		blockList[i] = &Block{
			File:         fileTask,
			PcpHost:      pcpHost,
			BlockNumber:  i,
			Size:         size,
			TimeDuration: 0,
			State:        BSTATE_FAIL,
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
		err := utils.MergeFiles(localPartFiles, fileTask.LocalFile)
		if err != nil {
			log.WithError(err).WithField("key", fileTask.ObjectKey).Errorln("failed to merge file")
			return err
		}
	} else {
		stats.Update(blockList[0])
	}

	// verify file with checksum
	if options.Checksum != "" {
		same, err := m.diffFile2Object(ctx, fileTask)
		if !same {
			log.WithError(err).WithField("file", fileTask.LocalFile).WithField("key", fileTask.ObjectKey).
				Errorln("failed to verify file")
			return err
		}
	}
	if options.DebugMode {
		log.WithField("file", fileTask.LocalFile).WithField("Bucket", fileTask.Bucket).
			WithField("key", fileTask.ObjectKey).WithField("Stats", stats).
			Infoln("successfully get file")
	}
	fileTask.Stats = FSTATE_OK
	return nil
}

func (m *FileManager) getLocalFileSize(fileTask *FileTask) (int64, error) {
	fileInfo, err := os.Stat(fileTask.LocalFile)
	if err != nil {
		log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
			Errorln("failed to open file")
		return 0, err
	}
	return fileInfo.Size(), nil
}

func (m *FileManager) headObject(ctx context.Context, fileTask *FileTask) error {
	options := GetOptions(ctx)

	input := &s3.HeadObjectInput{
		Bucket: aws.String(fileTask.Sts.BucketName),
		Key:    aws.String(fileTask.ObjectKey),
	}
	resp, err := fileTask.S3Client.HeadObject(context.TODO(), input)
	if err != nil {
		log.WithError(err).WithField("key", fileTask.ObjectKey).Errorln("failed to head object")
		return err
	}
	if resp.Metadata != nil {
		fileTask.Metadata = resp.Metadata
		if options.Checksum != "" {
			fileTask.ObjectChecksum = resp.Metadata["checksum_"+options.Checksum]
		}
	}
	fileTask.ETag = resp.ETag
	fileTask.ObjectSize = *resp.ContentLength
	fileTask.LocalSize = *resp.ContentLength
	fileTask.BlockCount = (fileTask.LocalSize + fileTask.BlockSize - 1) / fileTask.BlockSize
	return nil
}

func (m *FileManager) getLocalFileChecksum(ctx context.Context, fileTask *FileTask) (string, error) {
	options := GetOptions(ctx)
	if strings.EqualFold(options.Checksum, "md5") {
		checksum, err := utils.GetMD5Base64FromFile(fileTask.LocalFile)
		if err != nil {
			log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
				Errorln("failed to get MD5 checksum")
			return "", err
		}
		return checksum, nil

	} else if strings.EqualFold(options.Checksum, "crc32") {
		checksum, err := utils.GetCRC32Base64FromFile(fileTask.LocalFile)
		if err != nil {
			log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
				Errorln("failed to get CRC32 checksum")
			return "", err
		}
		return checksum, nil
	}
	return "", errors.New("failed to get local file checksum")
}

func (m *FileManager) diffFile2Object(ctx context.Context, fileTask *FileTask) (bool, error) {
	options := GetOptions(ctx)
	var err error
	if options.Checksum != "" {
		if fileTask.ObjectChecksum == "" {
			err = m.headObject(ctx, fileTask)
			if err != nil {
				log.WithError(err).WithField("ObjectKey", fileTask.ObjectKey).
					Errorln("failed to get head remote object")
				return false, err
			}
		}
		if fileTask.LocalChecksum == "" {
			fileTask.LocalChecksum, err = m.getLocalFileChecksum(ctx, fileTask)
			if err != nil {
				log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
					Errorln("failed to get local file checksum")
				return false, err
			}
		}
		if fileTask.ObjectChecksum == fileTask.LocalChecksum {
			return true, nil
		}
	} else {
		if fileTask.LocalSize == 0 {
			fileTask.LocalSize, err = m.getLocalFileSize(fileTask)
			if err != nil {
				log.WithError(err).WithField("LocalFile", fileTask.LocalFile).
					Errorln("failed to get local file size")
				return false, err
			}
		}
		if fileTask.ObjectSize == 0 {
			var err error
			err = m.headObject(ctx, fileTask)
			if err != nil {
				log.WithError(err).WithField("object", fileTask.ObjectKey).
					Errorln("failed to head remote object")
				return false, err
			}
		}
		if fileTask.LocalSize == fileTask.ObjectSize {
			return true, nil
		}
	}
	return false, nil
}

func (m *FileManager) updateFState(ctx context.Context, startTime int64, fileTask *FileTask) {
	fileTask.DurationTime = time.Now().UnixMilli() - startTime
	GetOptions(ctx).FileStats.Update(fileTask)
}

func (m *FileManager) processTask(ctx context.Context, task *FileTask) {
	defer m.wg.Done()

	// control concurrent number
	m.semaphore <- struct{}{}
	defer func() {
		<-m.semaphore
	}()

	if task.Type == FILE_TYPE_PUT {
		m.PutFile(ctx, task)
	} else if task.Type == FILE_TYPE_GET {
		m.GetFile(ctx, task)
	}
}
