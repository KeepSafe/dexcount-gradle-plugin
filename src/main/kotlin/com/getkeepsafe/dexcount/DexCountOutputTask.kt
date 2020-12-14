/*
 * Copyright (C) 2015-2019 KeepSafe Software
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

import com.getkeepsafe.dexcount.thrift.TreeGenOutput
import okio.buffer
import okio.gzip
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

abstract class DexCountOutputTask : DefaultTask() {
    @get:Input
    abstract val variantNameProperty: Property<String>

    @get:Nested
    abstract val configProperty: Property<DexCountExtension>

    @get:InputFile
    abstract val packageTreeFileProperty: RegularFileProperty

    @get:Internal
    abstract val androidProject: Property<Boolean>

    @TaskAction
    open fun run() {
        val thrift = readTreeGenFile()
        val thriftTree = checkNotNull(thrift.tree) { "Malformed dexcount data; please clean and rebuild." }
        val inputRepresentation = checkNotNull(thrift.inputRepresentation) { "Malformed dexcount data; please clean and rebuild." }
        val tree = PackageTree.fromThrift(thriftTree)

        val reporter = CountReporter(
            packageTree = tree,
            variantName = variantNameProperty.get(),
            styleable = StyleableTaskAdapter(this),
            config = configProperty.get(),
            inputRepresentation = inputRepresentation,
            isAndroidProject = androidProject.get(),
            isInstantRun = false
        )

        reporter.report()
    }

    private fun readTreeGenFile(): TreeGenOutput {
        return packageTreeFileProperty.asFile.get()
            .source()
            .gzip()
            .buffer()
            .transport()
            .compactProtocol()
            .use(TreeGenOutput.ADAPTER::read)
    }
}
