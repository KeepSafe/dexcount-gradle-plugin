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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;

class ThreeSixApplicator extends ThreeFourApplicator {
    static class Factory implements TaskApplicator.Factory {
        @Override
        public Revision getMinimumRevision() {
            return new Revision(3, 6);
        }

        @Override
        public TaskApplicator create(Project project, DexCountExtension ext) {
            return new ThreeSixApplicator(project, ext);
        }
    }

    ThreeSixApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
    }

    @NotNull
    @Override
    protected DirectoryProperty getOutputDirectory(PackageAndroidArtifact task) {
        return task.getOutputDirectory();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Provider<FileCollection> getMappingFile(com.android.build.gradle.api.BaseVariant variant) {
        return variant.getMappingFileProvider();
    }
}
