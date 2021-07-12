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

    public static GradleVersion gradleVersion(Project project) {
        return GradleVersion.parse(project.getGradle().getGradleVersion());
    }
}
