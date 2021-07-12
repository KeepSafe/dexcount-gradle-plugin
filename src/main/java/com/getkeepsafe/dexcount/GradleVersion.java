/*
 * Copyright (C) 2015-2021 KeepSafe Software
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
package com.getkeepsafe.dexcount;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class GradleVersion implements Comparable<GradleVersion> {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+).*$");

    private final int major;
    private final int minor;

    public GradleVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public static GradleVersion parse(String versionString) {
        Matcher matcher = VERSION_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Gradle version: " + versionString);
        }
        String majorString = matcher.group(1);
        String minorString = matcher.group(2);
        return new GradleVersion(Integer.parseInt(majorString), Integer.parseInt(minorString));
    }

    @Override
    public int compareTo(@NotNull GradleVersion other) {
        int majorCmp = Integer.compare(major, other.major);
        if (majorCmp != 0) {
            return majorCmp;
        }
        return Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return "" + major + "." + minor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GradleVersion that = (GradleVersion) o;

        if (major != that.major) return false;
        return minor == that.minor;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        return result;
    }
}
