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
import com.android.dexdeps.DexDataException
import com.android.dexdeps.FieldRef
import com.android.dexdeps.MethodRef

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

import static com.android.SdkConstants.PLATFORM_WINDOWS
import static com.android.SdkConstants.currentPlatform

/**
 * A physical file and the {@link DexData} contained therein.
 *
 * A DexFile contains an open file, possibly a temp file.  When consumers are
 * finished with the DexFile, it should be cleaned up with
 * {@link DexFile#dispose()}.
 */
final class DexFile {
    final DexData data
    final boolean isInstantRun
    RandomAccessFile raf
    File file
    boolean isTemp

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
        if (file == null || !file.exists()) {
            return [] as ArrayList
        }

        // AAR files need special treatment
        if (file.name.endsWith(".aar")) {
            return extractDexFromAar(file, dxTimeoutSecs)
        }

        try {
            return extractDexFromZip(file)
        } catch (ZipException ignored) {
            // not a zip, no problem
        }

        return [new DexFile(file, false)]
    }

    static List<DexFile> extractDexFromAar(File file, int dxTimeoutSecs) {
        // unzip classes.jar from the AAR
        def zipfile = new ZipFile(file)
        def entries = Collections.list(zipfile.entries())
        def jarFile = entries.find { it.name.matches("classes.jar") }
        def tempClasses = File.createTempFile("classes", ".jar")
        tempClasses.deleteOnExit()

        zipfile.getInputStream(jarFile).withStream { input ->
            IOUtil.drainToFile(input, tempClasses)
        }
        // convert it to DEX format by using the Android dx tool
        def androidSdkHome = DexMethodCountPlugin.sdkLocation
        if (androidSdkHome == null) {
            throw new Exception("Android SDK not found!")
        }

        def buildToolsSubDirs = new File(androidSdkHome, "build-tools")
        // get latest Dx tool by sorting by name
        def dirs = buildToolsSubDirs.listFiles().sort { it.name }.reverse()
        if (dirs.length == 0) {
            throw new Exception("No Build Tools found in " + buildToolsSubDirs.absolutePath)
        }

        def dxExe
        if (currentPlatform() == PLATFORM_WINDOWS) {
            dxExe = new File(dirs[0], "dx.bat")
        } else {
            dxExe = new File(dirs[0], "dx")
        }

        if (!dxExe.exists()) {
            throw new Exception("dx tool not found at " + dxExe.absolutePath)
        }

        // ~/android-sdk/build-tools/23.0.3/dx --dex --output=temp.dex classes.jar
        def tempDex = File.createTempFile("classes", ".dex")
        tempDex.deleteOnExit()

        def dxCmd = dxExe.absolutePath + " --dex --output=" + tempDex.absolutePath + " " + tempClasses.absolutePath

        final sout = new StringBuilder()
        final serr = new StringBuilder()
        final proc = dxCmd.execute()
        final finished = new AtomicBoolean(false)
        def thread = Thread.start {
            proc.waitForProcessOutput(sout, serr)
            finished.set(true)
        }

        try {
            thread.join(TimeUnit.SECONDS.toMillis(dxTimeoutSecs))
        } catch (InterruptedException ignored) {
            // oh well
        }

        if (!finished.get()) {
            thread.interrupt()
            proc.destroy()
            throw new DexCountException("dx timed out after $dxTimeoutSecs seconds")
        }

        def exitCode = proc.exitValue()
        if (exitCode != 0) {
            throw new DexCountException("dx exited with exit code $exitCode\nstderr=$serr")
        }

        if (!tempDex.exists()) {
            throw new DexCountException("Error converting classes.jar into classes.dex: $serr")
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
    static List<DexFile> extractDexFromZip(File file) {
        def zipfile = new ZipFile(file)
        def entries = Collections.list(zipfile.entries())
        def dexEntries = entries.findAll { it.name.matches("classes.*\\.dex") }

        def instantRunDexFiles = extractIncrementalDexFiles(zipfile, entries)

        def mainDexFiles = dexEntries.collect { entry ->
            def temp = File.createTempFile("dexcount", ".dex")
            temp.deleteOnExit()

            zipfile.getInputStream(entry).withStream { input ->
                IOUtil.drainToFile(input, temp)
            }

            return new DexFile(temp, true)
        }

        mainDexFiles.addAll(instantRunDexFiles)

        return mainDexFiles
    }

    /**
     * Attempts to extract dex files embedded in a nested instant-run.zip file
     * produced by Android Studio 2.0.  If present, such files are extracted to
     * temporary files on disk and returned as a list.  If not, an empty mutable
     * list is returned.
     *
     * @param apk the APK file from which to extract dex data.
     * @param zipEntries a list of ZipEntry objects inside of the APK.
     * @return a list, possibly empty, of instant-run dex data.
     */
    static List<DexFile> extractIncrementalDexFiles(ZipFile apk, List<ZipEntry> zipEntries) {
        def incremental = zipEntries.findAll { (it.name == 'instant-run.zip') }
        if (incremental.size() != 1) {
            return []
        }

        def instantRunFile = File.createTempFile("instant-run", ".zip")
        instantRunFile.deleteOnExit()

        apk.getInputStream(incremental.get(0)).withStream { input ->
            IOUtil.drainToFile(input, instantRunFile)
        }

        def instantRunZip = new ZipFile(instantRunFile)
        def entries = Collections.list(instantRunZip.entries())
        def dexEntries = entries.findAll { it.name.endsWith(".dex") }

        return dexEntries.collect { entry ->
            def temp = File.createTempFile("dexcount", ".dex")
            temp.deleteOnExit()

            instantRunZip.getInputStream(entry).withStream { input ->
                IOUtil.drainToFile(input, temp)
            }

            return new DexFile(temp, true, true)
        }
    }

    DexFile(File file, boolean isTemp, boolean isInstantRun = false) {
        this.file = file
        this.isTemp = isTemp
        this.isInstantRun = isInstantRun
        this.raf = new RandomAccessFile(file, 'r')
        this.data = new DexData(raf)

        try {
            data.load()
        } catch (EOFException | DexDataException e) {
            throw new DexCountException("Error loading dex file", e)
        }
    }

    List<MethodRef> getMethodRefs() {
        return data.getMethodRefs()
    }

    List<FieldRef> getFieldRefs() {
        return data.getFieldRefs()
    }

    void dispose() {
        raf.close()
        if (isTemp) {
            file.delete()
        }
    }
}
