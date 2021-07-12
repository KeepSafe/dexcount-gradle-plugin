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

import org.gradle.api.logging.LogLevel;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * A thing that can print output, styled based on a given {@link Color} and {@link LogLevel}.
 */
public interface Styleable {
    default void withStyledOutput(Color color, IOConsumer<PrintWriter> fn) throws IOException {
        withStyledOutput(color, null, fn);
    }

    default void withStyledOutput(LogLevel level, IOConsumer<PrintWriter> fn) throws IOException {
        withStyledOutput(Color.DEFAULT, level, fn);
    }

    default void withStyledOutput(IOConsumer<PrintWriter> fn) throws IOException {
        withStyledOutput(Color.DEFAULT, null, fn);
    }

    void withStyledOutput(Color color, LogLevel level, IOConsumer<PrintWriter> fn) throws IOException;
}
