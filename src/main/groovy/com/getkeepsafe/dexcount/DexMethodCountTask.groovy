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

import com.android.annotations.Nullable
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.api.BaseVariantOutput
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.TaskAction

class DexMethodCountTask extends DefaultTask {
    /**
     * The maximum number of method refs and field refs allowed in a single Dex
     * file.
     */
    private static final int MAX_DEX_REFS = 0xFFFF

    PackageTree tree

    BaseVariantOutput apkOrDex

    @Nullable
    File mappingFile

    File outputFile
    File summaryFile
    File chartDir

    DexMethodCountExtension config

    long startTime
    long ioTime
    long treegenTime
    long outputTime

    boolean isInstantRun

    @TaskAction
    void countMethods() {
        try {
            if (!checkIfApkExists()) {
                return
            }

            printPreamble()
            generatePackageTree()
            printSummary()
            printFullTree()
            printChart()
            printTaskDiagnosticData()
            failBuildMaxMethods()
        } catch (DexCountException e) {
            withStyledOutput() { out ->
                out.error("Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues", e)
            }
        }
    }

    static percentUsed(int count) {
        def used = ((double) count / MAX_DEX_REFS) * 100.0
        return sprintf("%.2f", used)
    }

    def printPreamble() {
        if (config.printVersion) {
            def projectName = getClass().package.implementationTitle
            def projectVersion = getClass().package.implementationVersion

            withStyledOutput() { out ->
                out.lifecycle("Dexcount name:    $projectName")
                out.lifecycle("Dexcount version: $projectVersion")
            }
        }
    }

    /**
     * Prints a summary of method and field counts
     * @return
     */
    def printSummary() {
        def filename = apkOrDex.outputFile.name

        if (isInstantRun) {
            withStyledOutput() { out ->
                out.warn("Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.")
            }
        }

        withStyledOutput() { out ->
            def percentMethodsUsed = percentUsed(tree.methodCount)
            def percentFieldsUsed = percentUsed(tree.fieldCount)

            def methodsRemaining = Math.max(MAX_DEX_REFS - tree.methodCount, 0)
            def fieldsRemaining = Math.max(MAX_DEX_REFS - tree.fieldCount, 0)

            out.warn("Total methods in ${filename}: ${tree.methodCount} ($percentMethodsUsed% used)")
            out.warn("Total fields in ${filename}:  ${tree.fieldCount} ($percentFieldsUsed% used)")
            out.warn("Methods remaining in ${filename}: $methodsRemaining")
            out.warn("Fields remaining in ${filename}:  $fieldsRemaining")
        }

        if (summaryFile != null) {
            summaryFile.parentFile.mkdirs()
            summaryFile.createNewFile()

            final String headers = "methods,fields"
            final String counts = "${tree.methodCount},${tree.fieldCount}"

            summaryFile.withOutputStream { stream ->
                def appendableStream = new PrintStream(stream)
                appendableStream.println(headers)
                appendableStream.println(counts)
            }
        }

        if (getPrintOptions().teamCityIntegration || config.teamCitySlug != null) {
            withStyledOutput() { out ->
                def prefix = "Dexcount"
                if (config.teamCitySlug != null) {
                    def slug = config.teamCitySlug.replace(' ', '_') // Not sure how TeamCity would handle spaces?
                    prefix = "${prefix}_${slug}"
                }

                printTeamCityStatisticValue(out, "${prefix}_${apkOrDex.name}_MethodCount", tree.methodCount)
                printTeamCityStatisticValue(out, "${prefix}_${apkOrDex.name}_FieldCount", tree.fieldCount)
            }
        }
    }

    /**
     * Reports to Team City statistic value
     * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
     */
    static printTeamCityStatisticValue(Logger out, String key, int value) {
        out.lifecycle("##teamcity[buildStatisticValue key='${key}' value='${value}']")
    }

    /**
     * Prints the package tree to the usual outputs/dexcount/variant.txt file.
     */
    def printFullTree() {
        printToFile(outputFile) { PrintStream out ->
            print(tree, out)
        }
        outputTime = System.currentTimeMillis()
    }

