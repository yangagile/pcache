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

package model

import (
	"fmt"
	"strings"
	"sync"
)

const (
	STATE_FAIL         = iota // 0
	STATE_OK_PCP_LOCAL        // 1
	STATE_OK_PCP_CACHE
	STATE_OK_LOCAL          // 2
	STATE_OK_LOCAL_PCP_FAIL // 2
)

const (
	BLOCK_TYPE_PUT = iota // 0
	BLOCK_TYPE_GET
)

type Block struct {
	Wg           *sync.WaitGroup
	Type         int
	PcpHost      string
	Bucket       string
	Key          string
	LocalFile    string
	BlockNumber  int64
	TotalNumber  int64
	Size         int64
	BlockSize    int64
	UploadId     *string
	Etag         *string
	TimeDuration int64
	State        int
	Err          error
}

func (c *Block) GetPcPath() string {
	if c.PcpHost != "" && !strings.HasSuffix(c.PcpHost, "/") {
		c.PcpHost += "/"
	}
	return fmt.Sprintf("%s%s/%s.%d_%d", c.PcpHost, c.Bucket, c.Key, c.BlockNumber, c.TotalNumber)
}

func (c *Block) Error(err error) {
	c.State = STATE_FAIL
	c.Err = err
}
