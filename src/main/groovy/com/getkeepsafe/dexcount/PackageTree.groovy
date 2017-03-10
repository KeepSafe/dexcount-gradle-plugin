/*
 * Copyright (C) 2015-2016 KeepSafe Software
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

package com.getkeepsafe.dexcount

import com.android.dexdeps.FieldRef
import com.android.dexdeps.HasDeclaringClass
import com.android.dexdeps.MethodRef
import com.android.dexdeps.Output
import com.google.gson.stream.JsonWriter
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

import java.nio.CharBuffer

@CompileStatic  // necessary to avoid JDK verifier bugs (issues #11 and #12)
class PackageTree {
    // A cached sum of this node and all children's method ref counts.
    // -1 means that there is no cached value.  Set by `getMethodCount()`, and
    // invalidated by adding new nodes.
    private int methodTotal_ = -1

    // A cached sum of this node and all children's field-ref counts.
    // Same semantics as methodTotal_.
    private int fieldTotal_ = -1

    private final boolean isClass_
    private final String name_
    private final SortedMap<String, PackageTree> children_ = new TreeMap<>()
    private final Deobfuscator deobfuscator_

    // The set of methods declared on this node.  Will be empty for package
    // nodes and possibly non-empty for class nodes.
    private final Set<HasDeclaringClass> methods_ = new HashSet<>()

    // The set of fields declared on this node.  Will be empty for package
    // nodes and possibly non-empty for class nodes.
    private final Set<HasDeclaringClass> fields_ = new HashSet<>()

    PackageTree() {
        this("", false, null)
    }

    PackageTree(Deobfuscator deobfuscator) {
        this("", false, deobfuscator)
    }

    PackageTree(String name, Deobfuscator deobfuscator) {
        this(name, isClassName(name), deobfuscator)
    }

    private PackageTree(name, isClass, Deobfuscator deobfuscator) {
        this.name_ = name
        this.isClass_ = isClass
        this.deobfuscator_ = (deobfuscator ?: new Deobfuscator(null))
    }

    private static boolean isClassName(String name) {
        return Character.isUpperCase(name.charAt(0)) || name.contains("[]")
    }

    void addMethodRef(MethodRef method) {
        addInternal(descriptorToDot(method), 0, true, method)
    }

    void addFieldRef(FieldRef field) {
        addInternal(descriptorToDot(field), 0, false, field)
    }

    private void addInternal(String name, int startIndex, boolean isMethod, HasDeclaringClass ref) {
        def ix = name.indexOf('.', startIndex)
        def segment = ix == -1 ? name.substring(startIndex) : name.substring(startIndex, ix)
        def child = children_[segment]
        if (child == null) {
            child = children_[segment] = new PackageTree(segment, deobfuscator_)
        }

        if (ix == -1) {
            if (isMethod) {
                child.methods_.add((MethodRef) ref)
            } else {
                child.fields_.add((FieldRef) ref)
            }
        } else {
            if (isMethod) {
                methodTotal_ = -1
            } else {
                fieldTotal_ = -1
            }
            child.addInternal(name, ix + 1, isMethod, ref)
        }
    }

    int getMethodCount() {
        if (methodTotal_ == -1) {
            methodTotal_ = (int) children_.values().inject(methods_.size()) {
                int sum, PackageTree child -> sum + child.getMethodCount() }
        }
        return methodTotal_
    }

    int getFieldCount() {
        if (fieldTotal_ == -1) {
            fieldTotal_ = (int) children_.values().inject(fields_.size()) {
                int sum, PackageTree child -> sum + child.getFieldCount() }
        }
        return fieldTotal_
    }

    void print(Appendable out, OutputFormat format, PrintOptions opts) {
        switch (format) {
            case OutputFormat.LIST:
                printPackageList(out, opts)
                break

            case OutputFormat.TREE:
                printTree(out, opts)
                break

            case OutputFormat.JSON:
                printJson(out, opts)
                break

            case OutputFormat.YAML:
                printYaml(out, opts)
                break

            default:
                throw new IllegalArgumentException("Unknown format: $format")
        }
    }

    void printPackageList(Appendable out, PrintOptions opts) {
        def sb = new StringBuilder(64)

        if (opts.includeTotalMethodCount) {
            out.append("Total methods: ${this.getMethodCount()}\n")
        }

        if (opts.printHeader) {
            printPackageListHeader(out, opts)
        }

        forEach(getChildren(opts)) { it -> it.printPackageListRecursively(out, sb, 0, opts) }
    }

    private static printPackageListHeader(Appendable out, PrintOptions opts) {
        if (opts.includeMethodCount) {
            out.append(String.format("%-8s ", "methods"))
        }

        if (opts.includeFieldCount) {
            out.append(String.format("%-8s ", "fields"))
        }

        out.append("package/class name\n")
    }

    private void printPackageListRecursively(Appendable out, StringBuilder sb, int depth, PrintOptions opts) {
        if (depth >= opts.maxTreeDepth) {
            return
        }

        def len = sb.length()
        if (len > 0) {
            sb.append(".")
        }
        sb.append(name_)

        if (isPrintable(opts)) {
            if (opts.includeMethodCount) {
                out.append(String.format("%-8d ", getMethodCount()))
            }

            if (opts.includeFieldCount) {
                out.append(String.format("%-8d ", getFieldCount()))
            }

            out.append(sb.toString())
            out.append('\n')
        }

        forEach(getChildren(opts)) { PackageTree it -> it.printPackageListRecursively(out, sb, depth + 1, opts) }
        sb.setLength(len)
    }

    void printTree(Appendable out, PrintOptions opts) {
        forEach(getChildren(opts)) { PackageTree it -> it.printTreeRecursively(out, 0, opts) }
    }

    private void printTreeRecursively(Appendable out, int indent, PrintOptions opts) {
        // 'indent' here is equal to the current tree depth
        if (indent >= opts.maxTreeDepth) {
            return
        }

        indent.times { out.append("  ") }
        out.append(name_)

        if (opts.includeFieldCount || opts.includeMethodCount) {
            out.append(" (")

            def appended = false
            if (opts.includeMethodCount) {
                out.append(String.valueOf(getMethodCount()))
                out.append(" ")
                out.append(pluralizedMethods(getMethodCount()))
                appended = true
            }

            if (opts.includeFieldCount) {
                if (appended) {
                    out.append(", ")
                }
                out.append(String.valueOf(getFieldCount()))
                out.append(" ")
                out.append(pluralizeFields(getFieldCount()))
            }

            out.append(")")
        }

        out.append("\n")

        forEach(getChildren(opts)) { PackageTree it -> it.printTreeRecursively(out, indent + 1, opts) }
    }

    void printJson(Appendable out, PrintOptions opts) {
        def json = new JsonWriter(new Writer() {
            @Override
            void write(char[] cbuf, int off, int len) throws IOException {
                CharSequence seq = CharBuffer.wrap(cbuf, off, len)
                out.append(seq)
            }

            @Override
            void flush() throws IOException {

            }

            @Override
            void close() throws IOException {

            }
        })

        // Setting an indentation enables pretty-printing
        json.indent = "  "

        printJsonRecursively(json, 0, opts)
    }

    private void printJsonRecursively(JsonWriter json, int depth, PrintOptions opts) {
        if (depth >= opts.maxTreeDepth) {
            return
        }

        json.beginObject()

        json.name("name").value(name_)

        if (opts.includeMethodCount) {
            json.name("methods").value(methodCount)
        }

        if (opts.includeFieldCount) {
            json.name("fields").value(fieldCount)
        }

        json.name("children")
        json.beginArray()

        forEach(getChildren(opts)) { PackageTree it -> it.printJsonRecursively(json, depth + 1, opts) }

        json.endArray()

        json.endObject()
    }

    void printYaml(Appendable out, PrintOptions opts) {
        out.append("---\n")

        if (opts.includeMethodCount) {
            out.append("methods: " + methodCount + "\n")
        }

        if (opts.includeFieldCount) {
            out.append("fields: " + fieldCount + "\n")
        }

        out.append("counts:\n")

        forEach(getChildren(opts)) { it.printYamlRecursively(out, 0, opts) }
    }

    private void printYamlRecursively(Appendable out, int depth, PrintOptions opts) {
        if (depth > opts.maxTreeDepth) {
            return
        }

        String indentText = "  " * ((depth * 2) + 1)

        out.append(indentText + "- name: ")
        out.append(name_)
        out.append("\n")

        indentText += "  "

        if (opts.includeMethodCount) {
            out.append(indentText)
            out.append("methods: " + methodCount)
            out.append("\n")
        }

        if (opts.includeFieldCount) {
            out.append(indentText)
            out.append("fields: " + fieldCount)
            out.append("\n")
        }

        def children = (depth + 1) == opts.maxTreeDepth ? (Collection<PackageTree>) [] : getChildren(opts)
        if (children.empty) {
            out.append(indentText)
            out.append("children: []\n")
        } else {
            out.append(indentText)
            out.append("children:\n")
            forEach(children) { PackageTree child -> child.printYamlRecursively(out, depth + 1, opts) }
        }
    }

    private Collection<PackageTree> getChildren(PrintOptions opts) {
        def printableChildren = children_.values().findAll {
            it.isPrintable(opts)
        }

        if (opts.orderByMethodCount) {
            // Return the child nodes sorted in descending order by method count.
            printableChildren = printableChildren.sort(false) { PackageTree it -> -it.methodCount }
        }

        return printableChildren
    }

    private boolean isPrintable(PrintOptions opts) {
        return opts.includeClasses || !isClass_
    }

    private static String pluralizedMethods(int n) {
        return n == 1 ? "method" : "methods"
    }

    private static String pluralizeFields(int n) {
        return n == 1 ? "field" : "fields"
    }

    private String descriptorToDot(HasDeclaringClass ref) {
        def descriptor = ref.getDeclClassName()
        def dot = Output.descriptorToDot(descriptor)
        dot = deobfuscator_.deobfuscate(dot)
        if (dot.indexOf('.') == -1) {
            // Classes in the unnamed package (e.g. primitive arrays)
            // will not appear in the output in the current PackageTree
            // implementation if classes are not included.  To work around,
            // we make an artificial package named "<unnamed>".
            dot = "<unnamed>." + dot
        }
        return dot
    }

    /**
     * Iterates through the elements of a collection, applying the given
     * closure to each element.
     *
     * Workaround for old versions of Gradle that do not have the method
     * {@link org.codehaus.groovy.runtime.DefaultGroovyMethods#each(Collection, Closure)}.
     *
     * Without any workaround, projects building using these versions of Groovy,
     * we will fail with a MethodMissingException.
     *
     * This is observed in projects using Gradle 2.2.1, which bundles Groovy 2.3.7.
     *
     * @param collection
     * @param closure
     */
    private static <E> void forEach(
            Collection<E> collection,
            @ClosureParams(FirstParam.FirstGenericType.class) Closure closure) {
        for (E element : collection) {
            closure.call(element)
        }
    }
}
