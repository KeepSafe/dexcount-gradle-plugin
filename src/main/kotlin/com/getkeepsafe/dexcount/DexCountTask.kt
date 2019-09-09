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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.annotation.Nullable

/**
 * The maximum number of method refs and field refs allowed in a single Dex
 * file.
 */
const val MAX_DEX_REFS: Int = 0xFFFF // 65535

open class DexCountTask: DefaultTask() {
    lateinit var tree: PackageTree

    lateinit var inputFileProvider: () -> File

    /**
     * The output of the 'package' task; will be either an APK or an AAR.
     */
    @InputFile
    fun getInputFile(): File {
        return inputFileProvider()
    }

    private val rawInputRepresentation: String
        get() = "${getInputFile()}"

    lateinit var variantOutputName: String

    @Nullable
    lateinit var mappingFileProvider: Provider<FileCollection>

    lateinit var outputFile: File
    lateinit var summaryFile: File
    lateinit var chartDir: File

    lateinit var config: DexCountExtension

    private var startTime   = 0L
    private var ioTime      = 0L
    private var treegenTime = 0L
    private var outputTime  = 0L

    private val printOptions by lazy { // needs to be lazy because config is lateinit
        PrintOptions(
            includeClassCount = config.includeClassCount,
            includeMethodCount = true,
            includeFieldCount = config.includeFieldCount,
            includeTotalMethodCount = config.includeTotalMethodCount,
            teamCityIntegration = config.teamCityIntegration,
            orderByMethodCount = config.orderByMethodCount,
            includeClasses = config.includeClasses,
            printHeader = true,
            maxTreeDepth = config.maxTreeDepth,
            printDeclarations = config.printDeclarations,
            isAndroidProject = isAndroidProject
        )
    }

    private val deobfuscator by lazy {
        val fileCollection = mappingFileProvider.get()
        val file = if (fileCollection.isEmpty) null else fileCollection.singleFile
        when {
            file == null -> Deobfuscator.empty
            !file.exists() -> {
                withStyledOutput(level = LogLevel.DEBUG) {
                    it.println("Mapping file specified at ${file.absolutePath} does not exist, assuming output is not obfuscated.")
                }
                Deobfuscator.empty
            }
            else -> Deobfuscator.create(file)
        }
    }

    private var isInstantRun = false
    private var isAndroidProject = true

    @TaskAction
    open fun execute() {
        try {
            check(config.enabled) { "Tasks should not be executed if the plugin is disabled" }

            if (!inputFileExists()) {
                return
            }

            printPreamble()
            generatePackageTree()
            printSummary()
            printFullTree()
            printChart()
            printTaskDiagnosticData()
            failBuildMaxMethods()
        } catch (e: DexCountException) {
            withStyledOutput(Color.RED, LogLevel.ERROR) { out ->
                out.println("Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues")
                e.printStackTrace(out)
            }
        }
    }

    private fun inputFileExists() = getInputFile().exists()

    private fun printPreamble() {
        if (config.printVersion) {
            val projectName = javaClass.`package`.implementationTitle
            val projectVersion = javaClass.`package`.implementationVersion

            withStyledOutput { out ->
                out.println("Dexcount name:    $projectName")
                out.println("Dexcount version: $projectVersion")
                out.println(    "Dexcount input:   $rawInputRepresentation")
            }
        }
    }

    /**
     * Creates a new PackageTree and populates it with the method and field
     * counts of the current dex/apk file.
     */
    private fun generatePackageTree() {
        val file = getInputFile()

        val isApk = file.extension == "apk"
        val isAar = file.extension == "aar"
        val isJar = file.extension == "jar"
        isAndroidProject = isAar || isApk

        check(isApk || isAar || isJar) { "File extension is unclear: $file" }

        startTime = System.currentTimeMillis()

        val dataList = if (isAndroidProject) DexFile.extractDexData(file, config.dxTimeoutSec) else emptyList()
        val jarFile = when {
            isAar && config.printDeclarations -> JarFile.extractJarFromAar(file)
            isJar && config.printDeclarations -> JarFile.extractJarFromJar(file)
            else -> null
        }

        ioTime = System.currentTimeMillis()
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

        treegenTime = System.currentTimeMillis()

        isInstantRun = dataList.any { it.isInstantRun }
    }

