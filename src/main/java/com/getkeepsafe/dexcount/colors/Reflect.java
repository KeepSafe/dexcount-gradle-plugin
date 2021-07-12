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

import java.lang.reflect.Method;

@SuppressWarnings({"unchecked", "JavaReflectionMemberAccess"})
class Reflect {
    static final Class<?> ServiceRegistry_class;
    static final Method ServiceRegistry_get;

    static final Class<?> StyledTextOutputFactory_class;
    static final Method StyledTextOutputFactory_create;
    static final Method StyledTextOutputFactory_createWithLevel;

    static final Class<?> StyledTextOutput_class;
    static final Method StyledTextOutput_withStyle;

    static final Class<Enum<?>> StyledTextOutputStyle_class;

    static final Method DefaultTask_getServices;

    private Reflect() {
        // no instances
    }

    static {
        try {
            ServiceRegistry_class = Class.forName("org.gradle.internal.service.ServiceRegistry");
            StyledTextOutputFactory_class = Class.forName("org.gradle.internal.logging.text.StyledTextOutputFactory");
            StyledTextOutput_class = Class.forName("org.gradle.internal.logging.text.StyledTextOutput");
            StyledTextOutputStyle_class = (Class<Enum<?>>) Class.forName("org.gradle.internal.logging.text.StyledTextOutput$Style");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            ServiceRegistry_get = ServiceRegistry_class.getDeclaredMethod("get", Class.class);
            ServiceRegistry_get.setAccessible(true);

            StyledTextOutputFactory_create = StyledTextOutputFactory_class.getDeclaredMethod("create", String.class);
            StyledTextOutputFactory_create.setAccessible(true);
            StyledTextOutputFactory_createWithLevel = StyledTextOutputFactory_class.getDeclaredMethod("create", String.class, LogLevel.class);
            StyledTextOutputFactory_createWithLevel.setAccessible(true);

            StyledTextOutput_withStyle = StyledTextOutput_class.getDeclaredMethod("withStyle", StyledTextOutputStyle_class);
            StyledTextOutput_withStyle.setAccessible(true);

            Class<?> clazz = DefaultTask.class;
            Method m = null;
            while (clazz != null && clazz != Object.class) {
                try {
                    m = clazz.getDeclaredMethod("getServices");
                    m.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }

            if (m == null) {
                throw new RuntimeException("No getServices method found on DefaultTask");
            }

            DefaultTask_getServices = m;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
