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

@file:Suppress("UnstableApiUsage")

package com.getkeepsafe.dexcount

import com.android.build.api.artifact.Artifacts
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.android.build.api.extension.ApplicationAndroidComponentsExtension as OldApplicationAndroidComponentsExtension
import com.android.build.api.extension.LibraryAndroidComponentsExtension as OldLibraryAndroidComponentsExtension
import com.android.build.api.extension.TestAndroidComponentsExtension as OldTestAndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestPlugin
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.tasks.BundleAar
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.repository.Revision
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.lang.reflect.Method

@Suppress("unused")
open class DexMethodCountPlugin : Plugin<Project> {
    companion object {
        var sdkLocation: File? = null
        private const val VERSION_3_ZERO_FIELD: String = "com.android.builder.Version" // <= 3.0
        private const val VERSION_3_ONE_FIELD: String = "com.android.builder.model.Version" // > 3.1
        private const val AGP_VERSION_FIELD: String = "ANDROID_GRADLE_PLUGIN_VERSION"
        private const val ANDROID_EXTENSION_NAME = "android"
        private const val SDK_DIRECTORY_METHOD = "getSdkDirectory"

        private val MIN_GRADLE_VERSION = GradleVersion(6, 0)
        private val MIN_AGP_VERSION = Revision(3, 4, 0)
    }

    override fun apply(project: Project) {
        if (ProjectUtils.gradleVersion(project) < MIN_GRADLE_VERSION) {
            project.logger.error("dexcount requires Gradle $MIN_GRADLE_VERSION or above")
            return
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

        // It's important not to invoke the private 'getSdkDirectory()' method until
        // *after* the project is evaluated; if we access it too early (as of 4.1.0-beta01)
        // we get a rather obscure exception about 'compileSdkVersion' not being set because
        // the extension is not yet initialized.
        project.afterEvaluate {
            val android = it.extensions.findByName(ANDROID_EXTENSION_NAME)
            val extClass = android?.javaClass
            val getSdkDirectory = extClass?.getMethod(SDK_DIRECTORY_METHOD)
            sdkLocation = getSdkDirectory?.invoke(android) as File?
        }

        val ext = project.extensions.create("dexcount", DexCountExtension::class.java).apply {
            // If the user has passed '--stacktrace' or '--full-stacktrace', assume
            // that they are trying to report a dexcount bug.  Help them help us out
            // by printing the current plugin title and version.
            if (project.gradle.startParameter.showStacktrace != ShowStacktrace.INTERNAL_EXCEPTIONS) {
                printVersion.set(true)
            }
        }

        // We need to do this check *after* we create the 'dexcount' Gradle extension.
        // If we bail on instant run builds any earlier, then the build will break
        // for anyone configuring dexcount due to the extension not being registered.
        if (ProjectUtils.isInstantRun(project)) {
            project.logger.info("Instant Run detected; disabling dexcount")
            return
        }

        val gradlePluginRevision = Revision.parseRevision(gradlePluginVersion, Revision.Precision.PREVIEW)
        if (gradlePluginRevision > JavaOnlyApplicator.Factory().minimumRevision && gradlePluginRevision < MIN_AGP_VERSION) {
            project.logger.error("dexcount requires Android Gradle Plugin $MIN_AGP_VERSION or above")
            return
        }

        val factories = listOf(
            SevenOhApplicator.Factory(),
            FourTwoApplicator.Factory(),
            FourOneApplicator.Factory(),
            ThreeSixApplicator.Factory(),
            ThreeFourApplicator.Factory(),
            JavaOnlyApplicator.Factory()
        )

        factories
            .first { gradlePluginRevision isAtLeast it.minimumRevision }
            .create(ext, project)
            .apply()
    }

    private infix fun Revision.isAtLeast(other: Revision): Boolean {
        return compareTo(other, Revision.PreviewComparison.IGNORE) >= 0
    }
}

interface TaskApplicator {
    fun apply()

