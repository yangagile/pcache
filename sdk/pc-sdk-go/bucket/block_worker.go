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
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	log "github.com/sirupsen/logrus"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	STATE_FAIL         = iota // 0
	STATE_OK_PCP_LOCAL        // 1
	STATE_OK_PCP_CACHE
	STATE_OK_LOCAL          // 2
	STATE_OK_LOCAL_PCP_FAIL // 2
)

type Block struct {
	File         *FileTask
	BlockNumber  int64
	Size         int64
	PcpHost      string
	Etag         *string
	TimeDuration int64
	State        int
}

func (c *Block) GetPcPath() string {
	if c.PcpHost != "" && !strings.HasSuffix(c.PcpHost, "/") {
		c.PcpHost += "/"
	}
	return fmt.Sprintf("%s%s/%s.%d_%d", c.PcpHost, c.File.Bucket, c.File.RemoteKey,
		c.BlockNumber, c.File.BlockCount)
}

func (c *Block) GetLocalPartPath() string {
	return fmt.Sprintf("%s.%d_%d", c.File.LocalPath, c.BlockNumber, c.File.BlockCount)
}

type BlockWorker struct {
	in         chan *Block
	ctx        context.Context
	concurrent int
	cancel     context.CancelFunc
	wg         sync.WaitGroup
}

