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

import com.android.build.api.artifact.Artifacts;
import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.LibraryAndroidComponentsExtension;
import com.android.build.api.variant.TestAndroidComponentsExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestPlugin;
import com.android.repository.Revision;
import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.treegen.ApkPackageTreeTask;
import com.getkeepsafe.dexcount.treegen.BundlePackageTreeTask;
import com.getkeepsafe.dexcount.treegen.JarPackageTreeTask;
import com.getkeepsafe.dexcount.treegen.LibraryPackageTreeTask;
import com.getkeepsafe.dexcount.treegen.ModernGeneratePackageTreeTask;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.impldep.org.codehaus.plexus.util.FileUtils;
import org.gradle.jvm.tasks.Jar;

class SevenOhApplicator extends AbstractTaskApplicator {
    static class Factory implements TaskApplicator.Factory {
        @Override
        public Revision getMinimumRevision() {
            return new Revision(7, 0);
        }

        @Override
        public TaskApplicator create(Project project, DexCountExtension ext) {
            return new SevenOhApplicator(project, ext);
        }
    }

    SevenOhApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
    }

    @Override
    public void apply() {
        if (!getExt().getEnabled().get()) {
            return;
        }

        getProject().getPlugins().withType(AppPlugin.class).configureEach(plugin -> {
            ApplicationAndroidComponentsExtension components = getProject().getExtensions().getByType(ApplicationAndroidComponentsExtension.class);
            components.onVariants(components.selector(), variant -> {
                registerApkTask(variant.getName(), variant.getArtifacts());
                registerAabTask(variant.getName(), variant.getArtifacts());
            });
        });

        getProject().getPlugins().withType(LibraryPlugin.class).configureEach(plugin -> {
            LibraryAndroidComponentsExtension components = getProject().getExtensions().getByType(LibraryAndroidComponentsExtension.class);
            components.onVariants(components.selector(), variant -> {
                registerAarTask(variant.getName(), variant.getArtifacts());
            });
        });

        getProject().getPlugins().withType(TestPlugin.class).configureEach(plugin -> {
            TestAndroidComponentsExtension components = getProject().getExtensions().getByType(TestAndroidComponentsExtension.class);
            components.onVariants(components.selector(), variant -> {
                registerApkTask(variant.getName(), variant.getArtifacts());
            });
        });

        getProject().afterEvaluate(project -> {
            if (project.getExtensions().findByType(CommonExtension.class) == null) {
                // No Android plugins were registered; this may be a jar-count usage.
                registerJarTask();
            }
        });
    }

    private void registerApkTask(String variantName, Artifacts artifacts) {
        if (getExt().getPrintDeclarations().get()) {
            throw new IllegalStateException("Cannot compute declarations for project " + getProject());
        }

        String genTaskName = String.format("generate%sPackageTree", StringUtils.capitalize(variantName));

        TaskProvider<ApkPackageTreeTask> gen = getProject().getTasks().register(genTaskName, ApkPackageTreeTask.class, t -> {
            setCommonProperties(t, artifacts, variantName);

            t.getApkDirectory().set(artifacts.get(SingleArtifact.APK.INSTANCE));

        });

        registerOutputTask(gen, variantName, true);
    }

    private void registerAabTask(String variantName, Artifacts artifacts) {
        if (getExt().getPrintDeclarations().get()) {
            throw new IllegalStateException("Cannot compute declarations for project " + getProject());
        }

        String genTaskName = String.format("generate%sBundlePackageTree", StringUtils.capitalize(variantName));

        TaskProvider<BundlePackageTreeTask> gen = getProject().getTasks().register(genTaskName, BundlePackageTreeTask.class, t -> {
            setCommonProperties(t, artifacts, variantName);

            t.getBundleFile().set(artifacts.get(SingleArtifact.BUNDLE.INSTANCE));
        });

        registerOutputTask(gen, variantName + "Bundle", true);
    }

    private void registerAarTask(String variantName, Artifacts artifacts) {
        String genTaskName = String.format("generate%sPackageTree", StringUtils.capitalize(variantName));

        TaskProvider<LibraryPackageTreeTask> gen = getProject().getTasks().register(genTaskName, LibraryPackageTreeTask.class, t -> {
            setCommonProperties(t, artifacts, variantName);

            t.getAarFile().set(artifacts.get(SingleArtifact.AAR.INSTANCE));
        });

        registerOutputTask(gen, variantName, true);
    }

    private void registerJarTask() {
        if (!getProject().getPlugins().hasPlugin(JavaLibraryPlugin.class) && !getProject().getPlugins().hasPlugin(JavaPlugin.class)) {
            return;
        }

        if (!getExt().getPrintDeclarations().get()) {
            throw new IllegalStateException("printDeclarations must be true for Java projects");
        }

        TaskProvider<Jar> jarTaskProvider = getProject().getTasks().named("jar", Jar.class);
        TaskProvider<JarPackageTreeTask> treegen = getProject().getTasks().register("generatePackageTree", JarPackageTreeTask.class, t -> {
            t.setDescription("Generate dex method counts");
            t.setGroup("Reporting");

            //noinspection NullableProblems
            Provider<String> jarFileName = jarTaskProvider.flatMap(jarTask -> jarTask.getArchiveFileName().map(FileUtils::removeExtension));
            DirectoryProperty buildDirectory = getProject().getLayout().getBuildDirectory();

            t.getConfigProperty().set(getExt());
            t.getOutputFileNameProperty().set(jarFileName);
            t.getJarFile().set(jarTaskProvider.flatMap(Jar::getArchiveFile));
            t.getPackageTreeFileProperty().set(buildDirectory.file("intermediates/dexcount/tree.compact.gz"));
            t.getOutputDirectoryProperty().set(buildDirectory.dir("outputs/dexcount"));
            t.getWorkerClasspath().from(getWorkerConfiguration());
        });

        registerOutputTask(treegen, "", false);
    }

    private <T extends ModernGeneratePackageTreeTask<?, ?>> void setCommonProperties(T task, Artifacts artifacts, String variantName) {
        DirectoryProperty buildDirectory = getProject().getLayout().getBuildDirectory();
        Provider<RegularFile> packageTreeFile = buildDirectory.file("intermediates/dexcount/" + variantName + "/tree.compact.gz");
        Provider<Directory> outputDirectory = buildDirectory.dir("outputs/dexcount/" + variantName);

        task.setDescription("Generate dex method counts");
        task.setGroup("Reporting");

        task.getConfigProperty().set(getExt());
        task.getOutputFileNameProperty().set(variantName);
        task.getLoaderProperty().set(artifacts.getBuiltArtifactsLoader());
        task.getMappingFileProperty().set(artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE));
        task.getPackageTreeFileProperty().set(packageTreeFile);
        task.getOutputDirectoryProperty().set(outputDirectory);
        task.getWorkerClasspath().from(getWorkerConfiguration());
    }
}
