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
	"testing"
	"time"
)

func Test_GenerateToken(t *testing.T) {
	ak := "user01"
	sk := "3oqMCtV+fwazQnpqBka5IaJ+lc0zDTpUJstXqqHDfh4=" // 生产环境应从安全来源获取
	expirationMs := int64(3600000)                       // 1小时

	// 自定义声明
	customClaims := map[string]interface{}{
		"user_id": 12345,
		"role":    "admin",
		"email":   "user@example.com",
	}

	// 1. 生成令牌
	token, err := GenerateToken(ak, sk, expirationMs, customClaims)
	if err != nil {
		fmt.Println("令牌生成失败:", err)
		return
	}
	fmt.Println("生成的JWT令牌:\n", token)

	// 2. 解析令牌
	claims, err := ParseToken(token, sk)
	if err != nil {
		fmt.Println("令牌解析失败:", err)
		return
	}

	// 3. 输出解析结果
	fmt.Println("\n解析后的声明:")
	fmt.Println("Subject (ak):", claims["sub"])
	fmt.Println("签发时间 (iat):", time.Unix(int64(claims["iat"].(float64)), 0))
	fmt.Println("过期时间 (exp):", time.Unix(int64(claims["exp"].(float64)), 0))

	fmt.Println("\n自定义声明:")
	for key, value := range claims {
		// 跳过标准声明
		if key == "sub" || key == "iat" || key == "exp" {
			continue
		}
		fmt.Printf("%s: %v\n", key, value)
	}
}
