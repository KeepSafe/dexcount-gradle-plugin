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

import java.lang.reflect.InvocationTargetException;

class ServiceRegistryWrapper {
    private final Object registry;

    ServiceRegistryWrapper(Object registry) {
        this.registry = registry;
    }

    StyledTextOutputFactoryWrapper getStyledTextOutputFactory() {
        Object factory;
        try {
            factory = Reflect.ServiceRegistry_get.invoke(registry, Reflect.StyledTextOutputFactory_class);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return new StyledTextOutputFactoryWrapper(factory);
    }
}
