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

import java.io.File

/**
 * An object that can produce an unobfuscated class name from a Proguard
 * mapping file.
 */
open class Deobfuscator(
    private val mapping: Map<String, String>,
) {
    open fun deobfuscate(name: String): String {
        return mapping[name] ?: name
    }

    companion object {
        /**
         * Proguard mapping files have the following syntax:
         *
         * ```
         * line : comment | class_mapping | member_mapping
         * comment: '#' ...
         * class_mapping: type_name ' -> ' obfuscated_name ':'
         * member_mapping: '    ' type_name ' ' member_name ' -> ' obfuscated_name
         * ```
         *
         * Class mapping lines are easily distinguished because they're the only
         * lines that start with an identifier character.  We can just pluck them
         * out of the file with a regex.
         */
        private val CLASS_LINE = Regex("^([a-zA-Z][^\\s]*) -> ([^:]+):$")

        @JvmStatic
        fun create(mappingFile: File?): Deobfuscator {
            if (mappingFile == null) {
                return empty
            }

            if (!mappingFile.exists()) {
                return empty
            }

            val mapping = mappingFile.useLines(Charsets.UTF_8) { lines ->
                lines
                    .mapNotNull { CLASS_LINE.matchEntire(it)?.destructured }
                    .map { (cleartext, obfuscated) -> obfuscated to cleartext }
                    .toMap()
            }

            return Deobfuscator(mapping)
        }

        /**
         * An always-empty deobfuscator that doesn't need to look things up.
         */
        val empty: Deobfuscator = object : Deobfuscator(emptyMap()) {
            override fun deobfuscate(name: String): String {
                return name
            }
        }
    }
}
