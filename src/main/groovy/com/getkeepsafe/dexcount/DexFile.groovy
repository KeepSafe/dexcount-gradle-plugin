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

import com.android.dexdeps.DexData
import com.android.dexdeps.FieldRef
import com.android.dexdeps.MethodRef

import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * A physical file and the {@link DexData} contained therein.
 *
 * A DexFile contains an open file, possibly a temp file.  When consumers are
 * finished with the DexFile, it should be cleaned up with
 * {@link DexFile#dispose()}.
 */
class DexFile {
    public DexData data
    private RandomAccessFile raf
    private File file
    private boolean isTemp

    /**
     * Extracts a list of {@link DexFile} instances from the given file.
     *
     * DexFiles can be extracted either from an Android APK file, or from a raw
     * {@code classes.dex} file.
     *
     * @param file the APK or dex file.
     * @param dxTimeoutSecs timeout when running Dx in seconds
     * @return a list of DexFile objects representing data in the given file.
     */
    static List<DexFile> extractDexData(File file, int dxTimeoutSecs) {
        try {
            // AAR files need special treatment
            if (file.name.endsWith(".aar")) {
                return extractDexFromAar(file, dxTimeoutSecs);
            }

            return extractDexFromZip(file)
        } catch (ZipException ignored) {
            // not a zip, no problem
        }

        return [new DexFile(file, false)]
    }

    private static List<DexFile> extractDexFromAar(File file, int dxTimeoutSecs) {
        // unzip classes.jar from the AAR
        def zipfile = new ZipFile(file)
        def entries = Collections.list(zipfile.entries())
        def jarFile = entries.find { it.name.matches("classes.jar") }
        def tempClasses = File.createTempFile("classes", ".jar")
        tempClasses.deleteOnExit()

        def buf = new byte[4096]
        zipfile.getInputStream(jarFile).withStream { input ->
            tempClasses.withOutputStream { output ->
                def read
                while ((read = input.read(buf)) != -1) {
                    output.write(buf, 0, read)
                }
                output.flush()
            }
        }
        // convert it to DEX format by using the Android dx tool
        def androidSdkHome = System.getenv("ANDROID_HOME")
        if (androidSdkHome == null) {
            throw new Exception("ANDROID_HOME env variable not defined!")
        }
        def buildToolsSubDirs = new File(androidSdkHome, "build-tools")
        def dirs = buildToolsSubDirs.listFiles()
        if (dirs.length == 0) {
            throw new Exception("No Build Tools found in " + buildToolsSubDirs.absolutePath)
        }
        def dxExe = new File(dirs[0].absolutePath + File.separatorChar + "dx")
        if (!dxExe.exists()) {
            throw new Exception("dx tool not found at " + dxExe.absolutePath)
        }
        // ~/android-sdk/build-tools/23.0.3/dx --dex --output=temp.dex classes.jar
        def tempDex = File.createTempFile("classes", ".dex")
        tempDex.deleteOnExit()
        def sout = new StringBuilder(), serr = new StringBuilder()
        def dxCmd = dxExe.absolutePath + " --dex --output=" + tempDex.absolutePath + " " + tempClasses.absolutePath
        def proc = dxCmd.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(dxTimeoutSecs * 1000)
        if (!tempDex.exists()) {
            throw new Exception("Error converting classes.jar into classes.dex: $serr")
        }
        // return resulting dex file in a list
        return [ new DexFile(tempDex, true) ]
    }

    /**
     * Attempts to unzip the file and extract all dex files inside of it.
     *
     * It is assumed that {@code file} is an APK file resulting from an Android
     * build, containing one or more appropriately-named classes.dex files.
     *
     * @param file the APK file from which to extract dex data.
     * @return a list of contained dex files.
     * @throws ZipException if {@code file} is not a zip file.
     */
    private static List<DexFile> extractDexFromZip(File file) {
        def zipfile = new ZipFile(file)
        def entries = Collections.list(zipfile.entries())
        def dexEntries = entries.findAll { it.name.matches("classes.*\\.dex") }
        def buf = new byte[4096]
        return dexEntries.collect { entry ->
            def temp = File.createTempFile("dexcount", ".dex")
            temp.deleteOnExit()

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

    private DexFile(File file, boolean isTemp) {
        this.file = file
        this.isTemp = isTemp
        this.raf = new RandomAccessFile(file, 'r')
        this.data = new DexData(raf)
        data.load()
    }

    def List<MethodRef> getMethodRefs() {
        return data.getMethodRefs()
    }

    def List<FieldRef> getFieldRefs() {
        return data.getFieldRefs()
    }

    void dispose() {
        raf.close()
        if (isTemp) {
            file.delete()
        }
    }
}
