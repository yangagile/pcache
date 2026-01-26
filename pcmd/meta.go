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
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
)

type PBucketItem struct {
	ID            int    `json:"id"`
	Name          string `json:"name"`
	PolicyRouting string `json:"policyRouting"`
}

type PBucketFile struct {
	Items []PBucketItem `json:"items"`
}

type SecretItem struct {
	ID        int    `json:"id"`
	AccessKey string `json:"accessKey"`
	SecretKey string `json:"secretKey"`
	IAM       string `json:"iam"`
}

type SecretFile struct {
	Items []SecretItem `json:"items"`
}

type VendorItem struct {
	ID           int    `json:"id"`
	Name         string `json:"name"`
	Region       string `json:"region"`
	AccessKey    string `json:"accessKey"`
	AccessSecret string `json:"accessSecret"`
	Endpoint     string `json:"endpoint"`
	StsEndpoint  string `json:"stsEndpoint"`
}

type VendorFile struct {
	Items []VendorItem `json:"items"`
}

type VendorBucketItem struct {
	ID     int    `json:"id"`
	Name   string `json:"name"`
	Vendor string `json:"vendor"`
	Region string `json:"region"`
}

type VendorBucketFile struct {
	Items []VendorBucketItem `json:"items"`
}

// default value
var metaPath = "./meta/"

var pbucket = PBucketItem{
	ID:            0,
	Name:          "pb-minio",
	PolicyRouting: "{\"router\":{\"type\":\"OneRouter\"},\"bucketIds\":[0]}",
}

var secret = SecretItem{
	ID:        0,
	AccessKey: "pms-admin",
	SecretKey: "82B089C7A07DDA727BD07B4A4491338B2087B24F",
	IAM:       "\"{\"Version\":\"2025-08-05\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"pms:admin\"],\"Resource\":[\"*\"]}]}\"",
}

var vendor = VendorItem{
	ID:           0,
	Name:         "MINIO",
	Region:       "us-east-1",
	AccessKey:    "sts-user",
	AccessSecret: "sts-password",
	Endpoint:     "http://127.0.0.1:9000",
	StsEndpoint:  "http://127.0.0.1:9000",
}

var vendorBucket = VendorBucketItem{
	ID:     0,
	Name:   "minio-test",
	Vendor: "MINIO",
	Region: "us-east-1",
}

// RegisterCalcCommand registers the calculation command
func CreateMetaCommand(config *Config) *Command {
	configCmd := &Command{
		Name:        "meta",
		Description: "init PCache meta files",
		Usage:       "pcmd meta [FLAGS] init",
		Handler:     handleMeta,
	}
	configCmd.Flags = flag.NewFlagSet("meta", flag.ExitOnError)

	configCmd.Flags.StringVar(&metaPath, "meta-path", metaPath, "Meta root path")
	configCmd.Flags.StringVar(&pbucket.Name, "pb-name", pbucket.Name, "PBucket name")
	configCmd.Flags.StringVar(&vendorBucket.Name, "vendor-bucket", vendorBucket.Name, "Vendor name")
	configCmd.Flags.StringVar(&vendor.Name, "vendor", vendor.Name, "Vendor name")
	configCmd.Flags.StringVar(&vendor.Region, "vendor-region", vendor.Region, "Vendor region")
	configCmd.Flags.StringVar(&vendor.AccessKey, "vendor-ak", vendor.AccessKey, "Vendor accessKey")
	configCmd.Flags.StringVar(&vendor.AccessSecret, "vendor-sk", vendor.AccessSecret, "Vendor accessSecret")
	configCmd.Flags.StringVar(&vendor.Endpoint, "vendor-endpoint", vendor.Endpoint, "Vendor endpoint")
	configCmd.Flags.StringVar(&vendor.StsEndpoint, "vendor-sts-endpoint", vendor.StsEndpoint, "Vendor STS endpoint")

	return configCmd
}

func generateMeta() error {
	// secret
	secretFile := SecretFile{
		Items: []SecretItem{secret},
	}
	if err := writeJSON(filepath.Join(metaPath, "secret"), secretFile); err != nil {
		return err
	}

	// vendor
	vendorFile := VendorFile{
		Items: []VendorItem{vendor},
	}
	if err := writeJSON(filepath.Join(metaPath, "vendor"), vendorFile); err != nil {
		return err
	}

	// vendorbucket
	vendorBucket.Vendor = vendor.Name
	vendorBucket.Region = vendor.Region
	vendorBucketFile := VendorBucketFile{
		Items: []VendorBucketItem{vendorBucket},
	}
	if err := writeJSON(filepath.Join(metaPath, "vendorbucket"), vendorBucketFile); err != nil {
		return err
	}

	// pbucket
	pbucketFile := PBucketFile{
		Items: []PBucketItem{pbucket},
	}
	return writeJSON(filepath.Join(metaPath, "pbucket"), pbucketFile)
}

func writeJSON(filename string, data interface{}) error {
	dir := filepath.Dir(filename)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	file, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer file.Close()

	encoder := json.NewEncoder(file)
	encoder.SetIndent("", "    ")
	defer fmt.Printf("created meta file: %s \n", filename)
	return encoder.Encode(data)
}

func handleMeta(config *Config, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("invalid arguments")
	}
	err := generateMeta()
	if err == nil {
		defer fmt.Printf("successfully created meta files.\n")
	}
	return err
}
