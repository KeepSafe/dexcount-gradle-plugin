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
import com.android.build.gradle.api.BaseVariantOutput
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import kotlin.Unit
import kotlin.jvm.functions.Function1
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.TaskAction


class ClosureToFunctionAdapter implements Function1<PrintWriter, Unit> {
    private Closure closure

    ClosureToFunctionAdapter(Closure closure) {
        this.closure = closure
    }

    @Override
    Unit invoke(PrintWriter printWriter) {
        closure(printWriter)
        return Unit.INSTANCE
    }
}

abstract class DexMethodCountTaskBase extends DefaultTask {
    /**
     * The maximum number of method refs and field refs allowed in a single Dex
     * file.
     */
    private static final int MAX_DEX_REFS = 0xFFFF // 65535

    PackageTree tree

    String variantOutputName

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

    @TaskAction countMethods() {
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
            withStyledOutput(Style.Error, LogLevel.ERROR) { out ->
                out.println("Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues")
                e.printStackTrace(out)
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

            withStyledOutput(Style.Normal) { out ->
                out.println("Dexcount name:    $projectName")
                out.println("Dexcount version: $projectVersion")
                out.println(    "Dexcount input:   ${rawInputRepresentation()}")
            }
        }
    }

    /**
     * Prints a summary of method and field counts
     * @return
     */
    def printSummary() {
        def filename = fileToCount().name

        if (isInstantRun) {
            withStyledOutput(Style.Failure) { out ->
                out.println("Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.")
            }
        }

        def style
        if (tree.methodCount < 50000) {
            style = Style.Identifier // green
        } else {
            style = Style.Info       // yellow
        }

        withStyledOutput(style) { out ->
            def percentMethodsUsed = percentUsed(tree.methodCount)
            def percentFieldsUsed = percentUsed(tree.fieldCount)
            def percentClassesUsed = percentUsed(tree.classCount)

            def methodsRemaining = Math.max(MAX_DEX_REFS - tree.methodCount, 0)
            def fieldsRemaining = Math.max(MAX_DEX_REFS - tree.fieldCount, 0)
            def classesRemaining = Math.max(MAX_DEX_REFS - tree.classCount, 0)

            out.println("Total methods in ${filename}: ${tree.methodCount} ($percentMethodsUsed% used)")
            out.println("Total fields in ${filename}:  ${tree.fieldCount} ($percentFieldsUsed% used)")
            out.println("Total classes in ${filename}:  ${tree.classCount} ($percentClassesUsed% used)")
            out.println("Methods remaining in ${filename}: $methodsRemaining")
            out.println("Fields remaining in ${filename}:  $fieldsRemaining")
            out.println("Classes remaining in ${filename}:  $classesRemaining")
        }

        if (summaryFile != null) {
            summaryFile.parentFile.mkdirs()
            summaryFile.createNewFile()

            final String headers = "methods,fields,classes"
            final String counts = "${tree.methodCount},${tree.fieldCount},${tree.classCount}"

            summaryFile.withOutputStream { stream ->
                def appendableStream = new PrintStream(stream)
                appendableStream.println(headers)
                appendableStream.println(counts)
            }
        }

        if (getPrintOptions().teamCityIntegration || config.teamCitySlug != null) {
            withStyledOutput(Style.Normal) { out ->
                def prefix = "Dexcount"
                if (config.teamCitySlug != null) {
                    def slug = config.teamCitySlug.replace(' ', '_') // Not sure how TeamCity would handle spaces?
                    prefix = "${prefix}_${slug}"
                }

                printTeamCityStatisticValue(out, "${prefix}_${variantOutputName}_ClassCount", tree.classCount)
                printTeamCityStatisticValue(out, "${prefix}_${variantOutputName}_MethodCount", tree.methodCount)
                printTeamCityStatisticValue(out, "${prefix}_${variantOutputName}_FieldCount", tree.fieldCount)
            }
        }
    }

