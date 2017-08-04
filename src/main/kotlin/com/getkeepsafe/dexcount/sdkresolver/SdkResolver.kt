/*
 * Copyright 2016 KeepSafe Software
 * Copyright 2014 Jake Wharton
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.getkeepsafe.dexcount.sdkresolver

import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.StopExecutionException

import com.android.SdkConstants.ANDROID_HOME_ENV
import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.SDK_DIR_PROPERTY
import com.android.SdkConstants.NDK_DIR_PROPERTY
import com.android.SdkConstants.currentPlatform
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*

/**
 * This was lifted and modified from Jake Wharton's SDK Manager gradle plugin:
 * https://github.com/JakeWharton/sdk-manager-plugin
 */
class SdkResolver {
    companion object {
        @JvmStatic
        fun resolve(project: Project?): File?
        {
            val isWindows = currentPlatform() == PLATFORM_WINDOWS
            return SdkResolver(project, System.Real(), isWindows).resolve()
        }
    }

    private val log = Logging.getLogger(SdkResolver::class.java)

    val project: Project?
    val system: System
    val userHome: File
    val userAndroid: File
    val localProperties: File
    val isWindows: Boolean

    private constructor(project: Project?, system: System, isWindows: Boolean) {
        this.project = project
        this.system = system
        this.isWindows = isWindows

        userHome = File(system.property("user.home"))
        userAndroid = File(userHome, ".android-sdk")

        val projectRoot = project?.rootDir ?: File(".")
        localProperties = File(projectRoot, FN_LOCAL_PROPERTIES)
    }

    fun resolve(): File? {
        // Check for existing local.properties file and the SDK it points to.
        if (localProperties.exists()) {
            log.debug("Found $FN_LOCAL_PROPERTIES at '$localProperties.absolutePath'.")

            val properties = Properties()
            localProperties.inputStream().use { properties.load(it) }

            val sdkDirPath = properties.getProperty(SDK_DIR_PROPERTY)
            if (sdkDirPath != null) {
                log.debug("Found $SDK_DIR_PROPERTY of '$sdkDirPath'.")
                val sdkDir = File(sdkDirPath)
                if (!sdkDir.exists()) {
                    throw StopExecutionException(
                        "Specified SDK directory '$sdkDirPath' in '$FN_LOCAL_PROPERTIES' is not found.")
                }
                return sdkDir
            }

            log.debug("Missing $SDK_DIR_PROPERTY in $FN_LOCAL_PROPERTIES.")
        } else {
            log.debug("Missing $FN_LOCAL_PROPERTIES.")
        }

        // Look for ANDROID_NDK_HOME environment variable. it's not defined in SdkConstants unluckily, so defining here
        // Some people use NDK_HOME, but androidrecommended is ANDROID_NDK_HOME
        val ANDROID_NDK_HOME = "ANDROID_NDK_HOME"
        val androidNdkHome = system.env(ANDROID_NDK_HOME)
        var ndkDir: File? = null
        if (androidNdkHome != null && androidNdkHome.isNotEmpty()) {
            ndkDir = File(androidNdkHome)
            if (ndkDir.exists()) {
                log.debug("Found $ANDROID_NDK_HOME at '$androidNdkHome'. Writing to $FN_LOCAL_PROPERTIES.")
            } else {
                log.debug("Found $ANDROID_NDK_HOME at '$androidNdkHome' but directory is missing.")
                ndkDir = null
            }
        }

        // Look for ANDROID_HOME environment variable.
        val androidHome: String? = system.env(ANDROID_HOME_ENV)
        if (androidHome != null && androidHome.isNotEmpty()) {
            val sdkDir = File(androidHome)
            if (sdkDir.exists()) {
                log.debug("Found $ANDROID_HOME_ENV at '$androidHome'. Writing to $FN_LOCAL_PROPERTIES.")
                writeLocalProperties(androidHome, ndkDir?.absolutePath)
            } else {
                log.debug("Found $ANDROID_HOME_ENV at '$androidHome' but directory is missing.")
                return null
            }
            return sdkDir
        }

        log.debug("Missing $ANDROID_HOME_ENV.")

        // Look for an SDK in the home directory.
        if (userAndroid.exists()) {
            log.debug("Found existing SDK at '$userAndroid.absolutePath'. Writing to $FN_LOCAL_PROPERTIES.")

            writeLocalProperties(userAndroid.absolutePath, ndkDir?.absolutePath)
            return userAndroid
        }

        return null
    }

    fun writeLocalProperties(sdkPath: String, ndkPath: String?) {
        var theSdkPath = sdkPath
        var theNdkPath = ndkPath
        if (isWindows) {
            // Escape Windows file separators when writing as a sdkPath.
            theSdkPath = theSdkPath.replace("\\", "\\\\")
            if (theNdkPath != null){
                theNdkPath = theNdkPath.replace("\\", "\\\\")
            }
        }
        if (localProperties.exists()) {
            localProperties.appendingWriter {
                it.write("$SDK_DIR_PROPERTY=$theSdkPath\n")
                if (theNdkPath!=null){
                    it.write("$NDK_DIR_PROPERTY=$theNdkPath\n")
                }
            }
        } else {
            localProperties.writer(Charsets.UTF_8).use {
                it.write("# DO NOT check this file into source control.\n")
                it.write("$SDK_DIR_PROPERTY=$theSdkPath\n")
                if (theNdkPath!=null){
                    it.write("$NDK_DIR_PROPERTY=$theNdkPath\n")
                }
            }
        }
    }

    private inline fun File.appendingWriter(fn: (BufferedWriter) -> Unit) {
        FileOutputStream(this, true).use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                BufferedWriter(writer).use { bufferedWriter ->
                    fn(bufferedWriter)
                    bufferedWriter.flush()
                }
            }
        }
    }
}
