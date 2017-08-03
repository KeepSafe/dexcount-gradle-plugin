/*
 * Copyright 2016-2017 KeepSafe Software
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
    fun env(name: String): String?
    fun property(key: String): String?
    fun property(key: String, defaultValue: String): String

    class Real: System {
        override fun env(name: String): String? {
            return java.lang.System.getenv(name)
        }

        override fun property(key: String): String? {
            return java.lang.System.getProperty(key)
        }

        override fun property(key: String, defaultValue: String): String {
            return java.lang.System.getProperty(key, defaultValue)!!
        }
    }
}
