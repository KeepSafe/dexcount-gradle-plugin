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
import com.getkeepsafe.dexcount.DexCountException;
import com.getkeepsafe.dexcount.DexCountExtension;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;

class JavaOnlyApplicator extends AbstractTaskApplicator {
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

    private boolean didCreateJarTasks = false;

    JavaOnlyApplicator(Project project, DexCountExtension ext) {
        super(project, ext);
    }

    @Override
    public void apply() {
        getPlugins().withType(JavaPlugin.class).configureEach(plugin -> {
            registerJarTask();
            didCreateJarTasks = true;
        });

        getProject().afterEvaluate(_project -> {
            if (!didCreateJarTasks) {
                throw new DexCountException("Java plugin not detected");
            }
        });
    }
}
