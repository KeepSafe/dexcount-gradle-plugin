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

import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

public class StyleableTaskAdapter implements Styleable {
    private final DefaultTask task;

    public StyleableTaskAdapter(DefaultTask task) {
        this.task = task;
    }

    @Override
    public void withStyledOutput(Color color, LogLevel level, IOConsumer<PrintWriter> fn) throws IOException {
        ServiceRegistryWrapper registry = getServices();
        StyledTextOutputFactoryWrapper styledTextOutputFactory = registry.getStyledTextOutputFactory();
        StyledTextOutputWrapper baseOutput = styledTextOutputFactory.create("dexcount", level);
        StyledTextOutputWrapper styledOutput = baseOutput.withStyle(color.getStyle());
        try (PrintWriter pw = styledOutput.asPrintWriter()) {
            fn.accept(pw);
        }
    }

    private ServiceRegistryWrapper getServices() {
        Object registry;
        try {
            registry = Reflect.DefaultTask_getServices.invoke(task);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        return new ServiceRegistryWrapper(registry);
    }
}
