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

/**
 * Configuration properties for {@link DexMethodCountTask} instances.
 */
class DexMethodCountExtension {
    /**
     * The format of the method count output, either "list", "tree", "json", or "yaml".
     */
    OutputFormat format = OutputFormat.LIST

    /**
     * When true, individual classes will be include in the package list - otherwise, only packages
     * are included.
     */
    boolean includeClasses

    /**
     * When true, the number of classes in a package or class will be included in the printed output.
     */
    boolean includeClassCount = false

    /**
     * When true, the number of fields in a package or class will be included in the printed output.
     */
    boolean includeFieldCount = true

    /**
     * When true, the total number of methods in the application will be included in the printed
     * output.
     */
    boolean includeTotalMethodCount = false

    /**
     * When true, packages will be sorted in descending order by the number of methods they contain.
     */
    boolean orderByMethodCount

    /**
     * When true, the output file will also be printed to the build's standard output.
     */
    boolean verbose

    /**
     * Sets the max number of package segments in the output - i.e. when set to 2, counts stop at
     * com.google, when set to 3 you get com.google.android, etc. "Unlimited" by default.
     */
    int maxTreeDepth = Integer.MAX_VALUE

    /**
     * When true, Team City integration strings will be printed.
     */
    boolean teamCityIntegration = false

    /**
     * A string which, if specified, will be added to TeamCity stat names. Null by default.
     */
    String teamCitySlug = null

    /**
     * When false, does not run count method during assemble task.
     */
    boolean runOnEachAssemble = true

    /**
     * When set, the build will fail when the APK/AAR has more methods than the max. 0 by default.
     */
    int maxMethodCount = -1

    /**
     * If the user has passed '--stacktrace' or '--full-stacktrace', assume that they are trying to
     * report a dexcount bug. Help us help them out by printing the current plugin title and version.
     */
    boolean printVersion

    /**
     * Timeout when running Dx in seconds.
     */
    int dxTimeoutSec = 60
    
    void setFormat(Object format) {
        if (format instanceof OutputFormat) {
            this.format = (OutputFormat) format
        } else {
            try {
                def formatName = "$format".toUpperCase(Locale.US)
                this.format = OutputFormat.valueOf(formatName)
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Unrecognized output format '$format'")
            }
        }
    }
}
