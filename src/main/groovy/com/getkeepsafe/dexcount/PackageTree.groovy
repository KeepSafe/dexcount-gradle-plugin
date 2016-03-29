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

import com.google.gson.stream.JsonWriter
import groovy.transform.CompileStatic

import java.nio.CharBuffer

@CompileStatic  // necessary to avoid JDK verifier bugs (issues #11 and #12)
class PackageTree {
    // A cached sum of this node and all children's method ref counts.
    // -1 means that there is no cached value.  Set by `getMethodCount()`, and
    // invalidated by adding new nodes.
    private int methodTotal_ = -1

    // The local count of method refs.  Will be zero for package nodes and
    // non-zero for class nodes.
    private int methodCount_ = 0

    // A cached sum of this node and all children's field-ref counts.
    // Same semantics as methodTotal_.
    private int fieldTotal_ = -1

    // The local count of field refs.  Will be zero for package nodes and
    // possibly non-zero for class nodes.
    private int fieldCount_ = 0

    private final boolean isClass_
    private final String name_
    private final SortedMap<String, PackageTree> children_ = new TreeMap<>()

    PackageTree() {
        this("", false)
    }

    PackageTree(String name) {
        this(name, isClassName(name))
    }

    private PackageTree(name, isClass) {
        this.name_ = name
        this.isClass_ = isClass
    }

    private static boolean isClassName(String name) {
        return Character.isUpperCase(name.charAt(0)) || name.contains("[]")
    }

    public void addMethodRef(String fullyQualifiedClassName) {
        addInternal(fullyQualifiedClassName, 0, true)
    }

    public void addFieldRef(String fullyQualifiedClassName) {
        addInternal(fullyQualifiedClassName, 0, false)
    }

    private void addInternal(String name, int startIndex, boolean isMethod) {
        def ix = name.indexOf('.', startIndex)
        def segment = ix == -1 ? name.substring(startIndex) : name.substring(startIndex, ix)
        def child = children_[segment]
        if (child == null) {
            child = children_[segment] = new PackageTree(segment)
        }

        if (ix == -1) {
            if (isMethod) {
                child.methodCount_++
            } else {
                child.fieldCount_++
            }
        } else {
            if (isMethod) {
                methodTotal_ = -1
            } else {
                fieldTotal_ = -1
            }
            child.addInternal(name, ix + 1, isMethod)
        }
    }

    int getMethodCount() {
        if (methodTotal_ == -1) {
            methodTotal_ = (int) children_.values().inject(methodCount_) {
                int sum, PackageTree child -> sum + child.getMethodCount() }
        }
        return methodTotal_
    }

    int getFieldCount() {
        if (fieldTotal_ == -1) {
            fieldTotal_ = (int) children_.values().inject(fieldCount_) {
                int sum, PackageTree child -> sum + child.getFieldCount() }
        }
        return fieldTotal_
    }

    void print(Appendable out, OutputFormat format, PrintOptions opts) {
        switch (format) {
            case OutputFormat.LIST:
                printPackageList(out, opts)
                break;

            case OutputFormat.TREE:
                printTree(out, opts)
                break;

            case OutputFormat.JSON:
                printJson(out, opts)
                break;

            case OutputFormat.YAML:
                printYaml(out, opts)
                break;

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

        getChildren(opts).each { it -> it.printPackageListRecursively(out, sb, 0, opts) }
    }

    private static def printPackageListHeader(Appendable out, PrintOptions opts) {
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

        getChildren(opts).each { PackageTree it -> it.printPackageListRecursively(out, sb, depth + 1, opts) }
        sb.setLength(len)
    }

    void printTree(Appendable out, PrintOptions opts) {
        getChildren(opts).each { PackageTree it -> it.printTreeRecursively(out, 0, opts) }
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

        getChildren(opts).each { PackageTree it -> it.printTreeRecursively(out, indent + 1, opts) }
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

        getChildren(opts).each { PackageTree it -> it.printJsonRecursively(json, depth + 1, opts) }

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

        getChildren(opts).each { it.printYamlRecursively(out, 0, opts) }
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
            children.each { PackageTree child -> child.printYamlRecursively(out, depth + 1, opts) }
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
}
