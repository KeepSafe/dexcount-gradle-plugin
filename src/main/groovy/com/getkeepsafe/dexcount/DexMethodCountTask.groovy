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
import com.android.dexdeps.HasDeclaringClass
import com.android.dexdeps.Output
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

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

    def DexMethodCountExtension config

    @TaskAction
    void countMethods() {
        generatePackageTree()
        printSummary()
        printFullTree()
        printTaskDiagnosticData()
    }

    /**
     * Prints a summary of method and field counts
     * @return
     */
    def printSummary() {
        def filename = apkOrDex.outputFile.name
        withStyledOutput(StyledTextOutput.Style.Info) { out ->
            out.println("Total methods in ${filename}: ${tree.methodCount}")
            out.println("Total fields in ${filename}:  ${tree.fieldCount}")
            out.println("Methods remaining in ${filename}: ${MAX_DEX_REFS - tree.methodCount}")
            out.println("Fields remaining in ${filename}:  ${MAX_DEX_REFS - tree.fieldCount}")
        }

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
    }

    /**
     * Prints the package tree to the usual outputs/dexcount/variant.txt file.
     */
    def printFullTree() {
        if (outputFile != null) {
            outputFile.parentFile.mkdirs()
            outputFile.createNewFile()
            outputFile.withOutputStream { stream ->
                def appendableStream = new PrintStream(stream)
                print(tree, appendableStream)
                appendableStream.flush()
                appendableStream.close()
            }
        }
    }

    /**
     * Logs the package tree to stdout at {@code LogLevel.DEBUG}, or at the
     * default level if verbose-mode is configured.
     */
    def printTaskDiagnosticData() {
        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        def level = config.verbose ? null : LogLevel.DEBUG

        withStyledOutput(StyledTextOutput.Style.Info, level) { out ->
            print(tree, out)
        }
    }

    def print(PackageTree tree, Appendable writer) {
        tree.print(writer, config.format, getPrintOptions())
    }

    private void withStyledOutput(
            StyledTextOutput.Style style,
            LogLevel level = null,
            @ClosureParams(value = SimpleType, options = ['org.gradle.logging.StyledTextOutput']) Closure closure) {
        def factory = services.get(StyledTextOutputFactory)
        def output = level == null ? factory.create('dexcount') : factory.create('dexcount', level)

        closure(output.withStyle(style))
    }

    /**
     * Creates a new PackageTree and populates it with the method and field
     * counts of the current dex/apk file.
     */
    private def generatePackageTree() {
        // Create a de-obfuscator based on the current Proguard mapping file.
        // If none is given, we'll get a default mapping.
        def deobs = getDeobfuscator()

        def dataList = DexFile.extractDexData(apkOrDex.outputFile)
        try {
            tree = new PackageTree()

            refListToClassNames(dataList*.getMethodRefs(), deobs).each {
                tree.addMethodRef(it)
            }

            refListToClassNames(dataList*.getFieldRefs(), deobs).each {
                tree.addFieldRef(it)
            }
        } finally {
            dataList*.dispose()
        }
    }

    static refListToClassNames(List<List<HasDeclaringClass>> refs, Deobfuscator deobfuscator) {
        return refs.flatten().collect { ref ->
            def descriptor = ref.getDeclClassName()
            def dot = Output.descriptorToDot(descriptor)
            dot = deobfuscator.deobfuscate(dot)
            if (dot.indexOf('.') == -1) {
                // Classes in the unnamed package (e.g. primitive arrays)
                // will not appear in the output in the current PackageTree
                // implementation if classes are not included.  To work around,
                // we make an artificial package named "<unnamed>".
                dot = "<unnamed>." + dot
            }
            return dot
        }
    }

    private def getPrintOptions() {
        return new PrintOptions(
                includeMethodCount: true,
                includeFieldCount: config.includeFieldCount,
                includeTotalMethodCount: config.includeTotalMethodCount,
                orderByMethodCount: config.orderByMethodCount,
                includeClasses: config.includeClasses,
                printHeader: true)
    }

    private def getDeobfuscator() {
        if (mappingFile != null && !mappingFile.exists()) {
            withStyledOutput(StyledTextOutput.Style.Normal, LogLevel.DEBUG) {
                it.println("Mapping file specified at ${mappingFile.absolutePath} does not exist, assuming output is not obfuscated.")
            }
            mappingFile = null
        }

        return Deobfuscator.create(mappingFile)
    }
}
