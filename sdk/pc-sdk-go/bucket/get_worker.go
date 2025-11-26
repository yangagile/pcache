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

type GetWorker struct {
	wg     *sync.WaitGroup
	client *PBucket
	ch     chan *model.GetBlock
	chErr  chan error
}

func (w *GetWorker) getFromPcp(blockInfo *model.GetBlock, stsInfo *model.StsInfo) error {
	req, err := http.NewRequestWithContext(context.Background(), "GET", blockInfo.Block.GetPcPath(), nil)
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
	outFile, err := os.Create(blockInfo.PartFile)
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
			log.WithError(err).WithField(" X-CACHE-HIT", blockInfo.PartFile).
				Errorln("failed to get local X-CACHE-HIT")
			blockInfo.State = model.STATE_OK_PCP_LOCAL
		}
	}
	return nil
}

func (w *GetWorker) getFromLocal(blockInfo *model.GetBlock, stsInfo *model.StsInfo) error {
	// 创建文件
	file, err := os.Create(blockInfo.PartFile)
	if err != nil {
		log.WithError(err).WithField("local file", blockInfo.PartFile).
			Errorln("failed to create local file")
		return err
	}
	defer file.Close()
	var result *s3.GetObjectOutput
	if blockInfo.TotalNumber > 1 {
		offset := blockInfo.BlockNumber * blockInfo.BlockSize
		rangeHeader := fmt.Sprintf("bytes=%d-%d", offset, offset+blockInfo.Size-1)

		// 执行范围下载
		result, err = w.client.s3ClientCache.Get().GetObject(context.TODO(), &s3.GetObjectInput{
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
		result, err = w.client.s3ClientCache.Get().GetObject(context.TODO(), &s3.GetObjectInput{
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
		log.WithError(err).WithField("local file", blockInfo.PartFile).
			Errorln("failed to write local file")
		return err
	}
	log.WithField("key", blockInfo.Key).WithField("localFile", blockInfo.PartFile).
		WithField("size", blockInfo.Size).Infoln("successfully get file from s3")
	return nil
}

func (w *GetWorker) GetBlock(c *model.GetBlock) error {
	startTime := time.Now().UnixMilli()
	var err error
	if c.PcpHost != "" {
		err = w.getFromPcp(c, w.client.router.GetStsInfo())
		if err != nil {
			c.Block.State = model.STATE_OK_LOCAL_PCP_FAIL
		}
	}
	if c.PcpHost == "" || err != nil {
		err = w.getFromLocal(c, w.client.router.GetStsInfo())
		if err == nil {
			if c.Block.State != model.STATE_OK_LOCAL_PCP_FAIL {
				c.Block.State = model.STATE_OK_LOCAL
			}
		} else {
			c.Block.State = model.STATE_FAIL
		}
	}
	c.Block.TimeDuration = time.Now().UnixMilli() - startTime
	return err
}

func (w *GetWorker) getWorker() {
	defer w.wg.Done()
	for c := range w.ch {
		err := w.GetBlock(c)
		if err != nil {
			w.chErr <- err
			return
		}
	}
}
