/*
 * Copyright (C) 2015-2021 KeepSafe Software
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
package com.getkeepsafe.dexcount.report;

import com.getkeepsafe.dexcount.CountReporter;
import com.getkeepsafe.dexcount.PackageTree;
import com.getkeepsafe.dexcount.thrift.TreeGenOutput;
import com.microsoft.thrifty.KtApiKt;
import com.microsoft.thrifty.protocol.Protocol;
import com.microsoft.thrifty.transport.Transport;
import okio.BufferedSource;
import okio.GzipSource;
import okio.Okio;
import okio.Source;
import org.gradle.workers.WorkAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public abstract class ReportOutputWorker implements WorkAction<ReportOutputWorkerParams> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportOutputWorker.class);

    @Override
    public void execute() {
        try {
            actuallyExecute();
        } catch (IOException e) {
            LOGGER.error("Error reporting dexcount output; please clean and rebuild.", e);
        }
    }

    private void actuallyExecute() throws IOException {
        TreeGenOutput treeGen = readTreeGenFile();
        if (treeGen.tree == null) {
            LOGGER.error("Corrupted dexcount data; please clean and rebuild.");
            return;
        }

        String inputRepresentation = treeGen.inputRepresentation;
        if (inputRepresentation == null) {
            LOGGER.error("Corrupted dexcount data; please clean and rebuild.");
            return;
        }

        PackageTree tree = PackageTree.fromThrift(treeGen.tree);
        CountReporter reporter = new CountReporter(
            tree,
            getParameters().getVariantName().get(),
            LOGGER,
            getParameters().getPrintOptions().get(),
            inputRepresentation,
            false);

        reporter.report();
    }

    private TreeGenOutput readTreeGenFile() throws IOException {
        File file = getParameters().getPackageTreeFile().getAsFile().get();

        try (
            Source source = Okio.source(file);
            GzipSource gzip = new GzipSource(source);
            BufferedSource bufferedSource = Okio.buffer(gzip);
            Transport transport = KtApiKt.transport(bufferedSource);
            Protocol protocol = KtApiKt.compactProtocol(transport)
        ) {
            return TreeGenOutput.ADAPTER.read(protocol);
        }
    }
}
