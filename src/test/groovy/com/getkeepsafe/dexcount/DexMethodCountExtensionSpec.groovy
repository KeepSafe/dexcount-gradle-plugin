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

import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.Project
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

final class DexMethodCountExtensionSpec extends Specification {
    @Rule final TemporaryFolder tempFolder = new TemporaryFolder()
    final static COMPILE_SDK_VERSION = 25
    final static BUILD_TOOLS_VERSION = "25.0.2"
    final static APPLICATION_ID = "com.example"
    Project project
    File apkFile

    def "setup"() {
        project = ProjectBuilder.builder().build()

        def manifestFile = project.file('src/main/AndroidManifest.xml')
        manifestFile.parentFile.mkdirs()
        manifestFile.write("""<?xml version="1.0" encoding="utf-8"?>
          <manifest package="com.getkeepsafe.dexcount.integration"
                    xmlns:android="http://schemas.android.com/apk/res/android">
              <application/>
          </manifest>
        """)

        apkFile = tempFolder.newFile("tiles.apk")
        def apkResource = getClass().getResourceAsStream("/tiles.apk")
        apkResource.withStream { input ->
            IOUtil.drainToFile(input, apkFile)
        }
    }

    def "maxMethodCount methods < tiles.apk methods, throw exception"() {
        given:
        project.apply plugin: "com.android.application"
        project.apply plugin: "com.getkeepsafe.dexcount"
        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion BUILD_TOOLS_VERSION

            defaultConfig {
                applicationId APPLICATION_ID
            }
        }
        project.dexcount {
            maxMethodCount = 100
        }

        when:
        project.evaluate()

        // Override APK file
        DexMethodCountTask task = project.tasks.getByName("countDebugDexMethods") as DexMethodCountTask
        task.variantOutputName = "extensionSpec"
        task.inputFile = null // tests fail using Gradle Plugin 2.3.3 if this remains set
        task.inputDirectory = apkFile.parentFile
        task.execute()

        then:
        thrown(TaskExecutionException) // Should be GradleException?
    }

    def "maxMethodCount methods > tiles.apk methods, no exception thrown"() {
        given:
        project.apply plugin: "com.android.application"
        project.apply plugin: "com.getkeepsafe.dexcount"
        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion BUILD_TOOLS_VERSION

            defaultConfig {
                applicationId APPLICATION_ID
            }
        }
        project.dexcount {
            maxMethodCount = 64000
        }

        when:
        project.evaluate()

        // Override APK file
        DexMethodCountTask task = project.tasks.getByName("countDebugDexMethods") as DexMethodCountTask
        task.variantOutputName = "extensionSpec"
        task.inputFile = null // tests fail using Gradle Plugin 2.3.3 if this remains set
        task.inputDirectory = apkFile.parentFile
        task.execute()

        then:
        noExceptionThrown()
    }

    def "format can be a String"() {
        given:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = "tree"

        then:
        ext.format == OutputFormat.TREE
    }

    def "format can be an OutputFormat enum"() {
        given:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = OutputFormat.TREE

        then:
        ext.format == OutputFormat.TREE
    }

    def "setFormat throws on invalid format class"() {
        given:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = 12345

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unrecognized output format '12345'"
    }

    def "setFormat throws on invalid format name"() {
        given:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = "splay-tree"

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unrecognized output format 'splay-tree'"
    }

    def "format defaults to LIST"() {
        expect:
        new DexMethodCountExtension().format == OutputFormat.LIST
    }
}
