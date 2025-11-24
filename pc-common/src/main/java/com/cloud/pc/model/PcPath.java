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

package com.cloud.pc.model;

import lombok.Getter;
import lombok.Setter;
import org.apache.hadoop.fs.Path;

@Getter
@Setter
public class PcPath {
    private String bucket;
    private String key;
    private long number;
    private long totalNumber;
    public PcPath(String path) {
        int pos = 0;
        if (path.startsWith(Path.SEPARATOR)) {
            pos = 1;
        }
        int pos2 = path.indexOf(Path.SEPARATOR, pos);
        this.bucket = path.substring(pos, pos2);
        pos = pos2 + 1;
        pos2 = path.lastIndexOf('.');

        this.key = path.substring(pos, pos2);
        String[] cols = path.substring(pos2 + 1).split("_");
        if (cols.length > 1) {
            this.number = Long.parseLong(cols[0]);
            this.totalNumber = Long.parseLong(cols[1]);
        }
    }

    public PcPath(String bucket, String key, long number, long totalNumber) {
        this.bucket = bucket;
        this.key = key;
        this.number = number;
        this.totalNumber = totalNumber;
    }

    @Override
    public String toString() {
        return String.format("%s/%s.%d_%d", bucket, key, number, totalNumber);
    }

    public boolean isSingleFile() {
        return totalNumber == 1;
    }
}
