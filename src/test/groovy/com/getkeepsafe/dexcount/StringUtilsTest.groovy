package com.getkeepsafe.dexcount

import spock.lang.Specification

class StringUtilsTest extends Specification {
    def "capitalize capitalizes strings"() {
        given:
        def text = "foo"

        when:
        def result = StringUtils.capitalize(text)

        then:

        result == "Foo"
    }

    def "capitalize does nothing to an empty string"() {
        when:
        def result = StringUtils.capitalize("")

        then:
        result == ""
    }

    def "capitalize returns an empty string given null"() {
        when:
        def result = StringUtils.capitalize(null)

        then:
        result == ""
    }

    def "capitalize works for non-ASCII strings"() {
        when:
        def result = StringUtils.capitalize("øøøø")

        then:
        result == "Øøøø"
    }

    def "removeExtension trims file extensions"() {
        given:
        def path = "/foo/bar/baz.quux"

        when:
        def result = StringUtils.removeExtension(path)

        then:
        result == "/foo/bar/baz"
    }

    def "removeExtension leaves files without extensions as-is"() {
        given:
        def path = "/foo/bar/baz"

        when:
        def result = StringUtils.removeExtension(path)

        then:
        result == "/foo/bar/baz"
    }

    def "removeExtension ignores dots in directory names"() {
        given:
        def path = "/foo/bar.baz/quux"

        when:
        def result = StringUtils.removeExtension(path)

        then:
        result == path
    }

    def "removeExtension removes trailing dots"() {
        given:
        def path = "/foo/bar/baz."

        when:
        def result = StringUtils.removeExtension(path)

        then:
        result == "/foo/bar/baz"
    }
}
