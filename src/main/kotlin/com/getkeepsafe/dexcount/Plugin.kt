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
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
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
    }

    override fun apply(project: Project) {
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

        val provider = when {
            gradlePluginRevision isAtLeast AGP_VERSION_3_6 -> ThreeSixProvider(project)
            gradlePluginRevision isAtLeast AGP_VERSION_3_3 -> ThreeThreeProvider(project)
            gradlePluginRevision isAtLeast AGP_VERSION_3 -> ThreeOhProvider(project)
            else -> LegacyProvider(project)
        }

        provider.apply()
    }

    private infix fun Revision.isAtLeast(versionText: String): Boolean {
        val other = Revision.parseRevision(versionText)
        return compareTo(other, Revision.PreviewComparison.IGNORE) >= 0
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
                ext!!.applicationVariants
            }

            project.plugins.hasPlugin("com.android.test") -> {
                val ext = project.extensions.findByType(TestExtension::class.java)
                ext!!.applicationVariants
            }

            project.plugins.hasPlugin("com.android.library") -> {
                val ext = project.extensions.findByType(LibraryExtension::class.java)
                ext!!.libraryVariants
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
        createTaskForJavaProject(ModernMethodCountTask::class, jarTask) { t ->
            checkPrintDeclarationsIsTrue()

            t.inputFileProvider = { jarTask.archivePath }
        }
    }

    protected fun addDexcountTaskToGraph(parentTask: Task, dexcountTask: DexMethodCountTaskBase) {
        // Dexcount tasks require that their parent task has been run...
        dexcountTask.dependsOn(parentTask)
        dexcountTask.mustRunAfter(parentTask)

        // But package should always imply that dexcount runs, unless configured not to.
        if (ext.runOnEachPackage) {
            parentTask.finalizedBy(dexcountTask)
        }
    }

    /**
     * Gets the outputs for the given [variant] as a basic [Collection].
     *
     * This is useful for AGP < 3.0, where the return type is _not_ a
     * [DomainObjectCollection].
     */
    protected fun getOutputsForVariant(variant: BaseVariant): Collection<BaseVariantOutput> {
        @Suppress("UNCHECKED_CAST")
        return baseVariant_getOutputs(variant) as Collection<BaseVariantOutput>
    }

    protected fun <T : DexMethodCountTaskBase> createTask(
            taskClass: KClass<T>,
            variant: BaseVariant,
            output: BaseVariantOutput?,
            applyInputConfiguration: (T) -> Unit): DexMethodCountTaskBase  {
        var slug = variant.name.capitalize()
        var path = "${project.buildDir}/outputs/dexcount/${variant.name}"
        var outputName = variant.name
        if (getOutputsForVariant(variant).size > 1) {
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

    protected fun <T : DexMethodCountTaskBase> createTaskForJavaProject(
            taskClass: KClass<T>,
            jarTask: Jar,
            applyInputConfiguration: (T) -> Unit): org.gradle.api.tasks.TaskProvider<T>  {

        return project.tasks.register("countDeclaredMethods", taskClass.java) { task ->
            val outputDir = "${project.buildDir}/dexcount"

            task.apply {
                description = "Outputs declared method count."
                group = "Reporting"
                variantOutputName = ""
                mappingFile = null
                outputFile = File(outputDir, name + (ext.format as OutputFormat).extension)
                summaryFile = File(outputDir, "$name.csv")
                chartDir = File(outputDir, name + "Chart")
                config = ext

                applyInputConfiguration(this)
            }

            task.dependsOn(jarTask)
            task.mustRunAfter(jarTask)

            if (ext.runOnEachPackage) {
                jarTask.finalizedBy(task)
            }
        }
    }

    protected fun checkPrintDeclarationsIsFalse() {
        check(!ext.printDeclarations) { "Cannot compute declarations for project $project" }
    }

    protected fun checkPrintDeclarationsIsTrue() {
        check(ext.printDeclarations) { "printDeclarations must be true for Java projects: $project" }
    }
}

class LegacyProvider(project: Project) : TaskProvider(project) {
    override fun applyToApplicationVariant(variant: ApplicationVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToTestVariant(variant: TestVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToLibraryVariant(variant: LibraryVariant) {
        getOutputsForVariant(variant).forEach { output ->
            val task = createTask(LegacyMethodCountTask::class, variant, output) { t -> t.variantOutput = output }
            addDexcountTaskToGraph(output.assemble, task)
        }
    }

    private fun applyToApkVariant(variant: ApkVariant) {
        checkPrintDeclarationsIsFalse()

        getOutputsForVariant(variant).forEach { output ->
            val task = createTask(LegacyMethodCountTask::class, variant, output) { t -> t.variantOutput = output }
            addDexcountTaskToGraph(output.assemble, task)
        }
    }
}

class ThreeOhProvider(project: Project) : TaskProvider(project) {
    override fun applyToApplicationVariant(variant: ApplicationVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToTestVariant(variant: TestVariant) {
        applyToApkVariant(variant)
    }

    override fun applyToLibraryVariant(variant: LibraryVariant) {
        val packageTask = variant.packageLibrary
        val dexcountTask = createTask(ModernMethodCountTask::class, variant, null) { t ->
            t.inputFileProvider = { packageTask.archivePath }
        }
        addDexcountTaskToGraph(packageTask, dexcountTask)
    }

    private fun applyToApkVariant(variant: ApkVariant) {
        checkPrintDeclarationsIsFalse()

        variant.outputs.all { output ->
            if (output is ApkVariantOutput) {
                // why wouldn't it be?
                val task = createTask(ModernMethodCountTask::class, variant, output) { t ->
                    t.inputFileProvider = { output.outputFile }
                }
                addDexcountTaskToGraph(output.packageApplication, task)
            } else {
                throw IllegalArgumentException("Unexpected output type for variant ${variant.name}: ${output::class.java}")
            }
        }
    }
}

open class ThreeThreeProvider(project: Project): TaskProvider(project) {
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

        val dexcountTask = createTask(ModernMethodCountTask::class, variant, null) { t ->
            t.inputFileProvider = { packageTask.archivePath }
        }

        addDexcountTaskToGraph(packageTask, dexcountTask)
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

            val dexcountTask = createTask(ModernMethodCountTask::class, variant, output) { t ->
                t.inputFileProvider = {
                    File(getOutputDirectory(packageTask), output.outputFileName)
                }
            }

            addDexcountTaskToGraph(packageTask, dexcountTask)
        }
    }

    protected open fun getOutputDirectory(task: PackageAndroidArtifact): File {
        return method_getOutputDirectory(task) as File
    }
}

class ThreeSixProvider(project: Project) : ThreeThreeProvider(project) {
    override fun getOutputDirectory(task: PackageAndroidArtifact): File {
        return task.outputDirectory.asFile.get()
    }
}
