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

package com.cloud.pc.meta.impl;

import com.cloud.pc.meta.*;
import com.cloud.pc.service.MetaService;
import com.cloud.pc.utils.ComUtils;
import com.cloud.pc.utils.FileUtils;
import com.cloud.pc.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component("file-loader")
public class FileLoader implements MetaLoader {
    private static final Logger LOG = LoggerFactory.getLogger(FileLoader.class);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode rootNode;
    String metaRoot;

    private int getMaxId(ArrayNode arrayNode) {
        int id = 0;
        for (JsonNode node : arrayNode) {
            int curID = Integer.parseInt(node.get("id").toString());
            if (curID > id) {
                id = curID;
            }
        }
        return id+1;
    }

    public int add(Class type, PcMeta item) throws IOException {
        return 1;
    }

    public int update(Class type, PcMeta item) throws IOException {
        return 1;
    }

    private void loadClassData(ObjectNode objectNode, Class<?> clazz) throws IOException {
        Path file = Paths.get(FileUtils.mergePath(metaRoot, ComUtils.className(clazz)));
        try (InputStream inputStream = Files.newInputStream(file);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            String content = byteArrayOutputStream.toString("UTF-8");
            objectNode.put(ComUtils.className(clazz), mapper.readTree(content));
        } catch (IOException e) {
            LOG.error("exception to load meta file {}", file, e);
        }
    }

    public void init(String metaRoot) throws IOException  {
        this.metaRoot = metaRoot;
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        loadClassData(objectNode, PBucket.class);
        loadClassData(objectNode, Secret.class);
        loadClassData(objectNode, Vendor.class);
        loadClassData(objectNode, VendorBucket.class);
        rootNode = objectNode;
    }

    public void sync(String content) throws IOException {
        rootNode = mapper.readTree(content);
    }

    public List<PcMeta> load(Class type) throws IOException {
        JsonNode node = rootNode.get(ComUtils.className(type));
        if (node != null) {
            return JsonUtils.parseList((ArrayNode)node.get("items"), type);
        }
        return null;
    }

    public String getRootPath() {
        return metaRoot;
    }
}
