#!/usr/bin/env bash

set -e

# Not sure how well Travis deals with cwd; restore it on script exit.
DIR=`pwd`
trap "cd ${DIR}" SIGINT SIGTERM EXIT

./gradlew build publishToMavenLocal --stacktrace --daemon

VERSION=`grep '^VERSION_NAME=' gradle.properties | cut -d '=' -f 2`

echo "Building integration test project..."
cd integration
../gradlew --daemon clean -PdexcountVersion="$VERSION" -Pandroid.debug.obsoleteApi=true :app:countDebugDexMethods 2>&1 --stacktrace | tee app.log
../gradlew --daemon clean -PdexcountVersion="$VERSION" -Pandroid.debug.obsoleteApi=true :lib:countDebugDexMethods 2>&1 --stacktrace | tee lib.log
../gradlew --daemon clean -PdexcountVersion="$VERSION" -Pandroid.debug.obsoleteApi=true :tests:countDebugDexMethods 2>&1 --stacktrace | tee tests.log
../gradlew --daemon clean -PdexcountVersion="$VERSION" -Pandroid.debug.obsoleteApi=true :app:countDebugBundleDexMethods 2>&1 --stacktrace | tee bundle.log

echo "Integration build done!  Running tests..."

function die() {
  echo $1
  exit 1
}

grep -F 'Total methods in app-debug-it.apk: 6725 (10.26% used)' app.log || die "Incorrect method count in app-debug-it.apk"
grep -F 'Total fields in app-debug-it.apk:  1916 (2.92% used)' app.log || die "Incorrect field count in app-debug-it.apk"
grep -F 'Total classes in app-debug-it.apk:  837 (1.28% used)' app.log || die "Incorrect field count in app-debug-it.apk"
grep -F 'Methods remaining in app-debug-it.apk: 58810' app.log || die "Incorrect remaining-method value in app-debug-it.apk"
grep -F 'Fields remaining in app-debug-it.apk:  63619' app.log || die "Incorrect remaining-field value in app-debug-it.apk"
grep -F 'Classes remaining in app-debug-it.apk:  64698' app.log || die "Incorrect remaining-field value in app-debug-it.apk"

grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_ClassCount' value='837']" app.log || die "Missing or incorrect Teamcity method count value"
grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_MethodCount' value='6725']" app.log || die "Missing or incorrect Teamcity method count value"
grep -F "##teamcity[buildStatisticValue key='Dexcount_app_debug_FieldCount' value='1916']" app.log || die "Missing or incorrect Teamcity field count value"

grep -F 'Total methods in tests-debug.apk: 4266 (6.51% used)' tests.log || die "Incorrect method count in tests-debug.apk"
grep -F 'Total fields in tests-debug.apk:  1268 (1.93% used)' tests.log || die "Incorrect field count in tests-debug.apk"
grep -F 'Total classes in tests-debug.apk:  723 (1.10% used)' tests.log || die "Incorrect field count in tests-debug.apk"
grep -F 'Methods remaining in tests-debug.apk: 61269' tests.log || die "Incorrect remaining-method value in tests-debug.apk"
grep -F 'Fields remaining in tests-debug.apk:  64267' tests.log || die "Incorrect remaining-field value in tests-debug.apk"
grep -F 'Classes remaining in tests-debug.apk:  64812' tests.log || die "Incorrect remaining-field value in tests-debug.apk"

grep -F 'Total methods in lib-debug.aar: 7 (0.01% used)' lib.log || die "Incorrect method count in lib-debug.aar"
grep -F 'Total fields in lib-debug.aar:  3 (0.00% used)' lib.log || die "Incorrect field count in lib-debug.aar"
grep -F 'Total classes in lib-debug.aar:  6 (0.01% used)' lib.log || die "Incorrect class count in lib-debug.aar"
grep -F 'Methods remaining in lib-debug.aar: 65528' lib.log || die "Incorrect remaining-method count in lib-debug.aar"
grep -F 'Fields remaining in lib-debug.aar:  65532' lib.log || die "Incorrect remaining-field count in lib-debug.aar"
grep -F 'Classes remaining in lib-debug.aar:  65529' lib.log || die "Incorrect remaining-class count in lib-debug.aar"

grep -F 'Total methods in app-debug.aab: 6725 (10.26% used)' bundle.log || die "Incorrect method count in app-debug.aab"
grep -F 'Total fields in app-debug.aab:  1916 (2.92% used)' bundle.log || die "Incorrect field count in app-debug.aab"
grep -F 'Total classes in app-debug.aab:  837 (1.28% used)' bundle.log || die "Incorrect field count in app-debug.aab"

# Note the '&&' here - grep exits with an error if no lines match,
# which is the condition we want here.  If any lines match, that
# signifies that we're using deprecated Gradle APIs and is a bug.
grep -F "WARNING: API 'variantOutput.getPackageApplication()'" app.log && die "Deprecated API use detected"
grep -F "WARNING: API 'variantOutput.getPackageApplication()'" lib.log && die "Deprecated API use detected"
grep -F "WARNING: API 'variantOutput.getPackageApplication()'" tests.log && die "Deprecated API use detected"

echo "Tests complete."
