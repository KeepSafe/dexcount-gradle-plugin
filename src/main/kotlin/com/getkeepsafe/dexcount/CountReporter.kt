package com.getkeepsafe.dexcount

import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import kotlin.math.max

/**
 * An object that can produce formatted output from a [PackageTree] instance.
 *
 * @property packageTree the tree containing the method, field, and class counts to be reported.
 * @property variantName the name of the variant being counted.
 * @param styleable a [Styleable] instance with which to print "interactive" reports.
 * @property config the current dexcount configuration
 * @property inputRepresentation a string describing the input from which [packageTree] was generated.
 * @property isAndroidProject true if the input is an APK, AAR, or other Android-related artifact.
 * @property isInstantRun true if the legacy "Instant Run" feature was used to build the input.
 */
class CountReporter(
    val packageTree: PackageTree,
    val variantName: String,
    styleable: Styleable,
    val config: DexCountExtension,
    val inputRepresentation: String,
    val isAndroidProject: Boolean = true,
    val isInstantRun: Boolean = false
) : Styleable by styleable {
    private val options = config.toPrintOptions(isAndroidProject)

    private val enabled = config.enabled.get()
    private val verbose = config.verbose.get()
    private val format = config.format.get()
    private val printVersion = config.printVersion.get()
    private val maxMethodCount = config.maxMethodCount.get()

    fun report() {
        try {
            check(enabled) { "Tasks should not be executed if the plugin is disabled" }

            printPreamble()
            printSummary()
            printTaskDiagnosticData()
            failBuildMaxMethods()
        } catch (e: DexCountException) {
            withStyledOutput(Color.RED, LogLevel.ERROR) { out ->
                out.println("Error counting dex methods. Please contact the developer at https://github.com/KeepSafe/dexcount-gradle-plugin/issues")
                e.printStackTrace(out)
            }
        }
    }

    private fun printPreamble() {
        if (printVersion) {
            val projectName = javaClass.`package`.implementationTitle
            val projectVersion = javaClass.`package`.implementationVersion

            withStyledOutput { out ->
                out.println("Dexcount name:    $projectName")
                out.println("Dexcount version: $projectVersion")
                out.println("Dexcount input:   $inputRepresentation")
            }
        }
    }

    private fun printSummary() {
        if (isInstantRun) {
            withStyledOutput(Color.RED) { out ->
                out.println("Warning: Instant Run build detected!  Instant Run does not run Proguard; method counts may be inaccurate.")
            }
        }

        val color = if (packageTree.methodCount < 50000) Color.GREEN else Color.YELLOW

        withStyledOutput(color) { out ->
            fun percentUsed(count: Int): String {
                val used = (count.toDouble() / MAX_DEX_REFS) * 100.0
                return String.format("%.2f", used)
            }

            val percentMethodsUsed = percentUsed(packageTree.methodCount)
            val percentFieldsUsed = percentUsed(packageTree.fieldCount)
            val percentClassesUsed = percentUsed(packageTree.classCount)

            val methodsRemaining = max(MAX_DEX_REFS - packageTree.methodCount, 0)
            val fieldsRemaining = max(MAX_DEX_REFS - packageTree.fieldCount, 0)
            val classesRemaining = max(MAX_DEX_REFS - packageTree.classCount, 0)

            val (methodCount, fieldCount, classCount) = when {
                isAndroidProject -> listOf(packageTree.methodCount, packageTree.fieldCount, packageTree.classCount)
                else -> listOf(packageTree.methodCountDeclared, packageTree.fieldCountDeclared, packageTree.classCountDeclared)
            }

            out.println("Total methods in $inputRepresentation: $methodCount ($percentMethodsUsed% used)")
            out.println("Total fields in $inputRepresentation:  $fieldCount ($percentFieldsUsed% used)")
            out.println("Total classes in $inputRepresentation:  $classCount ($percentClassesUsed% used)")

            if (isAndroidProject) {
                out.println("Methods remaining in $inputRepresentation: $methodsRemaining")
                out.println("Fields remaining in $inputRepresentation:  $fieldsRemaining")
                out.println("Classes remaining in $inputRepresentation:  $classesRemaining")
            }
        }

        if (options.teamCityIntegration || config.teamCitySlug.isPresent) {
            withStyledOutput { out ->
                val configuredSlug = config.teamCitySlug.getOrElse("")
                val slug = "Dexcount" + when (configuredSlug) {
                    null -> ""
                    else -> "_" + configuredSlug.replace(' ', '_')
                }
                val prefix = "${slug}_${variantName}"

                /**
                 * Reports to Team City statistic value
                 * Doc: https://confluence.jetbrains.com/display/TCD9/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
                 */
                fun printTeamCityStatisticValue(key: String, value: Int) {
                    out.println("##teamcity[buildStatisticValue key='${prefix}_${key}' value='$value']")
                }

                printTeamCityStatisticValue("ClassCount", packageTree.classCount)
                printTeamCityStatisticValue("MethodCount", packageTree.methodCount)
                printTeamCityStatisticValue("FieldCount", packageTree.fieldCount)
            }
        }
    }

    private fun printTaskDiagnosticData() {
        // Log the entire package list/tree at LogLevel.DEBUG, unless
        // verbose is enabled (in which case use the default log level).
        val level = if (verbose) LogLevel.LIFECYCLE else LogLevel.DEBUG

        withStyledOutput(Color.YELLOW, level) { out ->
            val strBuilder = StringBuilder()
            packageTree.print(strBuilder, format, options)

            out.format(strBuilder.toString())
        }
    }

    private fun failBuildMaxMethods() {
        if (maxMethodCount > 0 && packageTree.methodCount > maxMethodCount) {
            throw GradleException("The current APK has ${packageTree.methodCount} methods, the current max is: ${maxMethodCount}.")
        }
    }

    companion object {
        /**
         * The maximum number of method refs and field refs allowed in a single Dex
         * file.
         */
        const val MAX_DEX_REFS: Int = 0xFFFF // 65535
    }
}
