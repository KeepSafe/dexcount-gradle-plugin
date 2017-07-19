/*
 * Copyright 2016 KeepSafe Software
 * Copyright 2014 Jake Wharton
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.getkeepsafe.dexcount.sdkresolver

/**
 * This is from Jake Wharton's SDK manager:
 * https://github.com/JakeWharton/sdk-manager-plugin
 */
interface System {
    String env(String name)
    String property(String key)
    String property(String key, String defaultValue)

    static final class Real implements System {
        @Override String env(String name) {
            return java.lang.System.getenv(name)
        }

        @Override String property(String key) {
            return java.lang.System.getProperty(key)
        }

        @Override String property(String key, String defaultValue) {
            return java.lang.System.getProperty(key, defaultValue)
        }
    }
}
