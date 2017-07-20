#!/usr/bin/env bash

set -e

# Not sure how well Travis deals with cwd; restore it on script exit.
DIR=`pwd`
trap "cd $DIR" SIGINT SIGTERM EXIT

./gradlew clean build install

VERSION=`grep '^VERSION_NAME=' gradle.properties | cut -d '=' -f 2`

echo "Building integration test project..."
cd integration
./gradlew clean -PdexcountVersion="$VERSION" :app:assembleDebug 2>&1 -s | tee app.log
./gradlew clean -PdexcountVersion="$VERSION" :tests:assembleDebug 2>&1 -s | tee tests.log

echo "Integration build done!  Running tests..."

function die() {
  echo $1
  exit 1
}

grep -F 'Total methods in app-debug.apk: 17377 (26.52% used)' app.log || die "Incorrect method count in app-debug.apk"
grep -F 'Total fields in app-debug.apk:  7961 (12.15% used)' app.log || die "Incorrect field count in app-debug.apk"
grep -F 'Methods remaining in app-debug.apk: 48158' app.log || die "Incorrect remaining-method value in app-debug.apk"
grep -F 'Fields remaining in app-debug.apk:  57574' app.log || die "Incorrect remaining-field value in app-debug.apk"

grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_MethodCount' value='17377']" app.log || die "Missing or incorrect Teamcity method count value"
grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_FieldCount' value='7961']" app.log || die "Missing or incorrect Teamcity field count value"

grep -F 'Total methods in tests-debug.apk: 3086 (4.71% used)' tests.log || die "Incorrect method count in tests-debug.apk"
grep -F 'Total fields in tests-debug.apk:  774 (1.18% used)' tests.log || die "Incorrect field count in tests-debug.apk"
grep -F 'Methods remaining in tests-debug.apk: 62449' tests.log || die "Incorrect remaining-method value in tests-debug.apk"
grep -F 'Fields remaining in tests-debug.apk:  64761' tests.log || die "Incorrect remaining-field value in tests-debug.apk"

echo "Tests complete."
