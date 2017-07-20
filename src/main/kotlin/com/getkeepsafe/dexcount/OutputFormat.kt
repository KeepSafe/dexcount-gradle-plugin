/*
 * Copyright (C) 2015-2016 KeepSafe Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.getkeepsafe.dexcount

/**
 * Specifies what format the task output should take.
 */
enum class OutputFormat(val extension: String) {
    /**
     * Specifies that method counts will be printed in a flat list of packages.
     */
    LIST(".txt"),

    /**
     * Specifies that the output will be pretty-printed as an tree.
     */
    TREE(".txt"),

    /**
     * Specifies that the output will be a pretty-printed JSON object.
     */
    JSON(".json"),

    /**
     * Specifies that output will be a YAML document.
     */
    YAML(".yml")
}
