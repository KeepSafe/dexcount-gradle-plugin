/*
 * Copyright (C) 2015 KeepSafe Software
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
    @Input
    def BaseVariantOutput apkOrDex

    @Nullable
    def File mappingFile

    @OutputFile
    def File outputFileTxt

    @OutputFile
    def File outputFileCSV

    def DexMethodCountExtension config

    @TaskAction
    void countMethods() {
        def tree = getPackageTree()
        def methodCount = tree.getMethodCount()
        def fieldCount = tree.getFieldCount()

        if (outputFileTxt != null) {
            outputFileTxt.parentFile.mkdirs()
            outputFileTxt.createNewFile()
            outputFileTxt.withOutputStream { stream ->
                def appendableStream = new PrintStream(stream)
                print(tree, appendableStream)
                appendableStream.flush()
                appendableStream.close()
            }
        }

        def filename = apkOrDex.outputFile.name
        withStyledOutput(StyledTextOutput.Style.Info) { out ->
            out.println("Total methods in ${filename}: ${methodCount}")
            out.println("Total fields in ${filename}:  ${fieldCount}")
        }

        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        def level = config.verbose ? null : LogLevel.DEBUG

        withStyledOutput(StyledTextOutput.Style.Info, level) { out ->
            print(tree, out)
        }

        if (config.exportAsCSV && outputFileCSV != null) {
            outputFileCSV.parentFile.mkdirs()
            outputFileCSV.createNewFile()

            final String headers = config.includeFieldCount ? "methods,fields" : "methods";
            final String counts = config.includeFieldCount ? "${methodCount},${fieldCount}" : "${methodCount}";

            outputFileCSV.withOutputStream { stream ->
                def appendableStream = new PrintStream(stream)
                appendableStream.println(headers)
                appendableStream.println(counts);
            }
        }
    }

    def print(tree, writer) {
        def opts = getPrintOptions()
        if (config.printAsTree) {
            tree.printTree(writer, opts)
        } else {
            tree.printPackageList(writer, opts)
        }
    }

    private void withStyledOutput(
            StyledTextOutput.Style style,
            LogLevel level = null,
            @ClosureParams(value = SimpleType, options = ['org.gradle.logging.StyledTextOutput']) Closure closure) {
        def factory = services.get(StyledTextOutputFactory)
        def output = level == null ? factory.create('dexcount') : factory.create('dexcount', level)

        closure(output.withStyle(style))
    }

    def getPackageTree() {
        // Create a de-obfuscator based on the current Proguard mapping file.
        // If none is given, we'll get a default mapping.
        def deobs = getDeobfuscator()

        def dataList = DexFile.extractDexData(apkOrDex.outputFile)
        try {
            def tree = new PackageTree()
            refListToClassNames(dataList*.getMethodRefs(), deobs).each {
                tree.addMethodRef(it)
            }

            refListToClassNames(dataList*.getFieldRefs(), deobs).each {
                tree.addFieldRef(it)
            }

            return tree
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
