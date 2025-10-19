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

package com.cloud.pc.meta;

import java.io.IOException;
import java.util.List;

public interface MetaLoader {
    void init(String file) throws IOException;
    void sync(String content) throws IOException;

    List<PcMeta> load(Class type) throws IOException;

    int add(Class type, PcMeta item) throws IOException;

    int update(Class type, PcMeta item) throws IOException;

    String getRootPath();
}
