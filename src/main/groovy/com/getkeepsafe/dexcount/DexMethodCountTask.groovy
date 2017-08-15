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

import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.ParallelizableTask

@ParallelizableTask
class ModernMethodCountTask extends DexMethodCountTaskBase {
    /**
     * The output directory of the 'package' task; will contain an
     * APK or an AAR.  Will be null for Android projects using
     * build-tools versions before 3.0.
     */
    @InputDirectory
    File inputDirectory

    @Override
    File getFileToCount() {
        def fileList = inputDirectory.listFiles(new ApkFilenameFilter())
        return fileList.length > 0 ? fileList[0] : null
    }

    @Override
    String getRawInputRepresentation() {
        return "$inputDirectory"
    }

    // Tried to use a closure for this, but Groovy cannot decide between java.io.FilenameFilter
    // and java.io.FileFilter.  If we have to make it ugly, might as well make it efficient.
    static class ApkFilenameFilter implements FilenameFilter {
        @Override
        boolean accept(File dir, String name) {
            return name != null && name.endsWith(".apk")
        }
    }
}

@ParallelizableTask
class LegacyMethodCountTask extends DexMethodCountTaskBase {

    BaseVariantOutput variantOutput

    @Override
    File getFileToCount() {
        return variantOutput.outputFile
    }

    @Override
    String getRawInputRepresentation() {
        if (variantOutput == null) {
            return "variantOutput=null"
        } else {
            return "variantOutput{name=${variantOutput.name} outputFile=${variantOutput.outputFile}}"
        }
    }
}
