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
package com.getkeepsafe.dexcount.treegen.workers;

import com.getkeepsafe.dexcount.DexCountException;
import com.getkeepsafe.dexcount.DexMethodCountPlugin;
import com.getkeepsafe.dexcount.PackageTree;
import com.getkeepsafe.dexcount.PrintOptions;
import com.getkeepsafe.dexcount.thrift.TreeGenOutput;
import com.microsoft.thrifty.protocol.CompactProtocol;
import com.microsoft.thrifty.protocol.Protocol;
import com.microsoft.thrifty.transport.Transport;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;
import okio.Sink;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public abstract class BaseWorker<P extends BaseWorker.Params> implements WorkAction<P> {
    public interface Params extends WorkParameters {
        Property<String> getOutputFileName();

        RegularFileProperty getPackageTreeFile();

        DirectoryProperty getOutputDirectory();

        Property<PrintOptions> getPrintOptions();
    }

    @Override
    public void execute() {
        try {
            PackageTree packageTree = generatePackageTree();

            File outputDir = getParameters().getOutputDirectory().get().getAsFile();
            FileUtils.deleteDirectory(outputDir);
            FileUtils.forceMkdir(outputDir);

            writeIntermediateThriftFile(packageTree);
            writeSummaryFile(packageTree);
            writeChartFiles(packageTree);
            writeFullTree(packageTree);
        } catch (IOException e) {
            throw new DexCountException("Counting dex method references failed", e);
        }
    }

    private void writeIntermediateThriftFile(PackageTree packageTree) throws IOException {
        TreeGenOutput thrift = new TreeGenOutput.Builder()
            .tree(PackageTree.toThrift(packageTree))
            .inputRepresentation(getInputRepresentation())
            .build();

        File treeFile = getParameters().getPackageTreeFile().getAsFile().get();
        FileUtils.deleteQuietly(treeFile);

        try (Sink fileSink = Okio.sink(treeFile);
             Sink gzipSink = new GzipSink(fileSink);
             BufferedSink sink = Okio.buffer(gzipSink);
             Transport transport = new BufferedSinkTransport(sink);
             Protocol protocol = new CompactProtocol(transport)) {
            TreeGenOutput.ADAPTER.write(protocol, thrift);
            protocol.flush();
        }
    }

    private void writeSummaryFile(PackageTree packageTree) throws IOException {
        File summaryFile = getParameters().getOutputDirectory().file("summary.csv").get().getAsFile();
        FileUtils.forceMkdirParent(summaryFile);

        String headers = "methods,fields,classes";
        String counts = String.format(
            "%d,%d,%d",
            packageTree.getMethodCount(),
            packageTree.getFieldCount(),
            packageTree.getClassCount());

        try (BufferedWriter writer = Files.newBufferedWriter(summaryFile.toPath())) {
            writer.append(headers).append('\n');
            writer.append(counts).append('\n');
        }
    }

    private void writeChartFiles(PackageTree packageTree) throws IOException {
        File chartDirectory = getParameters().getOutputDirectory().dir("chart").get().getAsFile();
        FileUtils.forceMkdir(chartDirectory);

        PrintOptions options = getParameters().getPrintOptions().get()
            .toBuilder()
            .setIncludeClasses(true)
            .build();

        File dataJs = new File(chartDirectory, "data.js");
        try (BufferedWriter out = Files.newBufferedWriter(dataJs.toPath())) {
            out.write("var data = ");
            packageTree.printJson(out, options);
        }

        List<String> resourceNames = Arrays.asList("chart-builder.js", "d3.v3.min.js", "index.html", "styles.css");
        for (String resourceName : resourceNames) {
            String resourcePath = "/com/getkeepsafe/dexcount/" + resourceName;
            try (InputStream is = DexMethodCountPlugin.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    getLogger().error("No such resource: {}", resourcePath);
                    continue;
                }
                File target = new File(chartDirectory, resourceName);
                FileUtils.copyInputStreamToFile(is, target);
            }
        }
    }

    private void writeFullTree(PackageTree packageTree) throws IOException {
        PrintOptions options = getParameters().getPrintOptions().get();
        String fullCountFileName = getParameters().getOutputFileName().get() + options.getOutputFormat().getExtension();
        File fullCountFile = getParameters().getOutputDirectory().file(fullCountFileName).get().getAsFile();

        try (BufferedWriter bw = Files.newBufferedWriter(fullCountFile.toPath())) {
            packageTree.print(bw, options.getOutputFormat(), options);
        }
    }

    protected abstract PackageTree generatePackageTree() throws IOException;

    protected abstract String getInputRepresentation();

    protected abstract Logger getLogger();

    private static class BufferedSinkTransport extends Transport {
        private final BufferedSink sink;

        BufferedSinkTransport(BufferedSink sink) {
            this.sink = sink;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            sink.write(buffer, offset, count);
        }

        @Override
        public void flush() throws IOException {
            sink.flush();
        }

        @Override
        public void close() throws IOException {
            sink.close();
        }
    }
}
