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

	"github.com/yangagile/pcache/sdk/pc-sdk-go/bucket"
)

// RegisterCalcCommand registers the calculation command
func CreatePutCommand(config *Config) *Command {
	putCmd := &Command{
		Name:        "put",
		Description: "put a local file into bucket",
		Usage:       "pcmd put  [FLAGS] file s3://bucket/object",
		Handler:     handlePut,
	}
	putCmd.Flags = flag.NewFlagSet("put", flag.ExitOnError)
	putCmd.Flags = flag.NewFlagSet("get", flag.ExitOnError)
	putCmd.Flags.BoolVar(&config.Debug, "debug",
		config.Debug, "debug mode")
	putCmd.Flags.BoolVar(&config.IsSmallFile, "small-file",
		config.IsSmallFile, "size is less than block size, will take special method for performance.")
	putCmd.Flags.BoolVar(&config.SkipExisting, "skip-existing",
		config.IsSmallFile, "skip existing file or object")
	putCmd.Flags.BoolVar(&config.SkipUnchanged, "skip-unchanged",
		config.SkipUnchanged, "skip unchanged file or object with size for checksum")
	putCmd.Flags.StringVar(&config.Checksum, "checksum",
		config.Checksum, "checksum file for verify or compare, crc32 or md5")

	return putCmd
}

func handlePut(config *Config, args []string) error {
	if len(args) < 2 {
		return fmt.Errorf("source local file and target s3 path are required")
	}
	localFile := args[0]
	_, err := os.Stat(localFile)
	if err != nil {
		fmt.Printf("file %s is not existing!\n", localFile)
		return err
	}
	s3Key := args[1]
	objectInfo, err := utils.ParseObjectInfo(s3Key)
	if err != nil {
		fmt.Printf("s3key %s is not valid!\n", s3Key)
		return err
	}

	ctx := context.Background()
	pb, err := bucket.NewPBucket(ctx, config.Endpoint, objectInfo.Bucket, config.AK, config.SK,
		[]string{"PutObject"})
	if err != nil {
		fmt.Printf("failed to new PBucket with err:%v\n", err)
	}

	_, err = pb.PutObject(ctx, localFile, objectInfo.Key)
	if err != nil {
		fmt.Printf("failed to put local file %s to %s with err:%v\n", localFile, s3Key, err)
	}

	fmt.Printf("successfully put local file %s to %s\n", localFile, s3Key)
	return nil
}
