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
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestExtension;
import com.android.build.gradle.TestPlugin;
import com.android.build.gradle.tasks.BundleAar;
import com.android.repository.Revision;
import com.getkeepsafe.dexcount.DexCountException;
import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.treegen.Agp41LibraryPackageTreeTask;
import com.getkeepsafe.dexcount.treegen.ApkPackageTreeTask;
import com.getkeepsafe.dexcount.treegen.BundlePackageTreeTask;
import com.getkeepsafe.dexcount.treegen.JarPackageTreeTask;
import com.getkeepsafe.dexcount.treegen.ModernGeneratePackageTreeTask;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

class FourOneApplicator extends AbstractTaskApplicator {
    static class Factory implements TaskApplicator.Factory {
        @Override
        public Revision getMinimumRevision() {
            return new Revision(4, 1);
        }

        @Override
        public TaskApplicator create(Project project, DexCountExtension ext) {
            return new FourOneApplicator(project, ext);
        }
    }

    private static Class<?> clsVariantProperties;
    private static Method fnGetName;
    private static Method fnGetArtifacts;

    private static Class<?> clsKotlinUnaryFn;
    private static Class<?> clsCommonExtension;
    private static Method fnOnVariantProperties;

    private static Class<?> clsArtifactType;
    private static Enum<?> emArtifactTypeApk;
    private static Enum<?> emArtifactTypeBundle;
    private static Enum<?> emArtifactTypeAar;
    private static Enum<?> emArtifactTypeObfuscationMappingFile;

    private static Class<?> clsArtifacts;
    private static Method fnGetArtifact;

    FourOneApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
        try {
            clsVariantProperties = Class.forName("com.android.build.api.variant.VariantProperties");
            fnGetName = getMethod(clsVariantProperties, "getName");
            fnGetArtifacts = getMethod(clsVariantProperties, "getArtifacts");

            clsKotlinUnaryFn = Class.forName("kotlin.jvm.functions.Function1");
            clsCommonExtension = Class.forName("com.android.build.api.dsl.CommonExtension");
            fnOnVariantProperties = getMethod(clsCommonExtension, "onVariantProperties", clsKotlinUnaryFn);

            clsArtifactType = Class.forName("com.android.build.api.artifact.ArtifactType");
            emArtifactTypeApk = getEnumConstant(clsArtifactType, "APK");
            emArtifactTypeBundle = getEnumConstant(clsArtifactType, "BUNDLE");
            emArtifactTypeAar = getEnumConstant(clsArtifactType, "AAR");
            emArtifactTypeObfuscationMappingFile = getEnumConstant(clsArtifactType, "OBFUSCATION_MAPPING_FILE");

            clsArtifacts = Class.forName("com.android.build.api.artifact.Artifacts");
            fnGetArtifact = getMethod(clsArtifacts, "get", clsArtifactType);
        } catch (Exception e) {
            throw new DexCountException("Failed to initialize AGP 4.1 support", e);
        }
    }

    @SuppressWarnings("unchecked")
    enum OldArtifactType {
        APK {
            @Override
            Directory get(Artifacts artifacts) {
                try {
                    return (Directory) fnGetArtifact.invoke(artifacts, emArtifactTypeApk);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new AssertionError(e);
                }
            }
        },

        BUNDLE {
            @Override
            RegularFile get(Artifacts artifacts) {
                try {
                    return (RegularFile) fnGetArtifact.invoke(artifacts, emArtifactTypeBundle);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new AssertionError(e);
                }
            }
        },

        AAR {
            @Override
            RegularFile get(Artifacts artifacts) {
                try {
                    return (RegularFile) fnGetArtifact.invoke(artifacts, emArtifactTypeAar);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new AssertionError(e);
                }
            }
        },

        OBFUSCATION_MAPPING_FILE {
            @Override
            RegularFile get(Artifacts artifacts) {
                try {
                    return (RegularFile) fnGetArtifact.invoke(artifacts, emArtifactTypeObfuscationMappingFile);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new AssertionError(e);
                }
            }
        };

        abstract <T> T get(Artifacts artifacts);
    }

    @Override
    public void apply() {
        if (!getExt().getEnabled().get()) {
            return;
        }

        getProject().getPlugins().withType(AppPlugin.class).configureEach(plugin -> {
            ApplicationExtension android = getProject().getExtensions().getByType(ApplicationExtension.class);
            onVariantProperties(android, (variantName, artifacts) -> {
                registerApkTask(variantName, artifacts);
                registerBundleTask(variantName, artifacts);
            });
        });

        getProject().getPlugins().withType(LibraryPlugin.class).configureEach(plugin -> {
            LibraryExtension android = getProject().getExtensions().getByType(LibraryExtension.class);
            onVariantProperties(android, this::registerAarTask);
        });

        getProject().getPlugins().withType(TestPlugin.class).configureEach(plugin -> {
            TestExtension android = getProject().getExtensions().getByType(TestExtension.class);
            onVariantProperties(android, this::registerApkTask);
        });

        getProject().afterEvaluate(project -> {
            if (project.getExtensions().findByType(CommonExtension.class) == null) {
                // No Android plugins were registered; this may be a jar-count usage.
                registerJarTask();
            }
        });
    }

    private void onVariantProperties(Object target, BiConsumer<String, Artifacts> fn) {
        try {
            fnOnVariantProperties.invoke(target, makeVariantPropertiesCallback(fn));
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new DexCountException("Failed to register a variant-properties callback", e);
        }
    }

    private Object makeVariantPropertiesCallback(BiConsumer<String, Artifacts> fn) {
        return (Function1<Object, Unit>) receiver -> {
            try {
                String name = (String) fnGetName.invoke(receiver);
                Artifacts artifacts = (Artifacts) fnGetArtifacts.invoke(receiver);
                fn.accept(name, artifacts);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new DexCountException("Failed to execute a variant-properties callback", e);
            }
            return Unit.INSTANCE;
        };
    }

    protected void registerApkTask(String variantName, Artifacts artifacts) {
        if (getExt().getPrintDeclarations().get()) {
            throw new IllegalStateException("Cannot compute declarations for project " + getProject());
        }

        String genTaskName = String.format("generate%sPackageTree", StringUtils.capitalize(variantName));

        TaskProvider<ApkPackageTreeTask> gen = getProject().getTasks().register(genTaskName, ApkPackageTreeTask.class, t -> {
            setCommonProperties(t, variantName, artifacts);

            t.getApkDirectory().set(OldArtifactType.APK.<Directory>get(artifacts));

        });

        registerOutputTask(gen, variantName, true);
    }

    protected void registerBundleTask(String variantName, Artifacts artifacts) {
        if (getExt().getPrintDeclarations().get()) {
            throw new IllegalStateException("Cannot compute declarations for project " + getProject());
        }

        String genTaskName = String.format("generate%sBundlePackageTree", StringUtils.capitalize(variantName));

        TaskProvider<BundlePackageTreeTask> gen = getProject().getTasks().register(genTaskName, BundlePackageTreeTask.class, t -> {
            setCommonProperties(t, variantName, artifacts);

            t.getBundleFile().set(OldArtifactType.BUNDLE.<RegularFile>get(artifacts));
        });

        registerOutputTask(gen, variantName + "Bundle", true);
    }

    protected void registerAarTask(String variantName, Artifacts artifacts) {
        String genTaskName = String.format("generate%sPackageTree", StringUtils.capitalize(variantName));

        getProject().afterEvaluate(project -> {
            String bundleTaskName = String.format("bundle%sAar", StringUtils.capitalize(variantName));
            final TaskProvider<BundleAar> bundleTaskProvider = project.getTasks().named(bundleTaskName, BundleAar.class);

            TaskProvider<Agp41LibraryPackageTreeTask> treegen = project.getTasks().register(genTaskName, Agp41LibraryPackageTreeTask.class, t -> {
                t.getAarBundleFileCollection().from(bundleTaskProvider);

                setCommonProperties(t, variantName, artifacts);
            });

            registerOutputTask(treegen, variantName, true);
        });
    }

    protected void registerJarTask() {
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

    protected <T extends ModernGeneratePackageTreeTask<?, ?>> void setCommonProperties(T task, String variantName, Artifacts artifacts) {
        DirectoryProperty buildDirectory = getProject().getLayout().getBuildDirectory();
        Provider<RegularFile> packageTreeFile = buildDirectory.file("intermediates/dexcount/" + variantName + "/tree.compact.gz");
        Provider<Directory> outputDirectory = buildDirectory.dir("outputs/dexcount/" + variantName);

        task.setDescription("Generate dex method counts");
        task.setGroup("Reporting");

        task.getConfigProperty().set(getExt());
        task.getOutputFileNameProperty().set(variantName);
        task.getLoaderProperty().set(artifacts.getBuiltArtifactsLoader());
        task.getMappingFileProperty().set(OldArtifactType.OBFUSCATION_MAPPING_FILE.<RegularFile>get(artifacts));
        task.getPackageTreeFileProperty().set(packageTreeFile);
        task.getOutputDirectoryProperty().set(outputDirectory);
        task.getWorkerClasspath().from(getWorkerConfiguration());
    }
}
