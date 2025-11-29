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
	"testing"
)

func Test_NewS3ClientWithSTS(t *testing.T) {

	newS3ClientMgr := &S3ClientManager{
		s3Client:    nil,
		router:      nil,
		expiredTime: 100,
	}

	s3Client := newS3ClientMgr.GetS3Client()
	if s3Client != nil {
		t.Fatalf("s3Client should be nil")
	}

	stsInfo := newS3ClientMgr.GetStsInfo()
	if stsInfo != nil {
		t.Fatalf("stsInfo should be nil")
	}
}
