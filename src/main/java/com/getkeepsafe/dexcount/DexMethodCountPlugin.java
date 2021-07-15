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

import com.android.repository.Revision;
import com.android.repository.Revision.PreviewComparison;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class DexMethodCountPlugin implements Plugin<Project> {
    private static final String VERSION_3_ZERO_FIELD = "com.android.builder.Version"; // <= 3.0
    private static final String VERSION_3_ONE_FIELD = "com.android.builder.model.Version"; // > 3.1
    private static final String AGP_VERSION_FIELD = "ANDROID_GRADLE_PLUGIN_VERSION";
    private static final String ANDROID_EXTENSION_NAME = "android";

    private static final GradleVersion MIN_GRADLE_VERSION = new GradleVersion(6, 0);
    private static final Revision MIN_AGP_VERSION = new Revision(3, 4, 0);

    @Override
    public void apply(@NotNull Project project) {
        GradleVersion gradleVersion = ProjectUtils.gradleVersion(project);
        if (gradleVersion.compareTo(MIN_GRADLE_VERSION) < 0) {
            project.getLogger().error("dexcount requires Gradle {} or above", MIN_GRADLE_VERSION);
            return;
        }

        String gradlePluginVersion = null;
        Exception exception = null;

        try {
            gradlePluginVersion = Class.forName(VERSION_3_ZERO_FIELD).getDeclaredField(AGP_VERSION_FIELD).get(this).toString();
        } catch (Exception e) {
            exception = e;
        }

        try {
            gradlePluginVersion = Class.forName(VERSION_3_ONE_FIELD).getDeclaredField(AGP_VERSION_FIELD).get(this).toString();
        } catch (Exception e) {
            exception = e;
        }

        if (gradlePluginVersion == null && exception != null) {
            throw new IllegalStateException("dexcount requires the Android plugin to be configured", exception);
        } else if (gradlePluginVersion == null) {
            throw new IllegalStateException("dexcount requires the Android plugin to be configured");
        }

        DexCountExtension ext = project.getExtensions().create("dexcount", DexCountExtension.class);

        // If the user has passed '--stacktrace' or '--full-stacktrace', assume
        // that they are trying to report a dexcount bug.  Help them help us out
        // by printing the current plugin title and version.
        if (project.getGradle().getStartParameter().getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            ext.getPrintVersion().set(true);
        }

        // We need to do this check *after* we create the 'dexcount' Gradle extension.
        // If we bail on instant run builds any earlier, then the build will break
        // for anyone configuring dexcount due to the extension not being registered.
        if (ProjectUtils.isInstantRun(project)) {
            project.getLogger().info("Instant Run detected; disabling dexcount");
            return;
        }

        Revision gradlePluginRevision = Revision.parseRevision(gradlePluginVersion, Revision.Precision.PREVIEW);
        if (gradlePluginRevision.compareTo(new JavaOnlyApplicator.Factory().getMinimumRevision()) > 0
                && gradlePluginRevision.compareTo(MIN_AGP_VERSION) < 0) {
            project.getLogger().error("dexcount requires Android Gradle Plugin {} or above", MIN_AGP_VERSION);
            return;
        }

        List<TaskApplicator.Factory> factories = Arrays.asList(
            new SevenOhApplicator.Factory(),
            new FourTwoApplicator.Factory(),
            new FourOneApplicator.Factory(),
            new ThreeSixApplicator.Factory(),
            new ThreeFourApplicator.Factory(),
            new JavaOnlyApplicator.Factory()
        );

        for (TaskApplicator.Factory factory : factories) {
            if (gradlePluginRevision.compareTo(factory.getMinimumRevision(), PreviewComparison.IGNORE) < 0) {
                continue;
            }

            TaskApplicator applicator = factory.create(ext, project);
            applicator.apply();
            return;
        }

        project.getLogger().error("No dexcount TaskApplicator configured for Gradle version {} and AGP version {}", gradleVersion, gradlePluginRevision);
    }
}