    interface Factory {
        val minimumRevision: Revision
        fun create(ext: DexCountExtension, project: Project): TaskApplicator
    }
}

abstract class AbstractTaskApplicator(
    protected val ext: DexCountExtension,
    protected val project: Project
) : TaskApplicator {
    protected inline fun <reified T : Task> TaskContainer.register(name: String, crossinline fn: (T) -> Unit): TaskProvider<T> {
        return register(name, T::class.java) { t ->
            fn(t)
        }
    }
}

abstract class LegacyTaskApplicator(ext: DexCountExtension, project: Project) : AbstractTaskApplicator(ext, project) {
    private val baseVariant_getOutputs: Method by lazy {
        BaseVariant::class.java.getDeclaredMethod("getOutputs").apply {
            isAccessible = true
        }
    }

    @Suppress("USELESS_CAST")
    override fun apply() {
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

        variants.configureEach { variant ->
            if (!ext.enabled.get()) {
                return@configureEach
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
        val gen = project.tasks.register("generatePackageTree", JarPackageTreeTask::class.java) { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(jarTask.archiveFile.map { it.asFile.nameWithoutExtension })
            t.jarFileProperty.set(jarTask.archiveFile)
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount"))
        }

        project.tasks.register("countDeclaredMethods", DexCountOutputTask::class.java) { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set("")
            t.androidProject.set(false)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })

            if (ext.runOnEachPackage.get()) {
                jarTask.finalizedBy(t)
            }
        }
    }

    protected fun createTask(
            variant: BaseVariant,
            parentTask: TaskProvider<*>,
            output: BaseVariantOutput?,
            applyInputConfiguration: (LegacyGeneratePackageTreeTask) -> Unit)  {
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

        val treePath = path.replace("outputs", "intermediates") + "/tree.compact.gz"

        val gen = project.tasks.register("generate${slug}PackageTree", LegacyGeneratePackageTreeTask::class.java) { t ->
            t.description         = "Generates dex method count for ${variant.name}."
            t.group               = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(outputName)
            t.mappingFileProvider.set(getMappingFile(variant))
            t.outputDirectoryProperty.set(project.file(path))
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file(treePath))

            applyInputConfiguration(t)

            // Depending on the runtime AGP version, inputFileProperty (as provided in applyInputConfiguration)
            // may or may not carry task-dependency information with it.  We need to set that up manually here.
            t.dependsOn(parentTask)
        }

        val countTask = project.tasks.register("count${slug}DexMethods", DexCountOutputTask::class.java) { t ->
            t.description         = "Outputs dex method count for ${variant.name}."
            t.group               = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set(outputName)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(true)
        }

        if (ext.runOnEachPackage.get()) {
            parentTask.configure { t ->
                t.finalizedBy(countTask)
            }
        }
    }

    protected fun checkPrintDeclarationsIsFalse() {
        check(!ext.printDeclarations.get()) { "Cannot compute declarations for project $project" }
    }

    protected fun checkPrintDeclarationsIsTrue() {
        check(ext.printDeclarations.get()) { "printDeclarations must be true for Java projects: $project" }
    }

    protected open fun getMappingFile(variant: BaseVariant): Provider<FileCollection> {
        @Suppress("UnstableApiUsage", "DEPRECATION")
        return project.provider { variant.mappingFile?.let { project.files(it) } ?: project.files() }
    }
}

/**
 * Supports counting Java tasks only; used when no supported AGP version
 * is detected.
 */
class JavaOnlyApplicator(ext: DexCountExtension, project: Project) : LegacyTaskApplicator(ext, project) {
    class Factory : TaskApplicator.Factory {
        override val minimumRevision: Revision = Revision.parseRevision("0.0.0")

        override fun create(ext: DexCountExtension, project: Project): TaskApplicator {
            return JavaOnlyApplicator(ext, project)
        }
    }

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

open class ThreeFourApplicator(ext: DexCountExtension, project: Project): LegacyTaskApplicator(ext, project) {
    class Factory : TaskApplicator.Factory {
        override val minimumRevision: Revision = Revision.parseRevision("3.4.0")
        override fun create(ext: DexCountExtension, project: Project) = ThreeFourApplicator(ext, project)
    }

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
        createTask(variant, packageTaskProvider, null) { t ->
            t.inputFileProperty.set(packageTaskProvider.flatMap { it.archiveFile })
        }
    }

