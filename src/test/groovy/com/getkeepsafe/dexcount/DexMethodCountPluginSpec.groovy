package com.getkeepsafe.dexcount

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

final class DexMethodCountPluginSpec extends Specification {
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
    def e = thrown IllegalArgumentException
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
}
