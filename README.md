# Dexcount Gradle Plugin

[![Build Status](https://travis-ci.org/KeepSafe/dexcount-gradle-plugin.svg?branch=master)](https://travis-ci.org/KeepSafe/dexcount-gradle-plugin)

A Gradle plugin to report the number of method references in your APK on every build.

This helps you keep tabs on the growth of your app, with an eye to staying under the 65,536 method-reference limit, and avoiding the headache of eliminating methods or enabling multidex.

## Usage:

in `build.gradle`
```groovy
buildscript {
    repositories {
        mavenCentral() // or jCenter()
    }

    dependencies {
        classpath 'com.getkeepsafe.dexcount:dexcount-gradle-plugin:0.1.1'
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

####Note:
If your app is large enough for method counts to be a concern, you probably have hundreds or thousands of packages in this list.  If you want to quickly find the packages with the most methods, a feature is forthcoming.  In the meanwhile, a bit of command-line work will do:

`cat app/build/outputs/dexcount/debug.txt | sort -nr | less`

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
