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
	"bufio"
	"io"
	"math/rand"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

func CreateTestFile(dir, name string, size int64) (string, error) {
	err := os.MkdirAll(dir, 0755)
	if err != nil {
		return "", err
	}
	filePath := filepath.Join(dir, name)
	file, err := os.Create(filePath)
	if err != nil {
		return "", err
	}
	defer file.Close()
	bufSize := int64(1 * 1024 * 1024) // 1MB缓冲区
	if size < bufSize {
		bufSize = size
	}

	rnd := rand.New(rand.NewSource(time.Now().UnixNano()))
	buf := make([]byte, bufSize)

	var written int64
	for written < size {
		_, err := rnd.Read(buf)
		if err != nil {
			return "", err
		}
		remain := size - written
		if remain < bufSize {
			buf = buf[:remain]
		}
		n, err := file.Write(buf)
		if err != nil {
			return "", err
		}
		written += int64(n)
	}
	return filePath, nil
}

func MergeFiles(partPaths []string, targetPath string) error {
	// 创建目标文件
	targetFile, err := os.Create(targetPath)
	if err != nil {
		return err
	}
	defer targetFile.Close()

	// 使用带缓冲的写入器
	bufferedWriter := bufio.NewWriterSize(targetFile, 8*1024) // 8KB缓冲区
	defer bufferedWriter.Flush()

	// 遍历所有分片文件
	for _, partPath := range partPaths {
		// 打开分片文件
		partFile, err := os.Open(partPath)
		if err != nil {
			return err
		}

		// 使用带缓冲的读取器
		bufferedReader := bufio.NewReaderSize(partFile, 8*1024) // 8KB缓冲区

		// 复制文件内容
		if _, err := io.Copy(bufferedWriter, bufferedReader); err != nil {
			partFile.Close()
			return err
		}

		// 关闭当前分片文件
		partFile.Close()
	}

	if err := bufferedWriter.Flush(); err != nil {
		return err
	}

	for _, partPath := range partPaths {
		if err := os.Remove(partPath); err != nil {

			return err
		}
	}
	return nil
}

func MergePath(root, sub string) string {
	var path string

	// If root is not empty, set path to root
	if root != "" {
		path = root
	}

	// If path is not empty and does not end with "/", add "/"
	if path != "" && !strings.HasSuffix(path, "/") {
		path += "/"
	}

	// If sub starts with "/", remove the leading "/" and append it
	if strings.HasPrefix(sub, "/") {
		path += sub[1:]
	} else {
		path += sub
	}

	return path
}

func GetCurrentFunctionName() string {
	pc, _, _, ok := runtime.Caller(1)
	if !ok {
		return "Unknown"
	}

	fn := runtime.FuncForPC(pc)
	if fn == nil {
		return "Unknown"
	}
	pos := strings.LastIndex(fn.Name(), ".")
	if pos < 0 {
		pos = 0
	} else {
		pos = pos + 1
	}
	return fn.Name()[pos:]
}
