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

import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.getkeepsafe.dexcount.sdkresolver.SdkResolver
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class DexMethodCountPlugin implements Plugin<Project> {
    static File sdkLocation = SdkResolver.resolve(null)

    @Override
    void apply(Project project) {
        if (!isAtLeastJavaEight()) {
            project.logger.error("Java 8 or above is *STRONGLY* recommended - dexcount may not work properly on Java 7 or below!")
        }

        if (isInstantRun(project)) {
            project.logger.info("Instant Run detected; disabling dexcount")
            return
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

    static boolean isInstantRun(Project project) {
        def compilationOptionString = project.properties["android.optional.compilation"] ?: ""
        def compilationOptionList = compilationOptionString.split(",")
        return compilationOptionList.any { it == "INSTANT_DEV" }
    }

    static applyAndroid(Project project, DomainObjectCollection<BaseVariant> variants) {
        project.extensions.create('dexcount', DexMethodCountExtension)

        variants.all { variant ->
            variant.outputs.each { output ->
                def ext = project.extensions['dexcount'] as DexMethodCountExtension

                Task parentTask = null
                Task dexcountTask = null

                if (output instanceof ApkVariantOutput) {
                    def apkOutput = (ApkVariantOutput) output
                    parentTask = apkOutput.packageApplication
                    dexcountTask = createDexCountTask(project, ext, variant, output) { task ->
                        try {
                            // BuildTools >= 3.0.0
                            task.inputDirectory = apkOutput.packageApplication.outputDirectory
                        } catch (MissingPropertyException ignored) {
                            // Build Tools < 3.0.0
                            task.inputFile = apkOutput.packageApplication.outputFile
                        }
                    }
                } else {
                    project.logger.error("dexcount: Don't know how to handle variant ${variant.name} of type ${variant.class}, skipping")
                }

                if (dexcountTask != null && parentTask != null) {
                    // Dexcount tasks require that assemble has been run...
                    dexcountTask.dependsOn(parentTask)
                    dexcountTask.mustRunAfter(parentTask)

                    // But assemble should always imply that dexcount runs, unless configured not to.
                    def runOnEachAssemble = ext.runOnEachAssemble
                    if (runOnEachAssemble) {
                        parentTask.finalizedBy(dexcountTask)
                    }
                }
            }
        }
    }

    static Task createDexCountTask(
            Project project,
            DexMethodCountExtension ext,
            BaseVariant variant,
            BaseVariantOutput output,
            @ClosureParams(value = SimpleType, options = ['com.getkeepsafe.dexcount.DexMethodCountTask']) Closure applyOutputConfiguration) {
        def slug = variant.name.capitalize()
        def path = "${project.buildDir}/outputs/dexcount/${variant.name}"
        if (variant.outputs.size() > 1) {
            slug += output.name.capitalize()
            path += "/${output.name}"
        }

        def format = ext.format

        // If the user has passed '--stacktrace' or '--full-stacktrace', assume
        // that they are trying to report a dexcount bug.  Help us help them out
        // by printing the current plugin title and version.
        if (GradleApi.isShowStacktrace(project.gradle.startParameter)) {
            ext.printVersion = true
        }

        def task = project.tasks.create("count${slug}DexMethods", DexMethodCountTask)
        task.description = "Outputs dex method count for ${variant.name}."
        task.group = 'Reporting'
        task.variantOutputName = output.name
        task.mappingFile = variant.mappingFile
        task.outputFile = project.file(path + format.extension)
        task.summaryFile = project.file(path + '.csv')
        task.chartDir = project.file(path + 'Chart')
        task.config = ext

        applyOutputConfiguration(task)

        return task
    }
}
