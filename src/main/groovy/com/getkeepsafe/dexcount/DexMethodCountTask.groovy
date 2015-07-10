/*
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
import com.android.dexdeps.DexData
import com.android.dexdeps.MethodRef
import com.android.dexdeps.Output
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import java.util.zip.ZipException
import java.util.zip.ZipFile

class DexMethodCountTask extends DefaultTask {
    def BaseVariantOutput apkOrDex

    @OutputFile
    def File outputFile

    def DexMethodCountExtension config

    @TaskAction
    void countMethods() {
        def tree = getPackageTree()
        def count = tree.getCount()

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
        if (count > 60000) {
            color = 'RED'
        } else if (count > 40000) {
            color = 'YELLOW'
        } else {
            color = 'GREEN'
        }

        def filename = apkOrDex.outputFile.name
        withColor(StyledTextOutput.Style.Info, color) { out ->
            out.println("Total methods in ${filename}: ${count}")
        }

        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        def outputFactory = services.get(StyledTextOutputFactory)
        def output = config.verbose ? outputFactory.create('dexcount') : outputFactory.create('dexcount', LogLevel.DEBUG)
        print(tree, output.withStyle(StyledTextOutput.Style.Info))
    }

    def print(tree, writer) {
        if (config.printAsTree) {
            tree.printTree(writer, config.includeClasses)
        } else {
            tree.printPackageList(writer, config.includeClasses)
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
        def dataList = extractDexData(apkOrDex.outputFile)
        try {
            def tree = new PackageTree()
            dataList*.getMethodRefs().flatten().each { ref ->
                def classDescriptor = ref.getDeclClassName().replace('$', '.')
                def className = Output.descriptorToDot(classDescriptor)

                tree.add(className)
            }
            return tree
        } finally {
            dataList*.dispose()
        }
    }

    static List<DexFile> extractDexData(File file) {
        try {
            return extractDexFromZip(file)
        } catch (ZipException ignored) {
            // not a zip, no problem
        }

        return [new DexFile(file, false)]
    }

    static List<DexFile> extractDexFromZip(File file) {
        def zipfile = new ZipFile(file)
        def entries = Collections.list(zipfile.entries())
        def dexEntries = entries.findAll { it.name.matches("classes.*\\.dex") }
        return dexEntries.collect { entry ->
            def temp = File.createTempFile("dexcount", ".dex")
            temp.deleteOnExit()

            def buf = new byte[4096]
            zipfile.getInputStream(entry).withStream { input ->
                temp.withOutputStream { output ->
                    def read
                    while ((read = input.read(buf)) != -1) {
                        output.write(buf, 0, read)
                    }
                    output.flush()
                }
            }

            return new DexFile(temp, true)
        }
    }

    static class DexFile {
        public DexData data
        private RandomAccessFile raf
        private File file
        private boolean isTemp

        public DexFile(File file, boolean isTemp) {
            this.file = file
            this.isTemp = isTemp
            this.raf = new RandomAccessFile(file, 'r')
            this.data = new DexData(raf)
            data.load()
        }

        def List<MethodRef> getMethodRefs() {
            return data.getMethodRefs()
        }

        void dispose() {
            raf.close()
            if (isTemp) {
                file.delete()
            }
        }
    }
}
