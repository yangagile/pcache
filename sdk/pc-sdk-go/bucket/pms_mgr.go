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
	"testdisk/model"
	"testdisk/utils"
)

const URL_PATH_STS = "/api/v1/pb/"
const URL_PATH_PCP = "/api/v1/pcp/hash"

type PmsManager struct {
	pmsUrls   []string
	secretMgr *SecretManager
}

func NewPmsManager(ctx *context.Context, pmsUrls []string, secretMgr *SecretManager) (*PmsManager, error) {
	pm := &PmsManager{
		pmsUrls:   pmsUrls,
		secretMgr: secretMgr,
	}
	return pm, nil
}

func (pm *PmsManager) GetPmsUrl() string {
	return pm.pmsUrls[0]
}

func (pm *PmsManager) GetRoutingResult(bucket, path string, permission []string) (*model.Router, error) {
	url := pm.GetPmsUrl() + URL_PATH_STS + bucket + "/sts"
	strParams := make(map[string]string)
	if path != "" {
		strParams["path"] = path
	}

	allParams := utils.RequestParams{
		StringParams: strParams,
		ListParams: map[string][]string{
			"permissions": permission,
		},
	}

	header := make(map[string]string)
	header["X-AK"] = pm.secretMgr.GetAccessKey()

	claims := map[string]interface{}{
		"bucket":      bucket,
		"path":        path,
		"permissions": permission,
	}
	header["X-TOKEN"] = pm.secretMgr.GetToken(claims, 1800*1000)

	var routing model.Router
	err := utils.GetAndParseJSON(url, &allParams, header, &routing)
	if err != nil {
		return nil, err
	}
	return &routing, nil
}

func (pm *PmsManager) GetPcpList(checksum string) (*utils.PcpTable, error) {
	url := pm.GetPmsUrl() + URL_PATH_PCP
	strParams := make(map[string]string)
	if checksum != "" {
		strParams["checksum"] = checksum
	}
	allParams := utils.RequestParams{
		StringParams: strParams,
	}
	header := make(map[string]string)
	header["X-AK"] = pm.secretMgr.GetAccessKey()
	header["X-TOKEN"] = pm.secretMgr.GetToken(nil, 3600*1000)

	var pcpHashTable utils.PcpTable
	err := utils.GetAndParseJSON(url, &allParams, header, &pcpHashTable)
	if err != nil {
		return nil, err
	}
	return &pcpHashTable, nil
}
