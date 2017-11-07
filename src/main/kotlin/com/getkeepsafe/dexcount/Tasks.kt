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

import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.annotation.Nullable

/**
 * The maximum number of method refs and field refs allowed in a single Dex
 * file.
 */
const val MAX_DEX_REFS: Int = 0xFFFF // 65535

open class ModernMethodCountTask: DexMethodCountTaskBase() {

    lateinit var inputFileProvider: () -> File

    /**
     * The output of the 'package' task; will be either an APK or an AAR.
     */
    @InputFile
    fun getInputFile(): File {
        return inputFileProvider()
    }

    override val fileToCount: File?
        get() = getInputFile()

    override val rawInputRepresentation: String
        get() = "${getInputFile()}"
}

open class LegacyMethodCountTask: DexMethodCountTaskBase() {

    /**
     * The output of the 'assemble' task, on AGP < 3.0.  Its 'outputFile'
     * property will point to either an AAR or an APK.
     *
     * We don't use [@Input][org.gradle.api.tasks.Input] here because
     * [BaseVariantOutput] implementations aren't is [Serializable].  This
     * makes Gradle's input cache routines crash, sometimes.
     */

    lateinit var variantOutput: BaseVariantOutput

    override val fileToCount: File?
        get() = variantOutput.outputFile

    override val rawInputRepresentation: String
        get() = "variantOutput={name = ${variantOutput.name}, outputFile = ${variantOutput.outputFile}}"
}

abstract class DexMethodCountTaskBase: DefaultTask() {
    lateinit var tree: PackageTree

    lateinit var variantOutputName: String

    @Nullable
    var mappingFile: File? = null

    lateinit var outputFile: File
    lateinit var summaryFile: File
    lateinit var chartDir: File

    lateinit var config: DexMethodCountExtension

    private var startTime   = 0L
    private var ioTime      = 0L
    private var treegenTime = 0L
    private var outputTime  = 0L

    private val printOptions by lazy { // needs to be lazy because config is lateinit
        PrintOptions().apply {
            includeClassCount       = config.includeClassCount
            includeMethodCount      = true
            includeFieldCount       = config.includeFieldCount
            includeTotalMethodCount = config.includeTotalMethodCount
            teamCityIntegration     = config.teamCityIntegration
            orderByMethodCount      = config.orderByMethodCount
            includeClasses          = config.includeClasses
            printHeader             = true
            maxTreeDepth            = config.maxTreeDepth
        }
    }

    private val deobfuscator by lazy {
        val file = mappingFile
        if (file != null && !file.exists()) {
            withStyledOutput(level = LogLevel.DEBUG) {
                it.println("Mapping file specified at ${file.absolutePath} does not exist, assuming output is not obfuscated.")
            }
            mappingFile = null
        }

        Deobfuscator.create(mappingFile)
    }

    private var isInstantRun = false

    abstract val fileToCount: File?

    abstract val rawInputRepresentation: String

    @TaskAction
    open fun countMethods() {
        try {
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

    private fun inputFileExists() = fileToCount?.exists() ?: false

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
        val file = fileToCount ?: throw AssertionError("file is null: rawInput=${rawInputRepresentation}")

        startTime = System.currentTimeMillis()

        val dataList = DexFile.extractDexData(file, config.dxTimeoutSec)

        ioTime = System.currentTimeMillis()
        try {
            tree = PackageTree(deobfuscator)

            dataList.flatMap { it.methodRefs }.forEach(tree::addMethodRef)
            dataList.flatMap { it.fieldRefs }.forEach(tree::addFieldRef)
        } finally {
            dataList.forEach { it.close() }
        }

        treegenTime = System.currentTimeMillis()

        isInstantRun = dataList.any { it.isInstantRun }
    }

    /**
     * Prints a summary of method and field counts
     * @return
     */
    private fun printSummary() {
        val filename = fileToCount?.name

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

            out.println("Total methods in $filename: ${tree.methodCount} ($percentMethodsUsed% used)")
            out.println("Total fields in $filename:  ${tree.fieldCount} ($percentFieldsUsed% used)")
            out.println("Total classes in $filename:  ${tree.classCount} ($percentClassesUsed% used)")
            out.println("Methods remaining in $filename: $methodsRemaining")
            out.println("Fields remaining in $filename:  $fieldsRemaining")
            out.println("Classes remaining in $filename:  $classesRemaining")
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
