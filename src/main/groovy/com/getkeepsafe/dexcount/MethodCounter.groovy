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

import com.android.dexdeps.DexData
import com.android.dexdeps.Output
import org.apache.commons.io.IOUtils

import java.util.zip.ZipException
import java.util.zip.ZipFile

class MethodCounter {
    private int totalCount
    private Map<String, Integer> methodCountsByPackage

    static MethodCounter count(File file) {
        def dataList = extractDexData(file)

        try {
            int count = 0
            def methods = dataList*.data*.getMethodRefs()
                    .flatten()
                    .inject(new HashMap<String, Integer>()) { HashMap<String, Integer> map, ref ->
                def classDescriptor = ref.getDeclClassName().replace('$', '.')
                def className = Output.descriptorToDot(classDescriptor)

                ++count
                while (true) {
                    increment(map, className)
                    def nextIndex = className.lastIndexOf('.')
                    if (nextIndex == -1) {
                        break
                    }
                    className = className.substring(0, nextIndex)
                }

                map
            }

            def counter = new MethodCounter()
            counter.totalCount = count
            counter.methodCountsByPackage = methods
            return counter
        } finally {
            dataList*.dispose()
        }
    }

    int getTotalCount() {
        return totalCount
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

            zipfile.getInputStream(entry).withStream { input ->
                temp.withOutputStream { output ->
                    IOUtils.copy(input, output)
                    output.flush()
                }
            }

            return new DexFile(temp, true)
        }
    }

    static void increment(Map<String, Integer> map, String key) {
        Integer num = map[key]
        if (num == null) {
            map[key] = 1;
        } else {
            map[key] = num + 1
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

        void dispose() {
            raf.close()
            if (isTemp) {
                file.delete()
            }
        }
    }
}
