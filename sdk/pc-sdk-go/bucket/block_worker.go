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
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	log "github.com/sirupsen/logrus"
	"io"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	BSTATE_FAIL          = iota // 0
	BSTATE_OK_PCP_REMOTE        // 1
	BSTATE_OK_PCP_DISK
	BSTATE_OK_PCP_MEMORY     // 2
	BSTATE_OK_LOCAL          // 2
	BSTATE_OK_LOCAL_PCP_FAIL // 2
)

type Block struct {
	File           *FileTask
	BlockNumber    int64
	BlockSize      int64
	Size           int64
	OffsetInBlock  int64
	OffsetInBuffer int64
	PcpHost        string
	Etag           *string
	TimeDuration   int64
	State          int
}

func (c *Block) GetPcPath() string {
	if c.PcpHost != "" && !strings.HasSuffix(c.PcpHost, "/") {
		c.PcpHost += "/"
	}
	return fmt.Sprintf("%s%s/%s.%d_%d", c.PcpHost, c.File.Bucket, c.File.ObjectKey,
		c.BlockNumber, c.File.BlockCount)
}

func (c *Block) GetLocalPartPath() string {
	return fmt.Sprintf("%s.%d_%d", c.File.LocalFile, c.BlockNumber, c.File.BlockCount)
}

type BlockWorker struct {
	in         chan *Block
	ctx        context.Context
	concurrent int
	cancel     context.CancelFunc
	wg         sync.WaitGroup
	retryTimes int
	client     *http.Client
}

func NewBlockWorker(ctx context.Context, threadNumber, chanSize int) *BlockWorker {
	// start work
	ctx, cancel := context.WithCancel(context.Background())
	blockWorker := &BlockWorker{
		in:         make(chan *Block, chanSize),
		ctx:        ctx,
		concurrent: threadNumber,
		cancel:     cancel,
		retryTimes: 2,
		client:     createHTTPClient(ctx),
	}
	return blockWorker

}

func createHTTPClient(ctx context.Context) *http.Client {
	httpTimeoutFactor := 1.0
	opt := GetOptions(ctx)
	if opt != nil {
		httpTimeoutFactor = opt.HttpTimeoutFactor
	}
	return &http.Client{
		Timeout: time.Duration(httpTimeoutFactor*30) * time.Second,
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout: time.Duration(httpTimeoutFactor*5) * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   time.Duration(httpTimeoutFactor*5) * time.Second,
			ResponseHeaderTimeout: time.Duration(httpTimeoutFactor*10) * time.Second,
			MaxConnsPerHost:       100,
			MaxIdleConnsPerHost:   10,
		},
	}
}

func (w *BlockWorker) Start() {
	for i := 0; i < w.concurrent; i++ {
		go w.worker()
	}
}

func (w *BlockWorker) Close() {
	w.cancel()
	w.wg.Wait()
}

func (w *BlockWorker) Add(Block *Block) {
	w.in <- Block
}

// put
func (w *BlockWorker) putToPcp(block *Block, stsInfo *StsInfo, buffer []byte) (string, error) {
	req, err := http.NewRequestWithContext(
		context.Background(),
		"POST",
		block.GetPcPath(),
		bytes.NewReader(buffer),
	)
	if err != nil {
		return "", err
	}

	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("Content-Length", strconv.Itoa(len(buffer)))
	stsJson, err := json.Marshal(stsInfo)
	if err != nil {
		return "", err
	}
	req.Header.Set("X-STS", string(stsJson))
	if block.File.UploadId != nil {
		req.Header.Set("X-UPLOAD-ID", *block.File.UploadId)
	}
	if block.File.Metadata != nil && len(block.File.Metadata) > 0 {
		metaJson, err := json.Marshal(block.File.Metadata)
		if err != nil {
			return "", err
		}
		req.Header.Set("X-USER-META", string(metaJson))
	}

	resp, err := w.client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("failed to put with respose code: %d", resp.StatusCode)
	}

	etagBytes, err := io.ReadAll(io.LimitReader(resp.Body, 128))
	if err != nil {
		return "", err
	}

	return string(bytes.TrimSpace(etagBytes)), nil
}

