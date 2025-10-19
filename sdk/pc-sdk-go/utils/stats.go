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
	"context"
	"math"
	"testdisk/model"
)

const (
	StatsCounterPcpCacheKey     string = "counter_pcp_cache"
	StatsCounterPcpLocalKey     string = "counter_pcp_local"
	StatsCounterLocalKey        string = "counter_local"
	StatsCounterLocalPcpFailKey string = "counter_local_pcp_fail"
	StatsCounterFailKey         string = "counter_fail"
	StatsTimeAverageKey         string = "time_average"
	StatsTimeMinKey             string = "time_min"
	StatsTimeMaxKey             string = "time_max"
)

type Stats struct {
	TimeAverage       int64
	TimeMax           int64
	TimeMin           int64
	CountFail         int
	CountPcpLocal     int
	CountPcpCache     int
	CountLocal        int
	CountLocalPcpFail int
}

func NewStats() Stats {
	return Stats{0, 0, math.MaxInt, 0, 0, 0, 0, 0}
}

func (s *Stats) Update(b *model.Block) {
	switch b.State {
	case model.STATE_FAIL:
		s.CountFail++
	case model.STATE_OK_PCP_LOCAL:
		s.CountPcpLocal++
	case model.STATE_OK_PCP_CACHE:
		s.CountPcpCache++
	case model.STATE_OK_LOCAL:
		s.CountLocal++
	case model.STATE_OK_LOCAL_PCP_FAIL:
		s.CountLocalPcpFail++
	}
	if b.TimeDuration > s.TimeMax {
		s.TimeMax = b.TimeDuration
	}
	if b.TimeDuration < s.TimeMin {
		s.TimeMin = b.TimeDuration
	}
	s.TimeAverage += b.TimeDuration
}

func (s *Stats) End(ctx *context.Context) {
	total := int64(s.CountFail + s.CountPcpLocal + s.CountPcpCache + s.CountLocal)
	if total > 0 {
		s.TimeAverage /= total
	}
	t := GetTracker(ctx)
	if t != nil {
		t.RecordMetric(StatsTimeAverageKey, s.TimeAverage)
		t.RecordMetric(StatsTimeMaxKey, s.TimeMax)
		t.RecordMetric(StatsTimeMinKey, s.TimeMin)
		t.RecordMetric(StatsCounterFailKey, s.CountFail)
		t.RecordMetric(StatsCounterPcpLocalKey, s.CountPcpLocal)
		t.RecordMetric(StatsCounterPcpCacheKey, s.CountPcpCache)
		t.RecordMetric(StatsCounterLocalKey, s.CountLocal)
		t.RecordMetric(StatsCounterLocalPcpFailKey, s.CountLocalPcpFail)
	}
}
