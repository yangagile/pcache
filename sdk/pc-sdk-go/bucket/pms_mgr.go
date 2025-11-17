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
	log "github.com/sirupsen/logrus"
	"sort"
	"testdisk/model"
	"testdisk/utils"
	"time"
)

const URL_PATH_STS = "/api/v1/pb/"
const URL_PATH_PCP = "/api/v1/pcp/hash"
const URL_PATH_PMS = "/api/v1/pms/list"

type PmsInfo struct {
	Host        string `json:"host"`
	MetaVersion int64  `json:"metaVersion"`
	UpdateTime  int64  `json:"updateTime"`
}

type PmsUrlStats struct {
	URL          string
	ResponseTime int64
	Active       bool
	Msg          string
}

type PmsManager struct {
	pmsUrls   []*PmsUrlStats
	secretMgr *SecretManager
}

func NewPmsManager(ctx *context.Context, pmsUrl string, secretMgr *SecretManager) (*PmsManager, error) {
	pm := &PmsManager{
		secretMgr: secretMgr,
	}
	var err error
	pm.pmsUrls, err = pm.GetPmsList(pmsUrl)
	go pm.RefreshPmsList()
	return pm, err
}

func (pm *PmsManager) GetPmsUrl() string {
	return pm.pmsUrls[0].URL
}

func (pm *PmsManager) GetPmsList(pmsUrl string) ([]*PmsUrlStats, error) {
	url := utils.MergePath(pmsUrl, URL_PATH_PMS)
	header := make(map[string]string)
	if pm.secretMgr != nil {
		header["X-AK"] = pm.secretMgr.GetAccessKey()
		header["X-TOKEN"] = pm.secretMgr.GetToken(nil, 3600*1000)
	}
	var pmsList []*PmsInfo
	err := utils.GetAndParseJSON(url, nil, header, &pmsList)
	if err != nil {
		return nil, err
	}
	var curPmsUrls []*PmsUrlStats
	for _, pms := range pmsList {
		curPmsUrls = append(curPmsUrls, &PmsUrlStats{
			URL:          pms.Host,
			ResponseTime: 0,
			Active:       true,
			Msg:          "pms",
		})
	}
	return curPmsUrls, nil
}

func (pm *PmsManager) RefreshPmsList() error {
	pmsList, err := pm.GetPmsList(pm.GetPmsUrl())
	if err != nil {
		log.WithError(err).WithField("pmsUrl", pm.GetPmsUrl()).Errorln("failed to get PMS list")
		return err
	}
	for _, pms := range pmsList {
		startTime := time.Now().UnixMilli()
		_, err = pm.GetPmsList(pms.URL)
		if err != nil {
			log.WithError(err).WithField("pmsUrl", pms.URL).Errorln("failed to refresh PMS")
			pms.Active = false
			pms.Msg = err.Error()
		} else {
			pms.Active = true
			pms.Msg = "pms"
		}
		pms.ResponseTime = time.Now().UnixMilli() - startTime
	}
	sort.Slice(pmsList, func(i, j int) bool {
		return pmsList[i].ResponseTime < pmsList[j].ResponseTime
	})
	pm.pmsUrls = pmsList
	return nil
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
		go pm.RefreshPmsList()
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
	if pm.secretMgr != nil {
		header["X-AK"] = pm.secretMgr.GetAccessKey()
		header["X-TOKEN"] = pm.secretMgr.GetToken(nil, 3600*1000)
	}
	var pcpHashTable utils.PcpTable
	err := utils.GetAndParseJSON(url, &allParams, header, &pcpHashTable)
	if err != nil {
		go pm.RefreshPmsList()
		return nil, err
	}
	return &pcpHashTable, nil
}
