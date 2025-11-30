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
		Description: "sync a local folder into bucket prefix or revert",
		Usage:       "pcmd sync [FLAGS] folder s3://BUCKET/prefix or s3://BUCKET/prefix folder ",
		Handler:     handleSync,
	}
	syncCmd.Flags = flag.NewFlagSet("sync", flag.ExitOnError)
	syncCmd.Flags.BoolVar(&config.ForceReplace, "force",
		config.ForceReplace, "force overwrite of existing file")
	syncCmd.Flags.BoolVar(&config.DryRun, "dry",
		config.DryRun, "dry run mode")
	syncCmd.Flags.BoolVar(&config.Debug, "debug",
		config.Debug, "debug mode")

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
	bucket.GetOptions(ctx).DryRun = config.DryRun
	bucket.GetOptions(ctx).DebugMode = config.Debug
	if upload {
		pb, err := bucket.NewPBucket(ctx, config.Endpoint, objectInfo.Bucket,
			config.AK, config.SK, []string{"PutObject"})
		if err != nil {
			fmt.Printf("failed to new PBucket with err:%v\n", err)
		}
		err = pb.SyncFolderToPrefix(ctx, localFolder, objectInfo.Key)
		if err != nil {
			fmt.Printf("failed to put local file %s to %s with err:%v\n", localFolder, s3key, err)
		}

		fmt.Printf("successfully sync local flolder %s to %s\n", localFolder, s3key)
	} else {
		pb, err := bucket.NewPBucket(ctx, config.Endpoint, objectInfo.Bucket,
			config.AK, config.SK, []string{"GetObject,ListObject"})
		if err != nil {
			fmt.Printf("failed to new PBucket with err:%v\n", err)
		}
		err = pb.SyncPrefixToFolder(ctx, objectInfo.Key, localFolder)
		if err != nil {
			fmt.Printf("failed to get local file %s from %s with err:%v\n", localFolder, s3key, err)
		}
		fmt.Printf("successfully sync %s to local flolder %s\n", s3key, localFolder)
	}
	fmt.Printf("FileStats: %s BlockStats: %s\n",
		bucket.GetOptions(ctx).FileStats, bucket.GetOptions(ctx).BlockStats)

	return nil
}
