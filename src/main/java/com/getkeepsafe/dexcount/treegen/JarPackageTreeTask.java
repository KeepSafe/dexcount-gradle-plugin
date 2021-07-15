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

import com.getkeepsafe.dexcount.treegen.workers.JarWorker;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTask
public abstract class JarPackageTreeTask extends BaseGeneratePackageTreeTask<JarWorker.Params, JarWorker> {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getJarFile();

    @Override
    protected boolean isAndroidProject() {
        return false;
    }

    @Override
    protected Class<JarWorker> getWorkerClass() {
        return JarWorker.class;
    }

    @Override
    protected void configureParams(JarWorker.Params params) {
        super.configureParams(params);

        params.getJarFile().set(getJarFile());
    }
}
