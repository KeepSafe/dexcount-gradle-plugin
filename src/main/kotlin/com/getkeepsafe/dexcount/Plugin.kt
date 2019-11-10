/*
 * Copyright (C) 2015-2019 KeepSafe Software
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

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.repository.Revision
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.lang.reflect.Method
import kotlin.reflect.KClass

open class DexMethodCountPlugin : Plugin<Project> {
    companion object {
        var sdkLocation: File? = null
        private const val VERSION_3_ZERO_FIELD: String = "com.android.builder.Version" // <= 3.0
        private const val VERSION_3_ONE_FIELD: String = "com.android.builder.model.Version" // > 3.1
        private const val AGP_VERSION_FIELD: String = "ANDROID_GRADLE_PLUGIN_VERSION"
        private const val AGP_VERSION_3: String = "3.0.0"
        private const val AGP_VERSION_3_3 = "3.3.0"
        private const val AGP_VERSION_3_6 = "3.6.0"
        private const val ANDROID_EXTENSION_NAME = "android"
        private const val SDK_DIRECTORY_METHOD = "getSdkDirectory"

        private val MIN_GRADLE_VERSION = GradleVersion(major = 5, minor = 1)
    }

    override fun apply(project: Project) {
        if (project.gradleVersion < MIN_GRADLE_VERSION) {
            project.logger.error("dexcount requires Gradle $MIN_GRADLE_VERSION or above")
            return
        }

        if (!isAtLeastJavaEight) {
            project.logger.error("Java 8 or above is *STRONGLY* recommended - dexcount may not work properly on Java 7 or below!")
        }

        var gradlePluginVersion: String? = null
        var exception: Exception? = null

        try {
            gradlePluginVersion = Class.forName(VERSION_3_ZERO_FIELD).getDeclaredField(AGP_VERSION_FIELD).get(this).toString()
        } catch (e: Exception) {
            exception = e
        }

        try {
            gradlePluginVersion = Class.forName(VERSION_3_ONE_FIELD).getDeclaredField(AGP_VERSION_FIELD).get(this).toString()
        } catch (e: Exception) {
            exception = e
        }

        if (gradlePluginVersion == null && exception != null) {
            throw IllegalStateException("dexcount requires the Android plugin to be configured", exception)
        } else if (gradlePluginVersion == null) {
            throw IllegalStateException("dexcount requires the Android plugin to be configured")
        }

        val android = project.extensions.findByName(ANDROID_EXTENSION_NAME)
        sdkLocation = android?.javaClass?.getMethod(SDK_DIRECTORY_METHOD)?.invoke(android) as File?

        val gradlePluginRevision = Revision.parseRevision(gradlePluginVersion, Revision.Precision.PREVIEW)

        val applicator = when {
            gradlePluginRevision isAtLeast AGP_VERSION_3_6 -> ThreeSixApplicator(project)
            gradlePluginRevision isAtLeast AGP_VERSION_3_3 -> ThreeThreeApplicator(project)
            gradlePluginRevision isAtLeast AGP_VERSION_3 -> ThreeOhApplicator(project)
            else -> JavaOnlyApplicator(project)
        }

        applicator.apply()
    }

    private infix fun Revision.isAtLeast(versionText: String): Boolean {
        val other = Revision.parseRevision(versionText)
        return compareTo(other, Revision.PreviewComparison.IGNORE) >= 0
    }
}

abstract class TaskApplicator(
        protected val project: Project
) {
    private val ext: DexCountExtension = project.extensions.create(
            "dexcount", DexCountExtension::class.java).apply {
        // If the user has passed '--stacktrace' or '--full-stacktrace', assume
        // that they are trying to report a dexcount bug.  Help them help us out
        // by printing the current plugin title and version.
        if (project.gradle.startParameter.isShowStacktrace) {
            printVersion = true
        }
    }

    private val baseVariant_getOutputs: Method by lazy {
        BaseVariant::class.java.getDeclaredMethod("getOutputs").apply {
            isAccessible = true
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
                ext!!.applicationVariants as DomainObjectCollection<ApplicationVariant>
            }

            project.plugins.hasPlugin("com.android.test") -> {
                val ext = project.extensions.findByType(TestExtension::class.java)
                ext!!.applicationVariants as DomainObjectCollection<ApplicationVariant>
            }

            project.plugins.hasPlugin("com.android.library") -> {
                val ext = project.extensions.findByType(LibraryExtension::class.java)
                ext!!.libraryVariants as DomainObjectCollection<LibraryVariant>
            }

            project.plugins.hasPlugin(JavaPlugin::class.java) || project.plugins.hasPlugin(JavaLibraryPlugin::class.java) -> {
                val jar = project.tasks.findByName("jar") as? Jar
                    ?: throw IllegalArgumentException("Jar task is null for $project")

                applyToJavaProject(jar)
                return
            }

            else -> throw IllegalArgumentException("Dexcount plugin requires the Android plugin to be configured")
        }

        variants.all { variant ->
            if (!ext.enabled) {
                return@all
            }

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

    private fun applyToJavaProject(jarTask: Jar) {
        createTaskForJavaProject(DexCountTask::class, jarTask) { t ->
            checkPrintDeclarationsIsTrue()

            t.inputFileProperty.set(jarTask.archivePath)
        }
    }

    protected fun addDexcountTaskToGraph(parentTask: Task, dexcountTask: DexCountTask) {
        // Dexcount tasks require that their parent task has been run...
        dexcountTask.dependsOn(parentTask)
        dexcountTask.mustRunAfter(parentTask)

        // But package should always imply that dexcount runs, unless configured not to.
        if (ext.runOnEachPackage) {
            parentTask.finalizedBy(dexcountTask)
        }
    }

    protected fun createTask(
            variant: BaseVariant,
            parentTask: Task,
            output: BaseVariantOutput?,
            applyInputConfiguration: (DexCountTask) -> Unit): DexCountTask  {
        var slug = variant.name.capitalize()
        var path = "${project.buildDir}/outputs/dexcount/${variant.name}"
        val outputName = if (variant.outputs.size > 1) {
            if (output == null) { throw AssertionError("Output should never be null here") }
            slug += output.name.capitalize()
            path += "/${output.name}"
            output.name
        } else {
            variant.name
        }

        return project.tasks.create("count${slug}DexMethods", DexCountTask::class.java) { t ->
            t.description         = "Outputs dex method count for ${variant.name}."
            t.group               = "Reporting"
            t.config              = ext

            t.variantOutputName.set(outputName)
            t.mappingFileProvider.from(getMappingFile(variant))
            t.outputFile.set(project.file(path + (ext.format as OutputFormat).extension))
            t.summaryFile.set(project.file(path + ".csv"))
            t.chartDir.set(project.file(path + "Chart"))

            applyInputConfiguration(t)

            addDexcountTaskToGraph(parentTask, t)
        }
    }

    private fun <T : DexCountTask> createTaskForJavaProject(
            taskClass: KClass<T>,
            jarTask: Jar,
            applyInputConfiguration: (T) -> Unit): TaskProvider<T> {

        return project.tasks.register("countDeclaredMethods", taskClass.java) { task ->
            val outputDir = "${project.buildDir}/dexcount"

            task.apply {
                description = "Outputs declared method count."
                group = "Reporting"
                variantOutputName.set("")
                mappingFileProvider.from(project.files())
                outputFile.set(File(outputDir, name + (ext.format as OutputFormat).extension))
                summaryFile.set(File(outputDir, "$name.csv"))
                chartDir.set(File(outputDir, name + "Chart"))
                config = ext

                applyInputConfiguration(this)
            }

            addDexcountTaskToGraph(parentTask = jarTask, dexcountTask = task)
        }
    }

    protected fun checkPrintDeclarationsIsFalse() {
        check(!ext.printDeclarations) { "Cannot compute declarations for project $project" }
    }

    protected fun checkPrintDeclarationsIsTrue() {
        check(ext.printDeclarations) { "printDeclarations must be true for Java projects: $project" }
    }

    protected open fun getMappingFile(variant: BaseVariant): Provider<FileCollection> {
        @Suppress("UnstableApiUsage")
        return project.provider { variant.mappingFile?.let { project.files(it) } ?: project.files() }
    }
}

/**
 * Supports counting Java tasks only; used when no supported AGP version
 * is detected.
 */
