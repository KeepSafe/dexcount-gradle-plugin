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
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestPlugin;
import com.android.repository.Revision;
import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.treegen.LibraryPackageTreeTask;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

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

    FourTwoApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void apply() {
        if (!getExt().getEnabled().get()) {
            return;
        }

        getProject().getPlugins().withType(AppPlugin.class).configureEach(plugin -> {
            com.android.build.api.extension.ApplicationAndroidComponentsExtension androidComponents =
                getProject().getExtensions().getByType(com.android.build.api.extension.ApplicationAndroidComponentsExtension.class);

            androidComponents.onVariants(androidComponents.selector().all(), variant -> {
                registerApkTask(variant.getName(), variant.getArtifacts());
                registerBundleTask(variant.getName(), variant.getArtifacts());
            });
        });

        getProject().getPlugins().withType(LibraryPlugin.class).configureEach(plugin -> {
            com.android.build.api.extension.LibraryAndroidComponentsExtension androidComponents =
                getProject().getExtensions().getByType(com.android.build.api.extension.LibraryAndroidComponentsExtension.class);

            androidComponents.onVariants(androidComponents.selector().all(), variant -> {
                registerAarTask(variant.getName(), variant.getArtifacts());
            });
        });

        getProject().getPlugins().withType(TestPlugin.class).configureEach(plugin -> {
            com.android.build.api.extension.TestAndroidComponentsExtension androidComponents =
                getProject().getExtensions().getByType(com.android.build.api.extension.TestAndroidComponentsExtension.class);

            androidComponents.onVariants(androidComponents.selector().all(), variant -> {
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
