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
package com.getkeepsafe.dexcount;

import org.gradle.api.Project;

public final class ProjectUtils {
    private ProjectUtils() {
        // singleton
    }

    public static boolean isInstantRun(Project project) {
        Object maybeOptionString = project.getProperties().get("android.optional.compilation");
        if (maybeOptionString == null) {
            return false;
        }

        if (!(maybeOptionString instanceof String)) {
            return false;
        }

        String optionString = (String) maybeOptionString;
        String[] optionList = optionString.split(",");
        for (String option : optionList) {
            if ("INSTANT_DEV".equals(option)) {
                return true;
            }
        }

        return false;
    }
}
