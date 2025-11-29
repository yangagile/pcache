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
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
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
	urlProve   *utils.UrlProbe
	secretMgr  *SecretManager
	retryTimes int
}

func NewPmsManager(ctx context.Context, pmsUrl string, secretMgr *SecretManager) (*PmsManager, error) {
	pm := &PmsManager{
		secretMgr:  secretMgr,
		retryTimes: 3,
	}
	var err error
	pm.urlProve, err = utils.NewUrlProbe(pmsUrl, pm.ProbeFunction)
	return pm, err
}

func (pm *PmsManager) ProbeFunction(url string) ([]string, error) {
	pmsList, err := pm.GetPmsList(url)
	if err != nil {
		log.WithError(err).WithField("pmsUrl", url).Errorln("failed to get PMS list")
		return nil, err
	}
	urls := make([]string, 0)
	for _, pms := range pmsList {
		urls = append(urls, pms.URL)
	}
	return urls, nil
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

func (pm *PmsManager) GetRoutingResult(bucket, path string, permission []string) (*Router, error) {
	var err error
	var routing *Router
	for i := 0; i < pm.retryTimes; i++ {
		routing, err = pm.interGetRoutingResult(bucket, path, permission)
		if err == nil {
			return routing, nil
		}
		log.WithError(err).WithField("bucket", pm.urlProve.GetUrl()).WithField("retry time", i).
			Errorln("failed get routing result list, retry ...")
	}
	return routing, err
}

func (pm *PmsManager) interGetRoutingResult(bucket, path string, permission []string) (*Router, error) {
	url := utils.MergePath(pm.urlProve.GetUrl(), URL_PATH_STS+bucket+"/sts")
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

	var routing Router
	err := utils.GetAndParseJSON(url, &allParams, header, &routing)
	if err != nil {
		pm.urlProve.ReportFail(url)
		return nil, err
	}
	return &routing, nil
}

func (pm *PmsManager) GetPcpList(checksum string) (*utils.PcpTable, error) {
	var err error
	var pcpTable *utils.PcpTable
	for i := 0; i < pm.retryTimes; i++ {
		pcpTable, err = pm.interGetPcpList(checksum)
		if err == nil {
			return pcpTable, nil
		}
		log.WithError(err).WithField("bucket", pm.urlProve.GetUrl()).WithField("retry time", i).
			Errorln("failed get PCP list, retry ...")
	}
	return pcpTable, err
}

func (pm *PmsManager) interGetPcpList(checksum string) (*utils.PcpTable, error) {
	url := utils.MergePath(pm.urlProve.GetUrl(), URL_PATH_PCP)
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
		pm.urlProve.ReportFail(url)
		return nil, err
	}
	return &pcpHashTable, nil
}
