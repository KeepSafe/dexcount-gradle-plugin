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
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.getkeepsafe.dexcount.DexCountException;
import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class SourceFiles {
    private static final Pattern CLASSES_DEX = Pattern.compile("(.*/)*classes.*\\.dex");
    private static final Pattern CLASSES_JAR = Pattern.compile("(.*/)*classes\\.jar");
    private static final Pattern MIN_SDK_VERSION = Pattern.compile("android:minSdkVersion=\"(\\d+)\"");

    private SourceFiles() {
        // no instances
    }

    public static List<SourceFile> extractDexData(File file) throws IOException {
        if (file == null || !file.exists()) {
            return Collections.emptyList();
        }

        // AAR files need special treatment
        if (file.getName().endsWith(".aar")) {
            return extractDexFromAar(file);
        }

        try {
            return extractDexFromZip(file);
        } catch (ZipException ignored) {
            // not a zip, no problem
        }

        return Collections.singletonList(new DexFile(file, false));
    }

    private static List<SourceFile> extractDexFromAar(File file) throws IOException {
        int minSdk = 13;
        File tempClasses = null;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if ("AndroidManifest.xml".equals(entry.getName())) {
                    String text;
                    try (InputStream is = zip.getInputStream(entry)) {
                        text = IOUtils.toString(is, StandardCharsets.UTF_8);
                    }

                    Matcher matcher = MIN_SDK_VERSION.matcher(text);
                    if (!matcher.find()) {
                        continue;
                    }

                    minSdk = Integer.parseInt(matcher.group(1));
                }

                if (CLASSES_JAR.matcher(entry.getName()).matches()) {
                    tempClasses = makeTemp(entry.getName());
                    try (InputStream is = zip.getInputStream(entry)) {
                        FileUtils.copyInputStreamToFile(is, tempClasses);
                    }
                }
            }
        }

        if (tempClasses == null) {
            throw new IllegalArgumentException("No classes.jar entry found in " + file.getCanonicalPath());
        }

        Path tempDexDir = Files.createTempDirectory("dex");
        tempDexDir.toFile().deleteOnExit();

        try {
            D8Command command = D8Command.builder()
                .addProgramFiles(tempClasses.toPath())
                .setMinApiLevel(minSdk)
                .setOutput(tempDexDir, OutputMode.DexIndexed)
                .build();

            D8.run(command);
        } catch (CompilationFailedException e) {
            throw new DexCountException("Failed to run D8 on an AAR", e);
        }

        List<SourceFile> results = new ArrayList<>();
        for (Path path : Files.list(tempDexDir).collect(Collectors.toList())) {
            if (!Files.isRegularFile(path)) {
                continue;
            }

            results.add(new DexFile(path.toFile(), true));
        }

        return results;
    }

    private static List<SourceFile> extractDexFromZip(File file) throws IOException {
        List<SourceFile> results = new ArrayList<>();

        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!CLASSES_DEX.matcher(entry.getName()).matches()) {
                    continue;
                }

                File temp = makeTemp(entry.getName());
                try (InputStream is = zip.getInputStream(entry)) {
                    FileUtils.copyInputStreamToFile(is, temp);
                }

                results.add(new DexFile(temp, true));
            }
        }

        return results;
    }

    public static SourceFile extractJarFromAar(File aar) throws IOException {
        File tempClassesJar = null;
        try (ZipFile zip = new ZipFile(aar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (!CLASSES_JAR.matcher(entry.getName()).matches()) {
                    continue;
                }

                tempClassesJar = makeTemp(entry.getName());
                try (InputStream is = zip.getInputStream(entry)) {
                    FileUtils.copyInputStreamToFile(is, tempClassesJar);
                }
            }
        }

        if (tempClassesJar == null) {
            throw new IllegalArgumentException("No classes.jar entry found in " + aar.getCanonicalPath());
        }

        return extractJarFromJar(tempClassesJar);
    }

    public static SourceFile extractJarFromJar(File jar) throws IOException {
        // Unzip the classes.jar file and store all .class files in this directory.
        File classFilesDir = Files.createTempDirectory("classFilesDir").toFile();
        classFilesDir.deleteOnExit();

        final Path classFilesPath = classFilesDir.toPath();

        try (ZipFile zip = new ZipFile(jar)) {
            zip.stream().filter(it -> it.getName().endsWith(".class")).forEach(entry -> {
                String fileName = entry.getName();
                File file = new File(classFilesDir, fileName);

                try {
                    FileUtils.createParentDirectories(file);
                    try (InputStream is = zip.getInputStream(entry)) {
                        FileUtils.copyInputStreamToFile(is, file);
                    }
                } catch (IOException e) {
                    throw new DexCountException("Failed to unzip a classes.jar file");
                }
            });
        }

        ClassPool classPool = new ClassPool();
        classPool.appendSystemPath();

        List<CtClass> classes = new ArrayList<>();
        Files.walkFileTree(classFilesDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }

                String qualifiedClassName = classFilesPath.relativize(file)
                    .toFile()
                    .getPath()
                    .replace('/', '.')
                    .replace(".class", "");

                ByteArrayClassPath cp = new ByteArrayClassPath(qualifiedClassName, Files.readAllBytes(file));
                classPool.appendClassPath(cp);

                try {
                    classes.add(classPool.get(qualifiedClassName));
                } catch (NotFoundException e) {
                    throw new AssertionError("We literally just added this class to the pool", e);
                }

                return FileVisitResult.CONTINUE;
            }
        });

        List<MethodRef> methodRefs = classes.stream().flatMap(SourceFiles::extractMethodRefs).collect(Collectors.toList());
        List<FieldRef> fieldRefs = classes.stream().flatMap(SourceFiles::extractFieldRefs).collect(Collectors.toList());

        return new JarFile(methodRefs, fieldRefs);
    }

    private static Stream<MethodRef> extractMethodRefs(CtClass clazz) {
        String declaringClass = "L" + clazz.getName().replace(".", "/") + ";";

        // Unfortunately, it's necessary to parse the types from the strings manually.
        // We can't use the proper API because this requires all classes that are used
        // in parameters and return types to be loaded in the classpath. However,
        // that's not the case when we analyze a single jar file.
        List<MethodRef> results = new ArrayList<>();
        if (clazz.getClassInitializer() != null) {
            results.add(new MethodRef(declaringClass, new String[0], "V", "<clinit>"));
        }

        for (CtConstructor ctor : clazz.getDeclaredConstructors()) {
            String[] params = parseBehaviorParameters(ctor);
            results.add(new MethodRef(declaringClass, params, "V", "<init>"));
        }

        for (CtMethod method : clazz.getDeclaredMethods()) {
            String[] params = parseBehaviorParameters(method);
            String returnType = parseMethodReturnType(method);
            results.add(new MethodRef(declaringClass, params, returnType, method.getName()));
        }

        return results.stream();
    }

    private static Stream<FieldRef> extractFieldRefs(CtClass clazz) {
        return Arrays.stream(clazz.getDeclaredFields()).map(field -> {
            String type = field.getFieldInfo().getDescriptor();
            return new FieldRef(clazz.getSimpleName(), type, field.getName());
        });
    }

    private static String[] parseBehaviorParameters(CtBehavior behavior) {
        String signature = behavior.getSignature();
        int startIx = signature.indexOf('(');
        int endIx = signature.indexOf(')', startIx);
        String parameters = signature.substring(startIx + 1, endIx);
        return parameters.split(";");
    }

    private static String parseMethodReturnType(CtMethod method) {
        int ix = method.getSignature().indexOf(')');
        return method.getSignature().substring(ix + 1);
    }

    private static File makeTemp(String pattern) {
        int ix = pattern.indexOf('.');
        String prefix = pattern.substring(0, ix);
        String suffix = pattern.substring(ix);
        try {
            File temp = File.createTempFile(prefix, suffix);
            temp.deleteOnExit();
            return temp;
        } catch (IOException e) {
            throw new DexCountException("Failed to create temp file", e);
        }
    }
}
