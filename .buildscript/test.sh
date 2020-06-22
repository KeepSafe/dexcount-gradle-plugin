#!/usr/bin/env bash

set -e

# Not sure how well Travis deals with cwd; restore it on script exit.
DIR=`pwd`
trap "cd ${DIR}" SIGINT SIGTERM EXIT

./gradlew clean build publishToMavenLocal --stacktrace --daemon

VERSION=`grep '^VERSION_NAME=' gradle.properties | cut -d '=' -f 2`

echo "Building integration test project..."
cd integration
../gradlew --daemon clean -PdexcountVersion="$VERSION" -Pandroid.debug.obsoleteApi=true :app:assembleDebug 2>&1 --stacktrace | tee app.log
../gradlew --daemon clean -PdexcountVersion="$VERSION" -Pandroid.debug.obsoleteApi=true :lib:assembleDebug 2>&1 --stacktrace | tee lib.log
../gradlew --daemon clean -PdexcountVersion="$VERSION" -Pandroid.debug.obsoleteApi=true :tests:assembleDebug 2>&1 --stacktrace | tee tests.log

echo "Integration build done!  Running tests..."

function die() {
  echo $1
  exit 1
}

grep -F 'Total methods in app-debug-it.apk: 7356 (11.22% used)' app.log || die "Incorrect method count in app-debug-it.apk"
grep -F 'Total fields in app-debug-it.apk:  2597 (3.96% used)' app.log || die "Incorrect field count in app-debug-it.apk"
grep -F 'Total classes in app-debug-it.apk:  441 (0.67% used)' app.log || die "Incorrect field count in app-debug-it.apk"
grep -F 'Methods remaining in app-debug-it.apk: 58179' app.log || die "Incorrect remaining-method value in app-debug-it.apk"
grep -F 'Fields remaining in app-debug-it.apk:  62938' app.log || die "Incorrect remaining-field value in app-debug-it.apk"
grep -F 'Classes remaining in app-debug-it.apk:  65094' app.log || die "Incorrect remaining-field value in app-debug-it.apk"

grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_ClassCount' value='441']" app.log || die "Missing or incorrect Teamcity method count value"
grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_MethodCount' value='7356']" app.log || die "Missing or incorrect Teamcity method count value"
grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_FieldCount' value='2597']" app.log || die "Missing or incorrect Teamcity field count value"

grep -F 'Total methods in tests-debug.apk: 4266 (6.51% used)' tests.log || die "Incorrect method count in tests-debug.apk"
grep -F 'Total fields in tests-debug.apk:  1268 (1.93% used)' tests.log || die "Incorrect field count in tests-debug.apk"
grep -F 'Total classes in tests-debug.apk:  723 (1.10% used)' tests.log || die "Incorrect field count in tests-debug.apk"
grep -F 'Methods remaining in tests-debug.apk: 61269' tests.log || die "Incorrect remaining-method value in tests-debug.apk"
grep -F 'Fields remaining in tests-debug.apk:  64267' tests.log || die "Incorrect remaining-field value in tests-debug.apk"
grep -F 'Classes remaining in tests-debug.apk:  64812' tests.log || die "Incorrect remaining-field value in tests-debug.apk"

grep -F 'Total methods in lib-debug.aar: 7 (0.01% used)' lib.log || die "Incorrect method count in lib-debug.aar"
grep -F 'Total fields in lib-debug.aar:  3 (0.00% used)' lib.log || die "Incorrect field count in lib-debug.aar"
grep -F 'Total classes in lib-debug.aar:  5 (0.01% used)' lib.log || die "Incorrect class count in lib-debug.aar"
grep -F 'Methods remaining in lib-debug.aar: 65528' lib.log || die "Incorrect remaining-method count in lib-debug.aar"
grep -F 'Fields remaining in lib-debug.aar:  65532' lib.log || die "Incorrect remaining-field count in lib-debug.aar"
grep -F 'Classes remaining in lib-debug.aar:  65530' lib.log || die "Incorrect remaining-class count in lib-debug.aar"

# Note the '&&' here - grep exits with an error if no lines match,
# which is the condition we want here.  If any lines match, that
# signifies that we're using deprecated Gradle APIs and is a bug.
grep -F "WARNING: API 'variantOutput.getPackageApplication()'" app.log && die "Deprecated API use detected"
grep -F "WARNING: API 'variantOutput.getPackageApplication()'" lib.log && die "Deprecated API use detected"
grep -F "WARNING: API 'variantOutput.getPackageApplication()'" tests.log && die "Deprecated API use detected"

echo "Tests complete."
