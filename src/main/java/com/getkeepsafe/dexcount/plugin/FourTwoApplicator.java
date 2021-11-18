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
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.variant.ApplicationVariant;
import com.android.build.api.variant.LibraryVariant;
import com.android.build.api.variant.TestVariant;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestPlugin;
import com.android.repository.Revision;
import com.getkeepsafe.dexcount.DexCountException;
import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.treegen.LibraryPackageTreeTask;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class FourTwoApplicator extends FourOneApplicator {
    static class Factory implements TaskApplicator.Factory {
        @Override
        public Revision getMinimumRevision() {
            return new Revision(4, 2);
        }

        @Override
        public TaskApplicator create(Project project, DexCountExtension ext) {
            return new FourTwoApplicator(project, ext);
        }
    }

    private static final String ANDROID_COMPONENTS_EXTENSION =
        "com.android.build.api.extension.AndroidComponentsExtension";

    private static final String APPLICATION_ANDROID_COMPONENTS_EXTENSION =
        "com.android.build.api.extension.ApplicationAndroidComponentsExtension";

    private static final String LIBRARY_ANDROID_COMPONENTS_EXTENSION =
        "com.android.build.api.extension.LibraryAndroidComponentsExtension";

    private static final String TEST_ANDROID_COMPONENTS_EXTENSION =
        "com.android.build.api.extension.TestAndroidComponentsExtension";

    private static final String VARIANT_SELECTOR = "com.android.build.api.extension.VariantSelector";

    FourTwoApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void apply() {
        if (!getExt().getEnabled().get()) {
            return;
        }

        applyReflectively();
    }

    private void applyReflectively() {
        Class<?> clsAndroidComponentsExtension;
        Method fnOnVariants;
        Method fnSelector;

        Class<?> clsAppComponentsExtension;
        Class<?> clsLibraryComponentsExtension;
        Class<?> clsTestComponentsExtension;

        Class<?> clsVariantSelector;
        Method fnVariantSelectorAll;

        try {
            clsVariantSelector = Class.forName(VARIANT_SELECTOR);
            fnVariantSelectorAll = clsVariantSelector.getDeclaredMethod("all");

            clsAndroidComponentsExtension = Class.forName(ANDROID_COMPONENTS_EXTENSION);
            fnOnVariants = clsAndroidComponentsExtension.getDeclaredMethod("onVariants", clsVariantSelector, Action.class);
            fnSelector = clsAndroidComponentsExtension.getDeclaredMethod("selector");

            clsAppComponentsExtension = Class.forName(APPLICATION_ANDROID_COMPONENTS_EXTENSION);
            clsLibraryComponentsExtension = Class.forName(LIBRARY_ANDROID_COMPONENTS_EXTENSION);
            clsTestComponentsExtension = Class.forName(TEST_ANDROID_COMPONENTS_EXTENSION);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new DexCountException("Failed to load AGP types", e);
        }

        getProject().getPlugins().withType(AppPlugin.class).configureEach(plugin -> {
            try {
                Object androidComponents = getProject().getExtensions().getByType(clsAppComponentsExtension);
                Object selector = fnSelector.invoke(androidComponents);
                Object allVariants = fnVariantSelectorAll.invoke(selector);

                Action<ApplicationVariant> action = variant -> {
                    registerApkTask(variant.getName(), variant.getArtifacts());
                    registerBundleTask(variant.getName(), variant.getArtifacts());
                };

                fnOnVariants.invoke(androidComponents, allVariants, action);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new DexCountException("Failed to apply dexcount to application", e);
            }
        });

        getProject().getPlugins().withType(LibraryPlugin.class).configureEach(plugin -> {
            try {
                Object libraryComponents = getProject().getExtensions().getByType(clsLibraryComponentsExtension);
                Object selector = fnSelector.invoke(libraryComponents);
                Object allVariants = fnVariantSelectorAll.invoke(selector);

                Action<LibraryVariant> action = variant -> {
                    registerAarTask(variant.getName(), variant.getArtifacts());
                };

                fnOnVariants.invoke(libraryComponents, allVariants, action);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new DexCountException("Failed to apply dexcount to library", e);
            }
        });

        getProject().getPlugins().withType(TestPlugin.class).configureEach(plugin -> {
            try {
                Object testComponents = getProject().getExtensions().getByType(clsTestComponentsExtension);
                Object selector = fnSelector.invoke(testComponents);
                Object allVariants = fnVariantSelectorAll.invoke(selector);

                Action<TestVariant> action = variant -> {
                    registerApkTask(variant.getName(), variant.getArtifacts());
                };

                fnOnVariants.invoke(testComponents, allVariants, action);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new DexCountException("Failed to apply dexcount to test", e);
            }
        });

        getProject().afterEvaluate(project -> {
            if (project.getExtensions().findByType(CommonExtension.class) == null) {
                // No Android plugins were registered; this may be a jar-count usage.
                registerJarTask();
            }
        });
    }

    @Override
    protected void registerAarTask(String variantName, Artifacts artifacts) {
        String genTaskName = String.format("generate%sPackageTree", StringUtils.capitalize(variantName));

        TaskProvider<LibraryPackageTreeTask> gen = getProject().getTasks().register(genTaskName, LibraryPackageTreeTask.class, t -> {
            setCommonProperties(t, variantName, artifacts);

            t.getAarFile().set(OldArtifactType.AAR.<Provider<RegularFile>>get(artifacts));
        });

        registerOutputTask(gen, variantName, true);
    }
}
