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
        @JvmStatic
        fun create(mappingFile: File?): Deobfuscator {
            if (mappingFile == null) {
                return empty
            }

            if (!mappingFile.exists()) {
                return empty
            }

            val mapping = mappingFile.useLines(Charsets.UTF_8) { lines ->
                // This is a little dense.  We're reading the mapping file line-by-line, filtering out
                // everything except those lines that map a class to its obfuscated name.  Other line types
                // that we filter are:
                // - blank lines
                // - comments (those beginning with '#')
                // - method and field mappings (beginning with whitespace)
                //
                // Class mapping lines always have the form "com.foo.Bar -> a.b.c:", even when the class
                // is empty.
                lines.filter { it.isNotBlank() }
                    .filterNot { it.startsWith("#") }
                    .filterNot { it.matches(Regex("^\\s+.*")) }
                    .map { it.split(" -> ").map { parts -> parts.removeSuffix(":") } }
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