func (w *BlockWorker) putFromLocal(block *Block, buffer []byte) (string, error) {
	if block.File.IsSingleFile() {
		// small file
		input := &s3.PutObjectInput{
			Bucket:   aws.String(block.File.Sts.BucketName),
			Key:      aws.String(block.File.ObjectKey),
			Body:     bytes.NewReader(buffer),
			Metadata: block.File.Metadata,
		}
		uploadResp, err := block.File.S3Client.PutObject(context.TODO(), input)
		if err != nil {
			return "", err
		}
		return *uploadResp.ETag, nil
	} else {
		// big file
		PartNumber := int32(block.BlockNumber + 1)
		uploadResp, err := block.File.S3Client.UploadPart(context.TODO(), &s3.UploadPartInput{
			Bucket:     aws.String(block.File.Sts.BucketName),
			Key:        aws.String(block.File.ObjectKey),
			UploadId:   block.File.UploadId,
			PartNumber: &PartNumber,
			Body:       bytes.NewReader(buffer),
		})
		if err != nil {
			return "", err
		}
		return *uploadResp.ETag, nil
	}
}
func (w *BlockWorker) PutBlock(block *Block) error {
	options := GetOptions(*block.File.Ctx)
	// read file
	buf := make([]byte, block.Size)
	file, err := os.Open(block.File.LocalFile)
	defer file.Close()
	if err != nil {
		log.WithError(err).WithField("LocalFile", block.File.LocalFile).
			Errorln("failed to open local block file")
		return err
	}
	_, err = file.ReadAt(buf, block.BlockNumber*block.File.BlockSize)
	if err != nil {
		log.WithError(err).WithField("LocalFile", block.File.LocalFile).
			WithField("pos", block.BlockNumber*block.File.BlockSize).
			Errorln("failed to read local block file")
		return err
	}

	// try put to PCP
	eTag := ""
	if block.PcpHost != "" {
		for i := 0; i < w.retryTimes; i++ {
			eTag, err = w.putToPcp(block, block.File.Sts, buf)
			if err == nil {
				break
			} else {
				log.WithError(err).WithField("PcpHost", block.PcpHost).
					WithField("block", block.GetPcPath()).WithField("retryTimes", w.retryTimes).
					Errorln("failed to put block to PCP, will retry..")
			}
		}
		if err != nil {
			log.WithError(err).WithField("PcpHost", block.PcpHost).WithField("block", block.GetPcPath()).
				Errorln("failed to put block to PCP, will try from local.")
			// will try to put from local
			block.State = BSTATE_OK_LOCAL_PCP_FAIL
		} else {
			block.State = BSTATE_OK_PCP_DISK
			if options.DebugMode {
				log.WithField("PcpHost", block.PcpHost).WithField("block", block.GetPcPath()).
					Infoln("successfully put block to PCP")
			}
		}
	}

	// put from local
	if eTag == "" {
		eTag, err = w.putFromLocal(block, buf)
		if err != nil {
			log.WithError(err).WithField("block", block.GetPcPath()).
				Errorln("failed to put block from local")
			block.State = BSTATE_FAIL
			return err
		} else {
			if block.State != BSTATE_OK_LOCAL_PCP_FAIL {
				block.State = BSTATE_OK_LOCAL
			}
			if options.DebugMode {
				log.WithField("block", block.GetPcPath()).
					Infoln("successfully put block from local")
			}
		}
	}
	block.Etag = &eTag
	if block.File.IsSingleFile() {
		block.File.ETag = &eTag
	}
	return nil
}

