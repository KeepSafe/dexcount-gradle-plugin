package com.getkeepsafe.dexcount

import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.BuiltArtifactsLoader
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildResultException
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

final class DexMethodCountPluginSpec extends Specification {
    private File testProjectDir
    private File buildFile
    private String reportFolder
    private Project project
    private File manifestFile
    def MANIFEST_FILE_PATH = 'src/main/AndroidManifest.xml'
    def MANIFEST_FILE_TEXT = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.example\"/>"

    def 'setup'() {
        testProjectDir = File.createTempDir()
        buildFile = new File(testProjectDir, 'build.gradle')
        reportFolder = "${testProjectDir.absolutePath}/build/outputs/dexcount"
        new File(new File(testProjectDir, 'src'), 'main').mkdirs()
        new File(testProjectDir, MANIFEST_FILE_PATH) << MANIFEST_FILE_TEXT

        // TODO remove old testing strategy
        project = ProjectBuilder.builder().build()
        manifestFile = new File(project.projectDir, 'src/main/AndroidManifest.xml')
        manifestFile.parentFile.mkdirs()
        manifestFile.write(MANIFEST_FILE_TEXT)
    }

    def 'cleanup'() {
        FileUtils.deleteDirectory(testProjectDir)
    }

    @Unroll def '#projectPlugin project'() {
        given:
        buildFile <<
            """
        plugins {
            id "${projectPlugin}"
            id "com.getkeepsafe.dexcount"
        }

        android {
          compileSdkVersion 28

          defaultConfig {
            if ("${projectPlugin}" == 'com.android.application') {
              applicationId 'com.example'
            }
          }
        }
      """.stripIndent().trim()

        when:
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .build()

        then:
        noExceptionThrown()

        where:
        projectPlugin << ['com.android.application', 'com.android.library']
    }

    @Unroll def '#projectPlugin project success'() {
        given:
        buildFile <<
            """
        plugins {
            id "${projectPlugin}"
            id "com.getkeepsafe.dexcount"
        }

        dexcount {
          printDeclarations = true
        }
      """.stripIndent().trim()

        when:
        def result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("countDeclaredMethods", "--stacktrace")
            .build()

        then:
        result.task(":countDeclaredMethods").outcome == SUCCESS

        where:
        projectPlugin << ['java', 'java-library']
    }

    @Unroll def '#projectPlugin project failure when printDeclaration is false'() {
        given:
        buildFile <<
            """
        plugins {
            id "${projectPlugin}"
            id "com.getkeepsafe.dexcount"
        }

        dexcount {
          printDeclarations = false
        }
      """.stripIndent().trim()

        when:
        def result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("countDeclaredMethods")
            .build()

        then:
        thrown(UnexpectedBuildResultException) // Should be GradleException?

        where:
        projectPlugin << ['java', 'java-library']
    }

    @Unroll def '#taskName with default buildTypes'() {
        given:
        buildFile <<
            """
        plugins {
            id "com.android.application"
            id "com.getkeepsafe.dexcount"
        }

        // TODO(???) - Repositories from test
        repositories {
          google()
          jcenter()
        }

        android {
          compileSdkVersion 28

          defaultConfig {
            applicationId 'com.example'
          }
        }
      """.stripIndent().trim()

        when:
        def result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("${taskName}", "--full-stacktrace")
            .build()

        then:
        result.task(":${taskName}").outcome == SUCCESS

        where:
        taskName << ['countDebugDexMethods', 'countReleaseDexMethods']
    }

    @Unroll def '#taskName with buildTypes'() {
        given:
        buildFile <<
            """
        plugins {
            id "com.android.application"
            id "com.getkeepsafe.dexcount"
        }

        // TODO(???) - Repositories from test
        repositories {
          google()
          jcenter()
        }

        android {
          compileSdkVersion 28

          defaultConfig {
            applicationId 'com.example'
          }

          buildTypes {
           debug {}
           release {}
          }
        }
      """.stripIndent().trim()

        when:
        def result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("${taskName}")
            .build()

        then:
        result.task(":${taskName}").outcome == SUCCESS

        where:
        taskName << ['countDebugDexMethods', 'countReleaseDexMethods']
    }

    @Unroll def '#taskName with buildTypes + productFlavors + flavorDimensions'() {
        given:
        buildFile <<
            """
        plugins {
            id "com.android.application"
            id "com.getkeepsafe.dexcount"
        }

        // TODO(???) - Repositories from test
        repositories {
          google()
          jcenter()
        }

        android {
          compileSdkVersion 28

          defaultConfig {
            applicationId 'com.example'
          }

          buildTypes {
            debug {}
            release {}
          }

          flavorDimensions 'a', 'b'

          productFlavors {
            flavor1 { dimension 'a' }
            flavor2 { dimension 'a' }
            flavor3 { dimension 'b' }
            flavor4 { dimension 'b' }
          }
        }
      """.stripIndent().trim()

        when:
        def result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("${taskName}")
            .build()

        then:
        result.task(":${taskName}").outcome == SUCCESS

        where:
        taskName << ['countFlavor1Flavor3DebugDexMethods', 'countFlavor1Flavor3ReleaseDexMethods',
                     'countFlavor2Flavor4DebugDexMethods', 'countFlavor2Flavor4ReleaseDexMethods']
    }

