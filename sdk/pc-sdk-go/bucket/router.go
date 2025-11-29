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

type StsInfo struct {
	AccessKey     string `json:"accessKey"`
	AccessSecret  string `json:"accessSecret"`
	SecurityToken string `json:"securityToken"`
	Endpoint      string `json:"endpoint"`
	BucketName    string `json:"bucketName"`
	Path          string `json:"path"`
	StorageType   string `json:"storageType"`
	Region        string `json:"region"`
	Expiration    int64  `json:"expiration"`
}

type RoutingPolicy struct {
	Type string `json:"type"`
	Name string `json:"name"`
}

type Router struct {
	Algorithm RoutingPolicy `json:"algorithm"`
	StsInfos  []StsInfo     `json:"stsInfos"`
}

func (c *Router) GetStsInfo() *StsInfo {
	return &c.StsInfos[0]
}
