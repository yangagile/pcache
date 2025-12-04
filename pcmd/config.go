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

package main

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type Config struct {
	Endpoint      string
	AK            string
	SK            string
	DryRun        bool
	Debug         bool // print detail log
	IsSmallFile   bool // size is less than block size, will take special method for performance.
	SkipExisting  bool // if the local file or remote object is existing, not replace.
	SkipUnchanged bool // if the local file  remote object is same, not replace.
	Checksum      string
}

func NewConfig() *Config {
	cfg := Config{
		Endpoint:      "",
		AK:            "",
		SK:            "",
		DryRun:        false,
		Debug:         false,
		IsSmallFile:   false,
		SkipExisting:  false,
		SkipUnchanged: false,
		Checksum:      "",
	}
	cfg.loadConfig()
	return &cfg
}

// loadGlobalConfig loads configuration from ~/.pcmd.cfg file
func (c *Config) loadConfig() error {
	// Get user's home directory
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return fmt.Errorf("could not find home directory: %v", err)
	}

	// Construct config file path
	configPath := filepath.Join(homeDir, ".pcmd.cfg")
	// Check if config file exists
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		// Config file doesn't exist - this is not an error
		return nil
	}

	// Open config file
	file, err := os.Open(configPath)
	if err != nil {
		return fmt.Errorf("could not open config file: %v", err)
	}
	defer file.Close()

	// Read and parse config file
	scanner := bufio.NewScanner(file)
	lineNumber := 0
	for scanner.Scan() {
		lineNumber++
		line := strings.TrimSpace(scanner.Text())

		// Skip empty lines and comments
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		// Parse key=value pairs
		parts := strings.SplitN(line, "=", 2)
		if len(parts) != 2 {
			fmt.Printf("Warning: Invalid config format at line %d: %s\n", lineNumber, line)
			continue
		}

		key := strings.TrimSpace(parts[0])
		value := strings.TrimSpace(parts[1])

		// Remove quotes if present
		if len(value) >= 2 && ((value[0] == '"' && value[len(value)-1] == '"') ||
			(value[0] == '\'' && value[len(value)-1] == '\'')) {
			value = value[1 : len(value)-1]
		}

		switch key {
		case "endpoint":
			c.Endpoint = value
		case "ak":
			c.AK = value
		case "sk":
			c.SK = value
		default:
			fmt.Printf("Warning: Unknown config key at line %d: %s\n", lineNumber, key)
		}
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("error reading config file: %v", err)
	}

	fmt.Printf("Loaded configuration from: %s\n", configPath)
	return nil
}
