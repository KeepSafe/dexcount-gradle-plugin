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

import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

final class InstantRunSpec extends Specification {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    def "APKs with instant-run.zip are counted correctly"() {
        given:
        def apkFile = tempFolder.newFile("tiles.apk")
        def apkResource = getClass().getResourceAsStream("/tiles.apk")
        apkResource.withStream { input ->
            IOUtil.drainToFile(input, apkFile)
        }

        // Ugh why is Gradle so hard to test
        def project = ProjectBuilder.builder().build()
        def task = project.tasks.create('countDexMethods', DexMethodCountTask)
        task.config = new DexMethodCountExtension()
        task.apkOrDex = Mock(BaseVariantOutput)
        task.apkOrDex.outputFile >> apkFile

        when:
        task.generatePackageTree()

        then:
        task.tree.methodCount != 32446 // If this fails, then method deduplication has failed.
        task.tree.methodCount == 29363 // determined ahead-of-time; if this fails, something else is broken.
    }
}
