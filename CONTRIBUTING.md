## Contributing

Contributions are welcome!

1. Make your own fork of this repo
2. Make your changes in a branch, in your own repo.
3. If you add any new files, please make sure the Apache license header is included up top.
4. When you are done coding, be sure to run `test.sh` and make sure that it passes!
5. When your code is ready and the tests are passing, open up a pull request against our master branch.

If you have bug reports or feature requests, please open a GitHub Issue, and we'll get to it ASAP.

## Building

```sh
./gradlew build
```

Note that this runs the integration tests, which can be rather slow.  To run only the regular unit tests:

```sh
./gradlew test
```

## Releasing

We use the [`gradle-maven-publish-plugin`](https://github.com/vanniktech/gradle-maven-publish-plugin) to publish to Sonatype OSS (and from thence to Maven Central), and the [`com.gradle.plugin-publish](https://plugins.gradle.org/plugin/com.gradle.plugin-publish) plugin to publish to the Gradle Plugin Portal.  Each of these plugins requires a fair bit of configuration.

### Publishing to Sonatype

Pushing artifacts to Sonatype requires membership in the KeepSafe Sonatype org, which is by employment only.  Once
you have a login, put it in your private global Gradle file (e.g. `~/.gradle/gradle.properties`, along with a valid
GPG signing configuration.  Your properties file should look like so:

```properties
SONATYPE_NEXUS_USERNAME=<your username here>
SONATYPE_NEXUS_PASSWORD=<your password here>
signing.secretKeyRingFile=/path/to/your/.gnupg/secring.gpg
signing.keyId=<short hex key ID>
signing.password=<password associated with the key>
```

### Publishing to the Gradle Plugin Portal

Publishing to the Gradle Plugin Portal also requires credentials tied to your employment.  You'll need an API key and secret from `https://plugins.gradle.org`.  Put them in your global Gradle properties file like so:

```properties
gradle.publish.key=<your API key>
gradle.publish.secret=<your secret here>
```

Note that the Plugin Portal is incredibly lame and does not have a notion of more than one person being able to publish artifacts.  Consequently, we need to share credentials; in practice, only @benjamin-bader has the keys.

### Release Script

1. `git checkout master`
1. Edit version number in gradle.properties, remove "-SNAPSHOT" suffix
1. Edit version number in README.md
1. Edit version number in docs/index.html
1. Update CHANGELOG.md
1. `git add . && git commit -S -m "Release version <version number here>`
1. `git tag -s -a <version number> -m "Release version <version number>"`
1. `./gradlew clean check`
1. `./gradlew uploadArchives`
1. `./gradlew publishPlugins`
1. Edit version number in gradle.properties to the next SNAPSHOT version.
1. `git add . && git commit -S -m "Prepare next development version"`
1. `git push --tags`
1. Go to https://oss.sonatype.org, log in
1. Click "Staging Repositories"
1. Select the appropriate repo, then click "Close", then wait
1. Once closed, select the repo and click "Release", then wait

### Working with the website

We use [mkdocs](https://www.mkdocs.org/) with the [Material theme](https://squidfunk.github.io/mkdocs-material/getting-started/).  Follow the instructions at each link to install all the things. 

The site is generated from the Markdown files in `docs/`, according to the template and settings in `mkdocs.yml`.  The general workflow is:

1. `mkdocs serve`
1. Open the browser to `http://localhost:8000`
1. Edit docs and/or mkdocs.yml
1. Look at the changes in the browser
1. Repeat as necessary
1. Open a doc PR
1. Once merged, `mkdocs gh-deploy`
