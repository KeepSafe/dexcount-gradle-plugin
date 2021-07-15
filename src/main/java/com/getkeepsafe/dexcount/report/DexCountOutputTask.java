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

import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.PrintOptions;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

public abstract class DexCountOutputTask extends DefaultTask {
    @Input
    public abstract Property<String> getVariantNameProperty();

    @Nested
    public abstract Property<DexCountExtension> getConfigProperty();

    @InputFile
    public abstract RegularFileProperty getPackageTreeFileProperty();

    @Internal
    public abstract Property<Boolean> getAndroidProject();

    @Classpath
    public abstract ConfigurableFileCollection getWorkerClasspath();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void run() {
        PrintOptions opts = PrintOptions.fromDexCountExtension(getConfigProperty().get())
            .withIsAndroidProject(getAndroidProject().get());

        WorkQueue queue = getWorkerExecutor().classLoaderIsolation(spec -> {
            spec.getClasspath().from(getWorkerClasspath());
        });

        queue.submit(ReportOutputWorker.class, params -> {
            params.getVariantName().set(getVariantNameProperty());
            params.getPackageTreeFile().set(getPackageTreeFileProperty());
            params.getPrintOptions().set(opts);
        });
    }
}
