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

package bucket

import (
	"sync"
	"testdisk/utils"
	"time"
)

// PcpTable 包含节点列表和校验和

// PcpCache 缓存结构
type PcpCache struct {
	hashRing      *utils.ConsistentHash
	checksum      string
	pcpMap        map[string]*utils.PhysicalNode
	updateTime    int64
	mutex         sync.RWMutex
	cacheDuration int64                                 // 缓存持续时间（毫秒）
	fetchFunc     func(checksum string) *utils.PcpTable // 获取节点信息的函数
}

// NewPcpCache 创建新的 PcpCache 实例
func NewPcpCache(cacheSeconds int64, fetchFunc func(string) *utils.PcpTable) *PcpCache {
	pcpCache := &PcpCache{
		hashRing:      utils.NewConsistentHash(),
		pcpMap:        make(map[string]*utils.PhysicalNode),
		cacheDuration: cacheSeconds * 1000, // 转换为毫秒
		fetchFunc:     fetchFunc,
	}
	pcpCache.update()
	return pcpCache
}

// updateCache 更新缓存
func (c *PcpCache) updateCache(newInfo *utils.PcpTable) {
	tmpMap := make(map[string]*utils.PhysicalNode)

	// 处理新节点或更新的节点
	for _, node := range newInfo.PcpList {
		if lastNode, exists := c.pcpMap[node.Host]; exists {
			if node.Priority != lastNode.Priority {
				c.hashRing.RemoveNode(lastNode)
				c.hashRing.AddNode(node)
			}
		} else {
			c.hashRing.AddNode(node)
		}
		tmpMap[node.Host] = node
		delete(c.pcpMap, node.Host)
	}

	// 删除不再存在的节点
	for _, node := range c.pcpMap {
		c.hashRing.RemoveNode(node)
	}

	c.pcpMap = tmpMap
	c.checksum = newInfo.Checksum
}

func (c *PcpCache) update() {
	pcpTable := c.fetchFunc(c.checksum)
	if pcpTable == nil {
		return
	}
	// 获取写锁进行更新
	c.mutex.Lock()
	defer c.mutex.Unlock()

	c.updateTime = time.Now().UnixMilli()
	if pcpTable.Checksum != c.checksum {
		c.checksum = pcpTable.Checksum
		c.updateCache(pcpTable)
	}
}

// Get 获取 key 对应的节点
func (c *PcpCache) Get(key string) string {
	c.mutex.RLock()
	defer c.mutex.RUnlock()

	currentTime := time.Now().UnixMilli()
	if currentTime-c.updateTime < c.cacheDuration {
		go c.update()
	}

	node := c.hashRing.GetNode(key)
	return node
}
