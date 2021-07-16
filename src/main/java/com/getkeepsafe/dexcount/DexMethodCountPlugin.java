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
import com.android.repository.Revision.Precision;
import com.getkeepsafe.dexcount.plugin.TaskApplicator;
import com.getkeepsafe.dexcount.plugin.TaskApplicators;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DexMethodCountPlugin implements Plugin<Project> {
    private static final String VERSION_3_ZERO_FIELD = "com.android.builder.Version"; // <= 3.0
    private static final String VERSION_3_ONE_FIELD = "com.android.builder.model.Version"; // > 3.1
    private static final String VERSION_7_0_FIELD = "com.android.Version"; // >= 7.0
    private static final String AGP_VERSION_FIELD = "ANDROID_GRADLE_PLUGIN_VERSION";

    private static final GradleVersion MIN_GRADLE_VERSION = new GradleVersion(6, 0);
    private static final Revision MIN_AGP_VERSION = new Revision(3, 4, 0);

    @Override
    public void apply(@NotNull Project project) {
        GradleVersion gradleVersion = ProjectUtils.gradleVersion(project);
        if (gradleVersion.compareTo(MIN_GRADLE_VERSION) < 0) {
            project.getLogger().error("dexcount requires Gradle {} or above", MIN_GRADLE_VERSION);
            return;
        }

        Revision gradlePluginRevision = getCurrentAgpRevision();
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

        if (gradlePluginRevision.compareTo(MIN_AGP_VERSION) < 0) {
            project.getLogger().error("dexcount requires Android Gradle Plugin {} or above", MIN_AGP_VERSION);
            return;
        }

        Optional<TaskApplicator.Factory> maybeFactory = TaskApplicators.getFactory(gradlePluginRevision);
        if (maybeFactory.isPresent()) {
            TaskApplicator applicator = maybeFactory.get().create(project, ext);
            applicator.apply();
        } else {
            project.getLogger().error(
                "No dexcount TaskApplicator configured for Gradle version {} and AGP version {}",
                gradleVersion,
                gradlePluginRevision);
        }
    }

    private Revision getCurrentAgpRevision() throws RuntimeException {
        List<String> versionClassNames = Arrays.asList(VERSION_7_0_FIELD, VERSION_3_ONE_FIELD, VERSION_3_ZERO_FIELD);
        Exception thrown = null;

        for (String className : versionClassNames) {
            try {
                Class<?> versionClass = Class.forName(className);
                Field agpVersionField = versionClass.getDeclaredField(AGP_VERSION_FIELD);
                String agpVersion = agpVersionField.get(null).toString();
                return Revision.parseRevision(agpVersion, Precision.PREVIEW);
            } catch (Exception e) {
                thrown = e;
            }
        }

        throw new IllegalStateException("dexcount requires the Android plugin to be configured", thrown);
    }
}
