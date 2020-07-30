<h1 align="center">
	<p>Dexcount Gradle Plugin</p>
    <img src="docs/images/example.png" alt="a chart showing sample methods counts by package">
</h1>

[![Build Status](https://github.com/KeepSafe/dexcount-gradle-plugin/workflows/CI/badge.svg)](https://github.com/KeepSafe/dexcount-gradle-plugin/actions?query=workflow%3ACI)
[![Android Weekly](http://img.shields.io/badge/Android%20Weekly-%23174-2CB3E5.svg?style=flat)](http://androidweekly.net/issues/issue-174)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Dexcount%20Gradle%20Plugin-brightgreen.svg?style=flat)](http://android-arsenal.com/details/1/1940)

A Gradle plugin to report the number of method references in your APK, AAR, or java module.

This helps you keep tabs on the growth of your app, with an eye to staying under the 65,536 method-reference limit, and avoiding the headache of eliminating methods or enabling multidex.

For more information, please see [the website](https://keepsafe.github.io/dexcount-gradle-plugin/).

## Download

The plugin is available from the Gradle Plugin Portal under the ID `com.getkeepsafe.dexcount`, and from Maven Central under the coordinates `com.getkeepsafe.dexcount:dexcount-gradle-plugin:2.0.0`.

Snapshot builds are available from the Sonatype Snapshot Repository at `https://oss.sonatype.org/content/repositories/snapshots`.

Dexcount requires Java 8 or higher, Gradle 6.0 or higher, and Android Gradle Plugin 3.4.0 or higher.

## Credits

The Java code from the `com.android.dexdeps` package is sourced from the [Android source tree](https://android.googlesource.com/platform/dalvik.git/+/master/tools/dexdeps/).
Inspired by Mihail Parparita's [`dex-method-counts`](https://github.com/mihaip/dex-method-counts) project, to whom much credit is due.

Copyright 2015-2020 Keepsafe Software, Inc
