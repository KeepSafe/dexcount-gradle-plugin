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

import com.android.build.gradle.api.BaseVariantOutput
import com.android.dexdeps.HasDeclaringClass
import com.android.dexdeps.Output
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

    @OutputFile
    def File outputFile

    def DexMethodCountExtension config

    @TaskAction
    void countMethods() {
        def tree = getPackageTree()
        def methodCount = tree.getMethodCount()
        def fieldCount = tree.getFieldCount()

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

        String color
        if (methodCount > 60000) {
            color = 'RED'
        } else if (methodCount > 40000) {
            color = 'YELLOW'
        } else {
            color = 'GREEN'
        }

        def filename = apkOrDex.outputFile.name
        withColor(StyledTextOutput.Style.Info, color) { out ->
            out.println("Total methods in ${filename}: ${methodCount}")
            out.println("Total fields in ${filename}:  ${fieldCount}")
        }

        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        def outputFactory = services.get(StyledTextOutputFactory)
        def output = config.verbose ? outputFactory.create('dexcount') : outputFactory.create('dexcount', LogLevel.DEBUG)
        print(tree, output.withStyle(StyledTextOutput.Style.Info))
    }

    def print(tree, writer) {
        def opts = getPrintOptions()
        if (config.printAsTree) {
            tree.printTree(writer, opts)
        } else {
            tree.printPackageList(writer, opts)
        }
    }

    private void withColor(StyledTextOutput.Style style, String color, Closure<StyledTextOutput> closure) {
        def prop = "org.gradle.color.${style.name().toLowerCase()}"
        def oldValue = System.getProperty(prop)

        System.setProperty(prop, color)
        try {
            def sto = services.get(StyledTextOutputFactory)
                    .create("dexcount")
                    .withStyle(style)

            closure(sto)
        } finally {
            if (oldValue != null) {
                System.setProperty(prop, oldValue)
            } else {
                System.clearProperty(prop)
            }
        }
    }

    def getPackageTree() {
        def dataList = DexFile.extractDexData(apkOrDex.outputFile)
        try {
            def tree = new PackageTree()
            refListToClassNames(dataList*.getMethodRefs()).each {
                tree.addMethodRef(it)
            }

            refListToClassNames(dataList*.getFieldRefs()).each {
                tree.addFieldRef(it)
            }

            return tree
        } finally {
            dataList*.dispose()
        }
    }

    static refListToClassNames(List<List<HasDeclaringClass>> refs) {
        return refs.flatten().collect { ref ->
            def descriptor = ref.getDeclClassName()
            def dot = Output.descriptorToDot(descriptor)
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
}
