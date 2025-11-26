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
)

const (
	STATE_FAIL         = iota // 0
	STATE_OK_PCP_LOCAL        // 1
	STATE_OK_PCP_CACHE
	STATE_OK_LOCAL          // 2
	STATE_OK_LOCAL_PCP_FAIL // 2
)

type Block struct {
	PcpHost      string
	Bucket       string
	PartFile     string
	Key          string
	BlockNumber  int64
	TotalNumber  int64
	Size         int64
	BlockSize    int64
	TimeDuration int64
	State        int
}

func (c *Block) GetPcPath() string {
	if c.PcpHost != "" && !strings.HasSuffix(c.PcpHost, "/") {
		c.PcpHost += "/"
	}
	return fmt.Sprintf("%s%s/%s.%d_%d", c.PcpHost, c.Bucket, c.Key, c.BlockNumber, c.TotalNumber)
}

type GetBlock struct {
	Block
}

type PutBlock struct {
	Block
	UploadId *string
	Etag     *string
}
