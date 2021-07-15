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
import com.getkeepsafe.dexcount.source.SourceFile;
import com.getkeepsafe.dexcount.source.SourceFiles;
import org.gradle.api.file.RegularFileProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public abstract class JarWorker extends BaseWorker<JarWorker.Params> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JarWorker.class);

    public interface Params extends BaseWorker.Params {
        RegularFileProperty getJarFile();
    }

    @Override
    protected PackageTree generatePackageTree() throws IOException {
        PackageTree tree = new PackageTree(Deobfuscator.EMPTY);
        File jarFile = getParameters().getJarFile().getAsFile().get();
        try (SourceFile sf = SourceFiles.extractJarFromJar(jarFile)) {
            sf.getMethodRefs().forEach(tree::addDeclaredMethodRef);
            sf.getFieldRefs().forEach(tree::addDeclaredFieldRef);
        }
        return tree;
    }

    @Override
    protected String getInputRepresentation() {
        return getParameters().getJarFile().getAsFile().get().getName();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
