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

/**
 * Represents a Gradle version number.
 *
 * Despite its obviousness and the seemingly-public package "org.gradle.util",
 * the built-in [org.gradle.util.GradleVersion] is in fact an internal class, and
 * I really can't be bothered trying to fix things whenever Gradle decides to break
 * their customers yet again by repackaging an obviously-useful utility.
 *
 * Android tools define [com.android.ide.common.repository.GradleVersion], but I
 * can't find anything about in what version that class was introduced or whether
 * it's a stable API.
 *
 * So, here we are, reinventing the wheel yet again.
 *
 * No, I'm not still bitter about StyledTextOutput, why do you ask?
 */
data class GradleVersion(
    val major: Int,
    val minor: Int
) : Comparable<GradleVersion> {
    companion object {
        private val versionExpr = Regex("(\\d+)\\.(\\d+).*$")

        @JvmStatic fun parse(versionString: String): GradleVersion {
            val matchResult = versionExpr.matchEntire(versionString)
            check(matchResult != null) { "Invalid Gradle version: $versionString" }
            val (major, minor) = matchResult.destructured
            return GradleVersion(major.toInt(), minor.toInt())
        }
    }

    override fun compareTo(other: GradleVersion): Int {
        val majorCmp = major.compareTo(other.major)
        if (majorCmp != 0) {
            return majorCmp
        }
        return minor.compareTo(other.minor)
    }

    override fun toString(): String {
        return "$major.$minor"
    }
}

val Project.gradleVersion: GradleVersion
    get() = GradleVersion.parse(gradle.gradleVersion)
