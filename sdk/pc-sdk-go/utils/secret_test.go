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
	"os"
	"testing"
	"time"
)

func Test_GenerateToken(t *testing.T) {
	ak := "user01"
	sk := "3oqMCtV+fwazQnpqBka5IaJ+lc0zDTpUJstXqqHDfh4="
	expirationMs := int64(3600000)

	// 自定义声明
	customClaims := map[string]interface{}{
		"user_id": 12345,
		"role":    "admin",
		"email":   "user@example.com",
	}

	// 1. generate token
	token, err := GenerateToken(ak, sk, expirationMs, customClaims)
	if err != nil {
		t.Fatalf("failed to generate token with err:%v", err)
		return
	}
	fmt.Println("token:\n", token)

	// 2. parse token
	claims, err := ParseToken(token, sk)
	if err != nil {
		t.Fatalf("failed to parse token with err:%v", err)
		return
	}

	// 3. print parse result
	fmt.Println("\nparse result:")
	fmt.Println("Subject (ak):", claims["sub"])
	fmt.Println("Issued Time (iat):", time.Unix(int64(claims["iat"].(float64)), 0))
	fmt.Println("Expiry Time (exp):", time.Unix(int64(claims["exp"].(float64)), 0))

	fmt.Println("\nuser claims:")
	for key, value := range claims {
		if key == "sub" || key == "iat" || key == "exp" {
			continue
		}
		fmt.Printf("%s: %v\n", key, value)
	}
}

func Test_GenerateChecksum(t *testing.T) {
	testRoot := MergePath(os.TempDir(), "Test_GenerateChecksum")
	testfile, err := CreateTestFile(testRoot, "test.txt", 1024)
	if err != nil {
		t.Fatalf("failed to create local file:%v with err:%v", testfile, err)
	}
}
