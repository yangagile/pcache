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
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"time"
)

type RequestParams struct {
	StringParams map[string]string

	ListParams map[string][]string
}

func GetAndParseJSON(baseURL string, params *RequestParams, headers map[string]string, result interface{}) error {
	// Parse the base URL
	parsedURL, err := url.Parse(baseURL)
	if err != nil {
		return fmt.Errorf("failed to parse URL: %w", err)
	}

	// Add query parameters if provided
	if params != nil {
		queryParams := url.Values{}
		for key, value := range params.StringParams {
			queryParams.Set(key, value)
		}
		for key, values := range params.ListParams {
			for _, value := range values {
				queryParams.Add(key, value)
			}
		}
		//for key, value := range params {
		//	queryParams.Add(key, value)
		//}
		parsedURL.RawQuery = queryParams.Encode()
	}

	finalURL := parsedURL.String()
	log.Printf("Request URL: %s\n", finalURL)

	// Create context with timeout for request
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Create new HTTP request
	req, err := http.NewRequestWithContext(ctx, "GET", finalURL, nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	// Add headers if provided
	if headers != nil {
		for key, value := range headers {
			req.Header.Add(key, value)
		}
	}

	// Send HTTP request
	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("HTTP request failed: %w", err)
	}
	defer resp.Body.Close()

	// Check for non-200 status
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body) // Try to read error body
		return fmt.Errorf("unexpected status code: %d, response: %s", resp.StatusCode, string(body))
	}

	// Read response body
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read response body: %w", err)
	}

	// Parse JSON into target struct
	if err := json.Unmarshal(body, result); err != nil {
		return fmt.Errorf("JSON unmarshal failed: %w", err)
	}
	return nil
}
