/*
 * Copyright (C) 2019 KeepSafe Software
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

final class GradleVersionSpec extends Specification {
    def "can parse valid version strings"() {
        when:
        def version = GradleVersion.parse(validVersion)

        then:
        version != null

        where:
        validVersion << ["3.0", "4.12", "5.0-alpha1"]
    }

    def "comparisions work"() {
        expect:
        GradleVersion.parse(lhs).compareTo(GradleVersion.parse(rhs)) == cmp

        where:
        lhs   | rhs   || cmp
        "1.0" | "2.0" || -1
        "1.0" | "1.1" || -1
        "1.0" | "1.0" || 0
        "5.6" | "2.7" || 1
    }
}
