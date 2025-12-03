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
	"context"
)

type Options struct {
	DryRun        bool
	DebugMode     bool
	EnableStats   bool
	IsSmallFile   bool // LocalSize is less than block LocalSize, will take special method for performance
	skipExisting  bool // if the local file or remote object is existing, not replace.
	skipUnchanged bool // if the local file  remote object is same, not replace.
	Checksum      string
	BlockStats    *BlockStats
	FileStats     *FileStats
}

func NewOptions() *Options {
	return &Options{
		DryRun:        false,
		DebugMode:     false,
		EnableStats:   true,
		IsSmallFile:   false,
		skipExisting:  false,
		skipUnchanged: false,
		Checksum:      "",
		BlockStats:    NewBlockStats(),
		FileStats:     NewFileStats(),
	}
}

func WithOptions(ctx context.Context) context.Context {
	o := NewOptions()
	return context.WithValue(ctx, "options", o)
}

func GetOptions(ctx context.Context) *Options {
	if o, ok := ctx.Value("options").(*Options); ok {
		return o
	}
	return NewOptions()
}
