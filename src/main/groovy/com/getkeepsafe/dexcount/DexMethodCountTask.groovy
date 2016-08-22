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
import com.getkeepsafe.dexcount.ANSIConsole.Color
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.nativeintegration.services.NativeServices

import static com.getkeepsafe.dexcount.ANSIConsole.Color.*
import static org.gradle.api.logging.LogLevel.*

class DexMethodCountTask extends DefaultTask {
    /**
     * The maximum number of method refs and field refs allowed in a single Dex
     * file.
     */
    private static final int MAX_DEX_REFS = 0xFFFF;

    def PackageTree tree;

    def BaseVariantOutput apkOrDex

    @Nullable
    def File mappingFile

    @OutputFile
    def File outputFile

    @OutputFile
    def File summaryFile

    @OutputDirectory
    def File chartDir

    def DexMethodCountExtension config

    def long startTime
    def long ioTime
    def long treegenTime
    def long outputTime

    def boolean isInstantRun

    @TaskAction
    void countMethods() {
        try {
            printPreamble()
            generatePackageTree()
            printSummary()
            printFullTree()
            printChart()
            printTaskDiagnosticData()
        } catch (DexCountException e) {
            log(ERROR, RED, "Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues", e)
        }
    }

    static def percentUsed(int count) {
        def used = ((double) count / MAX_DEX_REFS) * 100.0
        return sprintf("%.2f", used)
    }

    def printPreamble() {
        if (config.printVersion) {
            def projectName = getClass().package.implementationTitle
            def projectVersion = getClass().package.implementationVersion

            log("Dexcount name:    $projectName")
            log("Dexcount version: $projectVersion")
        }
    }

    /**
     * Prints a summary of method and field counts
     * @return
     */
    def printSummary() {
        def filename = colorize(CYAN, apkOrDex.outputFile.name)

        if (isInstantRun) {
            log(WARN, YELLOW, "Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.")
        }

        Color color
        if (tree.methodCount < 50000) {
            color = GREEN
        } else {
            color = RED
        }

        def percentMethodsUsed = colorize(YELLOW, percentUsed(tree.methodCount))
        def percentFieldsUsed = colorize(YELLOW, percentUsed(tree.fieldCount))

        def methodsRemaining = colorize(color, "${Math.max(MAX_DEX_REFS - tree.methodCount, 0)}")
        def fieldsRemaining = colorize(color, "${Math.max(MAX_DEX_REFS - tree.fieldCount, 0)}")

        def methodCount = colorize(color, "$tree.methodCount")
        def fieldCount = colorize(color, "$tree.fieldCount")
        log(WARN, YELLOW, "Total methods in $filename: $methodCount ($percentMethodsUsed% used)")
        log(WARN, YELLOW, "Total fields in $filename:  $fieldCount ($percentFieldsUsed% used)")
        log(WARN, YELLOW, "Methods remaining in $filename: $methodsRemaining")
        log(WARN, YELLOW, "Fields remaining in $filename:  $fieldsRemaining")

        if (summaryFile != null) {
            summaryFile.parentFile.mkdirs()
            summaryFile.createNewFile()

            final String headers = "methods,fields";
            final String counts = "${tree.methodCount},${tree.fieldCount}";

            summaryFile.withOutputStream { stream ->
                def appendableStream = new PrintStream(stream)
                appendableStream.println(headers)
                appendableStream.println(counts);
            }
        }

        if (getPrintOptions().teamCityIntegration) {
            printTeamCityStatisticValue(getLogger(), "DexCount_${apkOrDex.name}_MethodCount", tree.methodCount.toString())
            printTeamCityStatisticValue(getLogger(), "DexCount_${apkOrDex.name}_FieldCount", tree.fieldCount.toString())
        }
    }

    /**
     * Reports to Team City statistic value
     * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
     */
    def printTeamCityStatisticValue(Logger out, String key, String value) {
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
            tree.printJson(out, printOptions);
        }

        ["chart-builder.js", "d3.v3.min.js", "index.html", "styles.css"].each { String resourceName ->
            def resource = getClass().getResourceAsStream("/com/getkeepsafe/dexcount/" + resourceName);
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
        def level = config.verbose ? LIFECYCLE : DEBUG

        def strBuilder = new StringBuilder()
        print(tree, strBuilder)

        log(level, MAGENTA, strBuilder.toString())
        log(level, MAGENTA, "\n\nTask runtimes:")
        log(level, MAGENTA, "--------------")
        log(level, MAGENTA, "parsing:    ${ioTime - startTime} ms")
        log(level, MAGENTA, "counting:   ${treegenTime - ioTime} ms")
        log(level, MAGENTA, "printing:   ${outputTime - treegenTime} ms")
        log(level, MAGENTA, "total:      ${outputTime - startTime} ms")
    }

    def print(PackageTree tree, Appendable out) {
        tree.print(out, config.format, getPrintOptions())
    }

    private void log(LogLevel level = LIFECYCLE, Color color = null, String message, Throwable t = null) {
        if (color == null) {
            getLogger().log(level, message, t as Throwable)
        } else {
            getLogger().log(level, colorize(color, message), t as Throwable)
        }
    }

    private String colorize(Color color, boolean bright = false, String str) {
        if (project.gradle.startParameter.consoleOutput == ConsoleOutput.Plain) {
            return str
        }

        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
        ConsoleMetaData consoleMetaData = consoleDetector.getConsole();
        if (consoleMetaData == null || !consoleMetaData.isStdOut()) {
            return str
        }

        return ANSIConsole.colorize(color, bright, str)
    }

    private void printToFile(
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
    @VisibleForTesting
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

    private def getPrintOptions() {
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

    private def getDeobfuscator() {
        if (mappingFile != null && !mappingFile.exists()) {
            log(DEBUG, YELLOW, "Mapping file specified at ${mappingFile.absolutePath} does not exist, assuming output is not obfuscated.")
            mappingFile = null
        }

        return Deobfuscator.create(mappingFile)
    }
}