    private fun applyToApkVariant(variant: ApkVariant) {
        checkPrintDeclarationsIsFalse()

        variant.outputs.configureEach { output ->
            if (output !is ApkVariantOutput) {
                throw IllegalArgumentException("Unexpected output type for variant ${variant.name}: ${output::class.java}")
            }

            createTask(variant, variant.packageApplicationProvider, output) { t ->
                val outputDirProvider = variant.packageApplicationProvider.flatMap { getOutputDirectory(it) }
                val fileProvider = outputDirProvider.map { it.file(output.outputFileName) }
                t.inputFileProperty.value(fileProvider)
            }
        }
    }

    @Suppress("UnstableApiUsage")
    protected open fun getOutputDirectory(task: PackageAndroidArtifact): DirectoryProperty {
        return project.objects.directoryProperty().apply {
            set(method_getOutputDirectory(task) as File)
        }
    }
}

open class ThreeSixApplicator(ext: DexCountExtension, project: Project) : ThreeFourApplicator(ext, project) {
    class Factory : TaskApplicator.Factory {
        override val minimumRevision: Revision = Revision.parseRevision("3.6.0")
        override fun create(ext: DexCountExtension, project: Project) = ThreeSixApplicator(ext, project)
    }

    override fun getOutputDirectory(task: PackageAndroidArtifact): DirectoryProperty {
        return task.outputDirectory
    }

    override fun getMappingFile(variant: BaseVariant): Provider<FileCollection> {
        return variant.mappingFileProvider
    }
}

@Suppress("UnstableApiUsage")
open class FourOneApplicator(ext: DexCountExtension, project: Project) : AbstractTaskApplicator(ext, project) {
    class Factory : TaskApplicator.Factory {
        override val minimumRevision: Revision = Revision.parseRevision("4.1.0")
        override fun create(ext: DexCountExtension, project: Project) = FourOneApplicator(ext, project)
    }

    companion object {
        private val clsVariantProperties: Class<*> by lazy { Class.forName("com.android.build.api.variant.VariantProperties") }
        private val fnGetName: Method by lazy { clsVariantProperties.getMethod("getName") }
        private val fnGetArtifacts: Method by lazy { clsVariantProperties.getMethod("getArtifacts") }

        private val clsKotlinUnaryFn: Class<*> by lazy { Class.forName("kotlin.jvm.functions.Function1") }
        private val clsCommonExtension: Class<*> by lazy { Class.forName("com.android.build.api.dsl.CommonExtension") }
        private val fnOnVariantProperties: Method by lazy { clsCommonExtension.getMethod("onVariantProperties", clsKotlinUnaryFn) }

        private val clsArtifactType: Class<*> by lazy { Class.forName("com.android.build.api.artifact.ArtifactType") }
        private val emArtifactTypeApk: Enum<*> by lazy { clsArtifactType.enumConstants.find { (it as Enum<*>).name == "APK" } as Enum<*> }
        private val emArtifactTypeBundle: Enum<*> by lazy { clsArtifactType.enumConstants.find { (it as Enum<*>).name == "BUNDLE" } as Enum<*> }
        private val emArtifactTypeAar: Enum<*> by lazy { clsArtifactType.enumConstants.find { (it as Enum<*>).name == "AAR" } as Enum<*> }
        private val emArtifactTypeObfuscationMappingFile: Enum<*> by lazy { clsArtifactType.enumConstants.find { (it as Enum<*>).name == "OBFUSCATION_MAPPING_FILE" } as Enum<*> }

        private val clsArtifacts: Class<*> by lazy { Class.forName("com.android.build.api.artifact.Artifacts") }
        private val fnGetArtifact: Method by lazy { clsArtifacts.getMethod("get", clsArtifactType ) }
    }

    // In AGP 4.1.0, 'onVariantProperties' lambdas have a receiver deriving from the VariantProperties
    // interface.  In 4.2.0, the receivers switch to deriving from Variant instead.
    // To work around this, we have to fall back to type-erasure and reflection to produce a callback
    // that the compiler will accept and will still do the job.
    private fun <T> makeVariantPropertiesCallback(fn: (String, Artifacts) -> Unit): T.() -> Unit {
        return {
            val name: String = fnGetName(this) as String
            val artifacts: Artifacts = fnGetArtifacts(this) as Artifacts

            fn(name, artifacts)
        }
    }

