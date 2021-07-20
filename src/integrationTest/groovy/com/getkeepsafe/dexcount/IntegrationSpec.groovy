package com.getkeepsafe.dexcount

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
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
        "7.1.0-alpha03" | "7.0.2"       || 7355       | 926        | 2590
        "7.0.0-rc01"    | "7.0.2"       || 7355       | 926        | 2592
        "4.2.0"         | "6.8.1"       || 7422       | 926        | 2677
        "4.1.0"         | "6.7.1"       || 7356       | 926        | 2597
        "3.6.0"         | "6.5.1"       || 7370       | 926        | 3780
        "3.5.4"         | "6.5.1"       || 7369       | 926        | 3780
        "3.4.0"         | "6.5.1"       || 7435       | 926        | 3847
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
        "7.1.0-alpha03" | "7.0.2"       || 7          | 5          | 3
        "7.0.0-rc01"    | "7.0.2"       || 7          | 5          | 3
        "4.2.0"         | "6.8.1"       || 7          | 6          | 3
        "4.1.0"         | "6.7.1"       || 7          | 6          | 3
        "3.6.0"         | "6.5.1"       || 7          | 6          | 7
        "3.5.4"         | "6.5.1"       || 7          | 6          | 7
        "3.4.0"         | "6.5.1"       || 7          | 6          | 6
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
        agpVersion         | gradleVersion || numMethods | numClasses | numFields
        "7.1.0-alpha03"    | "7.0.2"       || 4266       | 723        | 1268
        "7.0.0-rc01"       | "7.0.2"       || 4266       | 723        | 1268
        "4.2.0"            | "6.8.1"       || 4266       | 723        | 1268
        "4.1.0"            | "6.7.1"       || 4266       | 723        | 1268
        "3.6.0"            | "6.5.1"       || 4265       | 723        | 1271
        "3.5.4"            | "6.5.1"       || 4266       | 723        | 1271
        "3.4.0"            | "6.5.1"       || 4267       | 723        | 1271
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
        "7.1.0-alpha03" | "7.0.2"       || 7355       | 926        | 2590
        "7.0.0-rc01"    | "7.0.2"       || 7355       | 926        | 2592
        "4.2.0"         | "6.8.1"       || 7422       | 926        | 2677
        "4.1.0"         | "6.7.1"       || 7356       | 926        | 2597
    }

    private File projectDir(String agpVersion, String gradleVersion) {
        def projectDir = new File(new File("tmp", gradleVersion), agpVersion)
        new AntBuilder().copy(todir: projectDir.absolutePath) {
            fileset(dir: integrationTestDir.absolutePath)
        }

        def gradleProperties = new File(projectDir, "gradle.properties")
        gradleProperties.delete()
        gradleProperties << """
            org.gradle.caching=true
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
