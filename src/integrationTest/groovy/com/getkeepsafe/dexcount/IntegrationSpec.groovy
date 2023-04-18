package com.getkeepsafe.dexcount

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class IntegrationSpec extends Specification {
    @Shared File integrationTestDir = new File(["src", "integrationTest", "projects", "integration"].join(File.separator))

    @Unroll
    def "counting APKs using AGP #agpVersion and Gradle #gradleVersion"() {
        given: "an integration test project"
        def project = projectDir(agpVersion, gradleVersion)

        when:
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(project)
            .withArguments(":app:countDebugDexMethods", "--stacktrace")
            .build()

        then:
        result.task(":app:countDebugDexMethods").outcome == TaskOutcome.SUCCESS

        result.output =~ /Total methods in app-debug-it.apk:\s+${numMethods}/
        result.output =~ /Total fields in app-debug-it.apk:\s+${numFields}/
        result.output =~ /Total classes in app-debug-it.apk:\s+${numClasses}/

        where:
        agpVersion      | gradleVersion || numMethods | numClasses | numFields
        "8.1.0-alpha10" | "8.0.2"       || 6935       | 1024       | 2528
        "8.0.0"         | "8.0.2"       || 6929       | 1020       | 2528
        "7.4.1"         | "7.6"         || 7273       | 1065       | 2657
        "7.3.1"         | "7.6"         || 7263       | 1037       | 2666
        "7.2.2"         | "7.6"         || 7410       | 925        | 2666
        "7.1.1"         | "7.6"         || 7421       | 926        | 2676
        "7.0.0"         | "7.6"         || 7355       | 926        | 2592
    }

    @Unroll
    @Ignore
    def "completes successfully using AGP #agpVersion and Gradle #gradleVersion"() {
        given: "an integration test project"
        def project = projectDir(agpVersion, gradleVersion)

        when:
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(project)
            .withArguments(":app:countDebugDexMethods", "--stacktrace")
            .build()

        then:
        result.task(":app:countDebugDexMethods").outcome == TaskOutcome.SUCCESS

        // These version combinations were known to fail at some point.
        // This spec serves to guard against regression.
        where:
        agpVersion | gradleVersion | reportedIn
        "4.0.0"    | "6.1.1"       | "https://github.com/KeepSafe/dexcount-gradle-plugin/issues/410"
    }

    @Unroll
    def "counting AARs using AGP #agpVersion and Gradle #gradleVersion"() {
        given: "an integration test project"
        def project = projectDir(agpVersion, gradleVersion)

        when:
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(project)
            .withArguments(":lib:countDebugDexMethods", "--stacktrace")
            .build()

        then:
        result.task(":lib:countDebugDexMethods").outcome == TaskOutcome.SUCCESS

        result.output =~ /Total methods in lib-debug.aar:\s+${numMethods}/
        result.output =~ /Total fields in lib-debug.aar:\s+${numFields}/
        result.output =~ /Total classes in lib-debug.aar:\s+${numClasses}/

        where:
        agpVersion      | gradleVersion || numMethods | numClasses | numFields
        "8.1.0-alpha10" | "8.0.2"       || 4          | 3          | 0
        "8.0.0"         | "8.0.2"       || 4          | 3          | 0
        "7.4.1"         | "7.6"         || 7          | 5          | 3
        "7.3.1"         | "7.6"         || 7          | 5          | 3
        "7.2.2"         | "7.6"         || 7          | 5          | 3
        "7.1.1"         | "7.6"         || 7          | 5          | 3
        "7.0.0"         | "7.6"         || 7          | 5          | 3
    }

    @Unroll
    def "counting Android Test projects using AGP #agpVersion and Gradle #gradleVersion"() {
        given: "an integration test project"
        def project = projectDir(agpVersion, gradleVersion)

        when:
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(project)
            .withArguments(":tests:countDebugDexMethods", "--stacktrace")
            .build()

        then:
        result.task(":tests:countDebugDexMethods").outcome == TaskOutcome.SUCCESS

        result.output =~ /Total methods in tests-debug.apk:\s+${numMethods}/
        result.output =~ /Total fields in tests-debug.apk:\s+${numFields}/
        result.output =~ /Total classes in tests-debug.apk:\s+${numClasses}/

        where:
        agpVersion      | gradleVersion || numMethods | numClasses | numFields
        "8.1.0-alpha10" | "8.0.2"       || 4240       | 725        | 1265
        "8.0.0"         | "8.0.2"       || 4240       | 725        | 1265
        "7.4.1"         | "7.6"         || 4242       | 726        | 1268
        "7.3.1"         | "7.6"         || 4277       | 745        | 1284
        "7.2.2"         | "7.6"         || 4266       | 723        | 1268
        "7.1.1"         | "7.6"         || 4266       | 723        | 1268
        "7.0.0"         | "7.6"         || 4266       | 723        | 1268
    }

    @Unroll
    def "counting Android Bundles using AGP #agpVersion and Gradle #gradleVersion"() {
        given: "an integration test project"
        def project = projectDir(agpVersion, gradleVersion)

        when:
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(project)
            .withArguments(":app:countDebugBundleDexMethods", "--stacktrace")
            .build()

        then:
        result.task(":app:countDebugBundleDexMethods").outcome == TaskOutcome.SUCCESS

        result.output =~ /Total methods in app-debug.aab:\s+${numMethods}/
        result.output =~ /Total fields in app-debug.aab:\s+${numFields}/
        result.output =~ /Total classes in app-debug.aab:\s+${numClasses}/

        where:
        agpVersion      | gradleVersion || numMethods | numClasses | numFields
        "8.1.0-alpha10" | "8.0.2"       || 6935       | 1024       | 2528
        "8.0.0"         | "8.0.2"       || 6929       | 1020       | 2528
        "7.4.1"         | "7.6"         || 7273       | 1065       | 2657
        "7.3.1"         | "7.6"         || 7263       | 1037       | 2666
        "7.2.2"         | "7.6"         || 7410       | 925        | 2666
        "7.1.1"         | "7.6"         || 7421       | 926        | 2676
        "7.0.0"         | "7.6"         || 7355       | 926        | 2592
    }

    private File projectDir(String agpVersion, String gradleVersion) {
        def projectDir = new File(new File("tmp", gradleVersion), agpVersion)
        FileUtils.copyDirectory(integrationTestDir, projectDir, true)

        def gradleProperties = new File(projectDir, "gradle.properties")
        gradleProperties.delete()
        gradleProperties << """
            org.gradle.caching=true
            org.gradle.jvmargs=-XX:MaxMetaspaceSize=1024m
            agpVersion=$agpVersion
        """.stripIndent()

        def localProperties = new File(projectDir, "local.properties")
        localProperties.delete()

        String sdkRoot = System.getenv("ANDROID_HOME")
        if (sdkRoot == null) {
            sdkRoot = System.getenv("ANDROID_SDK_ROOT")
        }

        if (sdkRoot != null) {
            localProperties << "sdk.dir=$sdkRoot"
        }

        return projectDir
    }
}