    @Suppress("UNCHECKED_CAST")
    sealed class OldArtifactType<T>(val artifactType: () -> Enum<*>) {
        object Apk : OldArtifactType<Provider<Directory>>({ emArtifactTypeApk })
        object Bundle : OldArtifactType<Provider<RegularFile>>({ emArtifactTypeBundle })
        object Aar : OldArtifactType<Provider<RegularFile>>({ emArtifactTypeAar })
        object ObfuscationMappingFile : OldArtifactType<Provider<RegularFile>>({ emArtifactTypeObfuscationMappingFile })

        fun get(artifacts: Artifacts): T {
            return fnGetArtifact(artifacts, artifactType()) as T
        }
    }

    override fun apply() {
        if (!ext.enabled.get()) {
            return
        }

        project.plugins.withType(AppPlugin::class.java).configureEach {
            val android = project.extensions.getByType(ApplicationExtension::class.java)
            fnOnVariantProperties(android, makeVariantPropertiesCallback<Any> { name, artifacts ->
                registerApkTask(name, artifacts)
                registerAabTask(name, artifacts)
            })
        }

        project.plugins.withType(LibraryPlugin::class.java).configureEach {
            val android = project.extensions.getByType(LibraryExtension::class.java)
            fnOnVariantProperties(android, makeVariantPropertiesCallback<Any> { name, artifacts ->
                registerAarTask(name, artifacts)
            })
        }

        project.plugins.withType(TestPlugin::class.java).configureEach {
            val android = project.extensions.getByType(TestExtension::class.java)
            fnOnVariantProperties(android, makeVariantPropertiesCallback<Any> { name, artifacts ->
                registerApkTask(name, artifacts)
            })
        }

        project.afterEvaluate {
            if (project.extensions.findByType(CommonExtension::class.java) == null) {
                // No Android plugins were registered; this may be a jar-count usage.
                registerJarTask()
            }
        }
    }

    protected open fun registerApkTask(variantName: String, artifacts: Artifacts) {
        if (!ext.enabled.get()) {
            return
        }

        check(!ext.printDeclarations.get()) { "Cannot compute declarations for project $project" }

        val genTaskName = "generate${variantName.capitalize()}PackageTree"
        val taskName = "count${variantName.capitalize()}DexMethods"

        val gen = project.tasks.register<ApkPackageTreeTask>(genTaskName) { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(variantName)
            t.apkDirectoryProperty.set(OldArtifactType.Apk.get(artifacts))
            t.loaderProperty.set(artifacts.getBuiltArtifactsLoader())
            t.mappingFileProperty.set(OldArtifactType.ObfuscationMappingFile.get(artifacts))
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/$variantName/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount/$variantName"))
        }

        project.tasks.register<DexCountOutputTask>(taskName) { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set(variantName)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(true)
        }
    }

    protected open fun registerAabTask(variantName: String, artifacts: Artifacts) {
        if (!ext.enabled.get()) {
            return
        }

        check(!ext.printDeclarations.get()) { "Cannot compute declarations for project $project" }

        val taskName = "count${variantName.capitalize()}BundleDexMethods"

        val gen = project.tasks.register<BundlePackageTreeTask>("generate${variantName.capitalize()}BundlePackageTree") { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(variantName)
            t.bundleFileProperty.set(OldArtifactType.Bundle.get(artifacts))
            t.loaderProperty.set(artifacts.getBuiltArtifactsLoader())
            t.mappingFileProperty.set(OldArtifactType.ObfuscationMappingFile.get(artifacts))
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/$variantName/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount/$variantName"))
        }

        project.tasks.register<DexCountOutputTask>(taskName) { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set(variantName)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(true)
        }
    }

    protected open fun registerAarTask(variantName: String, artifacts: Artifacts) {
        if (!ext.enabled.get()) {
            return
        }

        val taskName = "count${variantName.capitalize()}DexMethods"

        // NOTE: This is for AGP 4.1.0 _only_.  ArtifactType.AAR didn't quite make it into
        //       the final API for 4.1.0, but will be available in 4.2.0 at which point we
        //       will need to cordon off this gross hack.
        project.afterEvaluate {
            val bundleTaskProvider = project.tasks.named("bundle${variantName.capitalize()}Aar", BundleAar::class.java)

            val gen = project.tasks.register<Agp41LibraryPackageTreeTask>("generate${variantName.capitalize()}PackageTree") { t ->
                t.description = "Generate dex method counts"
                t.group       = "Reporting"

                t.configProperty.set(ext)
                t.outputFileNameProperty.set(variantName)
                t.aarBundleFileCollection.from(bundleTaskProvider)
                t.loaderProperty.set(artifacts.getBuiltArtifactsLoader())
                t.mappingFileProperty.set(OldArtifactType.ObfuscationMappingFile.get(artifacts))
                t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/$variantName/tree.compact.gz"))
                t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount/$variantName"))
            }

            project.tasks.register<DexCountOutputTask>(taskName) { t ->
                t.description = "Output dex method counts"
                t.group       = "Reporting"

                t.configProperty.set(ext)
                t.variantNameProperty.set(variantName)
                t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
                t.androidProject.set(true)
            }
        }
    }

