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
	"github.com/yangagile/pcache/sdk/pc-sdk-go/model"
	"io"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"
)

type Worker struct {
	pb     *PBucket
	in     chan *model.Block
	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
}

// put
func (w *Worker) putToPcp(blockInfo *model.Block, stsInfo *model.StsInfo, buffer []byte) (string, error) {
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
	if blockInfo.UploadId != nil {
		req.Header.Set("X-UPLOAD-ID", *blockInfo.UploadId)
	}

	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close() // 确保关闭响应体

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("failed to put with respose code: %d", resp.StatusCode)
	}

	etagBytes, err := io.ReadAll(io.LimitReader(resp.Body, 128))
	if err != nil {
		return "", err
	}

	return string(bytes.TrimSpace(etagBytes)), nil
}

func (w *Worker) PutBlock(block *model.Block) {
	startTime := time.Now().UnixMilli()

	// read file
	buf := make([]byte, block.Size)
	file, err := os.Open(block.LocalFile)
	defer file.Close()
	if err != nil {
		block.Error(err)
		return
	}
	_, err = file.ReadAt(buf, block.BlockNumber*block.BlockSize)
	if err != nil {
		block.Error(err)
		return
	}

	// try put to PCP
	eTag := ""
	if block.PcpHost != "" {
		eTag, err = w.putToPcp(block, w.pb.router.GetStsInfo(), buf)
		if err != nil {
			log.WithError(err).WithField("PcpHost", block.PcpHost).Errorln("failed to put with PCP")
			block.State = model.STATE_OK_LOCAL_PCP_FAIL
		} else {
			block.State = model.STATE_OK_PCP_LOCAL
		}
	}

	// put from local
	if eTag == "" {
		if block.TotalNumber > 1 {
			// big file
			PartNumber := int32(block.BlockNumber + 1)
			uploadResp, err := w.pb.s3ClientCache.Get().UploadPart(context.TODO(), &s3.UploadPartInput{
				Bucket:     aws.String(w.pb.router.GetStsInfo().BucketName),
				Key:        aws.String(block.Key),
				UploadId:   block.UploadId,
				PartNumber: &PartNumber,
				Body:       bytes.NewReader(buf),
			})
			if err != nil {
				block.Error(err)
				return
			}
			eTag = *uploadResp.ETag
		} else {
			// small file
			input := &s3.PutObjectInput{
				Bucket: aws.String(w.pb.router.GetStsInfo().BucketName),
				Key:    aws.String(block.Key),
				Body:   bytes.NewReader(buf),
			}

			uploadResp, err := w.pb.s3ClientCache.Get().PutObject(context.TODO(), input)
			if err != nil {
				block.Error(err)
				return
			}
			block.State = model.STATE_OK_LOCAL
			eTag = *uploadResp.ETag
		}
	}
	block.Etag = &eTag
	if block.State == model.STATE_OK_LOCAL_PCP_FAIL {
		block.State = model.STATE_OK_LOCAL
	}
	block.TimeDuration = time.Now().UnixMilli() - startTime
}

// get
func (w *Worker) getFromPcp(blockInfo *model.Block, stsInfo *model.StsInfo) error {
	req, err := http.NewRequestWithContext(context.Background(), "GET", blockInfo.GetPcPath(), nil)
	if err != nil {
		return err
	}
	stsJson, err := json.Marshal(stsInfo)
	if err != nil {
		return err
	}

	req.Header.Set("X-STS", string(stsJson))
	req.Header.Set("X-DATA-SIZE", strconv.FormatInt(blockInfo.Size, 10))
	req.Header.Set("X-BLOCK-SIZE", strconv.FormatInt(blockInfo.BlockSize, 10))

	client := &http.Client{
		Transport: &http.Transport{
			ResponseHeaderTimeout: 60 * time.Second, // 读取超时
		},
		Timeout: 65 * time.Second, // 总超时（连接+读取）
	}

	// 发送请求
	resp, err := client.Do(req)
	if err != nil {
		return err
	}

	defer resp.Body.Close() // 确保关闭响应体

	// 检查响应状态
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("server returned: %s", resp.Status)
	}

	// 创建本地文件
	outFile, err := os.Create(blockInfo.LocalFile)
	if err != nil {
		return err
	}
	defer outFile.Close()

	// 使用缓冲写入器（8KB 缓冲区）
	bufferedWriter := bufio.NewWriterSize(outFile, 8192)
	defer bufferedWriter.Flush()

	// 使用缓冲读取器（8KB 缓冲区）
	bufferedReader := bufio.NewReaderSize(resp.Body, 8192)

	// 复制数据（使用 8KB 缓冲区）
	if _, err := io.CopyBuffer(bufferedWriter, bufferedReader, make([]byte, 8192)); err != nil {
		return err
	}

	// 确保所有数据写入磁盘
	if err := bufferedWriter.Flush(); err != nil {
		return err
	}

	if cacheHit := resp.Header.Get("X-CACHE-HIT"); cacheHit != "" {
		hitCount, err := strconv.Atoi(cacheHit)
		if err == nil {
			if hitCount > 0 {
				blockInfo.State = model.STATE_OK_PCP_CACHE
			} else {
				blockInfo.State = model.STATE_OK_PCP_LOCAL
			}
		} else {
			log.WithError(err).WithField(" X-CACHE-HIT", blockInfo.LocalFile).
				Errorln("failed to get local X-CACHE-HIT")
			blockInfo.State = model.STATE_OK_PCP_LOCAL
		}
	}
	return nil
}

