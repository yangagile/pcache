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
	"math"
)

type BlockStats struct {
	TimeTotal         int64
	TimeMax           int64
	TimeMin           int64
	CountTotal        int64
	CountFail         int64
	CountPcpLocal     int64
	CountPcpCache     int64
	CountLocal        int64
	CountLocalPcpFail int64
}

func NewBlockStats() *BlockStats {
	return &BlockStats{0, 0, math.MaxInt, 0, 0, 0,
		0, 0, 0}
}

func (s *BlockStats) Update(b *Block) {
	s.CountTotal++
	switch b.State {
	case STATE_FAIL:
		s.CountFail++
	case STATE_OK_PCP_LOCAL:
		s.CountPcpLocal++
	case STATE_OK_PCP_CACHE:
		s.CountPcpCache++
	case STATE_OK_LOCAL:
		s.CountLocal++
	case STATE_OK_LOCAL_PCP_FAIL:
		s.CountLocalPcpFail++
	}
	if b.TimeDuration > s.TimeMax {
		s.TimeMax = b.TimeDuration
	}
	if b.TimeDuration < s.TimeMin {
		s.TimeMin = b.TimeDuration
	}
	s.TimeTotal += b.TimeDuration
}

func (s *BlockStats) GetAverageTime() int64 {
	if s.CountTotal > 0 {
		return s.TimeTotal / s.CountTotal
	}
	return 0
}

type FileStats struct {
	CountTotal   int64
	CountFail    int64
	CountSuccess int64

	SizeTotal int64
	SizeMax   int64
	SizeMin   int64

	TimeTotal int64
	TimeMax   int64
	TimeMin   int64
}

func NewFileStats() *FileStats {
	return &FileStats{
		0, 0, 0,
		0, math.MaxInt64, 0,
		0, math.MaxInt64, 0}
}

func (s *FileStats) Update(f *FileTask) {
	// update count
	s.CountTotal++
	if f.Err != nil {
		s.CountFail++
	} else {
		s.CountSuccess++
	}

	// update time
	s.TimeTotal += f.TimeDuration
	if f.TimeDuration > s.TimeMax {
		s.TimeMax = f.TimeDuration
	}
	if f.TimeDuration < s.TimeMin {
		s.TimeMin = f.TimeDuration
	}

	// update size
	s.SizeTotal += f.Size
	if f.Size > s.SizeMax {
		s.SizeMax = f.Size
	}
	if f.Size < s.SizeMin {
		s.SizeMin = f.Size
	}
}

func (s *FileStats) GetAverageTime() int64 {
	if s.CountTotal > 0 {
		return s.TimeTotal / s.CountTotal
	}
	return 0
}

func (s *FileStats) GetAverageSize() int64 {
	if s.CountTotal > 0 {
		return s.SizeTotal / s.CountTotal
	}
	return 0
}
