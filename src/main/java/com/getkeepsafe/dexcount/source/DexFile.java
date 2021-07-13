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

import com.android.dexdeps.DexData;
import com.android.dexdeps.DexDataException;
import com.android.dexdeps.FieldRef;
import com.android.dexdeps.MethodRef;
import com.getkeepsafe.dexcount.DexCountException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

class DexFile implements SourceFile {
    private final File file;
    private final boolean isTemp;

    private final RandomAccessFile raf;
    private final DexData data;

    DexFile(File file, boolean isTemp) throws IOException {
        this.file = file;
        this.isTemp = isTemp;

        this.raf = new RandomAccessFile(file, "r");
        this.data = new DexData(raf);

        try {
            data.load();
        } catch (IOException | DexDataException e) {
            throw new DexCountException("Error loading dex file", e);
        }
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(raf);
        if (isTemp) {
            FileUtils.deleteQuietly(file);
        }
    }

    @Override
    public List<MethodRef> getMethodRefs() {
        return Arrays.asList(data.getMethodRefs());
    }

    @Override
    public List<FieldRef> getFieldRefs() {
        return Arrays.asList(data.getFieldRefs());
    }
}
