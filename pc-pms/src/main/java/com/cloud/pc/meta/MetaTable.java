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

import com.cloud.pc.utils.ComUtils;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
public class MetaTable {

    @JsonIgnore
    private final Class type;
    @JsonIgnore
    private MetaLoader loader;
    @JsonIgnore
    private volatile Map<String, Integer> keyMap;
    @JsonIgnore
    private boolean changed = false;

    private volatile List<PcMeta> items;
    private String checksum;
    private long lastUpdateTime = 0;

    public MetaTable(Class type, MetaLoader loader) {
        this.type = type;
        this.loader = loader;
    }

    public <T>List<T> getItems(Class<T> targetClass) {
        List<T> subList = new ArrayList<>();
        return items.stream()
                .filter(targetClass::isInstance)
                .map(targetClass::cast)
                .collect(Collectors.toList());
    }

    public boolean isEmpty() {
        if (items == null) {
            return true;
        } else {
            return items.isEmpty();
        }
    }

    public void updateAll() throws Exception{
        List<PcMeta> its = loader.load(type);
        if (its != null) {
            updateAll(its);
        }
    }

    private synchronized void updateAll(List<PcMeta> its) throws Exception{
        String check = ComUtils.checksumList(its);
        if(!check.equals(checksum)) {
            Map<String, Integer> tmpMap = new HashMap<>();
            for (int i = 0; i < its.size(); i++) {
                if (its.get(i).getLastUpdateTime() > lastUpdateTime) {
                    lastUpdateTime = its.get(i).getLastUpdateTime();
                }
                tmpMap.put(its.get(i).getKey(), i);
            }
            keyMap = tmpMap;
            this.checksum = check;
            this.items = its;
            this.changed = false;
        }
    }

    @JsonIgnore
    public PcMeta get(String key) {
        return items.get(keyMap.get(key));
    }
    private int getId() {
        int id = 0;
        for (PcMeta item : items) {
            if (item.getId() != null && item.getId() > id) {
                id = item.getId();
            }
        }
        return id+1;
    }

    public synchronized PcMeta add(PcMeta item) throws Exception {
        if (items == null) {
            items = new ArrayList<>();
        }
        if (item.getId() == null) {
            item.setId(getId());
        }
        items.add(item);
        if (keyMap == null) {
            keyMap = new HashMap<>();
        }

        keyMap.put(item.getKey(), items.size()-1);
        checksum = ComUtils.checksumList(items);
        lastUpdateTime = new Date().getTime();
        changed = true;
        loader.add(type, item);
        dump2File(loader.getRootPath());
        return item;
    }

    public synchronized int update(PcMeta item) throws Exception {
        for (PcMeta meta :items) {
            if (meta.getKey().equals(item.getKey())) {
                meta.getLastUpdateTime();
            }
        }
        checksum = ComUtils.checksumList(items);
        lastUpdateTime = new Date().getTime();
        changed = true;
        loader.update(type, item);
        dump2File(loader.getRootPath());
        return 1;
    }

    @JsonIgnore
    public String getTableName() {
        return ComUtils.className(type);
    }

    public void dump2File(String metaRoot) throws IOException {
        if(StringUtils.isNotBlank(metaRoot)) {
            Path filePath = Paths.get(metaRoot, getTableName());
            FileUtils.mkParentDir(filePath);
            FileUtils.dump2File(filePath.toString(), JsonUtils.toJsonNode(this).toString());
        }
    }

    public void sync(MetaLoader loader) throws Exception{
        List<PcMeta> its = loader.load(type);
        updateAll(its);
        dump2File(loader.getRootPath());
    }
}
