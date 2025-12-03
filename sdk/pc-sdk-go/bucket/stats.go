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
	"fmt"
	"math"
)

type BlockStats struct {
	TimeTotal         int64
	TimeMax           int64
	TimeMin           int64
	CountTotal        int64
	CountFail         int64
	CountPcpRemote    int64
	CountPcpDisk      int64
	CountPcpMemory    int64
	CountLocal        int64
	CountLocalPcpFail int64
}

func NewBlockStats() *BlockStats {
	return &BlockStats{0, 0, math.MaxInt,
		0, 0, 0, 0, 0, 0, 0}
}

func (s *BlockStats) Update(b *Block) {
	s.CountTotal++
	switch b.State {
	case BSTATE_FAIL:
		s.CountFail++
	case BSTATE_OK_PCP_REMOTE:
		s.CountPcpRemote++
	case BSTATE_OK_PCP_DISK:
		s.CountPcpDisk++
	case BSTATE_OK_PCP_MEMORY:
		s.CountPcpMemory++
	case BSTATE_OK_LOCAL:
		s.CountLocal++
	case BSTATE_OK_LOCAL_PCP_FAIL:
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
func (s BlockStats) String() string {
	return fmt.Sprintf("Count(total:%d ok_pcp_remote:%d ok_pcp_disk:%d ok_pcp_momery:%d "+
		"ok_local:%d ok_local_pcp_fail:%d fail:%d) Time(avg:%d max:%d min:%d)ms",
		s.CountTotal, s.CountPcpRemote, s.CountPcpDisk, s.CountPcpMemory, s.CountLocal,
		s.CountLocalPcpFail, s.CountFail, s.GetAverageTime(), s.TimeMax, s.TimeMin)
}

func (s *BlockStats) GetAverageTime() int64 {
	if s.CountTotal > 0 {
		return s.TimeTotal / s.CountTotal
	}
	return 0
}

type FileStats struct {
	CountTotal         int64
	CountFail          int64
	CountOk            int64
	CountSkipExisting  int64
	CountSkipUnchanged int64

	SizeTotal int64
	SizeMax   int64
	SizeMin   int64

	TimeTotal int64
	TimeMax   int64
	TimeMin   int64
}

func NewFileStats() *FileStats {
	return &FileStats{
		0, 0, 0, 0, 0,
		0, 0, math.MaxInt64,
		0, 0, math.MaxInt64}
}

func (s *FileStats) Update(f *FileTask) {
	// update count
	s.CountTotal++
	switch f.Stats {
	case FSTATE_FAIL:
		s.CountFail++
	case FSTATE_OK:
		s.CountOk++
	case FSTATE_OK_SKIP_EXIST:
		s.CountSkipExisting++
	case FSTATE_OK_SKIP_UNCHANG:
		s.CountSkipUnchanged++
	}

	// update time
	s.TimeTotal += f.DurationTime
	if f.DurationTime > s.TimeMax {
		s.TimeMax = f.DurationTime
	}
	if f.DurationTime < s.TimeMin {
		s.TimeMin = f.DurationTime
	}

	// update LocalSize
	s.SizeTotal += f.LocalSize
	if f.LocalSize > s.SizeMax {
		s.SizeMax = f.LocalSize
	}
	if f.LocalSize < s.SizeMin {
		s.SizeMin = f.LocalSize
	}
}

func (s FileStats) String() string {
	return fmt.Sprintf("Count(total:%d ok:%d fail:%d) LocalSize(total:%d avg:%d max:%d min:%d)bytes "+
		"Time(avg:%d max:%d min:%d)ms", s.CountTotal, s.CountOk, s.CountFail,
		s.SizeTotal, s.GetAverageSize(), s.SizeMax, s.SizeMin, s.GetAverageTime(), s.TimeMax, s.TimeMin)
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

func (s *FileStats) Reset() {
	s.CountTotal = 0
	s.CountFail = 0
	s.CountOk = 0
	s.CountSkipExisting = 0
	s.CountSkipUnchanged = 0
	s.SizeTotal = 0
	s.SizeMax = 0
	s.SizeMin = math.MaxInt64
	s.TimeTotal = 0
	s.TimeMax = math.MaxInt64
	s.TimeMin = 0
}