// get
func (w *BlockWorker) getFromPcp(blockInfo *Block, stsInfo *StsInfo) error {
	// prepare request context
	req, err := http.NewRequestWithContext(context.Background(), "GET",
		blockInfo.GetPcPath(), nil)
	if err != nil {
		return err
	}
	stsJson, err := json.Marshal(stsInfo)
	if err != nil {
		return err
	}
	req.Header.Set("X-STS", string(stsJson))
	req.Header.Set("X-BLOCK-SIZE", strconv.FormatInt(blockInfo.BlockSize, 10))
	req.Header.Set("X-DATA-SIZE", strconv.FormatInt(blockInfo.Size, 10))
	req.Header.Set("X-BLOCK-OFFSET", strconv.FormatInt(blockInfo.File.Offset, 10))

	// request
	resp, err := w.client.Do(req)
	if err != nil {
		log.WithError(err).WithField("PcPath", blockInfo.GetPcPath()).Errorln("failed to request PCP")
		return err
	}
	defer resp.Body.Close()

	// check response
	if resp.StatusCode != http.StatusOK {
		log.WithError(err).Errorln("invalid PCP response code")
		err = fmt.Errorf("invalid PCP response code: %s", resp.Status)
		return err
	}

	contentLength := resp.Header.Get("Content-Length")
	if contentLength != "" {
		dataSize, err := strconv.ParseInt(contentLength, 10, 64)
		if err == nil && dataSize > 0 {
			if blockInfo.Size == 0 {
				blockInfo.Size = dataSize
			} else if blockInfo.File.IsLocalFile() && dataSize != blockInfo.Size {
				return fmt.Errorf("LocalSize is mismatch revieve:%d block:%d", dataSize, blockInfo.Size)
			}
		}
	}

	// get response Stats
	if cacheHit := resp.Header.Get("X-CACHE-HIT"); cacheHit != "" {
		hitCount, err := strconv.Atoi(cacheHit)
		if err == nil {
			blockInfo.State = hitCount
		} else {
			log.WithError(err).WithField(" X-CACHE-HIT", hitCount).
				Errorln("invalid format of X-CACHE-HIT")
		}
	}

	buffer, err := io.ReadAll(resp.Body)

	if blockInfo.File.IsLocalFile() {
		// save to file
		var localFile string
		if blockInfo.File.IsSingleFile() {
			localFile = blockInfo.File.LocalFile
		} else {
			localFile = blockInfo.GetLocalPartPath()
		}
		outFile, err := os.Create(localFile)
		if err != nil {
			log.WithError(err).WithField("LocalFile", localFile).Errorln("failed to create local file")
			return err
		}
		defer outFile.Close()
		outFile.Write(buffer)
	} else {
		copy(blockInfo.File.DataBuffer[blockInfo.OffsetInBuffer:], buffer)
		blockInfo.File.DataSize += int64(len(buffer))
	}

	return nil
}

