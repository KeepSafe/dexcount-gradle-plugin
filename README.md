# Dexcount Gradle Plugin

[![Build Status](https://travis-ci.org/KeepSafe/dexcount-gradle-plugin.svg?branch=master)](https://travis-ci.org/KeepSafe/dexcount-gradle-plugin)
[![Android Weekly](http://img.shields.io/badge/Android%20Weekly-%23174-2CB3E5.svg?style=flat)](http://androidweekly.net/issues/issue-174)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Dexcount%20Gradle%20Plugin-brightgreen.svg?style=flat)](http://android-arsenal.com/details/1/1940)

A Gradle plugin to report the number of method references in your APK on every build.

This helps you keep tabs on the growth of your app, with an eye to staying under the 65,536 method-reference limit, and avoiding the headache of eliminating methods or enabling multidex.

## Usage:

in `app/build.gradle`
```groovy
buildscript {
    repositories {
        mavenCentral() // or jcenter()
    }

    dependencies {
        classpath 'com.getkeepsafe.dexcount:dexcount-gradle-plugin:0.4.0'
    }
}

apply plugin: 'com.getkeepsafe.dexcount'
```

## Sample output:

```
> ./gradlew assembleDebug

...buildspam...
:app:compileDebugSources
:app:preDexDebug UP-TO-DATE
:app:dexDebug
:app:packageDebug
:app:zipalignDebug
:app:assembleDebug
Total methods in MyApp-debug-5.3.14.apk: 56538

BUILD SUCCESSFUL

Total time: 33.017 secs
```

## Detailed method counts

By default, a breakdown of method references by package and class will be written to a file under `${buildDir}/outputs/dexcount/${variant}`.

For example, an excerpt from our own app (in `app/build/outputs/dexcount/debug.txt`):
```
methods  fields   package/class name
5037     1103     android.support.v4
29       1        android.support.v4.accessibilityservice
57       16       android.support.v4.animation
931      405      android.support.v4.app
87       31       android.support.v4.content
139      12       android.support.v4.graphics
116      11       android.support.v4.graphics.drawable
74       9        android.support.v4.internal
74       9        android.support.v4.internal.view
194      35       android.support.v4.media
11       0        android.support.v4.media.routing
156      26       android.support.v4.media.session
```

## Configuration

Dexcount is configurable via a Gradle extension (shown with default values):

in `app/build.gradle`:
```groovy
dexcount {
    format = "list"
    includeClasses = false
    includeFieldCount = true
    includeTotalMethodCount = false
    orderByMethodCount = false
    verbose = false
}
```

Each flag controls some aspect of the printed output:
- `format`: The format of the method count output, either "list", "tree", "json", or "yaml".
- `includeClasses`: When true, individual classes will be include in the package list - otherwise, only packages are included.
- `includeFieldCount`: When true, the number of fields in a package or class will be included in the printed output.
- `includeTotalMethodCount`: When true, the total number of methods in the application will be included in the printed output.
- `orderByMethodCount`: When true, packages will be sorted in descending order by the number of methods they contain.
- `verbose`: When true, the output file will also be printed to the build's standard output.

## Use with Jenkins Plot Plugin

A common use-case is to plot method and field counts across builds.  The [Jenkins Plot plugin][0] is a general-purpose tool that graphs per-build scalar values through time.  It reads java .properties files, CSV files, and XML files.  Dexcount generates two files for each variant - a full package list, and a summary CSV file.  The summary file is usable as-is with the Jenkins Plot Plugin.  You can find it in `app/build/outputs/variant.csv` (note the `.csv` extension).

Consult the plugin documentation for details on how to configure it.

## Snapshot Builds

We host snapshots in the Sonatype OSS repo.  They are updated on each commit.  As snapshots, they are inherently unstable - use at your own risk!  To use them, add the Sonatype Snapshot repo to your repositories:

```groovy
buildscript {
  repositories {
    // other repos should come first
    maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
  }

  dependencies {
    classpath 'com.getkeepsafe.dexcount:dexcount-gradle-plugin:0.4.1-SNAPSHOT'
  }
}
```


## Building

`./gradlew build`

Pushing artifacts to Sonatype requires membership in the KeepSafe Sonatype org, which is by employment only.  Once
you have a login, put it in your private global Gradle file (e.g. `~/.gradle/gradle.properties`, along with a valid
GPG signing configuration.

## Minutia

This plugin creates a task per output file, per variant, and configures each task to run after that variant's `assemble` task.  This means that if the `assemble` task does not run, no method count will be reported.

## Credits

The Java code from the `com.android.dexdeps` package is sourced from the [Android source tree](https://android.googlesource.com/platform/dalvik.git/+/master/tools/dexdeps/).
Inspired by Mihail Parparita's [`dex-method-counts`](https://github.com/mihaip/dex-method-counts) project, to whom much credit is due.

Copyright 2015-2016 KeepSafe Software, Inc

[0]: https://wiki.jenkins-ci.org/display/JENKINS/Plot+Plugin
