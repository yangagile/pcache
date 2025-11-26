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
	"github.com/yangagile/pcache/sdk/pc-sdk-go/model"
	"io"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"
)

type PutWorker struct {
	wg     *sync.WaitGroup
	client *PBucket
	ch     chan *model.PutBlock
	//chResult chan types.CompletedPart
	chErr chan error
}

func (w *PutWorker) putToPcp(blockInfo *model.PutBlock, stsInfo *model.StsInfo, buffer []byte) (string, error) {
	client := &http.Client{
		Timeout: 90 * time.Second,
	}

	req, err := http.NewRequestWithContext(
		context.Background(),
		"POST",
		blockInfo.Block.GetPcPath(),
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

func (w *PutWorker) PutBlock(c *model.PutBlock) error {
	startTime := time.Now().UnixMilli()
	buf := make([]byte, c.Size)
	file, err := os.Open(c.PartFile)
	if err != nil {
		c.Block.State = model.STATE_FAIL
		return fmt.Errorf("failed to open file %v with error: %w", c.PartFile, err)
	}
	_, err = file.ReadAt(buf, c.BlockNumber*c.BlockSize)
	file.Close()
	if err != nil {
		c.Block.State = model.STATE_FAIL
		return fmt.Errorf("failed to read file %v block with error: %w", c.PartFile, err)
	}

	eTag := ""
	if c.PcpHost != "" {
		eTag, err = w.putToPcp(c, w.client.router.GetStsInfo(), buf)
		if err != nil {
			log.WithError(err).WithField("PcpHost", c.PcpHost).Errorln("upload by PCP error")
			c.Block.State = model.STATE_OK_LOCAL_PCP_FAIL
		} else {
			c.Block.State = model.STATE_OK_PCP_LOCAL
		}
	}

	if eTag == "" {
		if c.TotalNumber > 1 {
			PartNumber := int32(c.BlockNumber + 1)
			uploadResp, err := w.client.s3ClientCache.Get().UploadPart(context.TODO(), &s3.UploadPartInput{
				Bucket:     aws.String(w.client.router.GetStsInfo().BucketName),
				Key:        aws.String(c.Key),
				UploadId:   c.UploadId,
				PartNumber: &PartNumber,
				Body:       bytes.NewReader(buf),
			})
			if err != nil {
				c.Block.State = model.STATE_FAIL
				return fmt.Errorf("upload part %d failed: %w", c.BlockNumber, err)
			}

			eTag = *uploadResp.ETag
		} else {
			input := &s3.PutObjectInput{
				Bucket: aws.String(w.client.router.GetStsInfo().BucketName),
				Key:    aws.String(c.Key),
				Body:   bytes.NewReader(buf),
			}

			uploadResp, err := w.client.s3ClientCache.Get().PutObject(context.TODO(), input)
			if err != nil {
				c.Block.State = model.STATE_FAIL
				return fmt.Errorf("failed to put:%v with err:%v", input, err)
			}
			c.Block.State = model.STATE_OK_LOCAL
			eTag = *uploadResp.ETag
		}
	}
	c.Etag = &eTag
	if c.Block.State == model.STATE_OK_LOCAL_PCP_FAIL {
		c.Block.State = model.STATE_OK_LOCAL
	}
	c.Block.TimeDuration = time.Now().UnixMilli() - startTime
	return nil
}

func (w *PutWorker) putWorker() {
	defer w.wg.Done()
	for c := range w.ch {
		err := w.PutBlock(c)
		if err != nil {
			w.chErr <- err
			return
		}
	}
}
