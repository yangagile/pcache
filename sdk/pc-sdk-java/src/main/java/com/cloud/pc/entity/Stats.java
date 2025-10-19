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

package com.cloud.pc.entity;

import com.cloud.pc.utils.FileUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString

public class Stats {
    int cntFail = 0;
    int cntPcp = 0;
    int cntLocal = 0;
    int pcpCacheHit = 0;
    long time = 0;
    long rate = 0; //BYTE/S

    public Stats() {
        time = System.currentTimeMillis();
    }

    public Stats add(Stats other) {
        cntFail += other.getCntFail();
        cntPcp += other.getCntPcp();
        cntLocal += other.getCntLocal();
        pcpCacheHit += other.getPcpCacheHit();
        return this;
    }

    public int addFail() {
        cntFail++;
        return cntFail;
    }

    public int addPcp() {
        cntPcp++;
        return cntPcp;
    }

    public int addLocal() {
        cntLocal++;
        return cntLocal;
    }

    public int addPcpCacheHit(int cnt) {
        pcpCacheHit += cnt;
        return pcpCacheHit;
    }

    public void finish(long size) {
        time = System.currentTimeMillis() - time;
        if (time > 0) {
            rate = size * 1000 / time;
        }
    }

    @Override
    public String toString() {
        return "stats: total " + (cntFail + cntPcp + cntLocal) + " blocks, fail:" + cntFail + " PCP:"
                + cntPcp + "(cache:" + pcpCacheHit + ") local:" + cntLocal + " duration time:" + time + " ms rate:"
                + FileUtils.formatDataSize(rate) + "/S" ;
    }
}
