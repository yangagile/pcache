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
	"crypto/md5"
	"encoding/base64"
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	"hash/crc32"
	"io"
	"os"
	"time"
)

func GenerateToken(ak, sk string, expirationMs int64, claimsMap map[string]interface{}) (string, error) {
	now := time.Now()
	exp := now.Add(time.Duration(expirationMs) * time.Millisecond)

	claims := jwt.MapClaims{
		"sub": ak,         // setSubject(ak)
		"iat": now.Unix(), // setIssuedAt
		"exp": exp.Unix(), // setExpiration
	}
	if claimsMap != nil {
		for key, value := range claimsMap {
			claims[key] = value
		}
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString([]byte(sk))
	if err != nil {
		return "", fmt.Errorf("failed to signed token: %w", err)
	}
	return tokenString, nil
}

// ParseToken 解析并验证JWT令牌
func ParseToken(tokenString, sk string) (jwt.MapClaims, error) {
	// 解析令牌
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		// 验证签名方法
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("非预期的签名方法: %v", token.Header["alg"])
		}
		return []byte(sk), nil
	})

	if err != nil {
		return nil, fmt.Errorf("令牌解析失败: %w", err)
	}

	// 验证令牌并提取 claims
	if claims, ok := token.Claims.(jwt.MapClaims); ok && token.Valid {
		return claims, nil
	}

	return nil, fmt.Errorf("无效令牌")
}

// generate MD5(Base64) from file
func GetMD5Base64FromFile(fileName string) (string, error) {
	file, err := os.Open(fileName)
	if err != nil {
		return "", err
	}
	defer file.Close()

	hash := md5.New()

	buffer := make([]byte, 4096)
	for {
		bytesRead, err := file.Read(buffer)
		if err != nil && err != io.EOF {
			return "", err
		}
		if bytesRead == 0 {
			break
		}
		hash.Write(buffer[:bytesRead])
	}

	hashBytes := hash.Sum(nil)
	return base64.StdEncoding.EncodeToString(hashBytes), nil
}

// generate CRC32(Base64) from file
func GetCRC32Base64FromFile(fileName string) (string, error) {
	file, err := os.Open(fileName)
	if err != nil {
		return "", err
	}
	defer file.Close()

	crc32Hash := crc32.NewIEEE()

	buffer := make([]byte, 4096)
	for {
		bytesRead, err := file.Read(buffer)
		if err != nil && err != io.EOF {
			return "", err
		}
		if bytesRead == 0 {
			break
		}
		crc32Hash.Write(buffer[:bytesRead])
	}

	crcValue := crc32Hash.Sum32()

	// Convert uint32 to a 4-byte array (big-endian)
	crcBytes := make([]byte, 4)
	crcBytes[0] = byte(crcValue >> 24)
	crcBytes[1] = byte(crcValue >> 16)
	crcBytes[2] = byte(crcValue >> 8)
	crcBytes[3] = byte(crcValue)

	return base64.StdEncoding.EncodeToString(crcBytes), nil
}