    /**
     * Prints a summary of method and field counts
     * @return
     */
    private fun printSummary() {
        val filename = getInputFile().name

        if (isInstantRun) {
            withStyledOutput(Color.RED) { out ->
                out.println("Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.")
            }
        }

        val color = if (tree.methodCount < 50000) Color.GREEN else Color.YELLOW

        withStyledOutput(color) { out ->
            fun percentUsed(count: Int): String {
                val used = (count.toDouble() / MAX_DEX_REFS) * 100.0
                return String.format("%.2f", used)
            }

            val percentMethodsUsed = percentUsed(tree.methodCount)
            val percentFieldsUsed = percentUsed(tree.fieldCount)
            val percentClassesUsed = percentUsed(tree.classCount)

            val methodsRemaining = Math.max(MAX_DEX_REFS - tree.methodCount, 0)
            val fieldsRemaining = Math.max(MAX_DEX_REFS - tree.fieldCount, 0)
            val classesRemaining = Math.max(MAX_DEX_REFS - tree.classCount, 0)

            if (isAndroidProject) {
                out.println("Total methods in $filename: ${tree.methodCount} ($percentMethodsUsed% used)")
                out.println("Total fields in $filename:  ${tree.fieldCount} ($percentFieldsUsed% used)")
                out.println("Total classes in $filename:  ${tree.classCount} ($percentClassesUsed% used)")
                out.println("Methods remaining in $filename: $methodsRemaining")
                out.println("Fields remaining in $filename:  $fieldsRemaining")
                out.println("Classes remaining in $filename:  $classesRemaining")
            } else {
                out.println("Total methods in $filename: ${tree.methodCountDeclared} ($percentMethodsUsed% used)")
                out.println("Total fields in $filename:  ${tree.fieldCountDeclared} ($percentFieldsUsed% used)")
                out.println("Total classes in $filename:  ${tree.classCountDeclared} ($percentClassesUsed% used)")
            }
        }

        summaryFile.parentFile.mkdirs()
        summaryFile.createNewFile()

        val headers = "methods,fields,classes"
        val counts = "${tree.methodCount},${tree.fieldCount},${tree.classCount}"

        summaryFile.printWriter().use { writer ->
            writer.println(headers)
            writer.println(counts)
        }

        if (printOptions.teamCityIntegration || config.teamCitySlug != null) {
            withStyledOutput { out ->
                val slug = "Dexcount" + (config.teamCitySlug?.let { "_" + it.replace(' ', '_') } ?: "")
                val prefix = "${slug}_$variantOutputName"

                /**
                 * Reports to Team City statistic value
                 * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
                 */
                fun printTeamCityStatisticValue(key: String, value: Int) {
                    out.println("##teamcity[buildStatisticValue key='${prefix}_${key}' value='$value']")
                }

                printTeamCityStatisticValue("ClassCount", tree.classCount)
                printTeamCityStatisticValue("MethodCount", tree.methodCount)
                printTeamCityStatisticValue("FieldCount", tree.fieldCount)
            }
        }
    }

    /**
     * Prints the package tree to the usual outputs/dexcount/variant.txt file.
     */
    private fun printFullTree() {
        outputFile.printStream().use(this::printTreeToAppendable)
        outputTime = System.currentTimeMillis()
    }

    /**
     * Prints the package tree as chart to the outputs/dexcount/${variant}Chart directory.
     */
    private fun printChart() {
        val printOptions = printOptions
        printOptions.includeClasses = true
        File(chartDir, "data.js").printStream().use { out ->
            out.print("var data = ")
            tree.printJson(out, printOptions)
        }

        listOf("chart-builder.js", "d3.v3.min.js", "index.html", "styles.css").forEach { resourceName ->
            val resource = javaClass.getResourceAsStream("/com/getkeepsafe/dexcount/" + resourceName)
            val targetFile = File(chartDir, resourceName)
            resource.copyToFile(targetFile)
        }
    }

    /**
     * Logs the package tree to stdout at {@code LogLevel.DEBUG}, or at the
     * default level if verbose-mode is configured.
     */
    private fun printTaskDiagnosticData() {
        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        val level = if (config.verbose) LogLevel.LIFECYCLE else LogLevel.DEBUG

        withStyledOutput(Color.YELLOW, level) { out ->
            val strBuilder = StringBuilder()
            printTreeToAppendable(strBuilder)

            out.format(strBuilder.toString())
            out.format("\n\nTask runtimes:")
            out.format("--------------")
            out.format("parsing:    ${ioTime - startTime} ms")
            out.format("counting:   ${treegenTime - ioTime} ms")
            out.format("printing:   ${outputTime - treegenTime} ms")
            out.format("total:      ${outputTime - startTime} ms")
            out.format("")
            out.format("input:      {}", rawInputRepresentation)
        }
    }

    /**
     * Fails the build when a user specifies a "max method count" for their current build.
     */
    private fun failBuildMaxMethods() {
        if (config.maxMethodCount > 0 && tree.methodCount > config.maxMethodCount) {
            throw GradleException("The current APK has ${tree.methodCount} methods, the current max is: ${config.maxMethodCount}.")
        }
    }

    private fun printTreeToAppendable(out: Appendable) {
        tree.print(out, config.format as OutputFormat, printOptions)
    }
}