class JavaOnlyApplicator(project: Project) : TaskApplicator(project) {
    override fun applyToApplicationVariant(variant: ApplicationVariant) {
        error("unreachable")
    }

    override fun applyToTestVariant(variant: TestVariant) {
        error("unreachable")
    }

    override fun applyToLibraryVariant(variant: LibraryVariant) {
        error("unreachable")
    }
}

class ThreeOhApplicator(project: Project) : TaskApplicator(project) {
    override fun applyToApplicationVariant(variant: ApplicationVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToTestVariant(variant: TestVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToLibraryVariant(variant: LibraryVariant) {
        val packageTask = variant.packageLibrary
        createTask(variant, packageTask, null) { t ->
            t.inputFileProperty.set(packageTask.archivePath)
        }
    }

    private fun applyToApkVariant(variant: ApkVariant) {
        checkPrintDeclarationsIsFalse()

        variant.outputs.all { output ->
            if (output is ApkVariantOutput) {
                // why wouldn't it be?
                createTask(variant, output.packageApplication, output) { t ->
                    t.inputFileProperty.set(output.outputFile)
                }
            } else {
                throw IllegalArgumentException("Unexpected output type for variant ${variant.name}: ${output::class.java}")
            }
        }
    }
}

open class ThreeThreeApplicator(project: Project): TaskApplicator(project) {
    // As of AGP 3.6, this method changed its return type from File to DirectoryProperty.
    // In versions 3.3->3.5, we need to reflectively access this.
    private val method_getOutputDirectory: Method by lazy {
        PackageAndroidArtifact::class.java.getDeclaredMethod("getOutputDirectory").apply {
            isAccessible = true
        }
    }

    override fun applyToApplicationVariant(variant: ApplicationVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToTestVariant(variant: TestVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToLibraryVariant(variant: LibraryVariant) {
        val packageTaskProvider = variant.packageLibraryProvider
        val packageTask = packageTaskProvider.orNull
        if (packageTask == null) {
            project.logger.error("LibraryVariant.getPackageLibraryProvider().getOrNull() unexpectedly returned null")
            return
        }

        createTask(variant, packageTask, null) { t ->
            t.inputFileProperty.set(packageTask.archivePath)
        }
    }

    private fun applyToApkVariant(variant: ApkVariant) {
        checkPrintDeclarationsIsFalse()

        variant.outputs.all { output ->
            if (output !is ApkVariantOutput) {
                throw IllegalArgumentException("Unexpected output type for variant ${variant.name}: ${output::class.java}")
            }

            val packageTask = variant.packageApplicationProvider.orNull
            if (packageTask == null) {
                project.logger.error("ApkVariant.getPackageApplicationProvider().getOrNull() unexpectedly returned null")
                return@all
            }

            createTask(variant, packageTask, output) { t ->
                t.inputFileProperty.set(File(getOutputDirectory(packageTask), output.outputFileName))
            }
        }
    }

    protected open fun getOutputDirectory(task: PackageAndroidArtifact): File {
        return method_getOutputDirectory(task) as File
    }
}

class ThreeSixApplicator(project: Project) : ThreeThreeApplicator(project) {
    override fun getOutputDirectory(task: PackageAndroidArtifact): File {
        return task.outputDirectory.asFile.get()
    }

    override fun getMappingFile(variant: BaseVariant): Provider<FileCollection> {
        return variant.mappingFileProvider
    }
}
