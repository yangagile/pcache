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
	"context"
	"fmt"
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
)

type SecretManager struct {
	accessKey string
	secretKey string
}

func NewSecretManager(ctx context.Context, ak, sk string) (*SecretManager, error) {
	sm := &SecretManager{
		accessKey: ak,
		secretKey: sk,
	}
	return sm, nil
}

func (sm *SecretManager) GetAccessKey() string {
	return sm.accessKey
}

func (sm *SecretManager) GetToken(claims map[string]interface{}, expirationMs int64) string {

	token, err := utils.GenerateToken(sm.accessKey, sm.secretKey, expirationMs, claims)
	if err != nil {
		fmt.Println("failed to create token:", err)
		return ""
	}
	return token
}
