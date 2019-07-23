/*
 * Copyright (C) 2016 KeepSafe Software
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

final class JarFileSpec extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "test AAR method count"() {
        given:
        def aarFile = temporaryFolder.newFile("test.aar")

        getClass().getResourceAsStream('/android-beacon-library-2.7.aar').withStream { input ->
            aarFile.append(input)
        }

        when:
        def jarFile = JarFile.extractJarFromAar(aarFile)

        then:
        jarFile != null
        jarFile.methodRefs.size() == 659
        jarFile.fieldRefs.size() == 405
    }
}
