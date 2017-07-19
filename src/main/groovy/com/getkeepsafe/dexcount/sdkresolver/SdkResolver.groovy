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
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.StopExecutionException

import static com.android.SdkConstants.ANDROID_HOME_ENV
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES
import static com.android.SdkConstants.PLATFORM_WINDOWS
import static com.android.SdkConstants.SDK_DIR_PROPERTY
import static com.android.SdkConstants.NDK_DIR_PROPERTY
import static com.android.SdkConstants.currentPlatform

/**
 * This was lifted and modified from Jake Wharton's SDK Manager gradle plugin:
 * https://github.com/JakeWharton/sdk-manager-plugin
 */
class SdkResolver {
    static File resolve(Project project) {
        boolean isWindows = currentPlatform() == PLATFORM_WINDOWS
        return new SdkResolver(project, new System.Real(), isWindows).resolve()
    }

    final Logger log = Logging.getLogger SdkResolver
    final Project project
    final System system
    final File userHome
    final File userAndroid
    final File localProperties
    final boolean isWindows

    SdkResolver(Project project, System system, boolean isWindows) {
        this.project = project
        this.system = system
        this.isWindows = isWindows

        userHome = new File(system.property('user.home'))
        userAndroid = new File(userHome, '.android-sdk')

        def projectRoot = project?.rootDir ?: new File(".")
        localProperties = new File(projectRoot, FN_LOCAL_PROPERTIES)
    }

    File resolve() {
        // Check for existing local.properties file and the SDK it points to.
        if (localProperties.exists()) {
            log.debug "Found $FN_LOCAL_PROPERTIES at '$localProperties.absolutePath'."
            def properties = new Properties()
            localProperties.withInputStream { properties.load it }
            def sdkDirPath = properties.getProperty SDK_DIR_PROPERTY
            if (sdkDirPath != null) {
                log.debug "Found $SDK_DIR_PROPERTY of '$sdkDirPath'."
                def sdkDir = new File(sdkDirPath)
                if (!sdkDir.exists()) {
                    throw new StopExecutionException(
                            "Specified SDK directory '$sdkDirPath' in '$FN_LOCAL_PROPERTIES' is not found.")
                }
                return sdkDir
            }

            log.debug "Missing $SDK_DIR_PROPERTY in $FN_LOCAL_PROPERTIES."
        } else {
            log.debug "Missing $FN_LOCAL_PROPERTIES."
        }

        // Look for ANDROID_NDK_HOME environment variable. it's not defined in SdkConstants unluckily, so defining here
        // Some people use NDK_HOME, but androidrecommended is ANDROID_NDK_HOME
        def ANDROID_NDK_HOME = "ANDROID_NDK_HOME"
        def androidNdkHome = system.env ANDROID_NDK_HOME
        def ndkDir = null
        if (androidNdkHome != null && !"".equals(androidNdkHome)) {
            ndkDir = new File(androidNdkHome)
            if (ndkDir.exists()) {
                log.debug "Found $ANDROID_NDK_HOME at '$androidNdkHome'. Writing to $FN_LOCAL_PROPERTIES."
            } else {
                log.debug "Found $ANDROID_NDK_HOME at '$androidNdkHome' but directory is missing."
                ndkDir = null
            }
        }

        // Look for ANDROID_HOME environment variable.
        def androidHome = system.env ANDROID_HOME_ENV
        if (androidHome != null && !"".equals(androidHome)) {
            def sdkDir = new File(androidHome)
            if (sdkDir.exists()) {
                log.debug "Found $ANDROID_HOME_ENV at '$androidHome'. Writing to $FN_LOCAL_PROPERTIES."
                writeLocalProperties(androidHome, ndkDir?.absolutePath)
            } else {
                log.debug "Found $ANDROID_HOME_ENV at '$androidHome' but directory is missing."
                return null
            }
            return sdkDir
        }

        log.debug "Missing $ANDROID_HOME_ENV."

        // Look for an SDK in the home directory.
        if (userAndroid.exists()) {
            log.debug "Found existing SDK at '$userAndroid.absolutePath'. Writing to $FN_LOCAL_PROPERTIES."

            writeLocalProperties(userAndroid.absolutePath, ndkDir)
            return userAndroid
        }

        return null
    }

    def writeLocalProperties(String sdkPath, String ndkPath) {
        if (isWindows) {
            // Escape Windows file separators when writing as a sdkPath.
            sdkPath = sdkPath.replace "\\", "\\\\"
            if (ndkPath!=null){
                ndkPath = ndkPath.replace "\\", "\\\\"
            }
        }
        if (localProperties.exists()) {
            localProperties.withWriterAppend('UTF-8') {
                it.write "$SDK_DIR_PROPERTY=$sdkPath\n" as String
                if (ndkPath!=null){
                    it.write "$NDK_DIR_PROPERTY=$ndkPath\n" as String
                }
            }
        } else {
            localProperties.withWriter('UTF-8') {
                it.write "# DO NOT check this file into source control.\n"
                it.write "$SDK_DIR_PROPERTY=$sdkPath\n" as String
                if (ndkPath!=null){
                    it.write "$NDK_DIR_PROPERTY=$ndkPath\n" as String
                }
            }
        }
    }
}
