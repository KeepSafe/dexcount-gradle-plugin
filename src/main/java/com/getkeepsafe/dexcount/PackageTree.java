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
package com.getkeepsafe.dexcount;

import com.android.dexdeps.FieldRef;
import com.android.dexdeps.HasDeclaringClass;
import com.android.dexdeps.MethodRef;
import com.android.dexdeps.Output;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageTree {
    private enum Type {
        DECLARED,
        REFERENCED,
    }

    private final String name;
    private final boolean isClass;
    private final Deobfuscator deobfuscator;

    private final LinkedHashMap<Type, Integer> classTotal = new LinkedHashMap<>();
    private final LinkedHashMap<Type, Integer> methodTotal = new LinkedHashMap<>();
    private final LinkedHashMap<Type, Integer> fieldTotal = new LinkedHashMap<>();
    private final SortedMap<String, PackageTree> children = new TreeMap<>();
    private final LinkedHashMap<Type, LinkedHashSet<MethodRef>> methods = new LinkedHashMap<>();
    private final LinkedHashMap<Type, LinkedHashSet<FieldRef>> fields = new LinkedHashMap<>();

    public PackageTree() {
        this("", false, null);
    }

    public PackageTree(Deobfuscator deobfuscator) {
        this("", false, deobfuscator);
    }

    public PackageTree(String name, Deobfuscator deobfuscator) {
        this(name, isClassName(name), deobfuscator);
    }

    public PackageTree(String name, boolean isClass, Deobfuscator deobfuscator) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        if (deobfuscator == null) {
            deobfuscator = Deobfuscator.EMPTY;
        }

        this.name = name;
        this.isClass = isClass;
        this.deobfuscator = deobfuscator;

        for (Type type : Type.values()) {
            methods.put(type, new LinkedHashSet<>());
            fields.put(type, new LinkedHashSet<>());
        }
    }

    public String getName() {
        return name;
    }

    public boolean isClass() {
        return isClass;
    }

    public int getClassCount() {
        return getClassCount(Type.REFERENCED);
    }

    public int getClassCountDeclared() {
        return getClassCount(Type.DECLARED);
    }

    public int getMethodCount() {
        return getMethodCount(Type.REFERENCED);
    }

    public int getMethodCountDeclared() {
        return getMethodCount(Type.DECLARED);
    }

    public int getFieldCount() {
        return getFieldCount(Type.REFERENCED);
    }

    public int getFieldCountDeclared() {
        return getFieldCount(Type.DECLARED);
    }

    private int getClassCount(Type type) {
        Integer maybeTotal = classTotal.get(type);
        if (maybeTotal != null) {
            return maybeTotal;
        }

        if (isClass) {
            classTotal.put(type, 1);
            return 1;
        }

        int result = children.values().parallelStream().mapToInt(child -> child.getClassCount(type)).sum();
        classTotal.put(type, result);

        return result;
    }

    private int getMethodCount(Type type) {
        Integer maybeTotal = methodTotal.get(type);
        if (maybeTotal != null) {
            return maybeTotal;
        }

        int result = methods.get(type).size() + children.values().parallelStream().mapToInt(child -> child.getMethodCount(type)).sum();
        methodTotal.put(type, result);

        return result;
    }

    private int getFieldCount(Type type) {
        Integer maybeTotal = fieldTotal.get(type);
        if (maybeTotal != null) {
            return maybeTotal;
        }

        int result = fields.get(type).size() + children.values().parallelStream().mapToInt(child -> child.getFieldCount(type)).sum();
        fieldTotal.put(type, result);

        return result;
    }

    public void addMethodRef(MethodRef ref) {
        addInternal(descriptorToDot(ref), 0, true, Type.REFERENCED, ref);
    }

    public void addFieldRef(FieldRef ref) {
        addInternal(descriptorToDot(ref), 0, false, Type.REFERENCED, ref);
    }

    public void addDeclaredMethodRef(MethodRef ref) {
        addInternal(descriptorToDot(ref), 0, true, Type.DECLARED, ref);
    }

    public void addDeclaredFieldRef(FieldRef ref) {
        addInternal(descriptorToDot(ref), 0, false, Type.DECLARED, ref);
    }

    private void addInternal(String name, int startIndex, boolean isMethod, Type type, HasDeclaringClass ref) {
        int ix = name.indexOf('.', startIndex);
        String segment;
        if (ix == -1) {
            segment = name.substring(startIndex);
        } else {
            segment = name.substring(startIndex, ix);
        }

        PackageTree child = children.get(segment);
        if (child == null) {
            child = new PackageTree(segment, deobfuscator);
            children.put(segment, child);
        }

        if (ix == -1) {
            if (isMethod) {
                child.methods.get(type).add((MethodRef) ref);
            } else {
                child.fields.get(type).add((FieldRef) ref);
            }
        } else {
            if (isMethod) {
                methodTotal.remove(type);
            } else {
                fieldTotal.remove(type);
            }
            child.addInternal(name, ix + 1, isMethod, type, ref);
        }
    }

    public void print(Appendable out, OutputFormat format, PrintOptions opts) throws IOException {
        switch (format) {
            case LIST:
                printPackageList(out, opts);
                break;

            case TREE:
                printTree(out, opts);
                break;

            case JSON:
                printJson(out, opts);
                break;

            case YAML:
                printYaml(out, opts);
                break;

            default:
                throw new IllegalArgumentException("Unexpected OutputFormat: " + format);
        }
    }

    public void printPackageList(Appendable out, PrintOptions opts) throws IOException {
        StringBuilder sb = new StringBuilder(64);

        if (opts.getIncludeTotalMethodCount()) {
            if (opts.isAndroidProject()) {
                out.append("Total methods: ").append(String.valueOf(getMethodCount())).append("\n");
            }

            if (opts.getPrintDeclarations()) {
                out.append("Total declared methods: ").append(String.valueOf(getClassCountDeclared())).append("\n");
            }
        }

        if (opts.getPrintHeader()) {
            printPackageListHeader(out, opts);
        }

        for (PackageTree child : getChildren(opts)) {
            child.printPackageListRecursively(out, sb, 0, opts);
        }
    }

    private void printPackageListHeader(Appendable out, PrintOptions opts) throws IOException {
        if (opts.getIncludeClassCount()) {
            out.append(String.format("%-8s ", "classes"));
        }

        if (opts.isAndroidProject()) {
            if (opts.getIncludeMethodCount()) {
                out.append(String.format("%-8s ", "methods"));
            }

            if (opts.getIncludeFieldCount()) {
                out.append(String.format("%-8s ", "fields"));
            }
        }

        if (opts.getPrintDeclarations()) {
            out.append(String.format("%-16s ", "declared methods"));
            out.append(String.format("%-16s ", "declared fields"));
        }

        out.append("package/class name\n");
    }

    private void printPackageListRecursively(Appendable out, StringBuilder sb, int depth, PrintOptions opts) throws IOException {
        if (depth >= opts.getMaxTreeDepth()) {
            return;
        }

        if (!isPrintable(opts)) {
            // Should be guaranteed by `getChildren()`
            throw new IllegalStateException("We should never recursively print a non-printable");
        }

        int len = sb.length();
        if (len > 0) {
            sb.append('.');
        }
        sb.append(getName());

        if (opts.getIncludeClassCount()) {
            out.append(String.format("%-8d ", getClassCount()));
        }

        if (opts.isAndroidProject()) {
            if (opts.getIncludeMethodCount()) {
                out.append(String.format("%-8d ", getMethodCount()));
            }

            if (opts.getIncludeFieldCount()) {
                out.append(String.format("%-8d ", getFieldCount()));
            }
        }

        if (opts.getPrintDeclarations()) {
            if (opts.getPrintHeader()) {
                // The header for the these two columns uses more space.
                out.append(String.format("%-16d ", getMethodCountDeclared()));
                out.append(String.format("%-16d ", getFieldCountDeclared()));
            } else {
                out.append(String.format("%-8d ", getMethodCountDeclared()));
                out.append(String.format("%-8d ", getFieldCountDeclared()));
            }
        }

        out.append(sb.toString()).append("\n");

        for (PackageTree child : getChildren(opts)) {
            child.printPackageListRecursively(out, sb, depth + 1, opts);
        }

        sb.setLength(len);
    }

    public void printTree(Appendable out, PrintOptions opts) throws IOException {
        for (PackageTree child : getChildren(opts)) {
            child.printTreeRecursively(out, 0, opts);
        }
    }

    private void printTreeRecursively(Appendable out, int depth, PrintOptions opts) throws IOException {
        if (depth >= opts.getMaxTreeDepth()) {
            return;
        }

        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append(getName());

        if (opts.getIncludeFieldCount() || opts.getIncludeMethodCount() || opts.getIncludeClassCount()) {
            out.append(" (");

            boolean appended = false;
            if (opts.getIncludeClassCount()) {
                out.append(String.valueOf(getClassCount()))
                    .append(" ")
                    .append(pluralizedClasses(getClassCount()));
                appended = true;
            }

            if (opts.isAndroidProject()) {
                if (opts.getIncludeMethodCount()) {
                    if (appended) {
                        out.append(", ");
                    }
                    out.append(String.valueOf(getMethodCount()))
                        .append(" ")
                        .append(pluralizedMethods(getMethodCount()));
                    appended = true;
                }

                if (opts.getIncludeFieldCount()) {
                    if (appended) {
                        out.append(", ");
                    }
                    out.append(String.valueOf(getFieldCount()))
                        .append(" ")
                        .append(pluralizedFields(getFieldCount()));
                    appended = true;
                }
            }

            if (opts.getPrintDeclarations()) {
                if (appended) {
                    out.append(", ");
                }
                out.append(String.valueOf(getMethodCountDeclared()))
                    .append(" declared ")
                    .append(pluralizedMethods(getMethodCountDeclared()))
                    .append(", ")
                    .append(String.valueOf(getFieldCountDeclared()))
                    .append(" declared ")
                    .append(pluralizedFields(getFieldCountDeclared()));
            }

            out.append(")\n");
        }

        for (PackageTree child : getChildren(opts)) {
            child.printTreeRecursively(out, depth + 1, opts);
        }
    }

    void printJson(Appendable out, PrintOptions opts) throws IOException {
        JsonWriter json = new JsonWriter(new Writer() {
            @Override
            public void write(@NotNull char[] chars, int offset, int length) throws IOException {
                out.append(CharBuffer.wrap(chars, offset, length));
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() {
                // no-op
            }
        });

        json.setIndent("  ");

        printJsonRecursively(json, 0, opts);
    }

    private void printJsonRecursively(JsonWriter json, int depth, PrintOptions opts) throws IOException {
        if (depth >= opts.getMaxTreeDepth()) {
            return;
        }

        json.beginObject();

        json.name("name").value(getName());

        if (opts.getIncludeClassCount()) {
            json.name("classes").value(getClassCount());
        }

        if (opts.isAndroidProject()) {
            if (opts.getIncludeMethodCount()) {
                json.name("methods").value(getMethodCount());
            }

            if (opts.getIncludeFieldCount()) {
                json.name("fields").value(getFieldCount());
            }
        }

        if (opts.getPrintDeclarations()) {
            json.name("declared_methods").value(getMethodCountDeclared());
            json.name("declared_fields").value(getFieldCountDeclared());
        }

        json.name("children");
        json.beginArray();
        for (PackageTree child : getChildren(opts)) {
            child.printJsonRecursively(json, depth + 1, opts);
        }
        json.endArray();

        json.endObject();
    }

    public void printYaml(Appendable out, PrintOptions opts) throws IOException {
        out.append("---\n");

        if (opts.getIncludeClassCount()) {
            out.append("classes: ").append(String.valueOf(getClassCount())).append("\n");
        }

        if (opts.isAndroidProject()) {
            if (opts.getIncludeMethodCount()) {
                out.append("methods: ").append(String.valueOf(getMethodCount())).append("\n");
            }

            if (opts.getIncludeFieldCount()) {
                out.append("fields: ").append(String.valueOf(getFieldCount())).append("\n");
            }
        }

        if (opts.getPrintDeclarations()) {
            out.append("declared_methods: ").append(String.valueOf(getMethodCountDeclared())).append("\n");
            out.append("declared_fields: ").append(String.valueOf(getFieldCountDeclared())).append("\n");
        }

        out.append("counts:\n");

        for (PackageTree child : getChildren(opts)) {
            child.printYamlRecursively(out, 0, opts);
        }
    }

    private void printYamlRecursively(Appendable out, int depth, PrintOptions opts) throws IOException {
        if (depth > opts.getMaxTreeDepth()) {
            return;
        }

        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < (depth * 2) + 1; ++i) {
            indentBuilder.append("  ");
        }
        String indent = indentBuilder.toString();

        out.append(indent).append("- name: ").append(getName()).append("\n");

        indent += "  ";

        if (opts.getIncludeClassCount()) {
            out.append(indent).append("classes: ").append(String.valueOf(getClassCount())).append("\n");
        }

        if (opts.isAndroidProject()) {
            if (opts.getIncludeMethodCount()) {
                out.append(indent).append("methods: ").append(String.valueOf(getMethodCount())).append("\n");
            }

            if (opts.getIncludeFieldCount()) {
                out.append(indent).append("fields: ").append(String.valueOf(getFieldCount())).append("\n");
            }
        }

        if (opts.getPrintDeclarations()) {
            out.append(indent).append("declared_methods: ").append(String.valueOf(getMethodCountDeclared())).append("\n");
            out.append(indent).append("declared_fields: ").append(String.valueOf(getFieldCountDeclared())).append("\n");
        }

        List<PackageTree> childNodes;
        if (depth + 1 == opts.getMaxTreeDepth()) {
            childNodes = Collections.emptyList();
        } else {
            childNodes = getChildren(opts);
        }

        if (childNodes.isEmpty()) {
            out.append(indent).append("children: []\n");
            return;
        }

        out.append(indent).append("children:\n");
        for (PackageTree child : getChildren(opts)) {
            child.printYamlRecursively(out, depth + 1, opts);
        }
    }

    private List<PackageTree> getChildren(PrintOptions opts) {
        Stream<PackageTree> result = children.values().stream().filter(it -> it.isPrintable(opts));

        if (opts.getOrderByMethodCount()) {
            result = result.sorted((lhs, rhs) -> Integer.compare(rhs.getMethodCount(), lhs.getMethodCount()));
        }

        return result.collect(Collectors.toList());
    }

    private boolean isPrintable(PrintOptions opts) {
        return opts.getIncludeClasses() || !isClass;
    }

    private String pluralizedClasses(int n) {
        if (n == 1) {
            return "class";
        } else {
            return "classes";
        }
    }

    private String pluralizedMethods(int n) {
        if (n == 1) {
            return "method";
        } else {
            return "methods";
        }
    }

    private String pluralizedFields(int n) {
        if (n == 1) {
            return "field";
        } else {
            return "fields";
        }
    }

    private String descriptorToDot(HasDeclaringClass ref) {
        String descriptor = ref.getDeclClassName();
        String dot = Output.descriptorToDot(descriptor);
        String deobfuscated = deobfuscator.deobfuscate(dot);
        if (deobfuscated.indexOf('.') == -1) {
            // Classes in the unnamed package (e.g. primitive arrays)
            // will not appear in the output in the current PackageTree
            // implementation if classes are not included.  To work around,
            // we make an artificial package named "<unnamed>".
            return "<unnamed>." + deobfuscated;
        } else {
            return deobfuscated;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PackageTree that = (PackageTree) o;

        if (isClass != that.isClass) return false;
        if (!name.equals(that.name)) return false;
        if (!children.equals(that.children)) return false;
        if (!methods.equals(that.methods)) return false;
        return fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (isClass ? 1 : 0);
        result = 31 * result + children.hashCode();
        result = 31 * result + methods.hashCode();
        result = 31 * result + fields.hashCode();
        return result;
    }

    private static boolean isClassName(String name) {
        return Character.isUpperCase(name.charAt(0)) || name.contains("[]");
    }

    private static com.getkeepsafe.dexcount.thrift.MethodRef methodRefToThrift(MethodRef methodRef) {
        return new com.getkeepsafe.dexcount.thrift.MethodRef(
            methodRef.getDeclClassName(),
            methodRef.getReturnTypeName(),
            methodRef.getName(),
            Arrays.asList(methodRef.getArgumentTypeNames())
        );
    }

    private static MethodRef methodRefFromThrift(com.getkeepsafe.dexcount.thrift.MethodRef methodRef) {
        String[] argTypes = new String[0];
        if (methodRef.argumentTypes != null) {
            argTypes = methodRef.argumentTypes.toArray(argTypes);
        }

        return new MethodRef(
            methodRef.declaringClass,
            argTypes,
            methodRef.returnType,
            methodRef.methodName
        );
    }

    private static com.getkeepsafe.dexcount.thrift.FieldRef fieldRefToThrift(FieldRef fieldRef) {
        return new com.getkeepsafe.dexcount.thrift.FieldRef(
            fieldRef.getDeclClassName(),
            fieldRef.getTypeName(),
            fieldRef.getName()
        );
    }

    private static FieldRef fieldRefFromThrift(com.getkeepsafe.dexcount.thrift.FieldRef fieldRef) {
        return new FieldRef(
            fieldRef.declaringClass,
            fieldRef.fieldType,
            fieldRef.fieldName
        );
    }

    public static com.getkeepsafe.dexcount.thrift.PackageTree toThrift(PackageTree tree) {
        Map<String, com.getkeepsafe.dexcount.thrift.PackageTree> children = new LinkedHashMap<>();
        for (Entry<String, PackageTree> entry : tree.children.entrySet()) {
            children.put(entry.getKey(), toThrift(entry.getValue()));
        }

        return new com.getkeepsafe.dexcount.thrift.PackageTree(
            tree.getName(),
            tree.isClass(),
            children,
            tree.methods.get(Type.DECLARED).stream().map(PackageTree::methodRefToThrift).collect(Collectors.toCollection(LinkedHashSet::new)),
            tree.methods.get(Type.REFERENCED).stream().map(PackageTree::methodRefToThrift).collect(Collectors.toCollection(LinkedHashSet::new)),
            tree.fields.get(Type.DECLARED).stream().map(PackageTree::fieldRefToThrift).collect(Collectors.toCollection(LinkedHashSet::new)),
            tree.fields.get(Type.REFERENCED).stream().map(PackageTree::fieldRefToThrift).collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }

    public static PackageTree fromThrift(com.getkeepsafe.dexcount.thrift.PackageTree tree) {
        String name = tree.name != null ? tree.name : "";
        boolean isClass = tree.isClass != null ? tree.isClass : false;

        PackageTree result = new PackageTree(name, isClass, Deobfuscator.EMPTY);

        if (tree.children != null) {
            for (String key : tree.children.keySet()) {
                result.children.put(key, fromThrift(tree.children.get(key)));
            }
        }

        if (tree.declaredMethods != null) {
            for (com.getkeepsafe.dexcount.thrift.MethodRef declaredMethod : tree.declaredMethods) {
                result.methods.get(Type.DECLARED).add(methodRefFromThrift(declaredMethod));
            }
        }

        if (tree.referencedMethods != null) {
            for (com.getkeepsafe.dexcount.thrift.MethodRef referencedMethod : tree.referencedMethods) {
                result.methods.get(Type.REFERENCED).add(methodRefFromThrift(referencedMethod));
            }
        }

        if (tree.declaredFields != null) {
            for (com.getkeepsafe.dexcount.thrift.FieldRef declaredField : tree.declaredFields) {
                result.fields.get(Type.DECLARED).add(fieldRefFromThrift(declaredField));
            }
        }

        if (tree.referencedFields != null) {
            for (com.getkeepsafe.dexcount.thrift.FieldRef referencedField : tree.referencedFields) {
                result.fields.get(Type.REFERENCED).add(fieldRefFromThrift(referencedField));
            }
        }

        return result;
    }
}
