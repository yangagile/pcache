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
		Description: "Put a local file into bucket",
		Usage:       "pcmd put [FLAGS] FILE s3://BUCKET/KEY",
		Handler:     handlePut,
	}
	putCmd.Flags = flag.NewFlagSet("put", flag.ExitOnError)
	putCmd.Flags.BoolVar(&config.ForceReplace, "force",
		config.ForceReplace, "force overwrite of existing file")

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

	err = pb.Put(ctx, localFile, objectInfo.Key)
	if err != nil {
		fmt.Printf("failed to put local file %s to %s with err:%v\n", localFile, s3Key, err)
	}

	fmt.Printf("successfully put local file %s to %s\n", localFile, s3Key)
	return nil
}
