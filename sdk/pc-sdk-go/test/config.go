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

package test

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"os"
	"path/filepath"
	"runtime"
	"sync"
)

var (
	config     *TestConfig
	configOnce sync.Once
	configPath string
)

type TestConfig struct {
	Pms struct {
		Url string `yaml:"url"`
	} `yaml:"pms"`
	Bucket struct {
		Ak     string `yaml:"ak"`
		Sk     string `yaml:"sk"`
		Name   string `yaml:"name"`
		Prefix string `yaml:"prefix"`
	} `yaml:"bucket"`
	Local struct {
		Root string `yaml:"root"`
	} `yaml:"local"`
}

func GetConfig() *TestConfig {
	configOnce.Do(func() {
		if configPath == "" {
			configPath = findConfigFile()
		}

		data, err := os.ReadFile(configPath)
		if err != nil {
			panic(fmt.Sprintf("Failed to read test config: %v", err))
		}

		cfg := &TestConfig{}
		if err := yaml.Unmarshal(data, cfg); err != nil {
			panic(fmt.Sprintf("Failed to parse test config: %v", err))
		}

		setDefaults(cfg)

		config = cfg
	})
	return config
}

func findConfigFile() string {
	_, filename, _, _ := runtime.Caller(0)
	dir := filepath.Dir(filename)

	return filepath.Join(dir, "test_config.yaml")
}

func setDefaults(cfg *TestConfig) {
	if cfg.Pms.Url == "" {
		cfg.Pms.Url = "http://127.0.0.1:8080"
	}
	if cfg.Bucket.Ak == "" {
		cfg.Bucket.Ak = "test-ak"
	}
	if cfg.Bucket.Sk == "" {
		cfg.Bucket.Sk = "test-sk"
	}
	if cfg.Bucket.Name == "" {
		cfg.Bucket.Name = "test-bucket"
	}
	if cfg.Local.Root == "" {
		cfg.Bucket.Name = os.TempDir()
	}
}
