/*
 * Copyright (C) 2015-2019 KeepSafe Software
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

import kotlin.Unit;
import org.gradle.api.GradleException;
import org.gradle.api.logging.LogLevel;

/**
 * An object that can produce formatted output from a {@link PackageTree} instance.
 */
public class CountReporter {
    /**
     * The maximum number of method refs and field refs allowed in a single Dex
     * file.
     */
    private static final int MAX_DEX_REFS = 0xFFFF; // 65535

    private final PackageTree packageTree;
    private final String variantName;
    private final Styleable styleable;
    private final boolean enabled;
    private final boolean verbose;
    private final OutputFormat format;
    private final int maxMethodCount;
    private final PrintOptions printOptions;
    private final String inputRepresentation;
    private final String teamCitySlug;
    private final boolean isAndroidProject;
    private final boolean isInstantRun;

    public CountReporter(
            PackageTree packageTree,
            String variantName,
            Styleable styleable,
            DexCountExtension config,
            String inputRepresentation,
            boolean isAndroidProject,
            boolean isInstantRun) {
        this.packageTree = packageTree;
        this.variantName = variantName;
        this.styleable = styleable;
        this.enabled = config.getEnabled().get();
        this.verbose = config.getVerbose().get();
        this.format = config.getFormat().get();
        this.maxMethodCount = config.getMaxMethodCount().get();
        this.printOptions = PrintOptions.fromDexCountExtension(config).withIsAndroidProject(isAndroidProject);
        this.inputRepresentation = inputRepresentation;
        this.teamCitySlug = config.getTeamCitySlug().getOrNull();
        this.isAndroidProject = isAndroidProject;
        this.isInstantRun = isInstantRun;
    }

    public void report() {
        try {
            if (!enabled) {
                throw new IllegalStateException("Tasks should not be executed if the plugin is disabled");
            }

            printPreamble();
            printSummary();
            printTaskDiagnosticData();
            failBuildMaxMethods();
        } catch (DexCountException e) {
            styleable.withStyledOutput(Color.RED, LogLevel.ERROR, out -> {
                out.println("Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues");
                e.printStackTrace(out);
                return Unit.INSTANCE;
            });
        }
    }

    private void printPreamble() {
        if (printOptions.getPrintHeader()) {
            String projectName = getClass().getPackage().getImplementationTitle();
            String projectVersion = getClass().getPackage().getImplementationVersion();

            styleable.withStyledOutput(Color.DEFAULT, null, out -> {
                out.println("Dexcount name:    " + projectName);
                out.println("Dexcount version: " + projectVersion);
                out.println("Dexcount input:   " + inputRepresentation);
                return Unit.INSTANCE;
            });
        }
    }

    private String percentUsed(int count) {
        double used = ((double) count / MAX_DEX_REFS) * 100.0;
        return String.format("%.2f", used);
    }

    private void printSummary() {
        if (isInstantRun) {
            styleable.withStyledOutput(Color.RED, null, out -> {
                out.println("Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.");
                return Unit.INSTANCE;
            });
        }

        Color color = packageTree.getMethodCount() < 50000 ? Color.GREEN : Color.YELLOW;

        styleable.withStyledOutput(color, null, out -> {
            String percentMethodsUsed = percentUsed(packageTree.getMethodCount());
            String percentFieldsUsed = percentUsed(packageTree.getFieldCount());
            String percentClassesUsed = percentUsed(packageTree.getClassCount());

            int methodsRemaining = Math.max(MAX_DEX_REFS - packageTree.getMethodCount(), 0);
            int fieldsRemaining = Math.max(MAX_DEX_REFS - packageTree.getFieldCount(), 0);
            int classesRemaining = Math.max(MAX_DEX_REFS - packageTree.getClassCount(), 0);

            int methodCount, fieldCount, classCount;
            if (isAndroidProject) {
                methodCount = packageTree.getMethodCount();
                fieldCount = packageTree.getFieldCount();
                classCount = packageTree.getClassCount();
            } else {
                methodCount = packageTree.getMethodCountDeclared();
                fieldCount = packageTree.getFieldCountDeclared();
                classCount = packageTree.getClassCountDeclared();
            }

            out.println("Total methods in " + inputRepresentation + ": " + methodCount + "(" + percentMethodsUsed + "% used)");
            out.println("Total fields in " + inputRepresentation + ": " + fieldCount + "(" + percentFieldsUsed + "% used)");
            out.println("Total classes in " + inputRepresentation + ": " + classCount + "(" + percentClassesUsed + "% used)");

            if (isAndroidProject) {
                out.println("Methods remaining in " + inputRepresentation + ": " + methodsRemaining);
                out.println("Fields remaining in " + inputRepresentation + ": " + fieldsRemaining);
                out.println("Classes remaining in " + inputRepresentation + ": " + classesRemaining);
            }

            return Unit.INSTANCE;
        });

        if (printOptions.getTeamCityIntegration() || (teamCitySlug != null && teamCitySlug.length() > 0)) {
            styleable.withStyledOutput(Color.DEFAULT, null, out -> {
                String slug = "Dexcount";
                if (teamCitySlug != null) {
                    slug += "_" + teamCitySlug.replace(' ', '_');
                }
                String prefix = slug + "_" + variantName;

                /*
                 * Reports to Team City statistic value
                 * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
                 */
                out.println(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "ClassCount", packageTree.getClassCount()));
                out.println(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "MethodCount", packageTree.getMethodCount()));
                out.println(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "FieldCount", packageTree.getFieldCount()));

                return Unit.INSTANCE;
            });
        }
    }

    private void printTaskDiagnosticData() {
        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        LogLevel level = verbose ? LogLevel.LIFECYCLE : LogLevel.DEBUG;

        styleable.withStyledOutput(Color.YELLOW, level, out -> {
            StringBuilder strBuilder = new StringBuilder();
            packageTree.print(strBuilder, format, printOptions);

            out.format(strBuilder.toString());

            return Unit.INSTANCE;
        });
    }

    private void failBuildMaxMethods() {
        if (maxMethodCount > 0 && packageTree.getMethodCount() > maxMethodCount) {
            String message = String.format("The current APK has %d methods, the current max is: %d.", packageTree.getMethodCount(), maxMethodCount);
            throw new GradleException(message);
        }
    }
}