func NewBlockWorker(threadNumber, chanSize int) *BlockWorker {
	// start work
	ctx, cancel := context.WithCancel(context.Background())
	blockWorker := &BlockWorker{
		in:         make(chan *Block, chanSize),
		ctx:        ctx,
		concurrent: threadNumber,
		cancel:     cancel,
	}
	return blockWorker

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
func (w *BlockWorker) putToPcp(blockInfo *Block, stsInfo *StsInfo, buffer []byte) (string, error) {
	client := &http.Client{
		Timeout: 90 * time.Second,
	}

	req, err := http.NewRequestWithContext(
		context.Background(),
		"POST",
		blockInfo.GetPcPath(),
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
	if blockInfo.File.UploadId != nil {
		req.Header.Set("X-UPLOAD-ID", *blockInfo.File.UploadId)
	}

	resp, err := client.Do(req)
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
			Bucket: aws.String(block.File.stsInfo.BucketName),
			Key:    aws.String(block.File.RemoteKey),
			Body:   bytes.NewReader(buffer),
		}
		uploadResp, err := block.File.s3Client.PutObject(context.TODO(), input)
		if err != nil {
			return "", err
		}
		return *uploadResp.ETag, nil
	} else {
		// big file
		PartNumber := int32(block.BlockNumber + 1)
		uploadResp, err := block.File.s3Client.UploadPart(context.TODO(), &s3.UploadPartInput{
			Bucket:     aws.String(block.File.stsInfo.BucketName),
			Key:        aws.String(block.File.RemoteKey),
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
	// read file
	buf := make([]byte, block.Size)
	file, err := os.Open(block.File.LocalPath)
	defer file.Close()
	if err != nil {
		return err
	}
	_, err = file.ReadAt(buf, block.BlockNumber*block.File.BlockSize)
	if err != nil {
		return err
	}

	// try put to PCP
	eTag := ""
	if block.PcpHost != "" {
		eTag, err = w.putToPcp(block, block.File.stsInfo, buf)
		if err != nil {
			log.WithError(err).WithField("PcpHost", block.PcpHost).Errorln("failed to put with PCP")
			// will try to put from local
			block.State = STATE_OK_LOCAL_PCP_FAIL
		} else {
			block.State = STATE_OK_PCP_LOCAL
		}
	}

	// put from local
	if eTag == "" {
		eTag, err = w.putFromLocal(block, buf)
		if err != nil {
			log.WithError(err).Errorln("failed to put with from local")
			block.State = STATE_FAIL
			return err
		} else {
			if block.State != STATE_OK_LOCAL_PCP_FAIL {
				block.State = STATE_OK_LOCAL
			}
		}
	}
	block.Etag = &eTag
	return nil
}

// get
func (w *BlockWorker) getFromPcp(blockInfo *Block, stsInfo *StsInfo) error {
	// prepare request context
	req, err := http.NewRequestWithContext(context.Background(), "GET",
		blockInfo.GetPcPath(), nil)
	if err != nil {
		log.WithError(err).WithField("url", blockInfo.GetPcPath()).Errorln("failed to new request")
		return err
	}
	stsJson, err := json.Marshal(stsInfo)
	if err != nil {
		log.WithError(err).WithField("stsInfo", stsInfo).Errorln("failed to Marshal stsInfo")
		return err
	}
	req.Header.Set("X-STS", string(stsJson))
	req.Header.Set("X-DATA-SIZE", strconv.FormatInt(blockInfo.File.Size, 10))
	req.Header.Set("X-BLOCK-SIZE", strconv.FormatInt(blockInfo.File.BlockSize, 10))

	client := &http.Client{
		Transport: &http.Transport{
			ResponseHeaderTimeout: 60 * time.Second, // 读取超时
		},
		Timeout: 65 * time.Second, // 总超时（连接+读取）
	}

	// request
	resp, err := client.Do(req)
	if err != nil {
		log.WithError(err).WithField("request", req).Errorln("failed to request http")
		return err
	}
	defer resp.Body.Close()

	// check response
	if resp.StatusCode != http.StatusOK {
		err = fmt.Errorf("server returned: %s", resp.Status)
		log.WithError(err).Errorln("invalid response code")
		return err
	}

	// save to file
	var localFile string
	if blockInfo.File.IsSingleFile() {
		localFile = blockInfo.File.LocalPath
	} else {
		localFile = blockInfo.GetLocalPartPath()
	}
	outFile, err := os.Create(localFile)
	if err != nil {
		log.WithError(err).WithField("localFile", localFile).Errorln("failed to create local file")
		return err
	}
	defer outFile.Close()

	bufferedWriter := bufio.NewWriterSize(outFile, 8192)
	defer bufferedWriter.Flush()
	bufferedReader := bufio.NewReaderSize(resp.Body, 8192)
	if _, err := io.CopyBuffer(bufferedWriter, bufferedReader, make([]byte, 8192)); err != nil {
		log.WithError(err).Errorln("failed to copy buffer")
		return err
	}
	if err := bufferedWriter.Flush(); err != nil {
		log.WithError(err).Errorln("failed to flush buffer")
		return err
	}

	// get response stats
	if cacheHit := resp.Header.Get("X-CACHE-HIT"); cacheHit != "" {
		hitCount, err := strconv.Atoi(cacheHit)
		if err == nil {
			if hitCount > 0 {
				blockInfo.State = STATE_OK_PCP_CACHE
			} else {
				blockInfo.State = STATE_OK_PCP_LOCAL
			}
		} else {
			log.WithError(err).WithField(" X-CACHE-HIT", localFile).
				Errorln("failed to get local X-CACHE-HIT")
			blockInfo.State = STATE_OK_PCP_CACHE
		}
	}
	return nil
}

func (w *BlockWorker) getFromLocal(block *Block) error {
	// create local file
	var localFile string
	if block.File.IsSingleFile() {
		localFile = block.File.LocalPath
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

	// get from local
	var result *s3.GetObjectOutput
	if block.File.IsSingleFile() {
		result, err = block.File.s3Client.GetObject(context.TODO(), &s3.GetObjectInput{
			Bucket: aws.String(block.File.stsInfo.BucketName),
			Key:    aws.String(block.File.RemoteKey),
		})
		if err != nil {
			log.WithError(err).WithField("bucket", block.File.stsInfo.BucketName).
				WithField("key", block.File.RemoteKey).
				Errorln("failed to get file from s3")
			return err
		}

	} else {
		offset := block.BlockNumber * block.File.BlockSize
		rangeHeader := fmt.Sprintf("bytes=%d-%d", offset, offset+block.File.Size-1)

		result, err = block.File.s3Client.GetObject(context.TODO(), &s3.GetObjectInput{
			Bucket: aws.String(block.File.stsInfo.BucketName),
			Key:    aws.String(block.File.RemoteKey),
			Range:  aws.String(rangeHeader),
		})
		if err != nil {
			log.WithError(err).WithField("bucket", block.File.stsInfo.BucketName).
				WithField("key", block.File.RemoteKey).
				WithField("rangeHeader", rangeHeader).
				Errorln("failed to get part of object from s3")
			return err
		}
	}
	defer result.Body.Close()
	if _, err := io.Copy(file, result.Body); err != nil {
		log.WithError(err).WithField("local file", localFile).Errorln("failed to write local file")
		return err
	}
	log.WithField("key", block.File.RemoteKey).WithField("localFile", localFile).
		WithField("size", block.File.Size).Infoln("successfully get file from s3")
	return nil
}

func (w *BlockWorker) GetBlock(block *Block) error {
	var err error
	if block.PcpHost != "" {
		err = w.getFromPcp(block, block.File.stsInfo)
		if err != nil {
			block.State = STATE_OK_LOCAL_PCP_FAIL
		}
	}
	if block.PcpHost == "" || err != nil {
		err = w.getFromLocal(block)
		if err == nil {
			if block.State != STATE_OK_LOCAL_PCP_FAIL {
				block.State = STATE_OK_LOCAL
			}
		} else {
			block.State = STATE_FAIL
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
