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
	"encoding/json"
	"sync"
	"time"
)

const (
	TrackerKey string = "tracker"
)

type Tracker struct {
	mu      sync.Mutex
	phases  []PhaseRecord
	metrics map[string]interface{}
}

type PhaseRecord struct {
	Name      string
	StartTime time.Time
	EndTime   time.Time
	Duration  time.Duration
}

func NewTracker() *Tracker {
	return &Tracker{
		metrics: make(map[string]interface{}),
	}
}

func (t *Tracker) StartPhase(phase string) {
	t.mu.Lock()
	defer t.mu.Unlock()

	t.phases = append(t.phases, PhaseRecord{
		Name:      phase,
		StartTime: time.Now(),
	})
}

func (t *Tracker) EndPhase(phase string) {
	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now()
	for i := len(t.phases) - 1; i >= 0; i-- {
		if t.phases[i].Name == phase && t.phases[i].EndTime.IsZero() {
			t.phases[i].EndTime = now
			t.phases[i].Duration = now.Sub(t.phases[i].StartTime)
			return
		}
	}

	t.phases = append(t.phases, PhaseRecord{
		Name:     phase,
		EndTime:  now,
		Duration: 0,
	})
}

func (t *Tracker) RecordMetric(metric string, value interface{}) {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.metrics[metric] = value
}

func (t *Tracker) GetMetric(metric string) interface{} {
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.metrics[metric]
}

func (t *Tracker) GetReport() string {
	content, _ := json.Marshal(t)
	return string(content)
}

func WithTracker(ctx *context.Context) context.Context {
	t := NewTracker()
	return context.WithValue(*ctx, TrackerKey, t)
}

func GetTracker(ctx *context.Context) *Tracker {
	if t, ok := (*ctx).Value(TrackerKey).(*Tracker); ok {
		return t
	}
	return nil
}
