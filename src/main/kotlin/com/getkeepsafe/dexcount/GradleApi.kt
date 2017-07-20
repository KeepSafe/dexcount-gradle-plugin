/*
 * Copyright (C) 2016 KeepSafe Software
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

import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.StartParameter

/**
 * Attempts to isolate our use bits of the Gradle API that have changed in
 * incompatible ways over time.
 */
object GradleApi {
    /**
     * Return `true` if Gradle was launched with `--stacktrace`,
     * otherwise `false`.
     *
     * Gradle broke compatibility between 2.13 and 2.14 by repackaging
     * the `ShowStacktrace` enum; consequently we need to refer to
     * it by string name only.
     */
    @JvmStatic fun isShowStacktrace(startParam: StartParameter): Boolean {
        val stacktrace = InvokerHelper.invokeMethod(startParam, "getShowStacktrace", null) as Enum<*>
        return "INTERNAL_EXCEPTIONS" != stacktrace.name
    }
}
