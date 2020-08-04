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
import com.microsoft.thrifty.protocol.CompactProtocol
import com.microsoft.thrifty.transport.BufferTransport
import okio.Buffer
import okio.gzip
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
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
import java.io.File
import java.io.PrintStream
import javax.annotation.Nullable
import javax.inject.Inject

fun DexCountExtension.toPrintOptions(isAndroidProject: Boolean = true): PrintOptions {
    return PrintOptions(
        includeClassCount = includeClassCount,
        includeMethodCount = true,
        includeFieldCount = includeFieldCount,
        includeTotalMethodCount = includeTotalMethodCount,
        teamCityIntegration = teamCityIntegration,
        orderByMethodCount = orderByMethodCount,
        includeClasses = includeClasses,
        printHeader = true,
        maxTreeDepth = maxTreeDepth,
        printDeclarations = printDeclarations,
        isAndroidProject = isAndroidProject
    )
}

abstract class BaseGeneratePackageTreeTask constructor(
    objects: ObjectFactory
) : DefaultTask() {
    /**
     * The plugin configuration, as provided by the 'dexcount' block.
     */
    @Nested
    val configProperty: Property<DexCountExtension> = objects.property()

    /**
     * The name of the the method-count report file, without a file extension.
     */
    @Input
    val outputFileNameProperty: Property<String> = objects.property()

    /**
     * The full path to the serialized [PackageTree] produced by this task.
     *
     * This file is an intermediate representation, not intended for public
     * consumption.  Its format is likely to change without notice.
     */
    @OutputFile
    val packageTreeFileProperty: RegularFileProperty = objects.fileProperty()

    /**
     * The directory in which plugin outputs (the report file, summary file,
     * and charts) will be written.
     */
    @OutputDirectory
    val outputDirectoryProperty: DirectoryProperty = objects.directoryProperty()

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
        val buffer = Buffer()
        val transport = BufferTransport(buffer)
        val protocol = CompactProtocol(transport)

        thrift.write(protocol)
        protocol.flush()

        val treeFile = packageTreeFileProperty.asFile.get()
        treeFile.parentFile.mkdirs()
        treeFile.delete()

        treeFile.sink().gzip().use { out ->
            buffer.readAll(out)
            out.flush()
        }
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

        val options = configProperty.get()
            .toPrintOptions(isAndroidProject)
            .copy(includeClasses = true)

        File(chartDirectory, "data.js").printStream().use { out ->
            out.print("var data = ")
            tree.printJson(out, options)
            out.flush()
        }

        listOf("chart-builder.js", "d3.v3.min.js", "index.html", "styles.css").forEach { resourceName ->
            javaClass.getResourceAsStream("/com/getkeepsafe/dexcount/$resourceName").use { resource ->
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
        val outputFormat = configProperty.get().format as OutputFormat
        val fullCountFileName = outputFileNameProperty.get() + outputFormat.extension
        val fullCountFile = outputDirectoryProperty.file(fullCountFileName).get().asFile

        fullCountFile.printStream().use {
            tree.print(it, outputFormat, configProperty.get().toPrintOptions(isAndroidProject))
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
abstract class LegacyGeneratePackageTreeTask @Inject constructor(
    objects: ObjectFactory
) : BaseGeneratePackageTreeTask(objects) {
    /**
     * The output of the 'package' task; will be either an APK or an AAR.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val inputFileProperty: RegularFileProperty = objects.fileProperty()

    @Nullable
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val mappingFileProvider: Property<FileCollection> = objects.property()

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

        return tree
    }
}

abstract class ModernGeneratePackageTreeTask constructor(
    objects: ObjectFactory
) : BaseGeneratePackageTreeTask(objects) {
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    val mappingFileProperty: RegularFileProperty = objects.fileProperty()

    @Internal
    val loaderProperty: Property<BuiltArtifactsLoader> = objects.property()

    @Internal
    protected val deobfuscatorProvider: Provider<Deobfuscator> = mappingFileProperty
        .map { Deobfuscator.create(it.asFile) }
        .orElse(Deobfuscator.empty)
}

abstract class ApkishPackageTreeTask constructor(
    objects: ObjectFactory
) : ModernGeneratePackageTreeTask(objects) {

    @get:Internal
    protected abstract val inputFile: File

    override var inputRepresentation: String = ""

    override fun generatePackageTree(): PackageTree {
        val file = inputFile

        inputRepresentation = file.name

        val dataList = DexFile.extractDexData(file, configProperty.get().dxTimeoutSec)
        try {
            val tree = PackageTree(deobfuscatorProvider.get())
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

@CacheableTask
abstract class ApkPackageTreeTask @Inject constructor(
    objects: ObjectFactory
) : ApkishPackageTreeTask(objects) {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val apkDirectoryProperty: DirectoryProperty = objects.directoryProperty()

    override val inputFile: File
        get() {
            val apkDirectory = apkDirectoryProperty.get()
            val builtApks = checkNotNull(loaderProperty.get().load(apkDirectory))
            return File(builtApks.elements.first().outputFile)
        }
}

@CacheableTask
abstract class BundlePackageTreeTask @Inject constructor(
    objects: ObjectFactory
) : ApkishPackageTreeTask(objects) {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val bundleFileProperty: RegularFileProperty = objects.fileProperty()

    override val inputFile: File
        get() = bundleFileProperty.asFile.get()
}

// This class is so-named because there is no `ArtifactType.AAR` in AGP 4.1,
// so we have to resort to looking up the bundle task by name, eschewing the
// new API for the time being.  In 4.2 we'll probably be able to consolidate
// this and the APK task above.
@CacheableTask
abstract class Agp41LibraryPackageTreeTask @Inject constructor(
    objects: ObjectFactory
) : ModernGeneratePackageTreeTask(objects) {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val aarBundleFileCollection: ConfigurableFileCollection = objects.fileCollection()

    @get:Input
    abstract val buildToolsVersion: Property<String>

    override var inputRepresentation: String = ""

    override fun generatePackageTree(): PackageTree {
        if (aarBundleFileCollection.isEmpty) {
            throw GradleException("Expected AAR bundle collection not to be empty")
        }

        val aar = aarBundleFileCollection.first { it.name.endsWith("aar") }
        inputRepresentation = aar.name

        val tree = PackageTree(deobfuscatorProvider.get())

        val dataList = DexFile.extractDexData(aar, configProperty.get().dxTimeoutSec, buildToolsVersion.get())
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

@CacheableTask
abstract class JarPackageTreeTask @Inject constructor(
    objects: ObjectFactory
) : BaseGeneratePackageTreeTask(objects) {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val jarFileProperty: RegularFileProperty = objects.fileProperty()

    override var inputRepresentation: String = ""

    override val isAndroidProject: Boolean
        get() = false

    override fun generatePackageTree(): PackageTree {
        val tree = PackageTree(Deobfuscator.empty)
        val jarFile = jarFileProperty.get().asFile
        JarFile.extractJarFromJar(jarFile).use { jar ->
            for (ref in jar.methodRefs) tree.addDeclaredMethodRef(ref)
            for (ref in jar.fieldRefs) tree.addDeclaredFieldRef(ref)
        }

        inputRepresentation = jarFile.name

        return tree
    }
}
