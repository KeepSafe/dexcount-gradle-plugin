/*
 * Copyright (C) 2015-2020 KeepSafe Software
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

import com.android.build.api.variant.BuiltArtifactsLoader
import com.getkeepsafe.dexcount.thrift.TreeGenOutput
import okio.buffer
import okio.gzip
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.Closeable
import java.io.File
import java.io.PrintStream

abstract class BaseGeneratePackageTreeTask : DefaultTask() {
    /**
     * The plugin configuration, as provided by the 'dexcount' block.
     */
    @get:Nested
    abstract val configProperty: Property<DexCountExtension>

    /**
     * The name of the the method-count report file, without a file extension.
     */
    @get:Input
    abstract val outputFileNameProperty: Property<String>

    /**
     * The full path to the serialized [PackageTree] produced by this task.
     *
     * This file is an intermediate representation, not intended for public
     * consumption.  Its format is likely to change without notice.
     */
    @get:OutputFile
    abstract val packageTreeFileProperty: RegularFileProperty

    /**
     * The directory in which plugin outputs (the report file, summary file,
     * and charts) will be written.
     */
    @get:OutputDirectory
    abstract val outputDirectoryProperty: DirectoryProperty

    @get:Internal
    abstract val inputRepresentation: String

    abstract fun generatePackageTree(): PackageTree

    @get:Internal
    protected open val isAndroidProject: Boolean
        get() = true

    @TaskAction
    open fun execute() {
        val packageTree = generatePackageTree()

        val outputDir = outputDirectoryProperty.get().asFile
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        writeIntermediateThriftFile(packageTree)
        writeSummaryFile(packageTree)
        writeChartFiles(packageTree)
        writeFullTree(packageTree)
    }

    private fun writeIntermediateThriftFile(tree: PackageTree) {
        val thrift = TreeGenOutput(
            tree = PackageTree.toThrift(tree),
            inputRepresentation = inputRepresentation
        )

        val treeFile = packageTreeFileProperty.asFile.get()
        treeFile.parentFile.mkdirs()

        treeFile
            .sink(append = false)
            .gzip()
            .buffer()
            .transport()
            .compactProtocol()
            .use(thrift::write)
    }

    private fun writeSummaryFile(tree: PackageTree) {
        val summaryFile = outputDirectoryProperty.file("summary.csv").get().asFile
        summaryFile.createNewFile()

        val headers = "methods,fields,classes"
        val counts = "${tree.methodCount},${tree.fieldCount},${tree.classCount}"

        summaryFile.printWriter().use { writer ->
            writer.println(headers)
            writer.println(counts)
            writer.flush()
        }
    }

    private fun writeChartFiles(tree: PackageTree) {
        val chartDirectory = outputDirectoryProperty.dir("chart").get().asFile
        chartDirectory.mkdirs()

        val options = PrintOptions.fromDexCountExtension(configProperty.get())
            .toBuilder()
            .setAndroidProject(isAndroidProject)
            .setIncludeClasses(true)
            .build();

        File(chartDirectory, "data.js").printStream().use { out ->
            out.print("var data = ")
            tree.printJson(out, options)
            out.flush()
        }

        listOf("chart-builder.js", "d3.v3.min.js", "index.html", "styles.css").forEach { resourceName ->
            javaClass.getResourceAsStream("/com/getkeepsafe/dexcount/$resourceName")?.use { resource ->
                val targetFile = File(chartDirectory, resourceName)
                targetFile.outputStream().use { output ->
                    resource.copyTo(output)
                    output.flush()
                }
                resource.close()
            }
        }
    }

    private fun writeFullTree(tree: PackageTree) {
        val outputFormat = configProperty.flatMap { it.format }.get()
        val fullCountFileName = outputFileNameProperty.get() + outputFormat.extension
        val fullCountFile = outputDirectoryProperty.file(fullCountFileName).get().asFile

        fullCountFile.printStream().use {
            tree.print(it, outputFormat, PrintOptions.fromDexCountExtension(configProperty.get()).withIsAndroidProject(isAndroidProject))
            it.flush()
        }
    }

    private fun File.printStream(): PrintStream {
        parentFile.mkdirs()
        createNewFile()
        return PrintStream(outputStream())
    }
}