    @Unroll def '#taskName with apk report example'() {
        given:
        new File(testProjectDir, "build/outputs/apk/$taskName".toString()).mkdirs()
        def apkFile = new File(testProjectDir, "build/outputs/apk/${taskName}/tiniest-smallest-app.apk".toString())
        def apkResource = getClass().getResourceAsStream('/tiniest-smallest-app.apk')
        apkResource.withStream { input ->
            apkFile.append(input)
        }

        buildFile <<
            """
        plugins {
            id "com.android.application"
            id "com.getkeepsafe.dexcount"
        }

        // TODO(???) - Repositories from test
        repositories {
          google()
          jcenter()
        }

        android {
          compileSdkVersion 28

          defaultConfig {
            applicationId 'com.example'
          }
        }
      """.stripIndent().trim()

        when:
        def result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("${taskName}")
            .build()

        then:
        result.task(":${taskName}").outcome == SUCCESS

        where:
        taskName << ['countDebugDexMethods', 'countReleaseDexMethods']
    }

    def 'when enabled is false, no dexcount tasks are added'() {
        given:
        buildFile <<
            """
        plugins {
            id "com.android.application"
            id "com.getkeepsafe.dexcount"
        }

        android {
          compileSdkVersion 28

          defaultConfig {
            applicationId 'com.example'
          }
        }

        dexcount {
          enabled = false
        }
      """.stripIndent().trim()

        when:
        def result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('tasks')
            .build()

        then:
        result.task(':tasks').outcome == SUCCESS
        !result.output.contains('countDebugDexMethods')
    }

    // TODO migrate to new test strategy
    def 'android apk report example'() {
        given:
        def apkFile = new File(testProjectDir, 'tiniest-smallest-app.apk')
        def apkResource = getClass().getResourceAsStream('/tiniest-smallest-app.apk')
        apkResource.withStream { input ->
            apkFile.append(input)
        }

        def apkArtifact = Mock(BuiltArtifact)
        def builtArtifacts = Mock(BuiltArtifacts)
        def loader = Mock(BuiltArtifactsLoader)

        apkArtifact.outputFile >> apkFile.canonicalPath
        builtArtifacts.elements >> [apkArtifact]
        loader.load(_) >> builtArtifacts

        project.apply plugin: 'com.android.application'
        project.apply plugin: 'com.getkeepsafe.dexcount'
        project.android {
            compileSdkVersion 28

            defaultConfig {
                applicationId 'com.example'
            }
        }

        when:
        project.evaluate()

        // Override APK file
        ApkPackageTreeTask task = project.tasks.getByName("generateDebugPackageTree") as ApkPackageTreeTask
        task.outputFileNameProperty.set("pluginSpec")
        task.apkDirectoryProperty.set(apkFile.parentFile)
        task.loaderProperty.set(loader)
        task.execute()

        then:
        // debug.csv - CSV
        def actualOutputFile = task.outputDirectoryProperty.file("pluginSpec.txt").get().asFile.absoluteFile.text.stripIndent().trim()
        def expectedOutputFile = """
            methods  fields   package/class name
            6        0        android
            2        0        android.app
            4        0        android.widget
            3        0        b
            3        0        b.a""".stripIndent().trim()

        // debug.txt - TXT
        def actualSummaryFile = task.outputDirectoryProperty.file("summary.csv").get().asFile.absoluteFile.text.stripIndent().trim()
        def expectedSummaryFile = """
            methods,fields,classes
            9,0,4
            """.stripIndent().trim()

        // debugChart/data.js - JSON
        def actualChartDir = task.outputDirectoryProperty.file("chart/data.js").get().asFile.text.stripIndent().trim()
        def expectedChartDir = """
            var data = {
              "name": "",
              "methods": 9,
              "fields": 0,
              "children": [
                {
                  "name": "android",
                  "methods": 6,
                  "fields": 0,
                  "children": [
                    {
                      "name": "app",
                      "methods": 2,
                      "fields": 0,
                      "children": [
                        {
                          "name": "Activity",
                          "methods": 2,
                          "fields": 0,
                          "children": []
                        }
                      ]
                    },
                    {
                      "name": "widget",
                      "methods": 4,
                      "fields": 0,
                      "children": [
                        {
                          "name": "RelativeLayout",
                          "methods": 2,
                          "fields": 0,
                          "children": []
                        },
                        {
                          "name": "TextView",
                          "methods": 2,
                          "fields": 0,
                          "children": []
                        }
                      ]
                    }
                  ]
                },
                {
                  "name": "b",
                  "methods": 3,
                  "fields": 0,
                  "children": [
                    {
                      "name": "a",
                      "methods": 3,
                      "fields": 0,
                      "children": [
                        {
                          "name": "M",
                          "methods": 3,
                          "fields": 0,
                          "children": []
                        }
                      ]
                    }
                  ]
                }
              ]
            }""".stripIndent().trim()

        actualOutputFile == expectedOutputFile
        actualSummaryFile == expectedSummaryFile
        actualChartDir == expectedChartDir
    }
}
