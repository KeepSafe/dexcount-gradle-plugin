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
package com.getkeepsafe.dexcount.treegen.workers;

import com.getkeepsafe.dexcount.Deobfuscator;
import com.getkeepsafe.dexcount.DexCountException;
import org.gradle.api.file.RegularFileProperty;

import java.io.IOException;

public abstract class ModernWorker<P extends ModernWorker.Params> extends BaseWorker<P> {
    public interface Params extends BaseWorker.Params {
        RegularFileProperty getMappingFile();
    }

    private Deobfuscator deobfuscator;

    protected Deobfuscator getDeobfuscator() {
        if (deobfuscator == null) {
            deobfuscator = getParameters()
                .getMappingFile()
                .map(it -> {
                try {
                    return Deobfuscator.create(it.getAsFile());
                } catch (IOException e) {
                    throw new DexCountException("Counting dex methods failed", e);
                }
            }).getOrElse(Deobfuscator.EMPTY);
        }
        return deobfuscator;
    }
}
