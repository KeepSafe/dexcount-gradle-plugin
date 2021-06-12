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

import spock.lang.Specification
import spock.lang.TempDir

final class DexFileSpec extends Specification {
    @TempDir
    File tempDir

    def "test AAR dexcount"() {
        given:
        def aarFile = new File(tempDir, "test.aar")

        getClass().getResourceAsStream('/android-beacon-library-2.7.aar').withStream { input ->
            aarFile.append(input)
        }

        when:
        def dexFiles = DexFile.extractDexData(aarFile)

        then:
        dexFiles != null
        dexFiles.size() == 1
        dexFiles[0].methodRefs.size() == 983
        dexFiles[0].fieldRefs.size() == 436
    }

    def "test APK built with tools v24"() {
        given:
        def apk = new File(tempDir, "app-debug-tools-v24.apk")

        getClass().getResourceAsStream("/app-debug-tools-v24.apk").withStream { input ->
            apk.append(input)
        }

        when:
        def dexFiles = DexFile.extractDexFromZip(apk)

        then:
        dexFiles != null
        dexFiles.size() == 2
        dexFiles[0].methodRefs.size() == 3
        dexFiles[1].methodRefs.size() == 297
    }
}
