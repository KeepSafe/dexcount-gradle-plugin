/*
 * Copyright (C) 2016-2017 KeepSafe Software
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

import org.gradle.StartParameter
import org.gradle.api.Project

/**
 * Return `true` if Gradle was launched with `--stacktrace`,
 * otherwise `false`.
 *
 * Gradle broke compatibility between 2.13 and 2.14 by repackaging
 * the `ShowStacktrace` enum; consequently we need to refer to
 * it by string name only.
 */
val StartParameter.isShowStacktrace: Boolean
    get() {
        val getShowStackTrace = StartParameter::class.java.getMethod("getShowStacktrace").also { it.isAccessible = true }
        val stacktrace = getShowStackTrace(this) as Enum<*>
        return "INTERNAL_EXCEPTIONS" != stacktrace.name
    }

/**
 * `true` if this project is compiled with Instant Run support, otherwise `false`.
 */
val Project.isInstantRun: Boolean
    get() {
        val optionString = properties["android.optional.compilation"] as? String
        val optionList = optionString?.split(",")?.map(String::trim) ?: emptyList()
        return optionList.any { it == "INSTANT_DEV" }
    }

/**
 * `true` if the current JVM version is 1.8 or above, otherwise `false`.
 */
val isAtLeastJavaEight: Boolean
    get() {
        var version = System.getProperty("java.version")
        if (version == null) {
            // All JVMs provide this property... what's going on?
            return false
        }

        // Java version strings are something like 1.8.0_65; we don't
        // care about the third component, if it exists.  Skip it.
        val indexOfDecimal = version.indexOf('.').let {
            if (it != -1) {
                version.indexOf('.', it + 1)
            } else {
                it
            }
        }

        if (indexOfDecimal != -1) {
            version = version.substring(0, indexOfDecimal)
        }

        return try {
            val numericVersion = java.lang.Double.parseDouble(version)
            numericVersion >= 1.8
        } catch (ignored: NumberFormatException) {
            // Invalid Java version number; who knows.
            false
        }
    }
