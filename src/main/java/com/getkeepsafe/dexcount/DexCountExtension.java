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

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;

/**
 * Configuration properties for [DexCountTask] instances.
 */
public class DexCountExtension {
    private final Property<Boolean> runOnEachPackageProperty;
    private final Property<OutputFormat> outputFormatProperty;
    private final Property<Boolean> includeClassesProperty;
    private final Property<Boolean> includeClassCountProperty;
    private final Property<Boolean> includeFieldCountProperty;
    private final Property<Boolean> includeTotalMethodCountProperty;
    private final Property<Boolean> orderByMethodCountProperty;
    private final Property<Boolean> verboseProperty;
    private final Property<Integer> maxTreeDepthProperty;
    private final Property<Boolean> teamCityIntegrationProperty;
    private final Property<String> teamCitySlugProperty;
    private final Property<Integer> maxMethodCountProperty;
    private final Property<Boolean> printVersionProperty;
    private final Property<Boolean> printDeclarationsProperty;
    private final Property<Boolean> enabledProperty;

    @Inject
    public DexCountExtension(ObjectFactory objects, ProviderFactory providers) {
        this.runOnEachPackageProperty = objects.property(Boolean.class).convention(Boolean.TRUE);
        this.outputFormatProperty = objects.property(OutputFormat.class).convention(OutputFormat.LIST);
        this.includeClassesProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.includeClassCountProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.includeFieldCountProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.includeTotalMethodCountProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.orderByMethodCountProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.verboseProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.maxTreeDepthProperty = objects.property(Integer.class).convention(Integer.MAX_VALUE);
        this.teamCityIntegrationProperty = objects.property(Boolean.class).convention(providers.provider(() -> System.getenv("TEAMCITY_VERSION") != null));
        this.teamCitySlugProperty = objects.property(String.class);
        this.maxMethodCountProperty = objects.property(Integer.class).convention(-1);
        this.printVersionProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.printDeclarationsProperty = objects.property(Boolean.class).convention(Boolean.FALSE);
        this.enabledProperty = objects.property(Boolean.class).convention(Boolean.TRUE);
    }

    /**
     * When false, does not automatically count methods following the `package` task.
     */
    @Internal("plugin input, not task input")
    public Property<Boolean> getRunOnEachPackage() {
        return runOnEachPackageProperty;
    }

    /**
     * The format of the method count output, either "list", "tree", "json", or "yaml".
     */
    @Input
    public Property<OutputFormat> getFormat() {
        return outputFormatProperty;
    }

    /**
     * When true, individual classes will be include in the package list - otherwise, only packages
     * are included.
     */
    @Input
    public Property<Boolean> getIncludeClasses() {
        return includeClassesProperty;
    }

    /**
     * When true, the number of classes in a package or class will be included in the printed output.
     */
    @Input
    public Property<Boolean> getIncludeClassCount() {
        return includeClassCountProperty;
    }

    /**
     * When true, the number of fields in a package or class will be included in the printed output.
     */
    @Input
    public Property<Boolean> getIncludeFieldCount() {
        return includeFieldCountProperty;
    }

    /**
     * When true, the total number of methods in the application will be included in the printed
     * output.
     */
    @Input
    public Property<Boolean> getIncludeTotalMethodCount() {
        return includeTotalMethodCountProperty;
    }

    /**
     * When true, packages will be sorted in descending order by the number of methods they contain.
     */
    @Input
    public Property<Boolean> getOrderByMethodCount() {
        return orderByMethodCountProperty;
    }

    /**
     * When true, the output file will also be printed to the build's standard output.
     */
    @Internal
    public Property<Boolean> getVerbose() {
        return verboseProperty;
    }

    /**
     * Sets the max number of package segments in the output - i.e. when set to 2, counts stop at
     * com.google, when set to 3 you get com.google.android, etc. "Unlimited" by default.
     */
    @Input
    public Property<Integer> getMaxTreeDepth() {
        return maxTreeDepthProperty;
    }

    /**
     * When true, Team City integration strings will be printed. If the TEAMCITY_VERSION System
     * environment variable is defined this will become true by default.
     */
    @Internal("TeamCity stats are stdout-only")
    public Property<Boolean> getTeamCityIntegration() {
        return teamCityIntegrationProperty;
    }

    /**
     * A string which, if specified, will be added to TeamCity stat names. Null by default.
     */
    @Internal("TeamCity stats are stdout-only")
    public Property<String> getTeamCitySlug() {
        return teamCitySlugProperty;
    }

    /**
     * When set, the build will fail when the APK/AAR has more methods than the max. 0 by default.
     */
    @Input
    public Property<Integer> getMaxMethodCount() {
        return maxMethodCountProperty;
    }

    /**
     * If the user has passed '--stacktrace' or '--full-stacktrace', assume that they are trying to
     * report a dexcount bug. Help us help them out by printing the current plugin title and version.
     */
    @Internal("stdout-only")
    public Property<Boolean> getPrintVersion() {
        return printVersionProperty;
    }

    /**
     * When true, then the plugin only counts the declared methods and fields inside this module.
     * This does NOT represent the actual reference method count, because method references are
     * ignored. This flag is false by default and can only be turned on for library modules.
     */
    @Input
    public Property<Boolean> getPrintDeclarations() {
        return printDeclarationsProperty;
    }

    /**
     * When true, the plugin is enabled and will be run as normal.  When false,
     * the plugin is disabled and will not be run.
     */
    @Internal("this is plugin input, not task input")
    public Property<Boolean> getEnabled() {
        return enabledProperty;
    }
}
