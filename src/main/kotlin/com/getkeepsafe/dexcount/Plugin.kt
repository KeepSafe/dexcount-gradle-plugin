/*
 * Copyright (C) 2015-2017 KeepSafe Software
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

import com.android.build.gradle.*
import com.android.build.gradle.api.*
import com.android.builder.Version
import com.android.repository.Revision
import com.getkeepsafe.dexcount.sdkresolver.SdkResolver
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import kotlin.reflect.KClass

val Project.isInstantRun: Boolean
    get() {
        val compilationOptionString = project.properties["android.optional.compilation"] as? String ?: ""
        val compilationOptionList = compilationOptionString.split(",").map(String::trim)
        return compilationOptionList.any { it == "INSTANT_DEV" }
    }

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

open class DexMethodCountPlugin: Plugin<Project> {
    companion object {
        var sdkLocation: File? = SdkResolver.resolve(null)
    }

    override fun apply(project: Project) {
        if (!isAtLeastJavaEight) {
            project.logger.error("Java 8 or above is *STRONGLY* recommended - dexcount may not work properly on Java 7 or below!")
        }

        try {
            Class.forName("com.android.builder.Version")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("dexcount requires the Android plugin to be configured", e)
        }

        sdkLocation = SdkResolver.resolve(project)

        val gradlePluginRevision = Revision.parseRevision(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        val threeOhRevision = Revision.parseRevision("3.0.0")

        val isBuildTools3 = gradlePluginRevision.compareTo(threeOhRevision, Revision.PreviewComparison.IGNORE) >= 0

        val provider = if (isBuildTools3) {
            ThreeOhProvider(project)
        } else {
            LegacyProvider(project)
        }

        provider.apply()
    }
}

abstract class TaskProvider(
        protected val project: Project
) {
    private val ext: DexMethodCountExtension = project.extensions.create(
            "dexcount", DexMethodCountExtension::class.java).apply {
        // If the user has passed '--stacktrace' or '--full-stacktrace', assume
        // that they are trying to report a dexcount bug.  Help them help us out
        // by printing the current plugin title and version.
        if (GradleApi.isShowStacktrace(project.gradle.startParameter)) {
            printVersion = true
        }
    }

    fun apply() {
        // We need to do this check *after* we create the 'dexcount' Gradle extension.
        // If we bail on instant run builds any earlier, then the build will break
        // for anyone configuring dexcount due to the extension not being registered.
        if (project.isInstantRun) {
            project.logger.info("Instant Run detected; disabling dexcount")
            return
        }

        val variants: DomainObjectCollection<out BaseVariant> = when {
            project.plugins.hasPlugin("com.android.application") -> {
                val ext = project.extensions.findByType(AppExtension::class.java)
                ext.applicationVariants
            }

            project.plugins.hasPlugin("com.android.test") -> {
                val ext = project.extensions.findByType(TestExtension::class.java)
                ext.applicationVariants
            }

            project.plugins.hasPlugin("com.android.library") -> {
                val ext = project.extensions.findByType(LibraryExtension::class.java)
                ext.libraryVariants
            }

            else -> throw IllegalArgumentException("Dexcount plugin requires the Android plugin to be configured")
        }

        variants.all { variant ->
            when (variant) {
                is ApplicationVariant -> applyToApplicationVariant(variant)
                is TestVariant -> applyToTestVariant(variant)
                is LibraryVariant -> applyToLibraryVariant(variant)
                else ->
                    project.logger.error("dexcount: Don't know how to handle variant ${variant.name} of type ${variant::class}, skipping")
            }
        }
    }

    abstract fun applyToApplicationVariant(variant: ApplicationVariant)
    abstract fun applyToTestVariant(variant: TestVariant)
    abstract fun applyToLibraryVariant(variant: LibraryVariant)

    protected fun addDexcountTaskToGraph(parentTask: Task, dexcountTask: DexMethodCountTaskBase) {
        // Dexcount tasks require that their parent task has been run...
        dexcountTask.dependsOn(parentTask)
        dexcountTask.mustRunAfter(parentTask)

        // But package should always imply that dexcount runs, unless configured not to.
        if (ext.runOnEachPackage) {
            parentTask.finalizedBy(dexcountTask)
        }
    }

    protected fun <T : DexMethodCountTaskBase> createTask(
            taskClass: KClass<T>,
            variant: BaseVariant,
            output: BaseVariantOutput?,
            applyInputConfiguration: (T) -> Unit): DexMethodCountTaskBase  {
        var slug = variant.name.capitalize()
        var path = "${project.buildDir}/outputs/dexcount/${variant.name}"
        var outputName = variant.name
        if (variant.outputs.size > 1) {
            if (output == null) { throw AssertionError("Output should never be null here") }
            slug += output.name.capitalize()
            path += "/${output.name}"
            outputName = output.name
        }

        return project.tasks.create("count${slug}DexMethods", taskClass.java).apply {
            description       = "Outputs dex method count for ${variant.name}."
            group             = "Reporting"
            variantOutputName = outputName
            mappingFile       = variant.mappingFile
            outputFile        = project.file(path + (ext.format as OutputFormat).extension)
            summaryFile       = project.file(path + ".csv")
            chartDir          = project.file(path + "Chart")
            config            = ext

            applyInputConfiguration(this)
        }
    }
}

class LegacyProvider(project: Project): TaskProvider(project) {
    override fun applyToApplicationVariant(variant: ApplicationVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToTestVariant(variant: TestVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToLibraryVariant(variant: LibraryVariant) {
        variant.outputs.all { output ->
            val task = createTask(LegacyMethodCountTask::class, variant, output) { t -> t.variantOutput = output }
            addDexcountTaskToGraph(output.assemble, task)
        }
    }

    private fun applyToApkVariant(variant: ApkVariant) {
        variant.outputs.all { output ->
            val task = createTask(LegacyMethodCountTask::class, variant, output) { t -> t.variantOutput = output }
            addDexcountTaskToGraph(output.assemble, task)
        }
    }
}

class ThreeOhProvider(project: Project): TaskProvider(project) {
    override fun applyToApplicationVariant(variant: ApplicationVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToTestVariant(variant: TestVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToLibraryVariant(variant: LibraryVariant) {
        val packageTask = variant.packageLibrary
        val dexcountTask = createTask(ModernMethodCountTask::class, variant, null) { t -> t.inputDirectory = packageTask.archivePath }
        addDexcountTaskToGraph(packageTask, dexcountTask)
    }

    private fun applyToApkVariant(variant: ApkVariant) {
        variant.outputs.all { output ->
            if (output is ApkVariantOutput) {
                // why wouldn't it be?
                val packageDirectory = output.packageApplication.outputDirectory
                val task = createTask(ModernMethodCountTask::class, variant, output) { t -> t.inputDirectory = packageDirectory }
                addDexcountTaskToGraph(output.packageApplication, task)
            } else {
                throw IllegalArgumentException("Unexpected output type for variant ${variant.name}: ${output::class.java}")
            }
        }
    }
}
