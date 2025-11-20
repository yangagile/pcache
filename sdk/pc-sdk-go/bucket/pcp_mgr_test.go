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
	"testing"
)

func Test_pcp_PutGet(t *testing.T) {
	// 创建 PcpCache 实例
	pcpCache := NewPcpCache(30, func(checksum string) *utils.PcpTable {
		// 这里实现实际的 API 调用逻辑
		// 返回示例数据
		return &utils.PcpTable{
			Checksum: "new_checksum",
			PcpList: []*utils.PhysicalNode{
				{Host: "node1", Priority: 0.5},
				{Host: "node2", Priority: 0.2},
			},
		}
	})

	// 使用缓存获取节点
	key := "user123"
	node := pcpCache.Get(key)
	println("Host", key, "belongs to node:", node)
}
