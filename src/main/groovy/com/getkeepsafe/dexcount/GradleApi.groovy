package com.getkeepsafe.dexcount

import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.StartParameter

/**
 * Attempts to isolate our use bits of the Gradle API that have changed in
 * incompatible ways over time.
 */
final class GradleApi {
    private GradleApi() {
        // no instances
    }

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
    static boolean isShowStacktrace(StartParameter startParam) {
        Enum stacktrace = (Enum) InvokerHelper.invokeMethod(
                startParam, "getShowStacktrace", null)
        return "INTERNAL_EXCEPTIONS" != stacktrace.name()
    }
}
