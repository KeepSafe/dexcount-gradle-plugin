/*
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

package com.getkeepsafe.dexcount

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project

class DexMethodCountPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project.plugins.hasPlugin('com.android.application')) {
            applyAndroid(project, (DomainObjectCollection<BaseVariant>) project.android.applicationVariants);
        } else if (project.plugins.hasPlugin('com.android.library')) {
            applyAndroid(project, (DomainObjectCollection<BaseVariant>) project.android.libraryVariants);
        } else {
            throw new IllegalArgumentException("Dexcount plugin requires the Android plugin to be configured");
        }
    }

    private static void applyAndroid(Project project, DomainObjectCollection<BaseVariant> variants) {
        variants.all { variant ->
            variant.outputs.each { output ->
                def taskName = "count${variant.name.capitalize()}${output.name.capitalize()}DexMethods"
                DexMethodCountTask task = project.tasks.create(taskName, DexMethodCountTask)
                task.output(output)
                variant.assemble.doLast { task.countMethods() }
            }
        }
    }
}
