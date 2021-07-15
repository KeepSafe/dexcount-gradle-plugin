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

import com.android.build.api.variant.BuiltArtifactsLoader;
import com.getkeepsafe.dexcount.treegen.workers.ModernWorker;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public abstract class ModernGeneratePackageTreeTask<P extends ModernWorker.Params, W extends ModernWorker<P>> extends BaseGeneratePackageTreeTask<P, W> {
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getMappingFileProperty();

    @Internal
    public abstract Property<BuiltArtifactsLoader> getLoaderProperty();

    @Override
    protected void configureParams(P params) {
        super.configureParams(params);

        params.getMappingFile().set(getMappingFileProperty());
    }
}
