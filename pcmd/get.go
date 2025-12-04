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
	"github.com/yangagile/pcache/sdk/pc-sdk-go/bucket"
	"pcmd/utils"
)

// RegisterCalcCommand registers the calculation command
func CreateGetCommand(config *Config) *Command {
	getCmd := &Command{
		Name:        "get",
		Description: "get file from bucket to local file",
		Usage:       "pcmd get  [FLAGS] s3://bucket/object file",
		Handler:     handleGet,
	}
	getCmd.Flags = flag.NewFlagSet("get", flag.ExitOnError)
	getCmd.Flags.BoolVar(&config.Debug, "debug",
		config.Debug, "debug mode")
	getCmd.Flags.BoolVar(&config.IsSmallFile, "small-file",
		config.IsSmallFile, "size is less than block size, will take special method for performance.")
	getCmd.Flags.BoolVar(&config.SkipExisting, "skip-existing",
		config.IsSmallFile, "skip existing file or object")
	getCmd.Flags.BoolVar(&config.SkipUnchanged, "skip-unchanged",
		config.SkipUnchanged, "skip unchanged file or object with size for checksum")
	getCmd.Flags.StringVar(&config.Checksum, "checksum",
		config.Checksum, "checksum file for verify or compare, crc32 or md5")
	return getCmd
}

func handleGet(config *Config, args []string) error {
	if len(args) < 2 {
		return fmt.Errorf("source local file and target s3 path are required")
	}
	localFile := args[1]
	s3Key := args[0]
	objectInfo, err := utils.ParseObjectInfo(s3Key)
	if err != nil {
		fmt.Printf("s3key %s is not valid!\n", s3Key)
		return err
	}

	ctx := context.Background()
	pb, err := bucket.NewPBucket(ctx, config.Endpoint, objectInfo.Bucket, config.AK, config.SK,
		[]string{"GetObject"})
	if err != nil {
		fmt.Printf("failed to new PBucket with err:%v\n", err)
	}

	_, err = pb.GetObject(ctx, objectInfo.Key, localFile)
	if err != nil {
		fmt.Printf("failed to get %s to local file %s with err:%v\n", s3Key, localFile, err)
	}

	fmt.Printf("successfully get %s to local file %s\n", s3Key, localFile)
	return nil
}
