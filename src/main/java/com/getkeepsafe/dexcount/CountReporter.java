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

import org.gradle.api.GradleException;
import org.slf4j.Logger;

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
    private final Logger logger;
    private final PrintOptions options;
    private final String inputRepresentation;
    private final boolean isInstantRun;

    public CountReporter(
            PackageTree packageTree,
            String variantName,
            Logger logger,
            PrintOptions options,
            String inputRepresentation,
            boolean isInstantRun) {
        this.packageTree = packageTree;
        this.variantName = variantName;
        this.logger = logger;
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
            logger.error("Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues", e);
        }
    }

    private void printPreamble() {
        if (options.getPrintHeader()) {
            String projectName = getClass().getPackage().getImplementationTitle();
            String projectVersion = getClass().getPackage().getImplementationVersion();

            logger.warn("Dexcount name:    {}", projectName);
            logger.warn("Dexcount version: {}", projectVersion);
            logger.warn("Dexcount input:   {}", inputRepresentation);
        }
    }

    private String percentUsed(int count) {
        double used = ((double) count / MAX_DEX_REFS) * 100.0;
        return String.format("%.2f", used);
    }

    private void printSummary() {
        if (isInstantRun) {
            logger.warn("Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.");
        }

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

        logger.warn("Total methods in " + inputRepresentation + ": " + methodCount + " (" + percentMethodsUsed + "% used)");
        logger.warn("Total fields in " + inputRepresentation + ": " + fieldCount + " (" + percentFieldsUsed + "% used)");
        logger.warn("Total classes in " + inputRepresentation + ": " + classCount + " (" + percentClassesUsed + "% used)");

        if (options.isAndroidProject()) {
            logger.warn("Methods remaining in " + inputRepresentation + ": " + methodsRemaining);
            logger.warn("Fields remaining in " + inputRepresentation + ": " + fieldsRemaining);
            logger.warn("Classes remaining in " + inputRepresentation + ": " + classesRemaining);
        }

        if (options.getTeamCityIntegration() || (options.getTeamCitySlug() != null && options.getTeamCitySlug().length() > 0)) {
            String slug = "Dexcount";
            if (options.getTeamCitySlug() != null) {
                slug += "_" + options.getTeamCitySlug().replace(' ', '_');
            }
            String prefix = slug + "_" + variantName;

            /*
             * Reports to Team City statistic value
             * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
             */
            logger.warn(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "ClassCount", packageTree.getClassCount()));
            logger.warn(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "MethodCount", packageTree.getMethodCount()));
            logger.warn(String.format("##teamcity[buildStatisticValue key='%s_%s' value='%d']", prefix, "FieldCount", packageTree.getFieldCount()));
        }
    }

    private void printTaskDiagnosticData() throws IOException {
        StringBuilder strBuilder = new StringBuilder();
        packageTree.print(strBuilder, options.getOutputFormat(), options);

        if (options.isVerbose()) {
            logger.warn(strBuilder.toString());
        } else {
            logger.info(strBuilder.toString());
        }
    }

    private void failBuildMaxMethods() {
        if (options.getMaxMethodCount() > 0 && packageTree.getMethodCount() > options.getMaxMethodCount()) {
            String message = String.format("The current APK has %d methods, the current max is: %d.", packageTree.getMethodCount(), options.getMaxMethodCount());
            throw new GradleException(message);
        }
    }
}
