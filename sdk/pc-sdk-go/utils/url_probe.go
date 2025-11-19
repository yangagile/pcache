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
	"log"
	"sort"
	"sync"
	"sync/atomic"
	"time"
)

// URL Probe
type UrlProbe struct {
	probeMinPeriodMs int64
	urlListPtr       atomic.Value //  *[]*UrlStats
	probeFunction    UrlProbeFunction
	isRunning        int32
	lastProbeTime    int64
	baseUrl          string
}

type UrlProbeFunction func(url string) ([]string, error)

type UrlStats struct {
	url          string
	responseTime int64
	active       int32
}

func NewUrlProbe(baseUrl string, probeFunction UrlProbeFunction) (*UrlProbe, error) {
	urls, err := probeFunction(baseUrl)
	if err != nil {
		log.Printf("Failed to get URL list from %s: %v", baseUrl, err)
		urls = []string{baseUrl}
		return nil, err
	}

	urlList := make([]*UrlStats, 0, len(urls))
	for _, url := range urls {
		urlList = append(urlList, &UrlStats{
			url:          url,
			responseTime: 0,
			active:       1,
		})
	}
	probe := UrlProbe{
		probeMinPeriodMs: 5 * 60 * 1000,
		probeFunction:    probeFunction,
		isRunning:        0,
		lastProbeTime:    0,
		baseUrl:          baseUrl,
	}
	probe.urlListPtr.Store(&urlList)
	return &probe, nil
}

func (u *UrlProbe) getUrlList() []*UrlStats {
	ptr := u.urlListPtr.Load()
	if ptr == nil {
		return nil
	}
	return *(ptr.(*[]*UrlStats))
}

func (u *UrlProbe) setUrlList(urlList []*UrlStats) {
	// 创建新的切片指针
	newPtr := &urlList
	u.urlListPtr.Store(newPtr)
}

// SetProbeMinPeriodMs
func (u *UrlProbe) SetProbeMinPeriodMs(period int64) {
	atomic.StoreInt64(&u.probeMinPeriodMs, period)
}

func (u *UrlProbe) GetUrl() string {
	urlList := u.getUrlList()
	if urlList == nil || len(urlList) == 0 {
		u.probe(true)
		return ""
	}

	// 首先尝试活跃的URL
	for _, urlStats := range urlList {
		if atomic.LoadInt32(&urlStats.active) == 1 {
			return urlStats.url
		}
	}

	// 如果没有活跃的URL，返回第一个
	if len(urlList) > 0 {
		return urlList[0].url
	}

	u.probe(false)
	return ""
}

// ReportFail 报告URL失败
func (u *UrlProbe) ReportFail(url string) {
	log.Printf("Report fail url: %s", url)
	force := false

	currentList := u.getUrlList()
	if currentList == nil {
		return
	}

	for _, urlStats := range currentList {
		if len(url) >= len(urlStats.url) && url[:len(urlStats.url)] == urlStats.url {
			if atomic.CompareAndSwapInt32(&urlStats.active, 1, 0) {
				force = true
			}
		}
	}

	if force {
		u.probe(true)
	}
}

// probe 触发探测
func (u *UrlProbe) probe(force bool) {
	currentTime := time.Now().UnixMilli()
	lastProbeTime := atomic.LoadInt64(&u.lastProbeTime)

	if !force && (currentTime-lastProbeTime < atomic.LoadInt64(&u.probeMinPeriodMs)) {
		return
	}

	if !atomic.CompareAndSwapInt32(&u.isRunning, 0, 1) {
		log.Println("Test is already running")
		return
	}

	// probe async
	go u.test()
}

// refresh UrlList
func (u *UrlProbe) refreshUrlList() []string {
	currentList := u.getUrlList()

	if currentList != nil {
		// try active urls
		for _, urlStats := range currentList {
			if atomic.LoadInt32(&urlStats.active) == 1 {
				urls, err := u.probeFunction(urlStats.url)
				if err == nil && len(urls) > 0 {
					return urls
				}
			}
		}

		// try all urls
		for _, urlStats := range currentList {
			urls, err := u.probeFunction(urlStats.url)
			if err == nil && len(urls) > 0 {
				return urls
			}
		}
	}

	// try base url
	urls, err := u.probeFunction(u.baseUrl)
	if err == nil && len(urls) > 0 {
		return urls
	}

	return []string{}
}

// probe the URLs and update the active status
func (u *UrlProbe) test() {
	defer func() {
		atomic.StoreInt32(&u.isRunning, 0)
		atomic.StoreInt64(&u.lastProbeTime, time.Now().UnixMilli())
	}()

	newUrlList := u.refreshUrlList()
	if len(newUrlList) == 0 {
		log.Println("Failed to get new URL list!")
		return
	}

	if len(newUrlList) == 1 {
		return
	}

	results := make([]*UrlStats, 0, len(newUrlList))
	var wg sync.WaitGroup
	var resultMutex sync.Mutex

	for _, url := range newUrlList {
		wg.Add(1)
		go func(targetUrl string) {
			defer wg.Done()

			startTime := time.Now()
			_, err := u.probeFunction(targetUrl)
			responseTime := time.Since(startTime).Milliseconds()

			resultMutex.Lock()
			defer resultMutex.Unlock()

			if err == nil {
				log.Printf("Got one active url: %s", targetUrl)
				results = append(results, &UrlStats{
					url:          targetUrl,
					responseTime: responseTime,
					active:       1,
				})
			} else {
				log.Printf("Failed to probe url %s: %v", targetUrl, err)
			}
		}(url)
	}

	wg.Wait()

	if len(results) > 0 {
		// sort by response time
		sort.Slice(results, func(i, j int) bool {
			return results[i].responseTime < results[j].responseTime
		})
		u.setUrlList(results)
	}
}

// GetActiveUrls 获取所有活跃的URL（用于调试和监控）
func (u *UrlProbe) GetActiveUrls() []string {
	currentList := u.getUrlList()

	activeUrls := make([]string, 0)
	for _, urlStats := range currentList {
		if atomic.LoadInt32(&urlStats.active) == 1 {
			activeUrls = append(activeUrls, urlStats.url)
		}
	}
	return activeUrls
}
