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

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.getkeepsafe.dexcount.sdkresolver.SdkResolver
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project

class DexMethodCountPlugin implements Plugin<Project> {
    static File sdkLocation = SdkResolver.resolve(null)

    @Override
    void apply(Project project) {
        if (!isAtLeastJavaEight()) {
            project.logger.error("Java 8 or above is *STRONGLY* recommended - dexcount may not work properly on Java 7 or below!")
        }

        sdkLocation = SdkResolver.resolve(project)

        if (project.plugins.hasPlugin('com.android.application')) {
            applyAndroid(project, (DomainObjectCollection<BaseVariant>) project.android.applicationVariants)
        } else if (project.plugins.hasPlugin('com.android.test')) {
            applyAndroid(project, (DomainObjectCollection<BaseVariant>) project.android.applicationVariants)
        } else if (project.plugins.hasPlugin('com.android.library')) {
            applyAndroid(project, (DomainObjectCollection<BaseVariant>) project.android.libraryVariants)
        } else {
            throw new IllegalArgumentException('Dexcount plugin requires the Android plugin to be configured')
        }
    }

    static boolean isAtLeastJavaEight() {
        String version = System.properties["java.version"]
        if (version == null) {
            // All JVMs provide this property... what's going on?
            return false
        }

        // Java version strings are something like 1.8.0_65; we don't
        // care about the third component, if it exists.  Skip it.
        def indexOfDecimal = version.indexOf('.')
        indexOfDecimal = version.indexOf('.', indexOfDecimal + 1)

        if (indexOfDecimal != -1) {
            version = version.substring(0, indexOfDecimal)
        }

        try {
            def numericVersion = Double.parseDouble(version)
            return numericVersion >= 1.8
        } catch (NumberFormatException ignored) {
            // Invalid Java version number; who knows.
            return false
        }
    }

    static applyAndroid(Project project, DomainObjectCollection<BaseVariant> variants) {
        project.extensions.create('dexcount', DexMethodCountExtension)

        variants.all { variant ->
            variant.outputs.each { output ->
                applyToVariantOutput(project, variant, output)
            }
        }
    }

    static applyToVariantOutput(Project project, BaseVariant variant, BaseVariantOutput output) {
        def slug = variant.name.capitalize()
        def path = "${project.buildDir}/outputs/dexcount/${variant.name}"
        if (variant.outputs.size() > 1) {
            slug += output.name.capitalize()
            path += "/${output.name}"
        }

        def ext = project.extensions['dexcount'] as DexMethodCountExtension
        def format = ext.format

        def isInstantRun = project.properties["android.optional.compilation"] == "INSTANT_DEV"
        if (isInstantRun && !ext.enableForInstantRun) {
            return
        }

        // If the user has passed '--stacktrace' or '--full-stacktrace', assume
        // that they are trying to report a dexcount bug.  Help us help them out
        // by printing the current plugin title and version.
        if (GradleApi.isShowStacktrace(project.gradle.startParameter)) {
            ext.printVersion = true
        }

        def task = project.tasks.create("count${slug}DexMethods", DexMethodCountTask)
        task.description = "Outputs dex method count for ${variant.name}."
        task.group = 'Reporting'
        task.apkOrDex = output
        task.mappingFile = variant.mappingFile
        task.outputFile = project.file(path + format.extension)
        task.summaryFile = project.file(path + '.csv')
        task.chartDir = project.file(path + 'Chart')
        task.config = ext

        // Dexcount tasks require that assemble has been run...
        task.dependsOn(variant.assemble)
        task.mustRunAfter(variant.assemble)

        // But assemble should always imply that dexcount runs, unless configured not to.
        def runOnEachAssemble = ext.runOnEachAssemble
        if (runOnEachAssemble) {
            variant.assemble.finalizedBy(task)
        }
    }
}
