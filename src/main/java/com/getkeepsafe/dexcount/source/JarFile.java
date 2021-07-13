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
package com.getkeepsafe.dexcount.source;

import com.android.dexdeps.FieldRef;
import com.android.dexdeps.MethodRef;

import java.util.List;

class JarFile implements SourceFile {
    private final List<MethodRef> methodRefs;
    private final List<FieldRef> fieldRefs;

    JarFile(List<MethodRef> methodRefs, List<FieldRef> fieldRefs) {
        this.methodRefs = methodRefs;
        this.fieldRefs = fieldRefs;
    }

    @Override
    public List<MethodRef> getMethodRefs() {
        return methodRefs;
    }

    @Override
    public List<FieldRef> getFieldRefs() {
        return fieldRefs;
    }

    @Override
    public void close() {
        // no-op
    }
}
