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

import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.PrintOptions;
import com.getkeepsafe.dexcount.treegen.workers.BaseWorker;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public abstract class BaseGeneratePackageTreeTask<P extends BaseWorker.Params, W extends BaseWorker<P>> extends DefaultTask {
    /**
     * The plugin configuration, as provided by the 'dexcount' block.
     */
    @Nested
    public abstract Property<DexCountExtension> getConfigProperty();

    /**
     * The name of the the method-count report file, without a file extension.
     */
    @Input
    public abstract Property<String> getOutputFileNameProperty();

    /**
     * The full path to the serialized [PackageTree] produced by this task.
     *
     * This file is an intermediate representation, not intended for public
     * consumption.  Its format is likely to change without notice.
     */
    @NotNull
    @OutputFile
    public abstract RegularFileProperty getPackageTreeFileProperty();

    /**
     * The directory in which plugin outputs (the report file, summary file,
     * and charts) will be written.
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectoryProperty();

    @Internal
    protected boolean isAndroidProject() {
        return true;
    }

    @Classpath
    public abstract ConfigurableFileCollection getWorkerClasspath();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void execute() {
        WorkQueue workQueue = getWorkerExecutor().classLoaderIsolation(spec -> {
            spec.getClasspath().from(getWorkerClasspath());
        });

        workQueue.submit(getWorkerClass(), this::configureParams);
    }

    @Internal
    protected abstract Class<W> getWorkerClass();

    protected void configureParams(P params) {
        PrintOptions options = PrintOptions.fromDexCountExtension(getConfigProperty().get())
            .withIsAndroidProject(isAndroidProject());

        params.getOutputFileName().set(getOutputFileNameProperty());
        params.getPackageTreeFile().set(getPackageTreeFileProperty());
        params.getOutputDirectory().set(getOutputDirectoryProperty());
        params.getPrintOptions().set(options);
    }
}
