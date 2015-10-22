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
        mavenCentral() // or jCenter()
    }

    dependencies {
        classpath 'com.getkeepsafe.dexcount:dexcount-gradle-plugin:0.2.1'
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
methods  package/class name
6        android.speech
6        android.speech.tts
5        android.speech.tts.TextToSpeech
1        android.speech.tts.UtteranceProgressListener
10789    android.support
20       android.support.annotation
1        android.support.annotation.CheckResult
4        android.support.annotation.FloatRange
2        android.support.annotation.IntDef
2        android.support.annotation.IntRange
6        android.support.annotation.RequiresPermission
1        android.support.annotation.RequiresPermission.Read
1        android.support.annotation.RequiresPermission.Write
4        android.support.annotation.Size
1        android.support.annotation.StringDef
7010     android.support.v4
1        android.support.v4.BuildConfig
41       android.support.v4.accessibilityservice
```

## Configuration

Dexcount is configurable via a Gradle extension (shown with default values):

in `app/build.gradle`:
```groovy
dexcount {
    includeClasses = false
    includeFieldCount = false
    printAsTree = false
    orderByMethodCount = false
    verbose = false
    exportAsCSV = false
}
```

Each flag controls some aspect of the printed output:
- `includeClasses`: When true, individual classes will be include in the pacage list - otherwise, only packages are included.
- `includeFieldCount`: When true, the number of fields in a package or class will be included in the printed output.
- `printAsTree`: When true, the output file will be formatted as a package tree, with nested packages indented, instead of the default list format.
- `orderByMethodCount`: When true, packages will be sorted in descending order by the number of methods they contain.
- `verbose`: When true, the output file will also be printed to the build's standard output.
- `exportAsCSV`: When true, the task will create a csv file with the summary: number of methods plus number of fields if includeFieldCount is true.

## Use with Jenkins Plot Plugin

A common use-case is to plot method and field counts across builds.  The [Jenkins Plot plugin][0] is a general-purpose tool that graphs per-build scalar values through time.  It reads java .properties files, CSV files, and XML files.  The default dexcount output is a tab-separated, and using command-line tools can easily be converted into a form Jenkins can use.  Assuming a UNIX enviroment, it is simple.

If you are counting both methods and fields, the following post-build script will (when you make the appropriate path substitutions) generate a .csv file:

```bash
INPUT=path/to/outputs/dexcount/debug.txt
PLOT_FILE=path/to/jenkins/report.csv

tail -n +2 $INPUT | awk '$3 !~ /\./ { methods += $1; fields += $2 } END { printf "methods,fields\n%d,%d\n", methods, fields }' > $PLOT_FILE
```

If you are counting only methods, the awk script changes slightly:

```bash
INPUT=path/to/outputs/dexcount/debug.txt
METHOD_FILE=path/to/jenkins/report.csv

tail -n +2 INPUT | awk '$2 !~ /\./ { methods += $1 } END { printf "methods\n%d\n", methods }' > $PLOT_FILE
```

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
    classpath 'com.getkeepsafe.dexcount:dexcount-gradle-plugin:0.2.2-SNAPSHOT'
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

Copyright 2015 KeepSafe Software, Inc

[0]: https://wiki.jenkins-ci.org/display/JENKINS/Plot+Plugin
