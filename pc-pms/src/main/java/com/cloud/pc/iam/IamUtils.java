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

package com.cloud.pc.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Paths;
import java.util.List;

public class IamUtils {
    public static String getSid(String roleArm) {
        int pos =  roleArm.lastIndexOf('/');
        if (pos > 0) {
            return roleArm.substring(pos+1);
        }
        return "";
    }

    public static boolean isCNPartition(String region) {
        return StringUtils.startsWith(region, "cn-");
    }

    public static String concatStsAcl(String region, String sid, String resource, List<String> permissions) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonNode = objectMapper.createObjectNode();

            ArrayNode statementList = objectMapper.createArrayNode();
            ObjectNode aclObject = objectMapper.createObjectNode();
            aclObject.put("Sid", sid);
            aclObject.put("Effect", "Allow");
            ArrayNode actionList = objectMapper.createArrayNode();
            permissions.forEach(permission -> {
                actionList.add("s3:" + permission);
            });
            aclObject.put("Action", actionList);
            String partition = isCNPartition(region) ? "aws-cn" : "aws";
            aclObject.put("Resource", "arn:" + partition + ":s3:::" + resource);
            statementList.add(aclObject);

            jsonNode.put("Version", "2012-10-17");
            jsonNode.putArray("Statement").addAll(statementList);
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String resourceAcl(String bucketName, String path) {
        return Paths.get(bucketName, path).normalize() + "/*";
    }

    public static void checkAction(IamPolicy iamPolicy, String actionPrefix) {
        for (Statement statement : iamPolicy.getStatements()) {
            if (statement.getEffect().equals("Allow")) {
                for (String action: statement.getActions()) {
                    if (action.startsWith(actionPrefix)) {
                        return;
                    }
                }
            }
        }
        throw new RuntimeException("IAM error: the AK does not have the permissions:" + actionPrefix);
    }
}
