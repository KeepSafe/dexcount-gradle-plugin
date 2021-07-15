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

import com.getkeepsafe.dexcount.treegen.workers.LegacyWorker;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;

@CacheableTask
public abstract class LegacyGeneratePackageTreeTask extends BaseGeneratePackageTreeTask<LegacyWorker.Params, LegacyWorker> {
    /**
     * The output of the 'package' task; will be either an APK or an AAR.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputFileProperty();

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Property<FileCollection> getMappingFileProvider();

    @Override
    protected Class<LegacyWorker> getWorkerClass() {
        return LegacyWorker.class;
    }

    @Override
    protected void configureParams(LegacyWorker.Params params) {
        super.configureParams(params);

        File mappingFile = null;
        FileCollection maybeMappingFiles = getMappingFileProvider().getOrNull();
        if (maybeMappingFiles != null && !maybeMappingFiles.isEmpty()) {
            mappingFile = maybeMappingFiles.getSingleFile();
        }

        params.getInputFile().set(getInputFileProperty());
        params.getMappingFile().set(mappingFile);
    }
}
