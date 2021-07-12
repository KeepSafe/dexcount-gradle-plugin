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
package com.getkeepsafe.dexcount.colors;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;

class AppendableWriterAdapter extends Writer {
    private final Appendable out;

    AppendableWriterAdapter(Appendable out) {
        this.out = out;
    }

    @Override
    public void write(@NotNull char[] chars) throws IOException {
        out.append(new String(chars));
    }

    @Override
    public void write(@NotNull String str) throws IOException {
        out.append(str);
    }

    @Override
    public void write(@NotNull String str, int off, int len) throws IOException {
        out.append(CharBuffer.wrap(str, off, len));
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        out.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        out.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        out.append(c);
        return this;
    }

    @Override
    public void write(@NotNull char[] chars, int length, int offset) throws IOException {
        out.append(CharBuffer.wrap(chars, length, offset));
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
