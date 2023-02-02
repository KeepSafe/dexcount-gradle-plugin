package com.getkeepsafe.dexcount.plugin

import spock.lang.Specification

class AbstractTaskApplicatorTest extends Specification {

    def "removeFileExtension trims file extensions"() {
        given:
        def path = "/foo/bar/baz.quux"

        when:
        def result = AbstractTaskApplicator.removeFileExtension(path)

        then:
        result == "/foo/bar/baz"
    }

    def "removeFileExtension leaves files without extensions as-is"() {
        given:
        def path = "/foo/bar/baz"

        when:
        def result = AbstractTaskApplicator.removeFileExtension(path)

        then:
        result == "/foo/bar/baz"
    }

    def "removeFileExtension ignores dots in directory names"() {
        given:
        def path = "/foo/bar.baz/quux"

        when:
        def result = AbstractTaskApplicator.removeFileExtension(path)

        then:
        result == path
    }

    def "removeFileExtension removes trailing dots"() {
        given:
        def path = "/foo/bar/baz."

        when:
        def result = AbstractTaskApplicator.removeFileExtension(path)

        then:
        result == "/foo/bar/baz"
    }
}
