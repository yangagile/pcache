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

package utils

import (
	"fmt"
	"strings"
)

type ObjectInfo struct {
	Bucket string
	Key    string
}

// parseObjectInfo parses an S3 format path into ObjectInfo
// Input format: "s3://bucket/path/to/key"
// Returns ObjectInfo with Bucket and Key fields, or error if parsing fails
func ParseObjectInfo(s3path string) (ObjectInfo, error) {
	// Check if the path starts with "s3://"
	if !strings.HasPrefix(s3path, "s3://") {
		return ObjectInfo{}, fmt.Errorf("invalid S3 path: must start with 's3://'")
	}

	// Remove the "s3://" prefix
	pathWithoutPrefix := s3path[5:]

	// Check if the path is empty after removing prefix
	if len(pathWithoutPrefix) == 0 {
		return ObjectInfo{}, fmt.Errorf("invalid S3 path: empty path after 's3://'")
	}

	// Find the first slash to separate bucket from key
	firstSlashIndex := strings.Index(pathWithoutPrefix, "/")

	var bucket, key string

	if firstSlashIndex == -1 {
		// No slash found - the entire path is the bucket, key is empty
		bucket = pathWithoutPrefix
		key = ""
	} else {
		// Extract bucket (before first slash) and key (after first slash)
		bucket = pathWithoutPrefix[:firstSlashIndex]
		key = pathWithoutPrefix[firstSlashIndex+1:]

		// Remove trailing slash from key if present
		key = strings.TrimSuffix(key, "/")
	}

	// Validate bucket name (basic validation)
	if len(bucket) == 0 {
		return ObjectInfo{}, fmt.Errorf("invalid S3 path: bucket name cannot be empty")
	}

	return ObjectInfo{
		Bucket: bucket,
		Key:    key,
	}, nil
}
