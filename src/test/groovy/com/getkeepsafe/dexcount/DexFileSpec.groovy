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

final class DexFileSpec extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "test AAR dexcount"() {
        given:
        if (new File("local.properties").exists()) {
            Properties properties = new Properties()
            InputStream inputStream = new FileInputStream("local.properties")
            properties.load(inputStream)
            inputStream.close()
            DexMethodCountPlugin.sdkLocation = new File(properties.getProperty("sdk.dir"))
        } else {
            DexMethodCountPlugin.sdkLocation = new File(System.getenv("ANDROID_HOME"))
        }

        def aarFile = temporaryFolder.newFile("test.aar")

        getClass().getResourceAsStream('/android-beacon-library-2.7.aar').withStream { input ->
            aarFile.append(input)
        }

        when:
        def dexFiles = DexFile.extractDexData(aarFile, 60)

        then:
        dexFiles != null
        dexFiles.size() == 1
        dexFiles[0].methodRefs.size() == 982
        dexFiles[0].fieldRefs.size() == 436
    }

    def "test APK built with tools v24"() {
        given:
        def apk = temporaryFolder.newFile("app-debug-tools-v24.apk")

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
