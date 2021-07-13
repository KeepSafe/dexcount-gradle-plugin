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

import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.BuiltArtifactsLoader
import com.getkeepsafe.dexcount.report.DexCountOutputTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.TempDir

@Ignore
final class DexCountExtensionSpec extends Specification {
    private Project project
    private File apkFile

    @TempDir
    private File tempDir

    private BuiltArtifact apkArtifact
    private BuiltArtifacts builtArtifacts
    private BuiltArtifactsLoader loader

    def "setup"() {
        project = ProjectBuilder.builder().build()
        //(project as ProjectInternal).services.get(GradlePropertiesController.class).loadGradlePropertiesFrom(project.rootDir)

        def manifestFile = project.file('src/main/AndroidManifest.xml')
        manifestFile.parentFile.mkdirs()
        manifestFile.write("""<?xml version="1.0" encoding="utf-8"?>
          <manifest package="com.getkeepsafe.dexcount.integration"
                    xmlns:android="http://schemas.android.com/apk/res/android">
              <application/>
          </manifest>
        """)

        apkFile = new File(tempDir, "tiles.apk")
        def apkResource = getClass().getResourceAsStream("/tiles.apk")
        apkResource.withStream { input ->
            apkFile.append(input)
        }

        apkArtifact = Mock()
        builtArtifacts = Mock()
        loader = Mock()

        apkArtifact.outputFile >> apkFile.canonicalPath
        builtArtifacts.elements >> [apkArtifact]
        loader.load(_) >> builtArtifacts
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
        ApkPackageTreeTask task = project.tasks.getByName("generateDebugPackageTree") as ApkPackageTreeTask
        task.outputFileNameProperty.set("extensionSpec")
        task.apkDirectoryProperty.set(apkFile.parentFile)
        task.loaderProperty.set(loader)
        task.execute()

        DexCountOutputTask outputTask = project.tasks.getByName("countDebugDexMethods") as DexCountOutputTask
        outputTask.run()

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
        ApkPackageTreeTask task = project.tasks.getByName("generateDebugPackageTree") as ApkPackageTreeTask
        task.outputFileNameProperty.set("extensionSpec")
        task.apkDirectoryProperty.set(apkFile.parentFile)
        task.loaderProperty.set(loader)
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
        ApkPackageTreeTask task = project.tasks.getByName("generateDebugPackageTree") as ApkPackageTreeTask
        task.outputFileNameProperty.set("extensionSpec")
        task.apkDirectoryProperty.set(apkFile.parentFile)
        task.loaderProperty.set(loader)
        task.execute()

        then:
        noExceptionThrown()
    }

    def "format defaults to LIST"() {
        expect:
        new DexCountExtension(project.objects, project.providers).format == OutputFormat.LIST
    }

    def "runOnEachPackage defaults to true"() {
        when:
        def ext = new DexCountExtension(project.objects, project.providers)

        then:
        ext.runOnEachPackage
    }

    def "runOnEachAssemble is a synonym for runOnEachPackage"() {
        given:
        def ext = new DexCountExtension(project.objects, project.providers)

        when:
        ext.runOnEachAssemble = false

        then:
        !ext.runOnEachPackage
    }

    def "runOnEachPackage is a synonym for runOnEachAssemble"() {
        given:
        def ext = new DexCountExtension(project.objects, project.providers)

        when:
        ext.runOnEachPackage = false

        then:
        !ext.runOnEachAssemble
    }
}
