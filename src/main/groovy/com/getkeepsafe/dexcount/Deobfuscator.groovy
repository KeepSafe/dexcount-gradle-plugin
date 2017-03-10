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

import com.android.annotations.Nullable
import proguard.obfuscate.MappingProcessor
import proguard.obfuscate.MappingReader

/**
 * An object that can produce an unobfuscated class name from a Proguard
 * mapping file.
 */
class Deobfuscator {
    final Map<String, String> mapping

    static Deobfuscator create(@Nullable File mappingFile) {
        if (mappingFile == null) {
            return EMPTY
        }

        return new Deobfuscator(new MappingReader(mappingFile))
    }

    Deobfuscator(@Nullable MappingReader reader) {
        mapping = new TreeMap<>()
        if (reader != null) {
            reader.pump(new Processor())
        }
    }

    String deobfuscate(String name) {
        return mapping[name] ?: name
    }
    /**
     * A Proguard MappingProcessor that builds a map from obfuscated to unobfuscated
     * class names.
     */
    final class Processor implements MappingProcessor {
        @Override
        boolean processClassMapping(String className, String newClassName) {
            mapping.put(newClassName, className)
            return false
        }

        @Override
        void processMethodMapping(String className, int firstLineNumber, int lastLineNumber, String methodReturnType, String methodName, String methodArguments, String newClassName, int newFirstLineNumber, int newLastLineNumber, String newMethodName) {
            // nothing
        }

        @Override
        void processFieldMapping(String className, String fieldType, String fieldName, String newClassName, String newFieldName) {
            // nothing
        }
    }

    /**
     * An always-empty deobfuscator that doesn't need to look things up.
     */
    static Deobfuscator EMPTY = new Deobfuscator(null) {
        @Override
        String deobfuscate(String name) {
            return name
        }
    }
}
