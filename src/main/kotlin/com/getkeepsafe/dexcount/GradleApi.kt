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

import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.StartParameter

/**
 * Attempts to isolate our use bits of the Gradle API that have changed in
 * incompatible ways over time.
 */
class GradleApi private constructor() {
    companion object {
        /**
         * Return {@code true} if Gradle was launched with {@code --stacktrace},
         * otherwise {@code false}.
         *
         * <p>Gradle broke compatibility between 2.13 and 2.14 by repackaging
         * the {@code ShowStacktrace} enum; consequently we need to refer to
         * it by string name only.
         *
         * @param startParam
         */
        @JvmStatic fun isShowStacktrace(startParam: StartParameter): Boolean {
            val stacktrace: Enum<*>? = InvokerHelper.invokeMethod(startParam, "getShowStacktrace", null) as Enum<*>
            return "INTERNAL_EXCEPTIONS" != stacktrace?.name
        }
    }
}
