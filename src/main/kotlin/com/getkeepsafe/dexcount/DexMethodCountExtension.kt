/*
 * Copyright (C) 2015-2016 KeepSafe Software
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

package com.getkeepsafe.dexcount

import java.util.Locale

/**
 * Configuration properties for [DexMethodCountTaskBase] instances.
 */
open class DexMethodCountExtension {
    /**
     * When false, does not automatically count methods following the `package` task.
     */
    var runOnEachPackage: Boolean = true

    /**
     * When false, does not automatically count methods following the `package` task.
     *
     * Deprecated since 0.7.0, as dexcount no longer depends on the `assemble` task.
     * Currently a synonym for {@link #runOnEachPackage}; will be removed in a future
     * version.
     */
    @Deprecated("since 0.7.0; prefer {@link #runOnEachPackage}.", ReplaceWith("runOnEachPackage"))
    var runOnEachAssemble: Boolean
        get() = runOnEachPackage
        set(value) {
            runOnEachPackage = value
        }

    /**
     * The format of the method count output, either "list", "tree", "json", or "yaml".
     */
    var format: Any = OutputFormat.LIST
        set(value) {
            if (value is OutputFormat) {
                field = value
            } else {
                try {
                    field = OutputFormat.valueOf("$value".toUpperCase(Locale.US))
                } catch (ignored: IllegalArgumentException) {
                    throw IllegalArgumentException("Unrecognized output format '$value'")
                }
            }
        }
    /**
     * When true, individual classes will be include in the package list - otherwise, only packages
     * are included.
     */
    var includeClasses: Boolean = false

    /**
     * When true, the number of classes in a package or class will be included in the printed output.
     */
    var includeClassCount = false

    /**
     * When true, the number of fields in a package or class will be included in the printed output.
     */
    var includeFieldCount = true

    /**
     * When true, the total number of methods in the application will be included in the printed
     * output.
     */
    var includeTotalMethodCount = false

    /**
     * When true, packages will be sorted in descending order by the number of methods they contain.
     */
    var orderByMethodCount: Boolean = false

    /**
     * When true, the output file will also be printed to the build's standard output.
     */
    var verbose: Boolean = false

    /**
     * Sets the max number of package segments in the output - i.e. when set to 2, counts stop at
     * com.google, when set to 3 you get com.google.android, etc. "Unlimited" by default.
     */
    var maxTreeDepth = Integer.MAX_VALUE

    /**
     * When true, Team City integration strings will be printed. If the TEAMCITY_VERSION System
     * environment variable is defined this will become true by default.
     */
    var teamCityIntegration = System.getenv("TEAMCITY_VERSION") != null

    /**
     * A string which, if specified, will be added to TeamCity stat names. Null by default.
     */
    var teamCitySlug: String? = null

    /**
     * When set, the build will fail when the APK/AAR has more methods than the max. 0 by default.
     */
    var maxMethodCount = -1

    /**
     * If the user has passed '--stacktrace' or '--full-stacktrace', assume that they are trying to
     * report a dexcount bug. Help us help them out by printing the current plugin title and version.
     */
    var printVersion: Boolean = false

    /**
     * Timeout when running Dx in seconds.
     */
    var dxTimeoutSec = 60

    /**
     * When true, the plugin is enabled and will be run as normal.  When false,
     * the plugin is disabled and will not be run.
     */
    var enabled: Boolean = true
}
