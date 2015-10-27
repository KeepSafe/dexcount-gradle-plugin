0.3.0 (Unreleased)
----------

* Remove `exportAsCSV` and just print the file unconditionally
* CHANGED: field counts are enabled by default
* Use Proguard mappings to de-obfuscate class and package names
* Add `exportAsCSV` option to support Jenkins Plot Plugin

0.2.1 (release 11 September 2015)
----------

* BUG: Fix invalid bytecode (issue #11)
* BUG: Work around Groovy-Java interop bug (issue #12)

0.2.0 (released 2 September 2015)
------------------

* Add field-reference count to DexMethodCountTask stdout
* Add configurability via a `dexcount` Gradle extension
* Add verbose output option
* Add option to include field counts in printed package lists
* Add option to sort printed package list by method count (issue #7)
* Add header to list-formatted output
* Include methods in the unnamed package (e.g. primitive arrays) in task output


0.1.1
-----

Initial open-source release
