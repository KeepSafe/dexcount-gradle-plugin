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

import org.gradle.api.Project

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
