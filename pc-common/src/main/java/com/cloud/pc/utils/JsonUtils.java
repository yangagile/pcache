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

package com.cloud.pc.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static Logger LOG = LoggerFactory.getLogger(JsonUtils.class);

    static {
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static<T> String toJson(T object) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    public static<T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            LOG.error("[pc-sdk] JsonUtil.fromJson() JSON反序列化异常", e);
            return null;
        }
    }
    public static<T> T fromJson(byte[] json, Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.readValue(json, clazz);
    }

    public static<T> JsonNode toJsonNode(T object) throws JsonProcessingException {
        return OBJECT_MAPPER.valueToTree(object);
    }

    public static <T> ArrayList<T> parseList(ArrayNode array, Class<T> clazz) {
        ArrayList<T> list = new ArrayList<>();
        if (array != null) {
            array.forEach(node -> list.add(OBJECT_MAPPER.convertValue(node, clazz)));
        }
        return list;
    }

    public static <T> List<T> parseList(String jsonString, Class<T> clazz)
            throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(jsonString,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
    }
}
