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

import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.annotation.Nullable
import javax.inject.Inject

private inline fun <reified T> ObjectFactory.property(): Property<T> {
    return property(T::class.java)
}

@Suppress("UnstableApiUsage")
abstract class ModernDexCountTask(
    objects: ObjectFactory
) : DefaultTask() {
    @Input
    val variantNameProperty: Property<String> = objects.property()

    @InputFile
    @Optional
    val mappingFileProperty: RegularFileProperty = objects.fileProperty()

    @Internal
    val loaderProperty: Property<BuiltArtifactsLoader> = objects.property()

    @Nested
    val configProperty: Property<DexCountExtension> = objects.property()

    @OutputDirectory
    val outputDirectoryProperty: DirectoryProperty = objects.directoryProperty()

    @get:Internal
    protected abstract val inputRepresentation: String

    protected abstract fun buildPackageTree(loader: BuiltArtifactsLoader, deobfuscator: Deobfuscator): PackageTree

    @TaskAction
    open fun execute() {
        val loader = loaderProperty.get()
        val deobfuscator = when {
            mappingFileProperty.isPresent -> Deobfuscator.create(mappingFileProperty.get().asFile)
            else -> Deobfuscator.empty
        }
        val tree = buildPackageTree(loader, deobfuscator)

        val reporter = CountReporter(
            packageTree = tree,
            variantName = variantNameProperty.get(),
            outputDir = outputDirectoryProperty.get().asFile,
            styleable = StyleableTaskAdapter(this), // builtApks.variantName, <-- this is buggy
            config = configProperty.get(),
            inputRepresentation = inputRepresentation,
            isAndroidProject = true,
            isInstantRun = false
        )

        reporter.report()
    }
}

@Suppress("UnstableApiUsage")
open class ApkDexCountTask @Inject constructor(
    objects: ObjectFactory
) : ModernDexCountTask(objects) {

    @InputDirectory
    val apkDirectoryProperty: DirectoryProperty = objects.directoryProperty()

    override var inputRepresentation: String = ""

    override fun buildPackageTree(loader: BuiltArtifactsLoader, deobfuscator: Deobfuscator): PackageTree {
        val apkDirectory = apkDirectoryProperty.get()
        val builtApks = checkNotNull(loader.load(apkDirectory))
        val apkFile = File(builtApks.elements.first().outputFile)

        inputRepresentation = apkFile.name

        val dataList = DexFile.extractDexData(apkFile, configProperty.get().dxTimeoutSec)
        try {
            val tree = PackageTree(deobfuscator)
            for (dexFile in dataList) {
                for (ref in dexFile.methodRefs) tree.addMethodRef(ref)
                for (ref in dexFile.fieldRefs) tree.addFieldRef(ref)
            }
            return tree
        } finally {
            dataList.forEach { it.close() }
        }
    }
}

// This class is so-named because there is no `ArtifactType.AAR` in AGP 4.1,
// so we have to resort to looking up the bundle task by name, eschewing the
// new API for the time being.  In 4.2 we'll probably be able to consolidate
// this and the APK task above.
@Suppress("UnstableApiUsage")
open class FourOneLibraryDexCountTask @Inject constructor(
    objects: ObjectFactory
) : ModernDexCountTask(objects) {

    @InputFiles
    val aarBundleFileCollection: ConfigurableFileCollection = objects.fileCollection()

    override var inputRepresentation: String = ""

    override fun buildPackageTree(loader: BuiltArtifactsLoader, deobfuscator: Deobfuscator): PackageTree {
        if (aarBundleFileCollection.isEmpty) {
            throw GradleException("Expected")
        }

        val aar = aarBundleFileCollection.first { it.name.endsWith("aar") }
        inputRepresentation = aar.name

        val tree = PackageTree(deobfuscator)

        val dataList = DexFile.extractDexData(aar, configProperty.get().dxTimeoutSec)
        try {
            for (dexFile in dataList) {
                for (ref in dexFile.methodRefs) tree.addMethodRef(ref)
                for (ref in dexFile.fieldRefs) tree.addFieldRef(ref)
            }
        } finally {
            dataList.forEach { it.close() }
        }

        if (configProperty.get().printDeclarations) {
            JarFile.extractJarFromAar(aar).use { jar ->
                for (ref in jar.methodRefs) tree.addDeclaredMethodRef(ref)
                for (ref in jar.fieldRefs) tree.addDeclaredFieldRef(ref)
            }
        }

        return tree
    }
}