    protected open fun registerJarTask() {
        if (!ext.enabled.get()) {
            return
        }

        if (!project.plugins.hasPlugin(JavaPlugin::class.java) && !project.plugins.hasPlugin(JavaLibraryPlugin::class.java)) {
            return
        }

        check(ext.printDeclarations.get()) { "printDeclarations must be true for Java projects: $project" }

        val jarTaskProvider = project.tasks.named("jar", Jar::class.java)

        val gen = project.tasks.register<JarPackageTreeTask>("generatePackageTree") { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(jarTaskProvider.flatMap { jarTask -> jarTask.archiveFile.map { it.asFile.nameWithoutExtension } })
            t.jarFileProperty.set(jarTaskProvider.flatMap { it.archiveFile })
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount"))
        }

        project.tasks.register<DexCountOutputTask>("countDeclaredMethods") { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set("")
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(false)
        }
    }
}

open class FourTwoApplicator(ext: DexCountExtension, project: Project) : FourOneApplicator(ext, project) {
    class Factory : TaskApplicator.Factory {
        override val minimumRevision: Revision = Revision.parseRevision("4.2.0")
        override fun create(ext: DexCountExtension, project: Project) = FourTwoApplicator(ext, project)
    }

    @Suppress("DEPRECATION")
    override fun apply() {
        if (!ext.enabled.get()) {
            return
        }

        project.plugins.withType(AppPlugin::class.java).configureEach {
            val androidComponents = project.extensions.getByType(OldApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                registerApkTask(variant.name, variant.artifacts)
                registerAabTask(variant.name, variant.artifacts)
            }
        }

        project.plugins.withType(LibraryPlugin::class.java).configureEach {
            val androidComponents = project.extensions.getByType(OldLibraryAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                registerAarTask(variant.name, variant.artifacts)
            }
        }

        project.plugins.withType(TestPlugin::class.java).configureEach {
            val androidComponents = project.extensions.getByType(OldTestAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                registerApkTask(variant.name, variant.artifacts)
            }
        }

        project.afterEvaluate {
            if (project.extensions.findByType(CommonExtension::class.java) == null) {
                // No Android plugins were registered; this may be a jar-count usage.
                registerJarTask()
            }
        }
    }

    override fun registerAarTask(variantName: String, artifacts: Artifacts) {
        val genTaskName = "generate${variantName.capitalize()}PackageTree"
        val taskName = "count${variantName.capitalize()}DexMethods"

        val gen = project.tasks.register<LibraryPackageTreeTask>(genTaskName) { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(variantName)
            t.aarFileProperty.set(OldArtifactType.Aar.get(artifacts))
            t.loaderProperty.set(artifacts.getBuiltArtifactsLoader())
            t.mappingFileProperty.set(OldArtifactType.ObfuscationMappingFile.get(artifacts))
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/$variantName/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount/$variantName"))
        }

        project.tasks.register<DexCountOutputTask>(taskName) { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set(variantName)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(true)
        }
    }
}

open class SevenOhApplicator(ext: DexCountExtension, project: Project) : AbstractTaskApplicator(ext, project) {
    class Factory : TaskApplicator.Factory {
        override val minimumRevision: Revision = Revision.parseRevision("7.0.0")
        override fun create(ext: DexCountExtension, project: Project) = SevenOhApplicator(ext, project)
    }

    override fun apply() {
        if (!ext.enabled.get()) {
            return
        }

        project.plugins.withType(AppPlugin::class.java).configureEach {
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                registerApkTask(variant.name, variant.artifacts)
                registerAabTask(variant.name, variant.artifacts)
            }
        }

