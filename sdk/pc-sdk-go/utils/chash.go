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
	"encoding/binary"
	"fmt"
	"sort"
	"sync"
)

type PcpTable struct {
	Checksum string          `json:"checksum"`
	PcpList  []*PhysicalNode `json:"pcpList"`
}

// HashFunction 定义哈希函数接口
type HashFunction interface {
	Hash(key string) uint64
}

// MD5Hash
type MD5Hash struct{}

func (m MD5Hash) Hash(key string) uint64 {
	hash := md5.Sum([]byte(key))
	return binary.BigEndian.Uint64(hash[:8])
}

type PhysicalNode struct {
	Host     string  `json:"host"`
	Priority float64 `json:"priority"`
}

type ConsistentHash struct {
	baseVirtualNodeCount int
	hashRingKeys         []uint64            // 排序的虚拟节点哈希值
	hashRingMap          map[uint64]string   // 哈希值到物理节点的映射
	nodeToHashes         map[string][]uint64 // 物理节点到哈希值列表的映射
	hashFunc             HashFunction
	lock                 sync.RWMutex
}

func NewConsistentHash() *ConsistentHash {
	return &ConsistentHash{
		baseVirtualNodeCount: 150,
		hashRingKeys:         make([]uint64, 0),
		hashRingMap:          make(map[uint64]string),
		nodeToHashes:         make(map[string][]uint64),
		hashFunc:             MD5Hash{},
	}
}

func (c *ConsistentHash) AddNode(node *PhysicalNode) {
	c.lock.Lock()
	defer c.lock.Unlock()

	if _, exists := c.nodeToHashes[node.Host]; exists {
		return // 节点已存在
	}

	virtualNodeCount := int(float64(c.baseVirtualNodeCount) * (1 + node.Priority))
	virtualHashes := make([]uint64, 0, virtualNodeCount)

	for i := 0; i < virtualNodeCount; i++ {
		virtualNode := fmt.Sprintf("%s#%d", node.Host, i)
		hash := c.hashFunc.Hash(virtualNode)
		virtualHashes = append(virtualHashes, hash)
		c.hashRingMap[hash] = node.Host
	}

	c.nodeToHashes[node.Host] = virtualHashes
	c.hashRingKeys = append(c.hashRingKeys, virtualHashes...)
	sort.Slice(c.hashRingKeys, func(i, j int) bool {
		return c.hashRingKeys[i] < c.hashRingKeys[j]
	})
}

func (c *ConsistentHash) RemoveNode(node *PhysicalNode) {
	c.lock.Lock()
	defer c.lock.Unlock()

	virtualHashes, exists := c.nodeToHashes[node.Host]
	if !exists {
		return
	}

	delete(c.nodeToHashes, node.Host)
	for _, hash := range virtualHashes {
		delete(c.hashRingMap, hash)
	}

	newKeys := make([]uint64, 0, len(c.hashRingKeys)-len(virtualHashes))
	for _, key := range c.hashRingKeys {
		keep := true
		for _, vh := range virtualHashes {
			if key == vh {
				keep = false
				break
			}
		}
		if keep {
			newKeys = append(newKeys, key)
		}
	}
	c.hashRingKeys = newKeys
}

func (c *ConsistentHash) GetNode(key string) string {
	c.lock.RLock()
	defer c.lock.RUnlock()

	if len(c.hashRingKeys) == 0 {
		return ""
	}

	hash := c.hashFunc.Hash(key)
	idx := sort.Search(len(c.hashRingKeys), func(i int) bool {
		return c.hashRingKeys[i] >= hash
	})

	if idx == len(c.hashRingKeys) {
		idx = 0
	}

	return c.hashRingMap[c.hashRingKeys[idx]]
}

func (c *ConsistentHash) PrintContent() {
	c.lock.RLock()
	defer c.lock.RUnlock()

	fmt.Println("hashRing:")
	for _, hash := range c.hashRingKeys {
		fmt.Printf("%d : %s\n", hash, c.hashRingMap[hash])
	}

	fmt.Println("\nnodes:")
	for node, hashes := range c.nodeToHashes {
		fmt.Printf("%s: ", node)
		for _, h := range hashes {
			fmt.Printf("%d ", h)
		}
		fmt.Println()
	}
}
