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

import proguard.obfuscate.MappingProcessor
import proguard.obfuscate.MappingReader
import java.io.File
import java.util.TreeMap

/**
 * An object that can produce an unobfuscated class name from a Proguard
 * mapping file.
 */
open class Deobfuscator {
    val mapping: MutableMap<String, String>

    constructor(reader: MappingReader?) {
        mapping = TreeMap<String, String>()
        reader?.pump(Processor())
    }

    open fun deobfuscate(name: String): String {
        return mapping[name] ?: name
    }

    /**
     * A Proguard MappingProcessor that builds a map from obfuscated to unobfuscated
     * class names.
     */
    inner class Processor : MappingProcessor {
        override fun processClassMapping(className: String?, newClassName: String?): Boolean {
            mapping.put(newClassName!!, className!!)
            return false
        }

        override fun processMethodMapping(className: String?, firstLineNumber: Int, lastLineNumber: Int, methodReturnType: String?, methodName: String?, methodArguments: String?, newClassName: String?, newFirstLineNumber: Int, newLastLineNumber: Int, newMethodName: String?) {
            // nothing
        }

        override fun processFieldMapping(className: String?, fieldType: String?, fieldName: String?, newClassName: String?, newFieldName: String?) {
            // nothing
        }
    }

    class EmptyDeobfuscator : Deobfuscator(null) {
        override fun deobfuscate(name: String): String {
            return name
        }
    }

    companion object {
        @JvmStatic fun create(mappingFile: File?): Deobfuscator {
            if (mappingFile == null) {
                return EMPTY
            }

            return Deobfuscator(MappingReader(mappingFile))
        }

        /**
         * An always-empty deobfuscator that doesn't need to look things up.
         */
        @JvmStatic val EMPTY: Deobfuscator = EmptyDeobfuscator()
    }
}
