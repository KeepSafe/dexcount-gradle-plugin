#!/usr/bin/env bash

set -e

# Not sure how well Travis deals with cwd; restore it on script exit.
DIR=`pwd`
trap "cd $DIR" SIGINT SIGTERM EXIT 

./gradlew clean check install

VERSION=`grep '^VERSION_NAME=' gradle.properties | cut -d '=' -f 2`

echo "Building integration test project..."
cd integration
./gradlew -PdexcountVersion="$VERSION" assembleDebug > output.log

echo "Integration build done!  Running tests..."

function die() {
  echo $1
  exit 1
}

grep -F 'Total methods in app-debug.apk: 16174 (24.68% used)' output.log || die "Incorrect method count"
grep -F 'Total fields in app-debug.apk:  7093 (10.82% used)' output.log || die "Incorrect field count"
grep -F 'Methods remaining in app-debug.apk: 49361' output.log || die "Incorrect remaining-method value"
grep -F 'Fields remaining in app-debug.apk:  58442' output.log || die "Incorrect remaining-field value"

echo "Tests complete."
