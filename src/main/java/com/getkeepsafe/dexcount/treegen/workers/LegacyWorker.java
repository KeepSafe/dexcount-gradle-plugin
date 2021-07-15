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

import com.getkeepsafe.dexcount.Deobfuscator;
import com.getkeepsafe.dexcount.PackageTree;
import com.getkeepsafe.dexcount.PrintOptions;
import com.getkeepsafe.dexcount.source.SourceFile;
import com.getkeepsafe.dexcount.source.SourceFiles;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.RegularFileProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class LegacyWorker extends BaseWorker<LegacyWorker.Params> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyWorker.class);

    public interface Params extends BaseWorker.Params {
        RegularFileProperty getInputFile();
        RegularFileProperty getMappingFile();
    }

    @Override
    protected PackageTree generatePackageTree() throws IOException {
        PrintOptions options = getParameters().getPrintOptions().get();
        Deobfuscator deobfuscator = Deobfuscator.create(getParameters().getMappingFile().getAsFile().getOrNull());
        File inputFile = getParameters().getInputFile().getAsFile().get();
        String fileName = inputFile.getName();

        boolean isApk = fileName.endsWith(".apk");
        boolean isAar = fileName.endsWith(".aar");
        boolean isJar = fileName.endsWith(".jar");
        boolean isAndroid = isApk || isAar;

        if (!isApk && !isAar && !isJar) {
            throw new IllegalStateException("File type is unclear: " + fileName);
        }

        PackageTree tree = new PackageTree(deobfuscator);

        if (isAndroid) {
            List<SourceFile> sourceFiles = SourceFiles.extractDexData(inputFile);
            try {
                sourceFiles.stream().flatMap(f -> f.getMethodRefs().stream()).forEach(tree::addMethodRef);
                sourceFiles.stream().flatMap(f -> f.getFieldRefs().stream()).forEach(tree::addFieldRef);
            } finally {
                IOUtils.closeQuietly(sourceFiles.toArray(new SourceFile[0]));
            }
        }

        SourceFile jar = null;
        if (isAar && options.getPrintDeclarations()) {
            jar = SourceFiles.extractJarFromAar(inputFile);
        } else if (isJar && options.getPrintDeclarations()) {
            jar = SourceFiles.extractJarFromJar(inputFile);
        }

        if (jar != null) {
            try {
                jar.getMethodRefs().forEach(tree::addDeclaredMethodRef);
                jar.getFieldRefs().forEach(tree::addDeclaredFieldRef);
            } finally {
                IOUtils.closeQuietly(jar);
            }
        }

        return tree;
    }

    @Override
    protected String getInputRepresentation() {
        return getParameters().getInputFile().getAsFile().get().getName();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