        project.plugins.withType(LibraryPlugin::class.java).configureEach {
            val androidComponents = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                registerAarTask(variant.name, variant.artifacts)
            }
        }

        project.plugins.withType(TestPlugin::class.java).configureEach {
            val androidComponents = project.extensions.getByType(TestAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                registerApkTask(variant.name, variant.artifacts)
            }
        }

        project.afterEvaluate {
            if (project.extensions.findByType(CommonExtension::class.java) == null) {
                // No Android plugins were registered; this may be a jar-count usage.
                registerJarTask()
            }
        }
    }

    protected fun registerApkTask(variantName: String, artifacts: Artifacts) {
        if (!ext.enabled.get()) {
            return
        }

        check(!ext.printDeclarations.get()) { "Cannot compute declarations for project $project" }

        val genTaskName = "generate${variantName.capitalize()}PackageTree"
        val taskName = "count${variantName.capitalize()}DexMethods"

        val gen = project.tasks.register<ApkPackageTreeTask>(genTaskName) { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(variantName)
            t.apkDirectoryProperty.set(artifacts.get(SingleArtifact.APK))
            t.loaderProperty.set(artifacts.getBuiltArtifactsLoader())
            t.mappingFileProperty.set(artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/$variantName/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount/$variantName"))
        }

        project.tasks.register<DexCountOutputTask>(taskName) { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set(variantName)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(true)
        }
    }

    protected fun registerAabTask(variantName: String, artifacts: Artifacts) {
        if (!ext.enabled.get()) {
            return
        }

        check(!ext.printDeclarations.get()) { "Cannot compute declarations for project $project" }

        val taskName = "count${variantName.capitalize()}BundleDexMethods"

        val gen = project.tasks.register<BundlePackageTreeTask>("generate${variantName.capitalize()}BundlePackageTree") { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(variantName)
            t.bundleFileProperty.set(artifacts.get(SingleArtifact.BUNDLE))
            t.loaderProperty.set(artifacts.getBuiltArtifactsLoader())
            t.mappingFileProperty.set(artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/$variantName/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount/$variantName"))
        }

        project.tasks.register<DexCountOutputTask>(taskName) { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set(variantName)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(true)
        }
    }

    protected fun registerAarTask(variantName: String, artifacts: Artifacts) {
        val genTaskName = "generate${variantName.capitalize()}PackageTree"
        val taskName = "count${variantName.capitalize()}DexMethods"

        val gen = project.tasks.register<LibraryPackageTreeTask>(genTaskName) { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(variantName)
            t.aarFileProperty.set(artifacts.get(SingleArtifact.AAR))
            t.loaderProperty.set(artifacts.getBuiltArtifactsLoader())
            t.mappingFileProperty.set(artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/$variantName/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount/$variantName"))
        }

        project.tasks.register<DexCountOutputTask>(taskName) { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set(variantName)
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(true)
        }
    }

    protected open fun registerJarTask() {
        if (!ext.enabled.get()) {
            return
        }

        if (!project.plugins.hasPlugin(JavaPlugin::class.java) && !project.plugins.hasPlugin(JavaLibraryPlugin::class.java)) {
            return
        }

        check(ext.printDeclarations.get()) { "printDeclarations must be true for Java projects: $project" }

        val jarTaskProvider = project.tasks.named("jar", Jar::class.java)

        val gen = project.tasks.register<JarPackageTreeTask>("generatePackageTree") { t ->
            t.description = "Generate dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.outputFileNameProperty.set(jarTaskProvider.flatMap { jarTask -> jarTask.archiveFile.map { it.asFile.nameWithoutExtension } })
            t.jarFileProperty.set(jarTaskProvider.flatMap { it.archiveFile })
            t.packageTreeFileProperty.set(project.layout.buildDirectory.file("intermediates/dexcount/tree.compact.gz"))
            t.outputDirectoryProperty.set(project.layout.buildDirectory.dir("outputs/dexcount"))
        }

        project.tasks.register<DexCountOutputTask>("countDeclaredMethods") { t ->
            t.description = "Output dex method counts"
            t.group       = "Reporting"

            t.configProperty.set(ext)
            t.variantNameProperty.set("")
            t.packageTreeFileProperty.set(gen.flatMap { it.packageTreeFileProperty })
            t.androidProject.set(false)
        }
    }
}
