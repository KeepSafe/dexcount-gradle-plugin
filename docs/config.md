Dexcount is configurable via a Gradle extension (shown with default values):

in `app/build.gradle`:
```groovy
dexcount {
    // When false, no build outputs will be counted.  Defaults to true.
    enabled = true

    // The format of the method count output, either "list", "tree", "json",
    // or "yaml".
    format = OutputFormat.LIST

    // When true, individual classes will be included in the package list -
    // otherwise, only packages are included.
    includeClasses = false

    // When true, the number of classes in a package will be included in the
    // printed output.
    includeClassCount = false

    // When true, the number of fields in a package or class will be included
    // in the printed output.
    includeFieldCount = true

    // When true, the total number of methods in the application will be included
    // in the printed output.
    includeTotalMethodCount = false

    // When true, packages will be sorted in descending order by the number of
    //methods they contain.
    orderByMethodCount = false

    // When true, the output file will also be printed to the build's standard
    // output.
    verbose = false

    // Sets the max number of package segments in the output - i.e. when set to 2,
    // counts stop at `com.google`, when set to 3 you get `com.google.android`,
    // etc.  "Unlimited" by default.
    maxTreeDepth = Integer.MAX_VALUE

    // When true, Team City integration strings will be printed.
    teamCityIntegration = false

    // A string which, if specified, will be added to TeamCity stat names.
    // Null by default.
    teamCitySlug = null

    // When set, the build will fail when the APK/AAR has more methods than the
    // max. 0 by default.
    maxMethodCount = 64000

    // When true, prints the declared method and field count. Only allowed in
    // library modules. False by default.
    printDeclarations = true
}
```
