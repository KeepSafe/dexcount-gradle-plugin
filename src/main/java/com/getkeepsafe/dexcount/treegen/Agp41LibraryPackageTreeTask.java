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
package com.getkeepsafe.dexcount.treegen;

import com.getkeepsafe.dexcount.DexCountException;
import com.getkeepsafe.dexcount.treegen.workers.ApkishWorker;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;

@CacheableTask
public abstract class Agp41LibraryPackageTreeTask extends ModernGeneratePackageTreeTask<ApkishWorker.Params, ApkishWorker> {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAarBundleFileCollection();

    @Override
    protected Class<ApkishWorker> getWorkerClass() {
        return ApkishWorker.class;
    }

    @Override
    protected void configureParams(ApkishWorker.Params params) {
        super.configureParams(params);

        File aarFile = null;
        for (File file : getAarBundleFileCollection()) {
            if (file.getName().endsWith(".aar")) {
                aarFile = file;
                break;
            }
        }

        if (aarFile == null) {
            throw new DexCountException("No AAR file found");
        }

        params.getApkishFile().set(aarFile);
    }
}
