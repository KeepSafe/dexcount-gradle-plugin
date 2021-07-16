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

import com.android.repository.Revision;
import com.getkeepsafe.dexcount.DexCountExtension;
import org.gradle.api.Project;

@SuppressWarnings("deprecation")
class JavaOnlyApplicator extends AbstractLegacyTaskApplicator {
    static class Factory implements TaskApplicator.Factory {
        @Override
        public Revision getMinimumRevision() {
            return new Revision(0, 0, 0);
        }

        @Override
        public TaskApplicator create(Project project, DexCountExtension ext) {
            return new JavaOnlyApplicator(project, ext);
        }
    }

    JavaOnlyApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
    }

    @Override
    protected void applyToApplicationVariant(com.android.build.gradle.api.ApplicationVariant variant) {
        throw new AssertionError("unreachable");
    }

    @Override
    protected void applyToLibraryVariant(com.android.build.gradle.api.LibraryVariant variant) {
        throw new AssertionError("unreachable");
    }

    @Override
    protected void applyToTestVariant(com.android.build.gradle.api.TestVariant variant) {
        throw new AssertionError("unreachable");
    }
}
