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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IamPolicy {
    @JsonProperty("Version")
    private String version = "2025-08-05";

    @JsonProperty("Statement")
    private List<Statement> statements;

    public static IamPolicy createS3BucketPolicy(String bucketName) {
        Statement statement = new Statement()
            .withEffect("Allow")
            .withActions("s3:*")
            .withResources(
                    "arn:aws:s3:::" + bucketName + "/*"
            );
        return new IamPolicy().withStatements(Arrays.asList(statement));
    }


    public IamPolicy withStatements(List<Statement> statements) {
        this.statements = statements;
        return this;
    }
    public void addStatements(Statement statement) {
        if (this.statements == null) {
            this.statements = new ArrayList<>();
        }
        this.statements.add(statement);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<Statement> getStatements() {
        return statements;
    }

    public void setStatements(List<Statement> statements) {
        this.statements = statements;
    }
}



