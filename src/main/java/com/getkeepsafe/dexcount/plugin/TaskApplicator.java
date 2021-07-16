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

/**
 * An object that knows how to configure the dexcount plugin's tasks for a given
 * version of the Android Gradle Plugin.
 */
public interface TaskApplicator {
    void apply();

    interface Factory {
        Revision getMinimumRevision();
        TaskApplicator create(Project project, DexCountExtension ext);
    }
}
