package com.getkeepsafe.dexcount

import spock.lang.Specification

class DexMethodCountExtensionTest extends Specification {
    def "format can be a String"() {
        setup:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = "tree"

        then:
        ext.format == OutputFormat.TREE
    }

    def "format can be an OutputFormat enum"() {
        setup:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = OutputFormat.TREE

        then:
        ext.format == OutputFormat.TREE
    }

    def "setFormat throws on invalid format class"() {
        setup:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = 12345

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unrecognized output format '12345'"
    }

    def "setFormat throws on invalid format name"() {
        setup:
        def ext = new DexMethodCountExtension()

        when:
        ext.format = "splay-tree"

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unrecognized output format 'splay-tree'"
    }

    def "format defaults to LIST"() {
        expect:
        new DexMethodCountExtension().format == OutputFormat.LIST
    }
}
