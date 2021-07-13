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

import com.getkeepsafe.dexcount.colors.Color;
import com.getkeepsafe.dexcount.colors.Styleable;
import org.gradle.api.GradleException;
import org.gradle.api.logging.LogLevel;

import java.io.IOException;

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
    private final PrintOptions options;
    private final String inputRepresentation;
    private final boolean isInstantRun;

    public CountReporter(
            PackageTree packageTree,
            String variantName,
            Styleable styleable,
            PrintOptions options,
            String inputRepresentation,
            boolean isInstantRun) {
        this.packageTree = packageTree;
        this.variantName = variantName;
        this.styleable = styleable;
        this.options = options;
        this.inputRepresentation = inputRepresentation;
        this.isInstantRun = isInstantRun;
    }

    public void report() throws IOException {
        try {
            printPreamble();
            printSummary();
            printTaskDiagnosticData();
            failBuildMaxMethods();
        } catch (DexCountException e) {
            styleable.withStyledOutput(Color.RED, LogLevel.ERROR, out -> {
                out.println("Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues");
                e.printStackTrace(out);
            });
        }
    }

    private void printPreamble() throws IOException {
        if (options.getPrintHeader()) {
            String projectName = getClass().getPackage().getImplementationTitle();
            String projectVersion = getClass().getPackage().getImplementationVersion();

            styleable.withStyledOutput(Color.DEFAULT, out -> {
                out.println("Dexcount name:    " + projectName);
                out.println("Dexcount version: " + projectVersion);
                out.println("Dexcount input:   " + inputRepresentation);
            });
        }
    }

    private String percentUsed(int count) {
        double used = ((double) count / MAX_DEX_REFS) * 100.0;
        return String.format("%.2f", used);
    }

    private void printSummary() throws IOException {
        if (isInstantRun) {
            styleable.withStyledOutput(Color.RED, out -> {
                out.println("Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.");
            });
        }

        Color color = packageTree.getMethodCount() < 50000 ? Color.GREEN : Color.YELLOW;

        styleable.withStyledOutput(color, out -> {
            String percentMethodsUsed = percentUsed(packageTree.getMethodCount());
            String percentFieldsUsed = percentUsed(packageTree.getFieldCount());
            String percentClassesUsed = percentUsed(packageTree.getClassCount());

            int methodsRemaining = Math.max(MAX_DEX_REFS - packageTree.getMethodCount(), 0);
            int fieldsRemaining = Math.max(MAX_DEX_REFS - packageTree.getFieldCount(), 0);
            int classesRemaining = Math.max(MAX_DEX_REFS - packageTree.getClassCount(), 0);

            int methodCount, fieldCount, classCount;
            if (options.isAndroidProject()) {
                methodCount = packageTree.getMethodCount();
                fieldCount = packageTree.getFieldCount();
                classCount = packageTree.getClassCount();
            } else {
                methodCount = packageTree.getMethodCountDeclared();
                fieldCount = packageTree.getFieldCountDeclared();
                classCount = packageTree.getClassCountDeclared();
            }

            out.println("Total methods in " + inputRepresentation + ": " + methodCount + " (" + percentMethodsUsed + "% used)");
            out.println("Total fields in " + inputRepresentation + ": " + fieldCount + " (" + percentFieldsUsed + "% used)");
            out.println("Total classes in " + inputRepresentation + ": " + classCount + " (" + percentClassesUsed + "% used)");

            if (options.isAndroidProject()) {
                out.println("Methods remaining in " + inputRepresentation + ": " + methodsRemaining);
                out.println("Fields remaining in " + inputRepresentation + ": " + fieldsRemaining);
                out.println("Classes remaining in " + inputRepresentation + ": " + classesRemaining);
            }
        });

        if (options.getTeamCityIntegration() || (options.getTeamCitySlug() != null && options.getTeamCitySlug().length() > 0)) {
            styleable.withStyledOutput(Color.DEFAULT, out -> {
                String slug = "Dexcount";
                if (options.getTeamCitySlug() != null) {
                    slug += "_" + options.getTeamCitySlug().replace(' ', '_');
                }
                String prefix = slug + "_" + variantName;

                /*
                 * Reports to Team City statistic value
                 * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
                 */
                out.println(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "ClassCount", packageTree.getClassCount()));
                out.println(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "MethodCount", packageTree.getMethodCount()));
                out.println(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "FieldCount", packageTree.getFieldCount()));
            });
        }
    }

    private void printTaskDiagnosticData() throws IOException {
        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        LogLevel level = options.isVerbose() ? null : LogLevel.DEBUG;

        styleable.withStyledOutput(Color.YELLOW, level, out -> {
            StringBuilder strBuilder = new StringBuilder();
            packageTree.print(strBuilder, options.getOutputFormat(), options);

            out.format(strBuilder.toString());
        });
    }

    private void failBuildMaxMethods() {
        if (options.getMaxMethodCount() > 0 && packageTree.getMethodCount() > options.getMaxMethodCount()) {
            String message = String.format("The current APK has %d methods, the current max is: %d.", packageTree.getMethodCount(), options.getMaxMethodCount());
            throw new GradleException(message);
        }
    }
}
