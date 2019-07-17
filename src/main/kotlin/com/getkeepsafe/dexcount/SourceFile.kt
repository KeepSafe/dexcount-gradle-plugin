/*
 * Copyright (C) 2015-2017 KeepSafe Software
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

import com.android.SdkConstants
import com.android.dexdeps.DexData
import com.android.dexdeps.DexDataException
import com.android.dexdeps.FieldRef
import com.android.dexdeps.MethodRef
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.RandomAccessFile

import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

internal sealed class SourceFile : Closeable {
    abstract val methodRefs: List<MethodRef>
    abstract val fieldRefs: List<FieldRef>
}

/**
 * A physical file and the {@link DexData} contained therein.
 *
 * A DexFile contains an open file, possibly a temp file.  When consumers are
 * finished with the DexFile, it should be cleaned up with [DexFile.close].
 */
@Suppress("ConvertSecondaryConstructorToPrimary")
internal class DexFile(
    private val file: File,
    private val isTemp: Boolean,
    val isInstantRun: Boolean = false
): SourceFile() {
    private val raf = RandomAccessFile(file, "r")
    private val data = DexData(raf).also {
        try {
            it.load()
        } catch (e: IOException) {
            throw DexCountException("Error loading dex file", e)
        } catch (e: DexDataException) {
            throw DexCountException("Error loading dex file", e)
        }
    }

    override val methodRefs: List<MethodRef>
        get() = data.methodRefs.toList()

    override val fieldRefs: List<FieldRef>
        get() = data.fieldRefs.toList()

    override fun close() {
        raf.close()
        if (isTemp) {
            file.delete()
        }
    }

    companion object {
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
        @JvmStatic
        fun extractDexData(file: File?, dxTimeoutSecs: Int): List<DexFile> {
            if (file == null || !file.exists()) {
                return emptyList()
            }

            // AAR files need special treatment
            if (file.name.endsWith(".aar")) {
                return extractDexFromAar(file, dxTimeoutSecs)
            }

            try {
                return extractDexFromZip(file)
            } catch (ignored: ZipException) {
                // not a zip, no problem
            }

            return listOf(DexFile(file, false))
        }

        @JvmStatic
        private fun extractDexFromAar(file: File, dxTimeoutSecs: Int): List<DexFile> {
            // unzip classes.jar from the AAR
            val tempClasses = file.unzip { entries ->
                val jarFile = entries.find { it.name.matches(Regex("classes\\.jar")) }
                makeTempFile("classes.jar").also {
                    jarFile!!.writeTo(it)
                }
            }

            // convert it to DEX format by using the Android dx tool
            val androidSdkHome = DexMethodCountPlugin.sdkLocation ?: throw Exception("Android SDK not found!")
            val buildToolsSubDirs = File(androidSdkHome, "build-tools")

            // get latest Dx tool by sorting by name
            val dirs = buildToolsSubDirs.listFiles().sortedBy { it.name }.asReversed()
            if (dirs.isEmpty()) {
                throw Exception ("No Build Tools found in " + buildToolsSubDirs.absolutePath)
            }

            val isWindows = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS
            val dxExeName = if (isWindows) "dx.bat" else "dx"

            val dxExe = File(dirs[0], dxExeName)

            if (!dxExe.exists()) {
                throw Exception ("dx tool not found at " + dxExe.absolutePath)
            }

            // ~/android-sdk/build-tools/23.0.3/dx --dex --output=temp.dex classes.jar
            val tempDex = makeTempFile("classes.dex")

            val sout = StringBuilder()
            val serr = StringBuilder()
            val proc = ProcessBuilder().command(
                    dxExe.absolutePath,
                    "--dex",
                    "--output=${tempDex.absolutePath}",
                    tempClasses.absolutePath)
                .start()

            val didFinish = proc.waitForProcessOutput(
                stdout = sout,
                stderr = serr,
                timeoutMillis = TimeUnit.SECONDS.toMillis(dxTimeoutSecs.toLong()))

            val exitCode = if (didFinish) proc.exitValue() else -1
            proc.dispose()

            if (!didFinish) {
                throw DexCountException("dx timed out after $dxTimeoutSecs seconds")
            }

            if (exitCode != 0) {
                throw DexCountException("dx exited with exit code $exitCode\nstderr=$serr")
            }

            if (!tempDex.exists()) {
                throw DexCountException("Error converting classes.jar into classes.dex: $serr")
            }

            return listOf(DexFile(tempDex, true))
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
        @JvmStatic
        fun extractDexFromZip(file: File): List<DexFile> {
            return file.unzip { entries ->
                entries
                    .filter { it.name.matches(Regex("classes.*\\.dex")) }
                    .map { entry ->
                        val temp = makeTempFile("dexcount.dex")
                        entry.writeTo(temp)
                        DexFile(temp, true)
                    }
                    .toList()
            }
        }
    }
}

internal class JarFile(
    override val methodRefs: List<MethodRef>,
    override val fieldRefs: List<FieldRef>
) : SourceFile() {

    override fun close() = Unit

    companion object {
        @JvmStatic
        fun extractJarFromAar(file: File): JarFile {
            // Extract the classes.jar file from the AAR.
            val tempClasses = file.unzip { entries ->
                val jarFile = entries.find { it.name.matches(Regex("classes\\.jar")) }
                makeTempFile("classes.jar").also {
                    jarFile!!.writeTo(it)
                }
            }

            return extractJarFromJar(tempClasses).also {
                check(tempClasses.deleteRecursively()) { "Couldn't delete $tempClasses" }
            }
        }

        @JvmStatic
        fun extractJarFromJar(jarFile: File): JarFile {
            // Unzip the classes.jar file and store all .class files in this directory.
            val classFilesDir = createTempDir(prefix = "classFilesDir")

            ZipFile(jarFile).use { zip ->
                zip.entries()
                    .asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach { zipEntry ->
                        val fileName = zipEntry.name.replace('/', '_')

                        FileOutputStream(File(classFilesDir, fileName)).use { outStream ->
                            zip.getInputStream(zipEntry).use { inStream -> inStream.copyTo(outStream) }
                        }
                    }
            }

            // Join all .class file paths to one string.
            val classFiles = classFilesDir.walk()
                .filter { it.extension == "class" }
                .map { it.absolutePath }
                .joinToString(separator = " ")

            /*
             * Call javap for for all class files. This returns an output similar like this:
             *
             * Compiled from "CreateBody.java"
             * public class com.abc.CreateBody {
             *   public final java.lang.String email;
             *   public com.abc.CreateBody(java.lang.String);
             *   private com.abc.CreateBody withPassword(java.lang.String);
             * }
             * Compiled from "AppAccountCache.kt"
             * public interface com.abc.AppAccountCache {
             *   public abstract void clearCache();
             * }
             */
            val lines = Runtime.getRuntime()
                .exec("javap -private $classFiles")
                .inputStream.reader()
                .use { it.readLines() }
                .map { it.trim() }

            check(classFilesDir.deleteRecursively()) { "Couldn't delete $classFilesDir" }

            return parseJavapOutput(lines)
        }

        private fun parseJavapOutput(lines: List<String>): JarFile {
            val methodRefs = mutableListOf<MethodRef>()
            val fieldRefs = mutableListOf<FieldRef>()

            var startIndex = 0

            while (startIndex < lines.size) {
                val begin = lines.indexOfFirstAfter(startIndex) {
                    it.endsWith("{") && (it.contains(" class ") || it.contains(" interface "))
                }

                val end = lines.indexOfFirstAfter(begin) { it == "}" }

                // Look either for "class" or "interface", substringABC returns the string itself
                // if the delimiter can't be found.
                val className = lines[begin].substringAfter("class ")
                    .substringAfter("interface ")
                    .substringBefore(" {")

                lines.subList(begin + 1, end)
                    // Ignore the static initializer.
                    .filterNot { it == "static {};" }
                    .forEach { line ->
                        when {
                            line.contains(" throws ") -> methodRefs += parseMethod(
                                line.substringBefore(" throws "), className
                            )
                            line.endsWith(");") -> methodRefs += parseMethod(line, className)
                            line.endsWith(";") -> fieldRefs += parseField(line, className)
                            else -> throw Exception(
                                "Unexpected line from javap in class $className: $line"
                            )
                        }
                    }

                startIndex = if (end >= 0) end + 1 else lines.size
            }

            return JarFile(methodRefs, fieldRefs)
        }

        private val JAVA_KEYWORDS = listOf(
            "public", "protected", "private", "final", "transient", "abstract", "class", "interface",
            "extends", "volatile", "synchronized", "native", "static", "implements"
        )

        private fun parseMethod(argLine: String, className: String): MethodRef {
            // Samples:
            // public com.abc.CreateBody(java.lang.String, java.lang.String);
            // private com.abc.CreateBody withPassword(java.lang.String);
            // private final com.abc.CreateBody withPassword(java.lang.String);
            // void something();
            // public boolean checkAvailability() throws org.altbeacon.beacon.BleNotAvailableException;

            val line = removeSpacesInLists(argLine)

            val argList = line.substringAfter("(").substringBefore(")")
            val args = argList.split(",").toTypedArray().filter { it.trim().isNotEmpty() }

            val split = line.substringBefore("(")
                .split(" ")
                .filterNot { JAVA_KEYWORDS.contains(it) }

            lateinit var returnType :String
            lateinit var methodName :String

            when (split.size) {
                1 -> {
                    returnType = split[0]
                    methodName = "constructor"
                }
                2 -> {
                    returnType = split[0]
                    methodName = split[1]
                }
                else -> throw Exception("Unexpected method in class $className with line $line")
            }

            return MethodRef(className, args.toTypedArray(), returnType, methodName)
        }

        private fun parseField(argLine: String, className: String): FieldRef {
            // Samples:
            //  public final java.lang.String name;
            //  public java.lang.String country_code;
            //  private final java.util.concurrent.ConcurrentMap<org.altbeacon.beacon.BeaconConsumer, org.altbeacon.beacon.BeaconManager$ConsumerInfo> consumers;

            val line = removeSpacesInLists(argLine)

            val split = line.substringBefore(";")
                .split(" ")
                .filterNot { JAVA_KEYWORDS.contains(it) }

            check(split.size == 2) { "Unexpected field in class $className with line $line" }
            return FieldRef(className, split[0], split[1])
        }

        private fun removeSpacesInLists(arg: String): String {
            // Generics are causing issues with splitting lines and using space as delimiter,
            // e.g. Pair<A, Pair<B, C>>. This method removes the spaces. Note that this also removes
            // the spaces in the parameters list, e.g. public Constructor(String, int, boolean).
            return arg.replace(", ", ",")
        }
    }
}

private fun <T> File.unzip(fn: (Sequence<StreamableZipEntry>) -> T): T {
    return ZipFile(this).use { zip ->
        val streamableEntries = zip.entries().asSequence().map { StreamableZipEntry(zip, it) }
        fn(streamableEntries)
    }
}

private fun makeTempFile(pattern: String): File {
    val ix = pattern.indexOf('.')
    val prefix = pattern.substring(0 until ix)
    val suffix = pattern.substring(ix)
    return File.createTempFile(prefix, suffix).apply { deleteOnExit() }
}

private class StreamableZipEntry(
    private val file: ZipFile,
    private val entry: ZipEntry
) {

    val name: String
        get() = entry.name

    fun inputStream(): InputStream {
        return file.getInputStream(entry)
    }

    fun writeTo(file: File) {
        inputStream().use { file.writeFromStream(it) }
    }
}

private fun Process.waitForProcessOutput(stdout: Appendable, stderr: Appendable, timeoutMillis: Long): Boolean {
    val (o, e) = consumeStdout(stdout) to consumeStderr(stderr)

    o.joinQuietly()
    e.joinQuietly()

    return try {
        waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (ignored: InterruptedException) {
        false
    }
}

private fun Process.consumeStdout(stdout: Appendable): Thread {
    return Thread(TextDumper(inputStream, stdout)).apply { start() }
}

private fun Process.consumeStderr(stderr: Appendable): Thread {
    return Thread(TextDumper(errorStream, stderr)).apply { start() }
}

private fun Closeable.closeQuietly() = try { close() } catch (ignored: IOException) {}

private fun Thread.joinQuietly() = try { join() } catch (ignored: InterruptedException) {}

/**
 * Returns index of the first element matching the given [predicate] starting the search
 * at [startIndex], or -1 if the list does not contain such element.
 */
inline fun <T> List<T>.indexOfFirstAfter(
    startIndex: Int,
    predicate: (T) -> Boolean
): Int {
    var index = startIndex
    for (item in subList(startIndex, size)) {
        if (predicate(item)) return index
        index++
    }
    return -1
}

private fun Process.dispose() {
    inputStream.closeQuietly()
    outputStream.closeQuietly()

    try {
        destroy()
    } catch (ignored: Exception) {
        // nada
    }
}

private class TextDumper(
    private val inputStream: InputStream,
    private val output: Appendable
) : Runnable {

    override fun run() {
        val reader = inputStream.bufferedReader()
        val lines = generateSequence { reader.readLine() }

        for (line in lines) {
            output.append(line)
            output.append("\n")
        }
    }
}
