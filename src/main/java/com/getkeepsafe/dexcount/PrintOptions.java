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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class PrintOptions {
    abstract boolean getIncludeClasses();
    abstract boolean getIncludeClassCount();
    abstract boolean getIncludeMethodCount();
    abstract boolean getIncludeFieldCount();
    abstract boolean getIncludeTotalMethodCount();
    abstract boolean getTeamCityIntegration();
    abstract boolean getPrintHeader();
    abstract boolean getOrderByMethodCount();
    abstract int getMaxTreeDepth();
    abstract boolean getPrintDeclarations();
    abstract boolean isAndroidProject();

    abstract Builder toBuilder();

    PrintOptions withIsAndroidProject(boolean isAndroidProject) {
        return toBuilder()
            .setAndroidProject(isAndroidProject)
            .build();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setIncludeClasses(boolean includeClasses);
        abstract Builder setIncludeClassCount(boolean includeClassCount);
        abstract Builder setIncludeMethodCount(boolean includeMethodCount);
        abstract Builder setIncludeFieldCount(boolean includeFieldCount);
        abstract Builder setIncludeTotalMethodCount(boolean includeTotalMethodCount);
        abstract Builder setTeamCityIntegration(boolean teamCityIntegration);
        abstract Builder setPrintHeader(boolean printHeader);
        abstract Builder setOrderByMethodCount(boolean orderByMethodCount);
        abstract Builder setMaxTreeDepth(int maxTreeDepth);
        abstract Builder setPrintDeclarations(boolean printDeclarations);
        abstract Builder setAndroidProject(boolean androidProject);

        abstract PrintOptions build();
    }

    static Builder builder() {
        return new AutoValue_PrintOptions.Builder()
            .setIncludeClasses(false)
            .setIncludeClassCount(false)
            .setIncludeMethodCount(true)
            .setIncludeFieldCount(false)
            .setIncludeTotalMethodCount(false)
            .setTeamCityIntegration(false)
            .setPrintHeader(false)
            .setOrderByMethodCount(false)
            .setMaxTreeDepth(Integer.MAX_VALUE)
            .setPrintDeclarations(false)
            .setAndroidProject(true);
    }

    static PrintOptions fromDexCountExtension(DexCountExtension ext) {
        return builder()
            .setIncludeClasses(ext.getIncludeClasses().get())
            .setIncludeClassCount(ext.getIncludeClassCount().get())
            .setIncludeMethodCount(true)
            .setIncludeFieldCount(ext.getIncludeFieldCount().get())
            .setIncludeTotalMethodCount(ext.getIncludeTotalMethodCount().get())
            .setTeamCityIntegration(ext.getTeamCityIntegration().get())
            .setPrintHeader(ext.getPrintVersion().get())
            .setPrintDeclarations(ext.getPrintDeclarations().get())
            .setMaxTreeDepth(ext.getMaxTreeDepth().get())
            .setOrderByMethodCount(ext.getOrderByMethodCount().get())
            .build();
    }
}
