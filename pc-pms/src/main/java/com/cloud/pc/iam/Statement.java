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

import com.cloud.pc.model.PcPermission;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class Statement {
    @JsonProperty("Effect")
    private String effect;

    @JsonProperty("Action")
    private List<String> actions = new ArrayList<>();

    @JsonProperty("Resource")
    private List<String> resource = new ArrayList<>();

    public Statement withEffect(String effect) {
        this.effect = effect;
        return this;
    }

    public Statement withActions(String... actions) {
        this.actions = Arrays.asList(actions);
        return this;
    }

    public Statement withActions(List<PcPermission> permissions) {
        for (PcPermission permission : permissions) {
            actions.add("s3:" + permission.toString());
        }
        return this;
    }

    public Statement withResources(String... resources) {
        for (String res : resources) {
            this.resource.add("arn:aws:s3:::" + res + "/*");
        }
        return this;
    }
}
