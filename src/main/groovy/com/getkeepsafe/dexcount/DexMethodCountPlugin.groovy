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

import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.api.TestVariant
import com.android.builder.Version
import com.android.repository.Revision
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

        try {
            Class.forName("com.android.builder.Version")
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("dexcount requires the Android plugin to be configured", e)
        }

        def gradlePluginRevision = Revision.parseRevision(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        def threeOhRevision = Revision.parseRevision("3.0.0")

        Provider provider

        //noinspection ChangeToOperator
        if (gradlePluginRevision.compareTo(threeOhRevision, Revision.PreviewComparison.IGNORE) >= 0) {
            provider = new ThreeOhProvider(project)
        } else {
            provider = new LegacyProvider(project)
        }

        sdkLocation = SdkResolver.resolve(project)
        provider.apply()
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

    abstract class Provider {
        protected Project project
        protected DexMethodCountExtension ext

        protected Provider(Project project) {
            this.project = project
            this.ext = project.extensions.create("dexcount", DexMethodCountExtension)

            // If the user has passed '--stacktrace' or '--full-stacktrace', assume
            // that they are trying to report a dexcount bug.  Help them help us out
            // by printing the current plugin title and version.
            if (GradleApi.isShowStacktrace(project.gradle.startParameter)) {
                ext.printVersion = true
            }
        }

        void apply() {
            DomainObjectCollection<BaseVariant> variants

            if (project.plugins.hasPlugin('com.android.application')) {
                variants = project.android.applicationVariants
            } else if (project.plugins.hasPlugin('com.android.test')) {
                variants = project.android.applicationVariants
            } else if (project.plugins.hasPlugin('com.android.library')) {
                variants = project.android.libraryVariants
            } else {
                throw new IllegalArgumentException('Dexcount plugin requires the Android plugin to be configured')
            }

            variants.all { variant ->
                if (variant instanceof ApplicationVariant) {
                    applyToApplicationVariant((ApplicationVariant) variant)
                } else if (variant instanceof TestVariant) {
                    applyToTestVariant((TestVariant) variant)
                } else if (variant instanceof LibraryVariant) {
                    applyToLibraryVariant((LibraryVariant) variant)
                } else {
                    project.logger.error("dexcount: Don't know how to handle variant ${variant.name} of type ${variant.class}, skipping")
                }
            }
        }

        abstract void applyToApplicationVariant(ApplicationVariant variant)
        abstract void applyToTestVariant(TestVariant variant)
        abstract void applyToLibraryVariant(LibraryVariant variant)

        protected void addDexcountTaskToGraph(Task parentTask, DexMethodCountTask dexcountTask) {
            if (dexcountTask != null && parentTask != null) {
                // Dexcount tasks require that assemble has been run...
                dexcountTask.dependsOn(parentTask)
                dexcountTask.mustRunAfter(parentTask)

                // But assemble should always imply that dexcount runs, unless configured not to.
                if (ext.runOnEachAssemble) {
                    parentTask.finalizedBy(dexcountTask)
                }
            }
        }

        protected DexMethodCountTask createTask(
                BaseVariant variant,
                BaseVariantOutput output,
                @ClosureParams(value = SimpleType, options = ['com.getkeepsafe.dexcount.DexMethodCountTask']) Closure applyInputConfiguration) {
            def slug = variant.name.capitalize()
            def path = "${project.buildDir}/outputs/dexcount/${variant.name}"
            if (variant.outputs.size() > 1) {
                assert output != null
                slug += output.name.capitalize()
                path += "/${output.name}"
            }

            def task = project.tasks.create("count${slug}DexMethods", DexMethodCountTask)
            task.description = "Outputs dex method count for ${variant.name}."
            task.group = 'Reporting'
            task.variantOutputName = slug.uncapitalize()
            task.mappingFile = variant.mappingFile
            task.outputFile = project.file(path + ext.format.extension)
            task.summaryFile = project.file(path + '.csv')
            task.chartDir = project.file(path + 'Chart')
            task.config = ext

            applyInputConfiguration(task)

            return task
        }
    }

    class LegacyProvider extends Provider {
        LegacyProvider(Project project) {
            super(project)
        }

        @Override
        void applyToApplicationVariant(ApplicationVariant variant) {
            applyToApkVariant(variant)
        }

        @Override
        void applyToTestVariant(TestVariant variant) {
            applyToApkVariant(variant)
        }

        @Override
        void applyToLibraryVariant(LibraryVariant variant) {
            variant.outputs.each { output ->
                def aar = output.outputFile
                def task = createTask(variant, output) { t -> t.inputFile = aar }
                addDexcountTaskToGraph(output.assemble, task)
            }
        }

        private void applyToApkVariant(ApkVariant variant) {
            variant.outputs.each { output ->
                def apk = output.outputFile
                def task = createTask(variant, output) { t -> t.inputFile = apk }
                addDexcountTaskToGraph(output.assemble, task)
            }
        }
    }

    class ThreeOhProvider extends Provider {
        ThreeOhProvider(Project project) {
            super(project)
        }

        @Override
        void applyToApplicationVariant(ApplicationVariant variant) {
            applyToApkVariant(variant)
        }

        @Override
        void applyToTestVariant(TestVariant variant) {
            applyToApkVariant(variant)
        }

        @Override
        void applyToLibraryVariant(LibraryVariant variant) {
            def packageTask = variant.packageLibrary
            def dexcountTask = createTask(variant, null) { t -> t.inputFile = packageTask.archivePath }
            addDexcountTaskToGraph(packageTask, dexcountTask)
        }

        private void applyToApkVariant(ApkVariant variant) {
            variant.outputs.each { output ->
                if (output instanceof ApkVariantOutput) {
                    // why wouldn't it be?
                    def apkOutput = (ApkVariantOutput) output
                    def packageDirectory = apkOutput.packageApplication.outputDirectory
                    def task = createTask(variant, apkOutput) { t -> t.inputDirectory = packageDirectory }
                    addDexcountTaskToGraph(apkOutput.packageApplication, task)
                } else {
                    throw new IllegalArgumentException("Unexpected output type for variant ${variant.name}: ${output.class}")
                }
            }
        }
    }
}
