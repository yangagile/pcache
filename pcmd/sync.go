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

package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"pcmd/utils"
	"strings"

	"github.com/yangagile/pcache/sdk/pc-sdk-go/bucket"
)

// RegisterCalcCommand registers the calculation command
func CreateSyncCommand(config *Config) *Command {
	syncCmd := &Command{
		Name:        "sync",
		Description: "sync between local folder and bucket prefix",
		Usage:       "pcmd sync [FLAGS] folder s3://bucket/prefix | s3://bucket/prefix folder",
		Handler:     handleSync,
	}
	syncCmd.Flags = flag.NewFlagSet("sync", flag.ExitOnError)
	syncCmd.Flags.BoolVar(&config.DryRun, "dry",
		config.DryRun, "dry run mode")
	syncCmd.Flags.BoolVar(&config.Debug, "debug",
		config.Debug, "debug mode")
	syncCmd.Flags.BoolVar(&config.IsSmallFile, "small-file",
		config.IsSmallFile, "size is less than block size, will take special method for performance.")
	syncCmd.Flags.BoolVar(&config.SkipExisting, "skip-existing",
		config.IsSmallFile, "skip existing file or object")
	syncCmd.Flags.BoolVar(&config.SkipUnchanged, "skip-unchanged",
		config.SkipUnchanged, "skip unchanged file or object with size for checksum")
	syncCmd.Flags.StringVar(&config.Checksum, "checksum",
		config.Checksum, "checksum file for verify or compare, crc32 or md5")
	syncCmd.Flags.IntVar(&config.BlockTheadNumber, "block-thead-number",
		config.BlockTheadNumber, "thread number of block worker")
	syncCmd.Flags.IntVar(&config.FileThreadNumber, "file-thread-number",
		config.FileThreadNumber, "thread number of file worker")
	syncCmd.Flags.Float64Var(&config.HttpTimeoutFactor, "http-timeout-factor",
		config.HttpTimeoutFactor, "block http timeout factor")
	return syncCmd
}

func handleSync(config *Config, args []string) error {
	if len(args) < 2 {
		return fmt.Errorf("source local file and target s3 path are required")
	}

	upload := true
	var localFolder, s3key string
	var objectInfo utils.ObjectInfo
	var err error
	if strings.HasPrefix(args[0], "s3://") {
		upload = false
		s3key = strings.TrimSpace(args[0])
		objectInfo, err = utils.ParseObjectInfo(s3key)
		if err != nil {
			fmt.Printf("s3key %s is not valid!\n", args[0])
			return err
		}
		localFolder = strings.TrimSpace(args[1])
	} else {
		s3key = strings.TrimSpace(args[1])
		objectInfo, err = utils.ParseObjectInfo(s3key)
		if err != nil {
			fmt.Printf("s3key %s is not valid!\n", args[0])
			return err
		}
		localFolder = strings.TrimSpace(args[0])
	}
	_, err = os.Stat(localFolder)
	if err != nil {
		fmt.Printf("folder %s is not existing!\n", localFolder)
		return err
	}
	ctx := bucket.WithOptions(context.Background())
	options := bucket.GetOptions(ctx)
	options.DryRun = config.DryRun
	options.DebugMode = config.Debug
	options.IsSmallFile = config.IsSmallFile
	options.SkipExisting = config.SkipUnchanged
	options.Checksum = config.Checksum
	options.SkipUnchanged = config.SkipUnchanged
	if upload {
		pb, err := bucket.NewPBucketWithOptions(ctx, config.Endpoint, objectInfo.Bucket,
			config.AK, config.SK, []string{"PutObject"},
			//bucket.WithBlockSize(config.BlockSize),
			bucket.WithBlockWorkerThreadNumber(config.BlockTheadNumber),
			bucket.WithFileTaskThreadNumber(config.FileThreadNumber))
		if err != nil {
			fmt.Printf("failed to new PBucket with err:%v\n", err)
			return err
		}
		err = pb.SyncFolderToPrefix(ctx, localFolder, objectInfo.Key)
		if err != nil {
			fmt.Printf("failed to put local file %s to %s with err:%v\n", localFolder, s3key, err)
			return err
		}

		fmt.Printf("successfully sync local flolder %s to %s\n", localFolder, s3key)
	} else {
		pb, err := bucket.NewPBucketWithOptions(ctx, config.Endpoint, objectInfo.Bucket,
			config.AK, config.SK, []string{"GetObject,ListObject"},
			//bucket.WithBlockSize(config.BlockSize),
			bucket.WithBlockWorkerThreadNumber(config.BlockTheadNumber),
			bucket.WithFileTaskThreadNumber(config.FileThreadNumber))
		if err != nil {
			fmt.Printf("failed to new PBucket with err:%v\n", err)
			return err
		}
		err = pb.SyncPrefixToFolder(ctx, objectInfo.Key, localFolder)
		if err != nil {
			fmt.Printf("failed to get local file %s from %s with err:%v\n", localFolder, s3key, err)
			return err
		}
		fmt.Printf("successfully sync %s to local flolder %s\n", s3key, localFolder)
	}
	return nil
}
