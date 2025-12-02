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
	"testing"
)

func Test_FileStats_String(t *testing.T) {
	fileStats := NewFileStats()
	fileStats.CountTotal = 10
	fileStats.CountSuccess = 8
	fileStats.CountFail = 2
	fileStats.SizeTotal = 1024 * 1024 * 1024
	fileStats.SizeMax = 10 * 1024 * 1024 * 1024
	fileStats.SizeMin = 1024 * 1024
	fileStats.TimeTotal = 10000
	fileStats.TimeMax = 20000
	fileStats.TimeMin = 100
	fmt.Printf("file stats: %s \n", fileStats)
}

func Test_BlockStats_String(t *testing.T) {
	blockStats := NewBlockStats()
	blockStats.CountTotal = 10
	blockStats.CountPcpRemote = 8
	blockStats.CountPcpDisk = 2
	blockStats.CountPcpMemory = 2
	blockStats.CountLocal = 2
	blockStats.TimeTotal = 10000
	blockStats.TimeMax = 20000
	blockStats.TimeMin = 100
	fmt.Printf("file stats: %s \n", blockStats)
}
