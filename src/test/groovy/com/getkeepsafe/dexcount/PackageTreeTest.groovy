package com.getkeepsafe.dexcount

import spock.lang.Specification

class PackageTreeTest extends Specification {
    def "adding duplicates increments count"() {
        setup:
        def tree = new PackageTree()
        tree.add("com.foo.Bar")

        when:
        tree.add("com.foo.Bar")

        then:
        tree.getCount() == 2
    }

    def "can print a package list with classes included"() {
        setup:
        def writer = new StringWriter()
        def tree = new PackageTree()

        when:
        tree.add("com.foo.Bar")
        tree.add("com.foo.Bar")
        tree.add("com.foo.Qux")
        tree.add("com.alpha.Beta")

        tree.printPackageListWithClasses(new PrintWriter(writer))

        then:
        writer.toString() == """4        com
1        com.alpha
1        com.alpha.Beta
3        com.foo
2        com.foo.Bar
1        com.foo.Qux
"""
    }

    def "can print a package list without classes"() {
        setup:
        def writer = new StringWriter()
        def tree = new PackageTree()

        when:
        tree.add("com.foo.Bar")
        tree.add("com.foo.Bar")
        tree.add("com.foo.Qux")
        tree.add("com.alpha.Beta")

        tree.printPackageList(new PrintWriter(writer))

        then:
        writer.toString() == """4        com
1        com.alpha
3        com.foo
"""
    }

    def "can print a tree"() {
        setup:
        def writer = new StringWriter()
        def tree = new PackageTree()

        when:
        tree.add("com.foo.Bar")
        tree.add("com.foo.Bar")
        tree.add("com.foo.Qux")
        tree.add("com.alpha.Beta")

        tree.printTree(new PrintWriter(writer))

        then:
        writer.toString() == """com (4)
  alpha (1)
    Beta (1)
  foo (3)
    Bar (2)
    Qux (1)
"""
    }
}