@Suppress("UnstableApiUsage")
abstract class JarDexCountTask @Inject constructor (
    objects: ObjectFactory
) : DefaultTask() {
    @InputFile
    val jarFileProperty: RegularFileProperty = objects.fileProperty()

    @OutputDirectory
    val outputDirectoryProperty: DirectoryProperty = objects.directoryProperty()

    @Nested
    val configProperty: Property<DexCountExtension> = objects.property()

    @TaskAction
    open fun execute() {
        val tree = PackageTree(Deobfuscator.empty)
        val jarFile = jarFileProperty.get().asFile
        JarFile.extractJarFromJar(jarFile).use { jar ->
            for (ref in jar.methodRefs) tree.addDeclaredMethodRef(ref)
            for (ref in jar.fieldRefs) tree.addDeclaredFieldRef(ref)
        }

        val reporter = CountReporter(
            packageTree = tree,
            variantName = "",
            outputDir = outputDirectoryProperty.get().asFile,
            styleable = StyleableTaskAdapter(this),
            config = configProperty.get(),
            inputRepresentation = jarFile.name,
            isAndroidProject = false,
            isInstantRun = false
        )

        reporter.report()
    }
}

@Suppress("UnstableApiUsage")
abstract class LegacyDexCountTask @Inject constructor(
    objects: ObjectFactory
): DefaultTask() {
    /**
     * The output of the 'package' task; will be either an APK or an AAR.
     */
    @InputFile
    val inputFileProperty: RegularFileProperty = objects.fileProperty()

    @Input
    val variantOutputName: Property<String> = objects.property()

    @Nullable
    @InputFiles
    val mappingFileProvider: Property<FileCollection> = objects.property()

    @OutputDirectory
    val outputDirectoryProperty: DirectoryProperty = objects.directoryProperty()

    @Nested
    val configProperty: Property<DexCountExtension> = objects.property()

    @TaskAction
    open fun execute() {
        val config = configProperty.get()
        val deobfuscator = Deobfuscator.create(mappingFileProvider.orNull?.singleOrNull())

        val file = inputFileProperty.get().asFile

        val isApk = file.extension == "apk"
        val isAar = file.extension == "aar"
        val isJar = file.extension == "jar"
        val isAndroidProject = isAar || isApk

        check(isApk || isAar || isJar) { "File extension is unclear: $file" }

        val dataList = if (isAndroidProject) DexFile.extractDexData(file, config.dxTimeoutSec) else emptyList()
        val jarFile = when {
            isAar && config.printDeclarations -> JarFile.extractJarFromAar(file)
            isJar && config.printDeclarations -> JarFile.extractJarFromJar(file)
            else -> null
        }

        val tree: PackageTree
        try {
            tree = PackageTree(deobfuscator)

            dataList.flatMap { it.methodRefs }.forEach(tree::addMethodRef)
            dataList.flatMap { it.fieldRefs }.forEach(tree::addFieldRef)

            if (jarFile != null) {
                jarFile.methodRefs.forEach(tree::addDeclaredMethodRef)
                jarFile.fieldRefs.forEach(tree::addDeclaredFieldRef)
            }
        } finally {
            dataList.forEach { it.close() }
            jarFile?.close()
        }

        val reporter = CountReporter(
            packageTree = tree,
            variantName = variantOutputName.get(),
            outputDir = outputDirectoryProperty.get().asFile,
            styleable = StyleableTaskAdapter(this),
            config = config,
            inputRepresentation = file.name,
            isAndroidProject = isAndroidProject,
            isInstantRun = dataList.any { it.isInstantRun }
        )

        reporter.report()
    }
}
