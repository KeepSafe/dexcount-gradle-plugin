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

import javax.annotation.Nullable;
import java.io.Serializable;

@AutoValue
public abstract class PrintOptions implements Serializable {
    private static final long serialVersionUID = -1L;

    public abstract boolean getIncludeClasses();
    public abstract boolean getIncludeClassCount();
    public abstract boolean getIncludeMethodCount();
    public abstract boolean getIncludeFieldCount();
    public abstract boolean getIncludeTotalMethodCount();
    public abstract boolean getTeamCityIntegration();
    @Nullable
    public abstract String getTeamCitySlug();
    public abstract boolean getPrintHeader();
    public abstract boolean getOrderByMethodCount();
    public abstract int getMaxTreeDepth();
    public abstract int getMaxMethodCount();
    public abstract boolean getPrintDeclarations();
    public abstract boolean isAndroidProject();
    public abstract boolean isVerbose();
    public abstract OutputFormat getOutputFormat();

    public abstract Builder toBuilder();

    public PrintOptions withIsAndroidProject(boolean isAndroidProject) {
        return toBuilder()
            .setAndroidProject(isAndroidProject)
            .build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setIncludeClasses(boolean includeClasses);
        public abstract Builder setIncludeClassCount(boolean includeClassCount);
        public abstract Builder setIncludeMethodCount(boolean includeMethodCount);
        public abstract Builder setIncludeFieldCount(boolean includeFieldCount);
        public abstract Builder setIncludeTotalMethodCount(boolean includeTotalMethodCount);
        public abstract Builder setTeamCityIntegration(boolean teamCityIntegration);
        public abstract Builder setTeamCitySlug(@Nullable String teamCitySlug);
        public abstract Builder setPrintHeader(boolean printHeader);
        public abstract Builder setOrderByMethodCount(boolean orderByMethodCount);
        public abstract Builder setMaxTreeDepth(int maxTreeDepth);
        public abstract Builder setMaxMethodCount(int maxMethodCount);
        public abstract Builder setPrintDeclarations(boolean printDeclarations);
        public abstract Builder setAndroidProject(boolean androidProject);
        public abstract Builder setVerbose(boolean verbose);
        public abstract Builder setOutputFormat(OutputFormat outputFormat);

        public abstract PrintOptions build();
    }

    public static Builder builder() {
        return new AutoValue_PrintOptions.Builder()
            .setIncludeClasses(false)
            .setIncludeClassCount(false)
            .setIncludeMethodCount(true)
            .setIncludeFieldCount(false)
            .setIncludeTotalMethodCount(false)
            .setTeamCityIntegration(false)
            .setTeamCitySlug(null)
            .setPrintHeader(false)
            .setOrderByMethodCount(false)
            .setMaxTreeDepth(Integer.MAX_VALUE)
            .setMaxMethodCount(-1)
            .setPrintDeclarations(false)
            .setAndroidProject(true)
            .setVerbose(false)
            .setOutputFormat(OutputFormat.LIST);
    }

    public static PrintOptions fromDexCountExtension(DexCountExtension ext) {
        return builder()
            .setIncludeClasses(ext.getIncludeClasses().get())
            .setIncludeClassCount(ext.getIncludeClassCount().get())
            .setIncludeMethodCount(true)
            .setIncludeFieldCount(ext.getIncludeFieldCount().get())
            .setIncludeTotalMethodCount(ext.getIncludeTotalMethodCount().get())
            .setTeamCityIntegration(ext.getTeamCityIntegration().get())
            .setTeamCitySlug(ext.getTeamCitySlug().getOrNull())
            .setPrintHeader(ext.getPrintVersion().get())
            .setPrintDeclarations(ext.getPrintDeclarations().get())
            .setMaxTreeDepth(ext.getMaxTreeDepth().get())
            .setMaxMethodCount(ext.getMaxMethodCount().get())
            .setOrderByMethodCount(ext.getOrderByMethodCount().get())
            .setVerbose(ext.getVerbose().get())
            .setOutputFormat(ext.getFormat().get())
            .build();
    }
}
