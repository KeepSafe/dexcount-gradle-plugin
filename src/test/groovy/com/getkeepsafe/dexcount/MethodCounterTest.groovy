package com.getkeepsafe.dexcount

import spock.lang.Specification

class MethodCounterTest extends Specification {
    def "prints sorted packages"() {
        setup:
        def map = new TreeMap<String, Integer>()
        map["a"] = 1
        map["c"] = 100
        map["b"] = 3
        map["b.a"] = 2

        def writer = new StringWriter()
        def counter = new MethodCounter(methodCountsByPackage: map, totalCount: 106)

        when:
        counter.printTree(new PrintWriter(writer))

        then:
        writer.toString() == """
1        a
3        b
2        b.a
100      c""".trim() + '\n'
    }
}