func (w *Worker) getFromLocal(blockInfo *model.Block, stsInfo *model.StsInfo) error {
	// 创建文件
	file, err := os.Create(blockInfo.LocalFile)
	if err != nil {
		log.WithError(err).WithField("local file", blockInfo.LocalFile).
			Errorln("failed to create local file")
		return err
	}
	defer file.Close()
	var result *s3.GetObjectOutput
	if blockInfo.TotalNumber > 1 {
		offset := blockInfo.BlockNumber * blockInfo.BlockSize
		rangeHeader := fmt.Sprintf("bytes=%d-%d", offset, offset+blockInfo.Size-1)

		// 执行范围下载
		result, err = w.pb.s3ClientCache.Get().GetObject(context.TODO(), &s3.GetObjectInput{
			Bucket: aws.String(stsInfo.BucketName),
			Key:    aws.String(blockInfo.Key),
			Range:  aws.String(rangeHeader),
		})
		if err != nil {
			log.WithError(err).WithField("bucket", stsInfo.BucketName).
				WithField("key", blockInfo.Key).
				WithField("rangeHeader", rangeHeader).
				Errorln("failed to get part of object from s3")
			return err
		}

	} else {
		result, err = w.pb.s3ClientCache.Get().GetObject(context.TODO(), &s3.GetObjectInput{
			Bucket: aws.String(stsInfo.BucketName),
			Key:    aws.String(blockInfo.Key),
		})
		if err != nil {
			log.WithError(err).WithField("bucket", stsInfo.BucketName).
				WithField("key", blockInfo.Key).
				Errorln("failed to get file from s3")
			return err
		}
	}
	defer result.Body.Close()
	if _, err := io.Copy(file, result.Body); err != nil {
		log.WithError(err).WithField("local file", blockInfo.LocalFile).
			Errorln("failed to write local file")
		return err
	}
	log.WithField("key", blockInfo.Key).WithField("localFile", blockInfo.LocalFile).
		WithField("size", blockInfo.Size).Infoln("successfully get file from s3")
	return nil
}

func (w *Worker) GetBlock(block *model.Block) error {
	startTime := time.Now().UnixMilli()
	var err error
	if block.PcpHost != "" {
		err = w.getFromPcp(block, w.pb.router.GetStsInfo())
		if err != nil {
			block.State = model.STATE_OK_LOCAL_PCP_FAIL
		}
	}
	if block.PcpHost == "" || err != nil {
		err = w.getFromLocal(block, w.pb.router.GetStsInfo())
		if err == nil {
			if block.State != model.STATE_OK_LOCAL_PCP_FAIL {
				block.State = model.STATE_OK_LOCAL
			}
		} else {
			block.State = model.STATE_FAIL
		}
	}
	block.TimeDuration = time.Now().UnixMilli() - startTime
	return err
}

func (w *Worker) processBlock(block *model.Block) {
	defer block.Wg.Done()
	if block.Type == model.BLOCK_TYPE_PUT {
		w.PutBlock(block)
	} else if block.Type == model.BLOCK_TYPE_GET {
		w.GetBlock(block)
	}
}

func (w *Worker) worker() {
	for {
		select {
		case <-w.ctx.Done():
			log.Infoln("Worker received stop signal, exiting...")
			return
		case c, ok := <-w.in:
			if !ok {
				log.Infoln("Input channel closed, worker exiting...")
				return
			}
			w.processBlock(c)
		}
	}
}

func (w *Worker) Stop() {
	w.cancel()
}
