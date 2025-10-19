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
	"fmt"
	"testing"
)

func Test_new_ConsistentHash(t *testing.T) {

	ch := NewConsistentHash()

	ch.AddNode(&PhysicalNode{Host: "node1", Priority: 0.5})
	ch.AddNode(&PhysicalNode{Host: "node2", Priority: 0.2})

	fmt.Println("Host 'user1' belongs to:", ch.GetNode("user1"))

	ch.RemoveNode(&PhysicalNode{Host: "node1"})

	fmt.Println("Host 'user1' after removal belongs to:", ch.GetNode("user1"))
}