func (w *BlockWorker) getFromLocal(block *Block) error {
	// get from local
	var result *s3.GetObjectOutput
	var err error
	if block.BlockSize == 0 {
		result, err = block.File.S3Client.GetObject(context.TODO(), &s3.GetObjectInput{
			Bucket: aws.String(block.File.Sts.BucketName),
			Key:    aws.String(block.File.ObjectKey),
		})
		if err != nil {
			log.WithError(err).WithField("Bucket", block.File.Sts.BucketName).
				WithField("key", block.File.ObjectKey).
				Errorln("failed to get file from s3")
			return err
		}
		block.File.ETag = result.ETag

	} else {
		var offset int64
		var rangeHeader string
		if block.File.IsLocalFile() {
			offset = block.BlockNumber * block.File.BlockSize
			rangeHeader = fmt.Sprintf("bytes=%d-%d", offset, offset+block.File.LocalSize-1)
		} else {
			offset = block.File.Offset
			rangeHeader = fmt.Sprintf("bytes=%d-%d", offset, offset+block.Size-1)
		}

		result, err = block.File.S3Client.GetObject(context.TODO(), &s3.GetObjectInput{
			Bucket: aws.String(block.File.Sts.BucketName),
			Key:    aws.String(block.File.ObjectKey),
			Range:  aws.String(rangeHeader),
		})
		if err != nil {
			log.WithError(err).WithField("Bucket", block.File.Sts.BucketName).
				WithField("key", block.File.ObjectKey).
				WithField("rangeHeader", rangeHeader).
				Errorln("failed to get part of object from s3")
			return err
		}
	}
	defer result.Body.Close()

	if block.File.IsLocalFile() {
		// create local file
		var localFile string
		if block.File.IsSingleFile() {
			localFile = block.File.LocalFile
		} else {
			localFile = block.GetLocalPartPath()
		}
		file, err := os.Create(localFile)
		if err != nil {
			log.WithError(err).WithField("local file", localFile).
				Errorln("failed to create local file")
			return err
		}
		defer file.Close()

		if _, err := io.Copy(file, result.Body); err != nil {
			log.WithError(err).WithField("local file", localFile).Errorln("failed to write local file")
			return err
		}
	} else {
		readLen, err := io.ReadFull(result.Body, block.File.DataBuffer[block.OffsetInBuffer:])
		if err != nil && err != io.ErrUnexpectedEOF {
			log.WithError(err).WithField("Bucket", block.File.Sts.BucketName).
				WithField("key", block.File.ObjectKey).
				Errorln("failed to read full body from S3 response to buffer")
			return err
		}
		block.File.DataSize += int64(readLen)
	}
	return nil
}

func (w *BlockWorker) GetBlock(block *Block) error {
	options := GetOptions(*block.File.Ctx)
	var err error
	if block.PcpHost != "" {
		for i := 0; i < w.retryTimes; i++ {
			err = w.getFromPcp(block, block.File.Sts)
			if err == nil {
				break
			} else {
				log.WithError(err).WithField("PcpHost", block.PcpHost).
					WithField("block", block.GetPcPath()).WithField("retryTimes", w.retryTimes).
					Errorln("failed to get block from PCP, will retry..")
			}
		}
		if err != nil {
			block.State = BSTATE_OK_LOCAL_PCP_FAIL
			log.WithError(err).WithField("PcpHost", block.PcpHost).WithField("block", block.GetPcPath()).
				Errorln("failed to get block from PCP, will try from local.")
		} else {
			if options.DebugMode {
				log.WithField("PcpHost", block.PcpHost).
					WithField("block", block.GetPcPath()).
					WithField("hit-cache", block.State).
					Infoln("successfully get block from PCP")
			}
		}
	}
	if block.PcpHost == "" || err != nil {
		err = w.getFromLocal(block)
		if err == nil {
			if block.State != BSTATE_OK_LOCAL_PCP_FAIL {
				block.State = BSTATE_OK_LOCAL
				if options.DebugMode {
					log.WithError(err).WithField("block", block.GetPcPath()).
						Errorln("successfully get block from local")
				}
			} else {
				if options.DebugMode {
					log.WithError(err).WithField("block", block.GetPcPath()).
						Errorln("successfully get block from local affter PCP failed")
				}
			}
		} else {
			block.State = BSTATE_FAIL
			log.WithError(err).WithField("block", block.GetPcPath()).
				Errorln("failed to get block.")
		}
	}
	return err
}

func (w *BlockWorker) processBlock(block *Block) {
	defer block.File.Wg.Done()
	startTime := time.Now().UnixMilli()
	if block.File.Type == FILE_TYPE_PUT {
		w.PutBlock(block)
	} else if block.File.Type == FILE_TYPE_GET {
		w.GetBlock(block)
	}
	block.TimeDuration = time.Now().UnixMilli() - startTime
}

func (w *BlockWorker) worker() {
	for {
		select {
		case <-w.ctx.Done():
			log.Infoln("BlockWorker received stop signal, exiting...")
			return
		case c, ok := <-w.in:
			if !ok {
				log.Infoln("Input channel closed, blockWorker exiting...")
				return
			}
			w.processBlock(c)
		}
	}
}

func (w *BlockWorker) Stop() {
	w.cancel()
}
