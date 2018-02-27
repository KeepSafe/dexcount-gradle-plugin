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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

final class DeobfuscatorSpec extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "when no mapping file exists, returns given classnames unaltered"() {
        given:
        def deobs = Deobfuscator.create(null)

        when:
        def classOne = deobs.deobfuscate("a")
        def classTwo = deobs.deobfuscate("b")

        then:
        classOne == "a"
        classTwo == "b"
    }

    def "maps proguarded names to original names"() {
        given:
        File file = temporaryFolder.newFile()
        file.withPrintWriter {
            // Proguard mapping for classnames is "old -> new:"
            it.println("com.foo.Bar -> a:")
            it.println("com.baz.Quux -> b:")
        }

        def deobs = Deobfuscator.create(file)

        when:
        def classOne = deobs.deobfuscate("a")
        def classTwo = deobs.deobfuscate("b")

        then:
        classOne == "com.foo.Bar"
        classTwo == "com.baz.Quux"
    }
}

