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
package com.getkeepsafe.dexcount.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.TestExtension;
import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.report.DexCountOutputTask;
import com.getkeepsafe.dexcount.treegen.BaseGeneratePackageTreeTask;
import com.getkeepsafe.dexcount.treegen.JarPackageTreeTask;
import com.getkeepsafe.dexcount.treegen.LegacyGeneratePackageTreeTask;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
abstract class AbstractLegacyTaskApplicator extends AbstractTaskApplicator {
    protected AbstractLegacyTaskApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
    }

    @Override
    public void apply() {
        DomainObjectCollection<? extends com.android.build.gradle.api.BaseVariant> variants;
        if (getProject().getPlugins().hasPlugin("com.android.application")) {
            AppExtension ext = getProject().getExtensions().getByType(AppExtension.class);
            variants = ext.getApplicationVariants();
        } else if (getProject().getPlugins().hasPlugin("com.android.test")) {
            TestExtension ext = getProject().getExtensions().getByType(TestExtension.class);
            variants = ext.getApplicationVariants();
        } else if (getProject().getPlugins().hasPlugin("com.android.library")) {
            LibraryExtension ext = getProject().getExtensions().getByType(LibraryExtension.class);
            variants = ext.getLibraryVariants();
        } else if (getProject().getPlugins().hasPlugin(JavaPlugin.class) || getProject().getPlugins().hasPlugin(JavaBasePlugin.class)) {
            Task maybeJar = getProject().getTasks().findByName("jar");
            if (!(maybeJar instanceof Jar)) {
                throw new IllegalArgumentException("Jar task is null for " + getProject());
            }
            Jar jar = (Jar) maybeJar;
            applyToJavaProject(jar);
            return;
        } else {
            throw new IllegalArgumentException("Dexcount plugin requires the Android plugin to be configured");
        }

        variants.configureEach(variant -> {
            if (!getExt().getEnabled().get()) {
                return;
            }

            if (variant instanceof com.android.build.gradle.api.ApplicationVariant) {
                applyToApplicationVariant((com.android.build.gradle.api.ApplicationVariant) variant);
            } else if (variant instanceof com.android.build.gradle.api.TestVariant) {
                applyToTestVariant((com.android.build.gradle.api.TestVariant) variant);
            } else if (variant instanceof com.android.build.gradle.api.LibraryVariant) {
                applyToLibraryVariant((com.android.build.gradle.api.LibraryVariant) variant);
            } else {
                getProject().getLogger().error(
                    "dexcount: Don't know how to handle variant {} of type {}, skipping",
                    variant.getName(),
                    variant.getClass());
            }
        });
    }

    protected abstract void applyToApplicationVariant(com.android.build.gradle.api.ApplicationVariant variant);
    protected abstract void applyToLibraryVariant(com.android.build.gradle.api.LibraryVariant variant);
    protected abstract void applyToTestVariant(com.android.build.gradle.api.TestVariant variant);

    private void applyToJavaProject(Jar jarTask) {
        TaskProvider<JarPackageTreeTask> gen = getProject().getTasks().register("generatePackageTree", JarPackageTreeTask.class, t -> {
            t.setDescription("Generate dex method counts");
            t.setGroup("Reporting");

            t.getConfigProperty().set(getExt());
            t.getOutputFileNameProperty().set(jarTask.getArchiveFile().map(it -> FilenameUtils.removeExtension(it.getAsFile().getName())));
            t.getJarFile().set(jarTask.getArchiveFile());
            t.getPackageTreeFileProperty().set(getProject().getLayout().getBuildDirectory().file("intermediates/dexcount/tree.compact.gz"));
            t.getOutputDirectoryProperty().set(getProject().getLayout().getBuildDirectory().dir("outputs/dexcount"));
            t.getWorkerClasspath().from(getWorkerConfiguration());
        });

        getProject().getTasks().register("countDeclaredMethods", DexCountOutputTask.class, t -> {
            t.setDescription("Output dex method counts");
            t.setGroup("Reporting");

            t.getConfigProperty().set(getExt());
            t.getVariantNameProperty().set("");
            t.getAndroidProject().set(false);
            t.getPackageTreeFileProperty().set(gen.flatMap(BaseGeneratePackageTreeTask::getPackageTreeFileProperty));
            t.getWorkerClasspath().from(getWorkerConfiguration());

            if (getExt().getRunOnEachPackage().get()) {
                jarTask.finalizedBy(t);
            }
        });
    }

    protected void createTask(
            com.android.build.gradle.api.BaseVariant variant,
            TaskProvider<?> parentTask,
            com.android.build.gradle.api.BaseVariantOutput output,
            Consumer<LegacyGeneratePackageTreeTask> applyInputConfiguration) {
        String slug = StringUtils.capitalize(variant.getName());
        String path = String.format("%s/outputs/dexcount/%s", getProject().getBuildDir(), variant.getName());
        String outputName;
        if (variant.getOutputs().size() > 1) {
            slug += StringUtils.capitalize(output.getName());
            path += "/" + output.getName();
            outputName = output.getName();
        } else {
            outputName = variant.getName();
        }

        final String finalPath = path;

        String treeTaskName = String.format("generate%sPackageTree", slug);
        String treePath = path.replace("outputs", "intermediates") + "/tree.compact.gz";

        TaskProvider<LegacyGeneratePackageTreeTask> gen = getProject().getTasks().register(treeTaskName, LegacyGeneratePackageTreeTask.class, t -> {
            t.setDescription("Generates dex method count for " + variant.getName() + ".");
            t.setGroup("Reporting");

            t.getConfigProperty().set(getExt());
            t.getOutputFileNameProperty().set(outputName);
            t.getMappingFileProvider().set(getMappingFile(variant));
            t.getOutputDirectoryProperty().set(getProject().file(finalPath));
            t.getPackageTreeFileProperty().set(getProject().getLayout().getBuildDirectory().file(treePath));
            t.getWorkerClasspath().from(getWorkerConfiguration());

            applyInputConfiguration.accept(t);

            // Depending on the runtime AGP version, inputFileProperty (as provided in applyInputConfiguration)
            // may or may not carry task-dependency information with it.  We need to set that up manually here.
            t.dependsOn(parentTask);
        });

        registerOutputTask(gen, slug, true);
    }

    protected void checkPrintDeclarationsIsFalse() {
        if (getExt().getPrintDeclarations().get()) {
            throw new IllegalStateException("Cannot compute declarations for project " + getProject());
        }
    }

    protected void checkPrintDeclarationsIsTrue() {
        if (!getExt().getPrintDeclarations().get()) {
            throw new IllegalStateException("printDeclarations must be true for Java projects: " + getProject());
        }
    }

    protected Provider<FileCollection> getMappingFile(com.android.build.gradle.api.BaseVariant variant) {
        return getProject().provider(() -> {
            File mappingFile = variant.getMappingFile();
            if (mappingFile == null) {
                return getProject().files();
            } else {
                return getProject().files(mappingFile);
            }
        });
    }
}
