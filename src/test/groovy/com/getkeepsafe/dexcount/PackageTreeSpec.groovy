/*
 * Copyright (C) 2015-2016 KeepSafe Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.getkeepsafe.dexcount

import spock.lang.Specification

import static com.getkeepsafe.dexcount.RefHelpers.fieldRef
import static com.getkeepsafe.dexcount.RefHelpers.methodRef

final class PackageTreeSpec extends Specification {
    def "adding different methods increments count"() {
        given:
        def tree = new PackageTree()
        tree.addMethodRef(methodRef("Lcom/foo/Bar;", "foo"))

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;", "bar"))

        then:
        tree.getMethodCount() == 2
    }

    def "adding duplicate methods does not increment count"() {
        given:
        def tree = new PackageTree()
        tree.addMethodRef(methodRef("Lcom/foo/Bar;", "foo"))

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;", "foo"))

        then:
        tree.getMethodCount() == 1
    }

    def "can print a package list with classes included"() {
        given:
        def writer = new StringBuilder()
        def tree = new PackageTree()

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addMethodRef(methodRef("Lcom/alpha/Beta;"))

        tree.printPackageList(writer, new PrintOptions(includeClasses: true))

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
        given:
        def writer = new StringBuilder()
        def tree = new PackageTree()

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addMethodRef(methodRef("Lcom/alpha/Beta;"))

        tree.printPackageList(writer, new PrintOptions(includeClasses: false))

        then:
        writer.toString() == """4        com
1        com.alpha
3        com.foo
"""
    }

    def "can print a tree"() {
        given:
        def sb = new StringBuilder()
        def tree = new PackageTree()

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addMethodRef(methodRef("Lcom/alpha/Beta;"))

        tree.printTree(sb, new PrintOptions(includeClasses: true))

        then:
        sb.toString() == """com (4 methods)
  alpha (1 method)
    Beta (1 method)
  foo (3 methods)
    Bar (2 methods)
    Qux (1 method)
"""
    }

    def "tree can be depth-limited"() {
        given:
        def sb = new StringBuilder()
        def tree = new PackageTree()

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addMethodRef(methodRef("Lcom/alpha/Beta;"))

        tree.printTree(sb, new PrintOptions(
            includeClasses: true,
            maxTreeDepth: 2))

        then:
        def expected = """
com (4 methods)
  alpha (1 method)
  foo (3 methods)
""".trim()

        sb.toString().trim() == expected
    }

    def "accepts autogenerated class names"() {
        given:
        def sb = new StringBuilder()
        def tree = new PackageTree()

        when:
        tree.addMethodRef(methodRef('Lcom/foo/bar/$$Generated$Class$$;'))

        tree.printPackageList(sb, new PrintOptions(includeClasses: true))

        then:
        def trimmed = sb.toString().trim()
        def ix = trimmed.lastIndexOf(' ')
        trimmed.substring(ix + 1) == 'com.foo.bar.$$Generated$Class$$'
    }

    def "prints a header when options say to"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.printHeader = true

        when:
        tree.printPackageList(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        trimmed == "methods  package/class name"
    }

    def "header includes column for fields when field count is specified"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.printHeader = true
        opts.includeFieldCount = true

        when:
        tree.printPackageList(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        trimmed == "methods  fields   package/class name"
    }

    def "package list can include field counts"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.printHeader = true
        opts.includeFieldCount = true
        opts.includeClasses = true

        when:
        tree.addMethodRef(methodRef("Lx/y/Z;"))
        tree.addMethodRef(methodRef("Lx/y/Z;"))
        tree.addMethodRef(methodRef("Lx/y/Z;"))
        tree.addFieldRef(fieldRef("Lx/y/Z;"))
        tree.addFieldRef(fieldRef("Lx/y/Z;"))
        tree.addFieldRef(fieldRef("Lx/y/W;"))
        tree.printPackageList(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
methods  fields   package/class name
3        3        x
3        3        x.y
0        1        x.y.W
3        2        x.y.Z""".trim()

        trimmed == expected
    }

    def "package list can be sorted by method count"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.printHeader = true
        opts.includeFieldCount = true
        opts.includeClasses = true
        opts.orderByMethodCount = true

        when:
        tree.addMethodRef(methodRef("Lx/y/Z;"))
        tree.addMethodRef(methodRef("Lx/y/Z;"))
        tree.addMethodRef(methodRef("Lx/y/Z;"))
        tree.addFieldRef(fieldRef("Lx/y/Z;"))
        tree.addFieldRef(fieldRef("Lx/y/Z;"))
        tree.addFieldRef(fieldRef("Lx/y/W;"))
        tree.printPackageList(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
methods  fields   package/class name
3        3        x
3        3        x.y
3        2        x.y.Z
0        1        x.y.W
""".trim()

        trimmed == expected
    }

    def "package list can include total method count"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.includeTotalMethodCount = true
        opts.includeClasses = false
        opts.printHeader = false

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addMethodRef(methodRef("Lcom/alpha/Beta;"))
        tree.addMethodRef(methodRef("Lorg/whatever/Foo;"))
        tree.addMethodRef(methodRef("Lorg/foo/Whatever;"))

        tree.printPackageList(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
Total methods: 5
3        com
1        com.alpha
2        com.foo
2        org
1        org.foo
1        org.whatever
""".trim()

        trimmed == expected
    }

    def "package list can include class count"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.printHeader = true
        opts.includeClassCount = true
        opts.includeMethodCount = true
        opts.includeFieldCount = true

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Class1;"))
        tree.addMethodRef(methodRef("Lcom/foo/Class1;"))
        tree.addMethodRef(methodRef("Lcom/foo/Class2;"))
        tree.addMethodRef(methodRef("Lorg/whatever/Foo;"))
        tree.addMethodRef(methodRef("Lorg/foo/Whatever;"))
        tree.addFieldRef(fieldRef("Lcom/foo/Class1;"))
        tree.addFieldRef(fieldRef("Lcom/foo/Class2;"))
        tree.addFieldRef(fieldRef("Lcom/foo/Class2;"))
        tree.addFieldRef(fieldRef("Lx/y/Z;"))
        tree.addFieldRef(fieldRef("Lx/y/W;"))

        tree.printPackageList(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
classes  methods  fields   package/class name
2        3        3        com
2        3        3        com.foo
2        2        0        org
1        1        0        org.foo
1        1        0        org.whatever
2        0        2        x
2        0        2        x.y
""".trim()

        trimmed == expected
    }

    def "package list can be depth-limited"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.includeTotalMethodCount = true
        opts.includeClasses = false
        opts.printHeader = false
        opts.maxTreeDepth = 1

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addMethodRef(methodRef("Lcom/alpha/Beta;"))
        tree.addMethodRef(methodRef("Lorg/whatever/Foo;"))
        tree.addMethodRef(methodRef("Lorg/foo/Whatever;"))

        tree.printPackageList(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
Total methods: 5
3        com
2        org
""".trim()

        trimmed == expected
    }

    def "packages can be YAML-formatted"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.includeTotalMethodCount = true

        when:
        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addMethodRef(methodRef("Lcom/alpha/Beta;"))
        tree.addMethodRef(methodRef("Lorg/whatever/Foo;"))
        tree.addMethodRef(methodRef("Lorg/foo/Whatever;"))

        tree.printYaml(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
---
methods: 5
counts:
  - name: com
    methods: 3
    children:
      - name: alpha
        methods: 1
        children: []
      - name: foo
        methods: 2
        children: []
  - name: org
    methods: 2
    children:
      - name: foo
        methods: 1
        children: []
      - name: whatever
        methods: 1
        children: []""".trim()

        trimmed == expected
    }

    def "can format YAML with only class counts"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.includeTotalMethodCount = true
        opts.includeClassCount = true
        opts.includeMethodCount = false

        tree.addFieldRef(fieldRef("Lorg/whatever/Foo;"))
        tree.addMethodRef(methodRef("Lorg/whatever/Foo;"))
        tree.addMethodRef(methodRef("Lorg/foo/Whatever;"))
        tree.addFieldRef(fieldRef("Lx/y/z/XYZ;"))
        tree.addFieldRef(fieldRef("Lx/y/z/XYZ;"))
        tree.addMethodRef(methodRef("Lx/y/z/XYZ;"))

        when:
        tree.printYaml(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
---
classes: 3
counts:
  - name: org
    classes: 2
    children:
      - name: foo
        classes: 1
        children: []
      - name: whatever
        classes: 1
        children: []
  - name: x
    classes: 1
    children:
      - name: y
        classes: 1
        children:
          - name: z
            classes: 1
            children: []""".trim()

        trimmed == expected
    }

    def "can format YAML with only field counts"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.includeTotalMethodCount = true
        opts.includeFieldCount = true
        opts.includeMethodCount = false

        tree.addFieldRef(fieldRef("Lcom/foo/Bar;"))
        tree.addFieldRef(fieldRef("Lcom/foo/Qux;"))
        tree.addFieldRef(fieldRef("Lcom/alpha/Beta;"))
        tree.addFieldRef(fieldRef("Lorg/whatever/Foo;"))
        tree.addFieldRef(fieldRef("Lorg/foo/Whatever;"))

        when:
        tree.printYaml(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
---
fields: 5
counts:
  - name: com
    fields: 3
    children:
      - name: alpha
        fields: 1
        children: []
      - name: foo
        fields: 2
        children: []
  - name: org
    fields: 2
    children:
      - name: foo
        fields: 1
        children: []
      - name: whatever
        fields: 1
        children: []""".trim()

        trimmed == expected
    }

    def "can format depth-limited YAML"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.includeTotalMethodCount = true
        opts.includeFieldCount = true
        opts.includeMethodCount = false
        opts.maxTreeDepth = 1

        tree.addFieldRef(fieldRef("Lcom/foo/Bar;"))
        tree.addFieldRef(fieldRef("Lcom/foo/Qux;"))
        tree.addFieldRef(fieldRef("Lcom/alpha/Beta;"))
        tree.addFieldRef(fieldRef("Lorg/whatever/Foo;"))
        tree.addFieldRef(fieldRef("Lorg/foo/Whatever;"))

        when:
        tree.printYaml(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
---
fields: 5
counts:
  - name: com
    fields: 3
    children: []
  - name: org
    fields: 2
    children: []""".trim()

        trimmed == expected
    }

    def "can format JSON with only class counts"() {
        given:
        def tree = new PackageTree()
        def sb = new StringBuilder()
        def opts = new PrintOptions()
        opts.includeTotalMethodCount = true
        opts.includeClassCount = true
        opts.includeMethodCount = false

        tree.addMethodRef(methodRef("Lcom/foo/Bar;"))
        tree.addMethodRef(methodRef("Lcom/foo/Qux;"))
        tree.addFieldRef(fieldRef("Lx/y/z/XYZ;"))

        when:
        tree.printJson(sb, opts)

        then:
        def trimmed = sb.toString().trim()
        def expected = """
{
  "name": "",
  "classes": 3,
  "children": [
    {
      "name": "com",
      "classes": 2,
      "children": [
        {
          "name": "foo",
          "classes": 2,
          "children": []
        }
      ]
    },
    {
      "name": "x",
      "classes": 1,
      "children": [
        {
          "name": "y",
          "classes": 1,
          "children": [
            {
              "name": "z",
              "classes": 1,
              "children": []
            }
          ]
        }
      ]
    }
  ]
}""".trim()

        trimmed == expected
    }
}
