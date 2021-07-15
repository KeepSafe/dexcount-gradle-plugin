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

import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.BuiltArtifactsLoader;
import com.getkeepsafe.dexcount.treegen.workers.ApkishWorker;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@CacheableTask
public abstract class ApkPackageTreeTask extends ModernGeneratePackageTreeTask<ApkishWorker.Params, ApkishWorker> {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getApkDirectory();

    @Override
    protected Class<ApkishWorker> getWorkerClass() {
        return ApkishWorker.class;
    }

    @Override
    protected void configureParams(ApkishWorker.Params params) {
        super.configureParams(params);

        Directory directory = getApkDirectory().get();
        BuiltArtifactsLoader loader = getLoaderProperty().get();
        BuiltArtifacts artifacts = loader.load(directory);
        if (artifacts == null) {
            throw new IllegalStateException("No output file found in " + directory.getAsFile().getAbsolutePath());
        }
        List<BuiltArtifact> elements = new ArrayList<>(artifacts.getElements());
        BuiltArtifact artifact = elements.get(0);

        params.getApkishFile().set(new File(artifact.getOutputFile()));
    }
}
