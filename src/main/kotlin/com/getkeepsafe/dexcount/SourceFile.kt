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

import com.android.dexdeps.DexData
import com.android.dexdeps.DexDataException
import com.android.dexdeps.FieldRef
import com.android.dexdeps.MethodRef
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import javassist.ByteArrayClassPath
import javassist.ClassPool
import javassist.CtBehavior
import javassist.CtMethod
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.streams.toList

internal sealed class SourceFile : Closeable {
    abstract val methodRefs: List<MethodRef>
    abstract val fieldRefs: List<FieldRef>
}

/**
 * A physical file and the [DexData] contained therein.
 *
 * A DexFile contains an open file, possibly a temp file.  When consumers are
 * finished with the DexFile, it should be cleaned up with [DexFile.close].
 */
internal class DexFile private constructor(
    private val file: File,
    private val isTemp: Boolean,
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
         * Extracts a list of [DexFile] instances from the given [file].
         *
         * DexFiles can be extracted either from an Android APK file, or from a raw
         * `classes.dex` file.
         *
         * @param file the APK or dex file.
         * @return a list of [DexFile] objects representing data in the given file.
         */
        @JvmStatic
        fun extractDexData(file: File?): List<DexFile> {
            if (file == null || !file.exists()) {
                return emptyList()
            }

            // AAR files need special treatment
            if (file.name.endsWith(".aar")) {
                return extractDexFromAar(file)
            }

            try {
                return extractDexFromZip(file)
            } catch (ignored: ZipException) {
                // not a zip, no problem
            }

            return listOf(DexFile(file, false))
        }

        @JvmStatic
        private fun extractDexFromAar(file: File): List<DexFile> {
            // unzip classes.jar from the AAR
            var minSdk = 13 // the default minSdkVersion of dx
            val tempClasses = file.unzip { entries ->
                var tempFile: File? = null

                for (entry in entries) {
                    if (entry.name == "AndroidManifest.xml") {
                        val manifestText = entry.inputStream().bufferedReader().use { it.readText() }
                        val match = Regex("""android:minSdkVersion="(\d+)"""").find(manifestText)
                        if (match != null) {
                            minSdk = match.groupValues[1].toInt()
                        }
                    }

                    if (entry.name.matches(Regex("classes\\.jar"))) {
                        tempFile = makeTempFile("classes.jar").also {
                            entry.writeTo(it)
                        }
                    }
                }

                checkNotNull(tempFile) { "No classes.jar file found in ${file.canonicalPath}" }
            }

            val tempDexDir = Files.createTempDirectory("dex").also { it.toFile().deleteOnExit() }

            D8Command.builder()
                .addProgramFiles(tempClasses.toPath())
                .setMinApiLevel(minSdk)
                .setOutput(tempDexDir, OutputMode.DexIndexed)
                .build()
                .also { D8.run(it) }

            return Files.list(tempDexDir)
                .filter { Files.isRegularFile(it) }
                .map { DexFile(it.toFile(), true) }
                .toList()
        }

        /**
         * Attempts to unzip the [file] and extract all dex files inside of it.
         *
         * It is assumed that [file] is an APK file resulting from an Android
         * build, containing one or more appropriately-named classes.dex files.
         *
         * @param file the APK file from which to extract dex data.
         * @return a list of contained dex files.
         * @throws ZipException if [file] is not a zip file.
         */
        @JvmStatic
        fun extractDexFromZip(file: File): List<DexFile> {
            return file.unzip { entries ->
                entries
                    .filter { it.name.matches(Regex("(.*/)*classes.*\\.dex")) }
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
            val classFilesDir = Files.createTempDirectory("classFilesDir").toFile()

            ZipFile(jarFile).use { zip ->
                zip.entries()
                    .asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach { zipEntry ->
                        val fileName = zipEntry.name
                        val file = File(classFilesDir, fileName)

                        if (!file.parentFile.exists()) {
                            check(file.parentFile.mkdirs()) { "Couldn't create parent dir." }
                        }

                        FileOutputStream(file).use { outStream ->
                            zip.getInputStream(zipEntry).use { inStream -> inStream.copyTo(outStream) }
                        }
                    }
            }

            val classPool = ClassPool()
            classPool.appendSystemPath()

            val classes = classFilesDir.walk()
                .filter { it.extension == "class" }
                .map { file ->
                    val fullyQualifiedClassName = file.relativeTo(classFilesDir)
                        .path
                        .replace('/', '.')
                        .substringBeforeLast(".class")

                    classPool.appendClassPath(ByteArrayClassPath(fullyQualifiedClassName, file.readBytes()))
                    classPool.get(fullyQualifiedClassName)
                }
                .toList()

            // Note that methodRefs contains synthetic methods.
            val methodRefs = classes
                .flatMap { clazz ->
                    val declaringClass = "L${clazz.name.replace('.', '/')};"

                    // Unfortunately, it's necessary to parse the types from the strings manually.
                    // We can't use the proper API because this requires all classes that are used
                    // in parameters and return types to be loaded in the classpath. However,
                    // that's not the case when we analyze a single jar file.
                    val classInitializer = if (clazz.classInitializer != null) {
                        listOf(MethodRef(declaringClass, emptyArray(), "V", "<clinit>"))
                    } else emptyList()

                    classInitializer + clazz.declaredConstructors.map { constructor ->
                        val parameterTypes = constructor.parseParameters()
                            .toTypedArray()
                        // V as return type stands for void.
                        MethodRef(declaringClass, parameterTypes, "V", "<init>")
                    } + clazz.declaredMethods.map { method ->
                        val parameterTypes = method.parseParameters()
                            .toTypedArray()
                        val returnType = method.parseReturnType()

                        MethodRef(declaringClass, parameterTypes, returnType, method.name)
                    }
                }

            val fieldRefs = classes.flatMap { clazz ->
                clazz.declaredFields.map { field ->
                    val type = field.fieldInfo.descriptor
                    FieldRef(clazz.simpleName, type, field.name)
                }
            }

            return JarFile(methodRefs, fieldRefs)
        }

        private fun CtBehavior.parseParameters(): List<String> {
            // Samples:
            // ()Lcom/abc/SomeType<Lcom/def/OtherType;>;
            // (Ljava/lang/String;)Lcom/abc/SomeType<Lcom/def/OtherType;>;
            // (Ljava/lang/String;Lcom/abc/SomeType;)Lcom/def/OtherType<Lcom/def/ThirdType;>;
            val parameterString = signature
                .substringAfter("(")
                .substringBefore(")")

            return parameterString.split(";")
        }

        private fun CtMethod.parseReturnType(): String = signature.substringAfter(")")
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
        inputStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
    }
}
