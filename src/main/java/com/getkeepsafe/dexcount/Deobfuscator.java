/*
 * Copyright (C) 2015-2021 KeepSafe Software
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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An object that can produce an unobfuscated class name from a Proguard
 * mapping file.
 */
public class Deobfuscator {
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
    private static final Pattern CLASS_LINE = Pattern.compile("^([a-zA-Z][^\\s]*) -> ([^:]+):$");

    public static final Deobfuscator EMPTY = new Deobfuscator(Collections.emptyMap());

    private final Map<String, String> mapping;

    public Deobfuscator(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public String deobfuscate(String name) {
        return mapping.getOrDefault(name, name);
    }

    public static Deobfuscator create(File mappingFile) throws IOException {
        if (mappingFile == null) {
            return EMPTY;
        }

        if (!mappingFile.exists()) {
            return EMPTY;
        }

        Map<String, String> mapping = new LinkedHashMap<>();

        List<String> lines = FileUtils.readLines(mappingFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            Matcher matcher = CLASS_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            String cleartext = matcher.group(1);
            String obfuscated = matcher.group(2);
            mapping.put(obfuscated, cleartext);
        }

        return new Deobfuscator(mapping);
    }
}
