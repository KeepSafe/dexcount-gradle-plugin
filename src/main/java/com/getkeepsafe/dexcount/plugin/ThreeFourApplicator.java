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

import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.repository.Revision;
import com.getkeepsafe.dexcount.DexCountExtension;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("deprecation")
class ThreeFourApplicator extends AbstractLegacyTaskApplicator {
    static class Factory implements TaskApplicator.Factory {
        @Override
        public Revision getMinimumRevision() {
            return new Revision(3, 4);
        }

        @Override
        public TaskApplicator create(Project project, DexCountExtension ext) {
            return new ThreeFourApplicator(project, ext);
        }
    }

    private final Method method_getOutputDirectory;

    ThreeFourApplicator(Project project, DexCountExtension ext) {
        super(project, ext);

        try {
            method_getOutputDirectory = PackageAndroidArtifact.class.getDeclaredMethod("getOutputDirectory");
            method_getOutputDirectory.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    protected void applyToApplicationVariant(com.android.build.gradle.api.ApplicationVariant variant) {
        applyToApkVariant(variant);
    }

    @Override
    protected void applyToLibraryVariant(com.android.build.gradle.api.LibraryVariant variant) {
        TaskProvider<Zip> packageTaskProvider = variant.getPackageLibraryProvider();
        createTask(variant, packageTaskProvider, null, task -> {
            task.getInputFileProperty().set(packageTaskProvider.flatMap(Zip::getArchiveFile));
        });
    }

    @Override
    protected void applyToTestVariant(com.android.build.gradle.api.TestVariant variant) {
        applyToApkVariant(variant);
    }

    private void applyToApkVariant(com.android.build.gradle.api.ApkVariant variant) {
        checkPrintDeclarationsIsFalse();

        variant.getOutputs().configureEach(output -> {
            if (!(output instanceof com.android.build.gradle.api.ApkVariantOutput)) {
                String message = String.format("Unexpected output type for variant %s: %s", variant.getName(), output.getClass());
                throw new IllegalArgumentException(message);
            }

            com.android.build.gradle.api.ApkVariantOutput apkVariantOutput = (com.android.build.gradle.api.ApkVariantOutput) output;
            createTask(variant, variant.getPackageApplicationProvider(), apkVariantOutput, task -> {
                Provider<Directory> outputDirProvider = variant.getPackageApplicationProvider().flatMap(this::getOutputDirectory);
                Provider<RegularFile> fileProvider = outputDirProvider.map(it -> it.file(apkVariantOutput.getOutputFileName()));
                task.getInputFileProperty().set(fileProvider);
            });
        });
    }

    @NotNull
    protected DirectoryProperty getOutputDirectory(PackageAndroidArtifact task) {
        DirectoryProperty result = getProject().getObjects().directoryProperty();
        try {
            result.set((File) method_getOutputDirectory.invoke(task));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
        return result;
    }
}
