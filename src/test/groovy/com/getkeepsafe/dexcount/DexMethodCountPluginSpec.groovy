package com.getkeepsafe.dexcount

import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.logging.Logger
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

final class DexMethodCountPluginSpec extends Specification {
  @Rule final TemporaryFolder tempFolder = new TemporaryFolder()
  final static COMPILE_SDK_VERSION = 25
  final static BUILD_TOOLS_VERSION = "25.0.2"
  final static APPLICATION_ID = "com.example"
  def project

  def "setup"() {
    project = ProjectBuilder.builder().build()
  }

  def "unsupported project project"() {
    when:
    new DexMethodCountPlugin().apply(project) // project.apply plugin: "com.getkeepsafe.dexcount"

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "Dexcount plugin requires the Android plugin to be configured"
  }

  @Unroll "#projectPlugin project"() {
    given:
    project.apply plugin: projectPlugin

    when:
    project.apply plugin: "com.getkeepsafe.dexcount"

    then:
    noExceptionThrown()

    where:
    projectPlugin << ["com.android.application", "com.android.library", "com.android.test"]
  }

  def "android - all tasks created"() {
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

    when:
    project.evaluate()

    then:
    project.tasks.getByName("countDebugDexMethods")
    project.tasks.getByName("countReleaseDexMethods")
  }

  def "android [buildTypes] - all tasks created"() {
    given:
    project.apply plugin: "com.android.application"
    project.apply plugin: "com.getkeepsafe.dexcount"
    project.android {
      compileSdkVersion COMPILE_SDK_VERSION
      buildToolsVersion BUILD_TOOLS_VERSION

      defaultConfig {
        applicationId APPLICATION_ID
      }

      buildTypes {
        debug {}
        release {}
      }
    }

    when:
    project.evaluate()

    then:
    project.tasks.getByName("countDebugDexMethods")
    project.tasks.getByName("countReleaseDexMethods")
  }

  def "android [buildTypes + productFlavors] - all tasks created"() {
    given:
    project.apply plugin: "com.android.application"
    project.apply plugin: "com.getkeepsafe.dexcount"
    project.android {
      compileSdkVersion COMPILE_SDK_VERSION
      buildToolsVersion BUILD_TOOLS_VERSION

      defaultConfig {
        applicationId APPLICATION_ID
      }

      buildTypes {
        debug {}
        release {}
      }

      productFlavors {
        flavor1 {}
        flavor2 {}
      }
    }

    when:
    project.evaluate()

    then:
    project.tasks.getByName("countFlavor1DebugDexMethods")
    project.tasks.getByName("countFlavor1ReleaseDexMethods")
    project.tasks.getByName("countFlavor2DebugDexMethods")
    project.tasks.getByName("countFlavor2ReleaseDexMethods")
  }

  def "android [buildTypes + productFlavors + flavorDimensions] - all tasks created"() {
    given:
    project.apply plugin: "com.android.application"
    project.apply plugin: "com.getkeepsafe.dexcount"
    project.android {
      compileSdkVersion COMPILE_SDK_VERSION
      buildToolsVersion BUILD_TOOLS_VERSION

      defaultConfig {
        applicationId APPLICATION_ID
      }

      buildTypes {
        debug {}
        release {}
      }

      flavorDimensions "a", "b"

      productFlavors {
        flavor1 { dimension "a" }
        flavor2 { dimension "a" }
        flavor3 { dimension "b" }
        flavor4 { dimension "b" }
      }
    }

    when:
    project.evaluate()

    then:
    project.tasks.getByName("countFlavor1Flavor3DebugDexMethods")
    project.tasks.getByName("countFlavor1Flavor3ReleaseDexMethods")
    project.tasks.getByName("countFlavor2Flavor4DebugDexMethods")
    project.tasks.getByName("countFlavor2Flavor4ReleaseDexMethods")
  }

  def "android apk report example"() {
    given:
    def apkFile = tempFolder.newFile("tiniest-smallest-app.apk")
    def apkResource = getClass().getResourceAsStream("/tiniest-smallest-app.apk")
    apkResource.withStream { input ->
      IOUtil.drainToFile(input, apkFile)
    }

    project.apply plugin: "com.android.application"
    project.apply plugin: "com.getkeepsafe.dexcount"
    project.android {
      compileSdkVersion COMPILE_SDK_VERSION
      buildToolsVersion BUILD_TOOLS_VERSION

      defaultConfig {
        applicationId APPLICATION_ID
      }
    }

    when:
    project.evaluate()

    // Override APK file
    DexMethodCountTask task = project.tasks.getByName("countDebugDexMethods") as DexMethodCountTask
    task.apkOrDex = Mock(BaseVariantOutput)
    task.apkOrDex.outputFile >> apkFile
    task.execute()

    then:
    // debug.csv - CSV
    def actualOutputFile = task.outputFile.absoluteFile.text
    def expectedOutputFile = "methods  fields   package/class name\n" +
            "6        0        android\n" +
            "2        0        android.app\n" +
            "4        0        android.widget\n" +
            "3        0        b\n" +
            "3        0        b.a\n"
    // debug.txt - TXT
    def actualSummaryFile = task.summaryFile.absoluteFile.text
    def expectedSummaryFile = "methods,fields\n" +
            "9,0\n"
    // debugChart/data.js - JSON
    def actualChartDir = new File(task.chartDir, "data.js").text
    def expectedChartDir = "var data = {\n" +
            "  \"name\": \"\",\n" +
            "  \"methods\": 9,\n" +
            "  \"fields\": 0,\n" +
            "  \"children\": [\n" +
            "    {\n" +
            "      \"name\": \"android\",\n" +
            "      \"methods\": 6,\n" +
            "      \"fields\": 0,\n" +
            "      \"children\": [\n" +
            "        {\n" +
            "          \"name\": \"app\",\n" +
            "          \"methods\": 2,\n" +
            "          \"fields\": 0,\n" +
            "          \"children\": [\n" +
            "            {\n" +
            "              \"name\": \"Activity\",\n" +
            "              \"methods\": 2,\n" +
            "              \"fields\": 0,\n" +
            "              \"children\": []\n" +
            "            }\n" +
            "          ]\n" +
            "        },\n" +
            "        {\n" +
            "          \"name\": \"widget\",\n" +
            "          \"methods\": 4,\n" +
            "          \"fields\": 0,\n" +
            "          \"children\": [\n" +
            "            {\n" +
            "              \"name\": \"RelativeLayout\",\n" +
            "              \"methods\": 2,\n" +
            "              \"fields\": 0,\n" +
            "              \"children\": []\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"TextView\",\n" +
            "              \"methods\": 2,\n" +
            "              \"fields\": 0,\n" +
            "              \"children\": []\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"b\",\n" +
            "      \"methods\": 3,\n" +
            "      \"fields\": 0,\n" +
            "      \"children\": [\n" +
            "        {\n" +
            "          \"name\": \"a\",\n" +
            "          \"methods\": 3,\n" +
            "          \"fields\": 0,\n" +
            "          \"children\": [\n" +
            "            {\n" +
            "              \"name\": \"M\",\n" +
            "              \"methods\": 3,\n" +
            "              \"fields\": 0,\n" +
            "              \"children\": []\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}"
    actualOutputFile == expectedOutputFile
    actualSummaryFile == expectedSummaryFile
    actualChartDir == expectedChartDir
  }
}
