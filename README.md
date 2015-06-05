# Dexcount Gradle Plugin

A Gradle plugin to report the number of method references in your APK on every build.

This helps you keep tabs on the growth of your app, with an eye to staying under the 65,536 method-reference limit, and avoiding the headache of eliminating methods or enabling multidex.

## Usage:

in `build.gradle`
```groovy
buildScript {
  repositories {
    maven { url "https://oss.sonatype.org/content/repositories/snapshots/" }
  }
  
  classpath {
    classpath 'com.getkeepsafe.dexcount:dexcount-gradle-plugin:0.1.0-SNAPSHOT'
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
