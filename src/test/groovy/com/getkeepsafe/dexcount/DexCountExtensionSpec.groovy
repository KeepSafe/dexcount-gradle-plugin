/*
 * Copyright (C) 2015-2019 KeepSafe Software
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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

final class DexCountExtensionSpec extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()
    private Project project
    private File apkFile

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

        apkFile = temporaryFolder.newFile("tiles.apk")
        def apkResource = getClass().getResourceAsStream("/tiles.apk")
        apkResource.withStream { input ->
            apkFile.append(input)
        }
    }

    def "maxMethodCount methods < tiles.apk methods, throw exception"() {
        given:
        project.apply plugin: "com.android.application"
        project.apply plugin: "com.getkeepsafe.dexcount"
        project.android {
            compileSdkVersion 28

            defaultConfig {
                applicationId 'com.example'
            }
        }
        project.dexcount {
            maxMethodCount = 100
        }

        when:
        project.evaluate()

        // Override APK file
        DexCountTask task = project.tasks.getByName("countDebugDexMethods") as DexCountTask
        task.variantOutputName = "extensionSpec"
        task.inputFileProperty.set(apkFile)
        task.execute()

        then:
        thrown(GradleException)
    }

    def "printDeclarations not allowed for application projects"() {
        given:
        project.apply plugin: "com.android.application"
        project.apply plugin: "com.getkeepsafe.dexcount"
        project.android {
            compileSdkVersion 28

            defaultConfig {
                applicationId 'com.example'
            }
        }
        project.dexcount {
            printDeclarations = true
        }

        when:
        project.evaluate()

        // Override APK file
        DexCountTask task = project.tasks.getByName("countDebugDexMethods") as DexCountTask
        task.variantOutputName = "extensionSpec"
        task.inputFileProperty.set(apkFile)
        task.execute()

        then:
        def exception = thrown(ProjectConfigurationException)
        exception.cause.message.contains("Cannot compute declarations for project root project 'test'")
    }

    def "maxMethodCount methods > tiles.apk methods, no exception thrown"() {
        given:
        project.apply plugin: "com.android.application"
        project.apply plugin: "com.getkeepsafe.dexcount"
        project.android {
            compileSdkVersion 28

            defaultConfig {
                applicationId 'com.example'
            }
        }
        project.dexcount {
            maxMethodCount = 64000
        }

        when:
        project.evaluate()

        // Override APK file
        DexCountTask task = project.tasks.getByName("countDebugDexMethods") as DexCountTask
        task.variantOutputName = "extensionSpec"
        task.inputFileProperty.set(apkFile)
        task.execute()

        then:
        noExceptionThrown()
    }

    def "format can be a String"() {
        given:
        def ext = new DexCountExtension()

        when:
        ext.format = "tree"

        then:
        ext.format == OutputFormat.TREE
    }

    def "format can be an OutputFormat enum"() {
        given:
        def ext = new DexCountExtension()

        when:
        ext.format = OutputFormat.TREE

        then:
        ext.format == OutputFormat.TREE
    }

    def "setFormat throws on invalid format class"() {
        given:
        def ext = new DexCountExtension()

        when:
        ext.format = 12345

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unrecognized output format '12345'"
    }

    def "setFormat throws on invalid format name"() {
        given:
        def ext = new DexCountExtension()

        when:
        ext.format = "splay-tree"

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unrecognized output format 'splay-tree'"
    }

    def "format defaults to LIST"() {
        expect:
        new DexCountExtension().format == OutputFormat.LIST
    }

    def "runOnEachPackage defaults to true"() {
        when:
        def ext = new DexCountExtension()

        then:
        ext.runOnEachPackage
    }

    def "runOnEachAssemble is a synonym for runOnEachPackage"() {
        given:
        def ext = new DexCountExtension()

        when:
        ext.runOnEachAssemble = false

        then:
        !ext.runOnEachPackage
    }

    def "runOnEachPackage is a synonym for runOnEachAssemble"() {
        given:
        def ext = new DexCountExtension()

        when:
        ext.runOnEachPackage = false

        then:
        !ext.runOnEachAssemble
    }
}