    /**
     * Prints the package tree as chart to the outputs/dexcount/${variant}Chart directory.
     */
    def printChart() {
        def printOptions = getPrintOptions()
        printOptions.includeClasses = true
        printToFile(new File(chartDir, "data.js")) { PrintStream out ->
            out.print("var data = ")
            tree.printJson(out, printOptions)
        }

        ["chart-builder.js", "d3.v3.min.js", "index.html", "styles.css"].each { String resourceName ->
            def resource = getClass().getResourceAsStream("/com/getkeepsafe/dexcount/" + resourceName)
            def targetFile = new File(chartDir, resourceName)
            targetFile.write resource.text
        }
    }

    /**
     * Logs the package tree to stdout at {@code LogLevel.DEBUG}, or at the
     * default level if verbose-mode is configured.
     */
    def printTaskDiagnosticData() {
        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        def level = config.verbose ? LogLevel.LIFECYCLE : LogLevel.DEBUG

        withStyledOutput() { out ->
            def strBuilder = new StringBuilder()
            print(tree, strBuilder)

            out.log(level, strBuilder.toString())
            out.log(level, "\n\nTask runtimes:")
            out.log(level, "--------------")
            out.log(level, "parsing:    ${ioTime - startTime} ms")
            out.log(level, "counting:   ${treegenTime - ioTime} ms")
            out.log(level, "printing:   ${outputTime - treegenTime} ms")
            out.log(level, "total:      ${outputTime - startTime} ms")
        }
    }

    def print(PackageTree tree, Appendable out) {
        tree.print(out, config.format, getPrintOptions())
    }

    def withStyledOutput(@ClosureParams(value = SimpleType, options = ['org.gradle.api.logging.Logger']) Closure closure) {
        // TODO: Actually make this stylized when we have our own solution: https://github.com/KeepSafe/dexcount-gradle-plugin/issues/124
        closure(getLogger())
    }

    def printToFile(
            File file,
            @ClosureParams(value = SimpleType, options = ['java.io.PrintStream']) Closure closure) {
        if (outputFile != null) {
            file.parentFile.mkdirs()
            file.createNewFile()
            file.withOutputStream { stream ->
                def out = new PrintStream(stream)
                closure(out)
                out.flush()
                out.close()
            }
        }
    }

    /**
     * Creates a new PackageTree and populates it with the method and field
     * counts of the current dex/apk file.
     */
    def generatePackageTree() {
        startTime = System.currentTimeMillis()

        // Create a de-obfuscator based on the current Proguard mapping file.
        // If none is given, we'll get a default mapping.
        def deobs = getDeobfuscator()

        def dataList = DexFile.extractDexData(apkOrDex.outputFile, config.dxTimeoutSec)

        ioTime = System.currentTimeMillis()
        try {
            tree = new PackageTree(deobs)

            dataList*.getMethodRefs().flatten().each {
                tree.addMethodRef(it)
            }

            dataList*.getFieldRefs().flatten().each {
                tree.addFieldRef(it)
            }
        } finally {
            dataList*.dispose()
        }

        treegenTime = System.currentTimeMillis()

        isInstantRun = dataList.any { it.isInstantRun }
    }

    def getPrintOptions() {
        return new PrintOptions(
                includeMethodCount: true,
                includeFieldCount: config.includeFieldCount,
                includeTotalMethodCount: config.includeTotalMethodCount,
                teamCityIntegration: config.teamCityIntegration,
                orderByMethodCount: config.orderByMethodCount,
                includeClasses: config.includeClasses,
                printHeader: true,
                maxTreeDepth: config.maxTreeDepth)
    }

    def getDeobfuscator() {
        if (mappingFile != null && !mappingFile.exists()) {
            withStyledOutput() {
                it.debug("Mapping file specified at ${mappingFile.absolutePath} does not exist, assuming output is not obfuscated.")
            }
            mappingFile = null
        }

        return Deobfuscator.create(mappingFile)
    }

    def checkIfApkExists() {
        return apkOrDex != null && apkOrDex.outputFile != null && apkOrDex.outputFile.exists()
    }

    /**
     * Fails the build when a user specifies a "max method count" for their current build.
     */
    def failBuildMaxMethods() {
        if (config.maxMethodCount > 0 && tree.methodCount > config.maxMethodCount) {
            throw new GradleException(String.format("The current APK has %d methods, the current max is: %d.", tree.methodCount, config.maxMethodCount))
        }
    }
}
