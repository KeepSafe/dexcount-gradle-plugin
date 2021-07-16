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
package com.getkeepsafe.dexcount.plugin;

import com.getkeepsafe.dexcount.DexCountException;
import com.getkeepsafe.dexcount.DexCountExtension;
import com.getkeepsafe.dexcount.report.DexCountOutputTask;
import com.getkeepsafe.dexcount.treegen.BaseGeneratePackageTreeTask;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

abstract class AbstractTaskApplicator implements TaskApplicator {
    private final Project project;
    private final DexCountExtension ext;
    private Configuration workerConfiguration;

    protected AbstractTaskApplicator(Project project, DexCountExtension ext) {
        this.project = project;
        this.ext = ext;
    }

    protected Project getProject() {
        return project;
    }

    protected DexCountExtension getExt() {
        return ext;
    }

    protected Configuration getWorkerConfiguration() {
        if (workerConfiguration == null) {
            try {
                workerConfiguration = makeConfiguration();
            } catch (IOException e) {
                throw new DexCountException("Configuring dexcount plugin failed", e);
            }
        }
        return workerConfiguration;
    }

    private Configuration makeConfiguration() throws IOException {
        List<String> lines;
        try (InputStream deps = getClass().getClassLoader().getResourceAsStream("dependencies.list")) {
            if (deps == null) {
                throw new IllegalStateException("Missing dependencies.list resource");
            }
            lines = IOUtils.readLines(deps, StandardCharsets.UTF_8);
        }

        Configuration c = getProject().getConfigurations().create("dexcountWorker")
            .setDescription("configuration for dexcount-gradle-plugin")
            .setVisible(false)
            .setTransitive(true);

        c.defaultDependencies(dependencies -> lines.stream()
            .map(line -> getProject().getDependencies().create(line))
            .forEach(dependencies::add));

        c.attributes(attrs ->
            attrs.attribute(Usage.USAGE_ATTRIBUTE, getProject().getObjects().named(Usage.class, Usage.JAVA_RUNTIME)));

        return c;
    }

    @SuppressWarnings("Convert2MethodRef")
    protected void registerOutputTask(
        TaskProvider<? extends BaseGeneratePackageTreeTask<?, ?>> treegenTask,
        String variantName,
        boolean isAndroid) {
        String reportTaskName = String.format("count%sDexMethods", StringUtils.capitalize(variantName));

        getProject().getTasks().register(reportTaskName, DexCountOutputTask.class, t -> {
            t.setDescription("Output dex method counts");
            t.setGroup("Reporting");

            t.getConfigProperty().set(getExt());
            t.getVariantNameProperty().set(variantName);
            t.getPackageTreeFileProperty().set(treegenTask.flatMap(it -> it.getPackageTreeFileProperty()));
            t.getAndroidProject().set(isAndroid);
            t.getWorkerClasspath().from(getWorkerConfiguration());
        });
    }

    protected static Method getMethod(Class<?> clazz, String name, Class<?>... args) throws NoSuchMethodException {
        Method method = clazz.getMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    protected static Enum<?> getEnumConstant(Class<?> clazz, String name) {
        for (Object constant : clazz.getEnumConstants()) {
            Enum<?> typedConstant = (Enum<?>) constant;
            if (name.equals(typedConstant.name())){
                return typedConstant;
            }
        }
        throw new EnumConstantNotPresentException((Class<? extends Enum<?>>) clazz, name);
    }
}
