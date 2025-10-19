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

package com.cloud.pc.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpUtils {

    public static HttpResponse sendRequest(
            String url,
            String method,
            Map<String, String> headers,
            Map<String, String> queryParams,
            String requestBody
    ) throws IOException {
        String fullUrl = buildUrlWithQueryParams(url, queryParams);
        HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();

        try {
            conn.setRequestMethod(method.toUpperCase());
            conn.setConnectTimeout(5000); // 连接超时 5 秒
            conn.setReadTimeout(10000);   // 读取超时 10 秒

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if ("POST".equalsIgnoreCase(method) && requestBody != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int statusCode = conn.getResponseCode();
            Map<String, String> responseHeaders = new HashMap<>();
            conn.getHeaderFields().forEach((key, value) -> {
                if (key != null) {
                    responseHeaders.put(key, String.join(", ", value));
                }
            });

            String body;
            try (InputStream inputStream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                if (inputStream == null) {
                    body = "";
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        body = reader.lines().collect(Collectors.joining("\n"));
                    }
                }
            }
            return new HttpResponse(statusCode, responseHeaders, body);

        } finally {
            conn.disconnect();
        }
    }

    private static String buildUrlWithQueryParams(String url, Map<String, String> queryParams) throws IOException {
        if (queryParams == null || queryParams.isEmpty()) return url;
        StringBuilder urlBuilder = new StringBuilder(url);
        if (!url.contains("?")) {
            urlBuilder.append("?");
        } else {
            urlBuilder.append("&");
        }
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String encodedKey = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name());
            String encodedValue = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name());
            urlBuilder.append(encodedKey).append("=").append(encodedValue).append("&");
        }
        if (urlBuilder.charAt(urlBuilder.length() - 1) == '&') {
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }
        return urlBuilder.toString();
    }

    public static class HttpResponse {
        private final int statusCode;
        private final Map<String, String> headers;
        private final String body;

        public HttpResponse(int statusCode, Map<String, String> headers, String body) {
            this.statusCode = statusCode;
            this.headers = headers != null ? headers : Collections.emptyMap();
            this.body = body != null ? body : "";
        }
        public int getStatusCode() {
            return statusCode;
        }
        public Map<String, String> getHeaders() {
            return headers;
        }
        public String getBody() {
            return body;
        }
    }
}