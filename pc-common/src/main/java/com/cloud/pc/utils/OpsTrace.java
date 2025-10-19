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

public class OpsTrace {
    private static ThreadLocal<String> threadLocalOps = ThreadLocal.withInitial(() -> "thread-" + Thread.currentThread().getId());

    public static String get() {
        return "[" + threadLocalOps.get() + "]";
    }

    public static String get(String info) {
        return "[" + threadLocalOps.get() + " " + info + "]";
    }

    public static void set(String ops) {
        threadLocalOps.set("thread-" + + Thread.currentThread().getId() + " " + ops);
    }
}
