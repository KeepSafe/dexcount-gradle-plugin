package com.getkeepsafe.dexcount

import org.gradle.logging.StyledTextOutput
import spock.lang.Specification


class DexCountExceptionTest extends Specification {
    def ex = new DexCountException()
    def out = Mock(StyledTextOutput)

    def "printStackTrace works with StyledTextOutputs"() {
        given:
        def expected = new StringWriter().withCloseable { sw ->
            new PrintWriter(sw, false).withCloseable { pw ->
                ex.printStackTrace(pw)
                pw.flush()
            }
            sw.toString()
        }

        when:
        ex.printStackTrace(out)

        then:
        1 * out.println(expected)
    }
}
