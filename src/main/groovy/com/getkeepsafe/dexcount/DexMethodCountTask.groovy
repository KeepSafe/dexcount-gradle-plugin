/*
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

import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

class DexMethodCountTask extends DefaultTask {
    private BaseVariantOutput output

    void output(BaseVariantOutput output) {
        this.output = output;
    }

    @TaskAction
    void countMethods() {
        def counter = MethodCounter.count(output.outputFile)
        def count = counter.getTotalCount()

        String color
        if (count > 60000) {
            color = 'RED'
        } else if (count > 40000) {
            color = 'YELLOW'
        } else {
            color = 'GREEN'
        }

        withColor(StyledTextOutput.Style.Info, color) { out ->
            out.println("Total methods in ${output.outputFile.name}: ${count}")
        }
    }

    void withColor(StyledTextOutput.Style style, String color, Closure<StyledTextOutput> closure) {
        def prop = "org.gradle.color.${style.name().toLowerCase()}"
        def oldValue = System.getProperty(prop)

        System.setProperty(prop, color)
        try {
            def sto = services.get(StyledTextOutputFactory)
                    .create("dexcount")
                    .withStyle(style)

            closure(sto)
        } finally {
            if (oldValue != null) {
                System.setProperty(prop, oldValue)
            } else {
                System.clearProperty(prop)
            }
        }
    }
}