    /**
     * Reports to Team City statistic value
     * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
     */
    static printTeamCityStatisticValue(PrintWriter out, String key, int value) {
        out.println("##teamcity[buildStatisticValue key='${key}' value='${value}']")
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

        withStyledOutput(Style.Info, level) { out ->
            def strBuilder = new StringBuilder()
            print(tree, strBuilder)

            out.format(strBuilder.toString())
            out.format("\n\nTask runtimes:")
            out.format("--------------")
            out.format("parsing:    ${ioTime - startTime} ms")
            out.format("counting:   ${treegenTime - ioTime} ms")
            out.format("printing:   ${outputTime - treegenTime} ms")
            out.format("total:      ${outputTime - startTime} ms")
            out.format("")
            out.format("input:      {}", rawInputRepresentation())
        }
    }

    def print(PackageTree tree, Appendable out) {
        tree.print(out, config.format, getPrintOptions())
    }

    def withStyledOutput(
        Style style,
        LogLevel level = null,
        @ClosureParams(value = SimpleType, options = ['java.io.PrintWriter']) Closure closure) {

        ColorConsoleKt.withStyledOutput(this, style, level, new ClosureToFunctionAdapter(closure))
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
        def file = fileToCount()

        if (file == null) {
            throw new AssertionError("file is null: rawInput=${rawInputRepresentation()}")
        }

        startTime = System.currentTimeMillis()

        // Create a de-obfuscator based on the current Proguard mapping file.
        // If none is given, we'll get a default mapping.
        def deobs = getDeobfuscator()

        def dataList = DexFile.extractDexData(file, config.dxTimeoutSec)

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
            dataList*.close()
        }

        treegenTime = System.currentTimeMillis()

        isInstantRun = dataList.any { it.isInstantRun }
    }

    def getPrintOptions() {
        return new PrintOptions(
                includeClassCount: config.includeClassCount,
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
            withStyledOutput(Style.Normal, LogLevel.DEBUG) {
                it.println("Mapping file specified at ${mappingFile.absolutePath} does not exist, assuming output is not obfuscated.")
            }
            mappingFile = null
        }

        return Deobfuscator.create(mappingFile)
    }

    def checkIfApkExists() {
        def file = fileToCount()
        return file != null && file.exists()
    }

    abstract File fileToCount()

    abstract String rawInputRepresentation()

    /**
     * Fails the build when a user specifies a "max method count" for their current build.
     */
    def failBuildMaxMethods() {
        if (config.maxMethodCount > 0 && tree.methodCount > config.maxMethodCount) {
            throw new GradleException(String.format("The current APK has %d methods, the current max is: %d.", tree.methodCount, config.maxMethodCount))
        }
    }
}

@ParallelizableTask
class ModernMethodCountTask extends DexMethodCountTaskBase {
    /**
     * The output directory of the 'package' task; will contain an
     * APK or an AAR.  Will be null for Android projects using
     * build-tools versions before 3.0.
     */
    @InputDirectory
    File inputDirectory

    @Override
    File fileToCount() {
        def fileList = inputDirectory.listFiles(new ApkFilenameFilter())
        return fileList.length > 0 ? fileList[0] : null
    }

    @Override
    String rawInputRepresentation() {
        return "$inputDirectory"
    }

    // Tried to use a closure for this, but Groovy cannot decide between java.io.FilenameFilter
    // and java.io.FileFilter.  If we have to make it ugly, might as well make it efficient.
    static class ApkFilenameFilter implements FilenameFilter {
        @Override
        boolean accept(File dir, String name) {
            return name != null && name.endsWith(".apk")
        }
    }
}

@ParallelizableTask
class LegacyMethodCountTask extends DexMethodCountTaskBase {

    BaseVariantOutput variantOutput

    @Override
    File fileToCount() {
        return variantOutput.outputFile
    }

    @Override
    String rawInputRepresentation() {
        if (variantOutput == null) {
            return "variantOutput=null"
        } else {
            return "variantOutput{name=${variantOutput.name} outputFile=${variantOutput.outputFile}}"
        }
    }
}
