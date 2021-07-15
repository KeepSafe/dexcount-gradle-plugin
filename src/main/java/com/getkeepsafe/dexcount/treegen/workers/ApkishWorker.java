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

import com.getkeepsafe.dexcount.PackageTree;
import com.getkeepsafe.dexcount.source.SourceFile;
import com.getkeepsafe.dexcount.source.SourceFiles;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.RegularFileProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class ApkishWorker extends ModernWorker<ApkishWorker.Params> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApkishWorker.class);

    public interface Params extends ModernWorker.Params {
        RegularFileProperty getApkishFile();
    }

    @Override
    protected PackageTree generatePackageTree() throws IOException {
        PackageTree tree = new PackageTree(getDeobfuscator());

        File inputFile = getInputFile();

        List<SourceFile> sourceFiles = SourceFiles.extractDexData(inputFile);
        try {
            sourceFiles.forEach(sf -> {
                sf.getMethodRefs().forEach(tree::addMethodRef);
                sf.getFieldRefs().forEach(tree::addFieldRef);
            });
        } finally {
            IOUtils.closeQuietly(sourceFiles.toArray(new SourceFile[0]));
        }

        return tree;
    }

    private File getInputFile() {
        return getParameters().getApkishFile().getAsFile().get();
    }

    @Override
    protected String getInputRepresentation() {
        return getInputFile().getName();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
