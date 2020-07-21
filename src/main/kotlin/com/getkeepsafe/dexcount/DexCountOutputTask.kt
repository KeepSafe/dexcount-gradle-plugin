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

@file:Suppress("UnstableApiUsage")

package com.getkeepsafe.dexcount

import com.getkeepsafe.dexcount.thrift.TreeGenOutput
import com.microsoft.thrifty.protocol.CompactProtocol
import com.microsoft.thrifty.transport.BufferTransport
import okio.Buffer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class DexCountOutputTask @Inject constructor (
    objects: ObjectFactory
) : DefaultTask() {
    @get:Input
    val variantNameProperty: Property<String> = objects.property()

    @Nested
    val configProperty: Property<DexCountExtension> = objects.property()

    @InputFile
    val packageTreeFileProperty: RegularFileProperty = objects.fileProperty()

    @Internal
    val androidProject: Property<Boolean> = objects.property()

    @TaskAction
    open fun run() {
        lateinit var tree: PackageTree
        lateinit var inputRepresentation: String
        packageTreeFileProperty.asFile.get().inputStream().use {
            val buffer = Buffer()
            buffer.readFrom(it)

            val transport = BufferTransport(buffer)
            val protocol = CompactProtocol(transport)
            val thrift = TreeGenOutput.ADAPTER.read(protocol)

            tree = PackageTree.fromThrift(thrift.tree!!)
            inputRepresentation = thrift.inputRepresentation ?: ""
        }

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
}
