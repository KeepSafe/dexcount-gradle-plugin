#!/bin/bash
#
# Deploy a jar, source jar, and javadoc jar to Sonatype's snapshot repo.
#
# Adapted from https://coderwall.com/p/9b_lfq and
# http://benlimmer.com/2013/12/26/automatically-publish-javadoc-to-gh-pages-with-travis-ci/

SLUG="KeepSafe/dexcount-gradle-plugin"
EVENT="push"
BRANCH="refs/heads/master"

set -e

if [ "$GITHUB_REPOSITORY" != "$SLUG" ]; then
  echo "Skipping snapshot deployment: wrong repository. Expected '$SLUG' but was '$TRAVIS_REPO_SLUG'."
  exit 1
elif [ "$GITHUB_EVENT_NAME" != "$EVENT" ]; then
  echo "Skipping snapshot deployment: was pull request."
  exit 1
elif [ "$GITHUB_REF" != "$BRANCH" ]; then
  echo "Skipping snapshot deployment: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'."
  exit 1
else
  echo "Deploying snapshot..."
  ./gradlew clean publish
  echo "Snapshot deployed!"
fi
