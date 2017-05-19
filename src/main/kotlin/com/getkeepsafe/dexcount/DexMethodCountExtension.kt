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

import java.lang.IllegalArgumentException
import java.util.Locale

/**
 * Configuration properties for {@link DexMethodCountTask} instances.
 */
open class DexMethodCountExtension {
    /**
     * The format of the method count output, either "list", "tree", "json", or "yaml".
     */
    var format: OutputFormat = OutputFormat.LIST
        set(value) {
            if (value is OutputFormat) {
                field = value
            } else {
                try {
                    val formatName = "$value".toUpperCase(Locale.US)
                    field = OutputFormat.valueOf(formatName)
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
     * When true, the number of fields in a package or class will be included in the printed output.
     */
    var includeFieldCount: Boolean = true

    /**
     * When true, the total number of methods in the application will be included in the printed
     * output.
     */
    var includeTotalMethodCount: Boolean = false

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
    var maxTreeDepth: Int = Integer.MAX_VALUE

    /**
     * When true, Team City integration strings will be printed.
     */
    var teamCityIntegration: Boolean = false

    /**
     * When true, count methods even for Instant Run builds. False by default.
     */
    var enableForInstantRun: Boolean = false

    /**
     * A string which, if specified, will be added to TeamCity stat names. Null by default.
     */
    var teamCitySlug: String? = null

    /**
     * When false, does not run count method during assemble task.
     */
    var runOnEachAssemble: Boolean = true

    /**
     * When set, the build will fail when the APK/AAR has more methods than the max. 0 by default.
     */
    var maxMethodCount: Int = -1

    /**
     * If the user has passed '--stacktrace' or '--full-stacktrace', assume that they are trying to
     * report a dexcount bug. Help us help them out by printing the current plugin title and version.
     */
    var printVersion: Boolean = false

    /**
     * Timeout when running Dx in seconds.
     */
    var dxTimeoutSec: Int = 60
}
