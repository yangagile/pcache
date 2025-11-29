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
	"github.com/yangagile/pcache/sdk/pc-sdk-go/utils"
	"time"
)

type PcpManager struct {
	hashRing    *utils.ConsistentHash
	pcpMap      map[string]*utils.PhysicalNode
	Checksum    string
	expiredTime int64
}

func (m *PcpManager) IsExpired() bool {
	return m.expiredTime < time.Now().Unix()
}

func NewPcpManager(expiredTime int64, pcpTable *utils.PcpTable) *PcpManager {
	pcpCache := &PcpManager{
		hashRing:    utils.NewConsistentHash(),
		pcpMap:      make(map[string]*utils.PhysicalNode),
		expiredTime: expiredTime,
	}
	pcpCache.update(pcpTable)
	return pcpCache
}

// update cache
func (c *PcpManager) updateCache(newInfo *utils.PcpTable) {
	tmpMap := make(map[string]*utils.PhysicalNode)

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

	for _, node := range c.pcpMap {
		c.hashRing.RemoveNode(node)
	}

	c.pcpMap = tmpMap
	c.Checksum = newInfo.Checksum
}

func (c *PcpManager) update(pcpTable *utils.PcpTable) {
	if pcpTable != nil && pcpTable.Checksum != c.Checksum {
		c.Checksum = pcpTable.Checksum
		c.updateCache(pcpTable)
	}
}

func (c *PcpManager) get(key string) string {
	return c.hashRing.GetNode(key)
}