@CacheableTask
abstract class LegacyGeneratePackageTreeTask : BaseGeneratePackageTreeTask() {
    /**
     * The output of the 'package' task; will be either an APK or an AAR.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFileProperty: RegularFileProperty

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mappingFileProvider: Property<FileCollection>

    override var inputRepresentation: String = ""

    override fun generatePackageTree(): PackageTree {
        val config = this.configProperty.get()
        val deobfuscator = Deobfuscator.create(mappingFileProvider.orNull?.singleOrNull())

        val file = inputFileProperty.get().asFile

        inputRepresentation = file.name

        val isApk = file.extension == "apk"
        val isAar = file.extension == "aar"
        val isJar = file.extension == "jar"
        val isAndroidProject = isAar || isApk

        check(isApk || isAar || isJar) { "File extension is unclear: $file" }

        val tree = PackageTree(deobfuscator)

        if (isAndroidProject) {
            DexFile.extractDexData(file).useMany { dataList ->
                dataList.flatMap { it.methodRefs }.forEach(tree::addMethodRef)
                dataList.flatMap { it.fieldRefs }.forEach(tree::addFieldRef)
            }
        }

        val jarFile = when {
            isAar && config.printDeclarations.get() -> JarFile.extractJarFromAar(file)
            isJar && config.printDeclarations.get() -> JarFile.extractJarFromJar(file)
            else -> null
        }

        jarFile?.use { jar ->
            jar.methodRefs.forEach(tree::addDeclaredMethodRef)
            jar.fieldRefs.forEach(tree::addDeclaredFieldRef)
        }

        return tree
    }
}

abstract class ModernGeneratePackageTreeTask : BaseGeneratePackageTreeTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mappingFileProperty: RegularFileProperty

    @get:Internal
    abstract val loaderProperty: Property<BuiltArtifactsLoader>

    @get:Internal
    protected val deobfuscatorProvider: Provider<Deobfuscator>
        get() = mappingFileProperty.map { Deobfuscator.create(it.asFile) }.orElse(Deobfuscator.EMPTY)
}

abstract class ApkishPackageTreeTask : ModernGeneratePackageTreeTask() {

    @get:Internal
    protected abstract val inputFile: File

    override var inputRepresentation: String = ""

    override fun generatePackageTree(): PackageTree {
        val file = inputFile

        inputRepresentation = file.name

        return DexFile.extractDexData(file).useMany { dataList ->
            dataList.forEachWithObject(PackageTree(deobfuscatorProvider.get())) { tree, dexFile ->
                for (ref in dexFile.methodRefs) tree.addMethodRef(ref)
                for (ref in dexFile.fieldRefs) tree.addFieldRef(ref)
            }
        }
    }
}

@CacheableTask
abstract class ApkPackageTreeTask : ApkishPackageTreeTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectoryProperty: DirectoryProperty

    override val inputFile: File
        get() {
            val apkDirectory = apkDirectoryProperty.get()
            val builtApks = checkNotNull(loaderProperty.get().load(apkDirectory))
            return File(builtApks.elements.first().outputFile)
        }
}

@CacheableTask
abstract class BundlePackageTreeTask : ApkishPackageTreeTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundleFileProperty: RegularFileProperty

    override val inputFile: File
        get() = bundleFileProperty.asFile.get()
}

@CacheableTask
abstract class LibraryPackageTreeTask : ApkishPackageTreeTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val aarFileProperty: RegularFileProperty

    override val inputFile: File
        get() = aarFileProperty.asFile.get()
}

// This class is so-named because there is no `ArtifactType.AAR` in AGP 4.1,
// so we have to resort to looking up the bundle task by name, eschewing the
// new API for the time being.  In 4.2 we'll probably be able to consolidate
// this and the APK task above.
@CacheableTask
abstract class Agp41LibraryPackageTreeTask : ModernGeneratePackageTreeTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val aarBundleFileCollection: ConfigurableFileCollection

    override var inputRepresentation: String = ""

    override fun generatePackageTree(): PackageTree {
        if (aarBundleFileCollection.isEmpty) {
            throw GradleException("Expected AAR bundle collection not to be empty")
        }

        val aar = aarBundleFileCollection.first { it.name.endsWith("aar") }
        inputRepresentation = aar.name

        val tree = PackageTree(deobfuscatorProvider.get())

        DexFile.extractDexData(aar).useMany {
            for (dexFile in it) {
                for (ref in dexFile.methodRefs) tree.addMethodRef(ref)
                for (ref in dexFile.fieldRefs) tree.addFieldRef(ref)
            }
        }

        if (configProperty.flatMap { it.printDeclarations }.get()) {
            JarFile.extractJarFromAar(aar).use { jar ->
                for (ref in jar.methodRefs) tree.addDeclaredMethodRef(ref)
                for (ref in jar.fieldRefs) tree.addDeclaredFieldRef(ref)
            }
        }

        return tree
    }
}

@CacheableTask
abstract class JarPackageTreeTask : BaseGeneratePackageTreeTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jarFileProperty: RegularFileProperty

    override var inputRepresentation: String = ""

    override val isAndroidProject: Boolean
        get() = false

    override fun generatePackageTree(): PackageTree {
        val tree = PackageTree(Deobfuscator.EMPTY)
        val jarFile = jarFileProperty.get().asFile
        JarFile.extractJarFromJar(jarFile).use { jar ->
            for (ref in jar.methodRefs) tree.addDeclaredMethodRef(ref)
            for (ref in jar.fieldRefs) tree.addDeclaredFieldRef(ref)
        }

        inputRepresentation = jarFile.name

        return tree
    }
}

internal inline fun <C : Collection<Closeable>, R> C.useMany(fn: (C) -> R): R {
    try {
        return fn(this)
    } finally {
        forEach { it.close() }
    }
}

internal inline fun <T, R> Iterable<T>.forEachWithObject(obj: R, fn: (R, T) -> Unit): R {
    for (elem in this) {
        fn(obj, elem)
    }
    return obj
}
