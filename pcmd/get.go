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
	"os"
	"pcmd/utils"
)

// RegisterCalcCommand registers the calculation command
func CreateGetCommand(config *Config) *Command {
	putCmd := &Command{
		Name:        "get",
		Description: "Get file from bucket to local file",
		Usage:       "pcmd get s3://BUCKET/OBJECT LOCAL_FILE",
		Handler:     handleGet,
	}
	putCmd.Flags = flag.NewFlagSet("get", flag.ExitOnError)
	putCmd.Flags.BoolVar(&config.ForceReplace, "force",
		config.ForceReplace, "force overwrite of existing file")
	return putCmd
}

func handleGet(config *Config, args []string) error {
	if len(args) < 2 {
		return fmt.Errorf("source local file and target s3 path are required")
	}
	localFile := args[1]
	_, err := os.Stat(localFile)
	if err == nil && !config.ForceReplace {
		fmt.Printf("local file %s is alread existing!\n", localFile)
		return err
	}
	s3Key := args[0]
	objectInfo, err := utils.ParseObjectInfo(s3Key)
	if err != nil {
		fmt.Printf("s3key %s is not valid!\n", s3Key)
		return err
	}

	ctx := context.Background()
	pb, err := bucket.NewPBucket(&ctx, config.Endpoint, objectInfo.Bucket, config.AK, config.SK,
		bucket.WithPermissions("GetObject"))
	if err != nil {
		fmt.Printf("failed to new PBucket with err:%v\n", err)
	}

	err = pb.Get(&ctx, objectInfo.Key, localFile)
	if err != nil {
		fmt.Printf("failed to get %s to local file %s with err:%v\n", s3Key, localFile, err)
	}

	fmt.Printf("successfully get %s to local file %s\n", s3Key, localFile)
	return nil
}
