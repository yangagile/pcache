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

var urlActive = "http://active"
var urlSlow = "http://slow"
var urlDead = "http://dead"

func exampleProbeFunction(url string) ([]string, error) {
	if url == urlActive {
		return []string{
			urlActive,
			urlSlow,
			urlDead,
		}, nil
	} else if url == urlSlow {
		time.Sleep(10 * time.Microsecond)
		return []string{
			urlActive,
			urlSlow,
			urlDead,
		}, nil

	} else if url == urlDead {
		time.Sleep(100 * time.Microsecond)
		return nil, fmt.Errorf("dead")
	} else {
		return nil, fmt.Errorf("unknown base url: %s", url)
	}
}

func Test_URL_probe(t *testing.T) {
	probe, err := NewUrlProbe(urlActive, exampleProbeFunction)
	if err != nil {
		t.Fatalf("failed to new UrlProbe with err:%v", err)
	}

	urlList := probe.GetActiveUrls()
	if len(urlList) != 3 {
		t.Fatalf("active url should be 3, URL list:%v", urlList)
	}

	// first time to get active url, will trigger probe function
	url := probe.GetUrl()
	if url != urlActive {
		t.Fatalf("failed to get active URL:%v", url)
	}

	// report fail, the active url will be changed to slow
	probe.ReportFail(url)
	url = probe.GetUrl()
	if url != urlSlow {
		t.Fatalf("failed to get slow URL:%v", url)
	}

	// wait for probe function is over
	time.Sleep(1 * time.Second)

	// the failed active url will be back
	url = probe.GetUrl()
	if url != urlActive {
		t.Fatalf("failed to get active URL:%v", url)
	}

	// the dead url will be probed
	urlList = probe.GetActiveUrls()
	if len(urlList) != 2 {
		t.Fatalf("active url should be 2, URL list:%v", urlList)
	}
}
