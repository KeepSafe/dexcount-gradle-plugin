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
import java.io.Writer
import java.nio.CharBuffer
import java.util.*

class PackageTree(
        private val name_: String,
        private val isClass_: Boolean,
        deobfuscator: Deobfuscator?) {

    companion object {
        @JvmStatic private fun isClassName(name: String): Boolean {
            return Character.isUpperCase(name[0]) || name.contains("[]")
        }

        private inline fun <T> Iterable<T>.sumBy(initialCount: Int, selector: (T) -> Int): Int {
            return initialCount + sumBy(selector)
        }
    }

    // A cached sum of classes.
    // Set by `getClassCount()`, and invalidated by adding new nodes.
    private var classTotal_: Int? = null

    // A cached sum of this node and all children's method ref counts.
    // Set by `getMethodCount()`, and invalidated by adding new nodes.
    private var methodTotal_: Int? = null

    // A cached sum of this node and all children's field-ref counts.
    // Same semantics as methodTotal_.
    private var fieldTotal_: Int? = null

    private val deobfuscator_: Deobfuscator = deobfuscator ?: Deobfuscator.empty
    private val children_: SortedMap<String, PackageTree> = TreeMap()

    // The set of methods declared on this node.  Will be empty for package
    // nodes and possibly non-empty for class nodes.
    private val methods_ = HashSet<HasDeclaringClass>()

    // The set of fields declared on this node.  Will be empty for package
    // nodes and possibly non-empty for class nodes.
    private val fields_ = HashSet<HasDeclaringClass>()

    val classCount: Int
        get() {
            if (classTotal_ == null) {
                if (isClass_) {
                    classTotal_ = 1
                } else {
                    classTotal_ = children_.values.sumBy { it.classCount }
                }
            }
            return classTotal_!!
        }

    val methodCount: Int
        get() {
            if (methodTotal_ == null) {
                methodTotal_ = children_.values.sumBy(methods_.size) { it.methodCount }
            }
            return methodTotal_!!
        }

    val fieldCount: Int
        get() {
            if (fieldTotal_ == null) {
                fieldTotal_ = children_.values.sumBy(fields_.size) { it.fieldCount }
            }
            return fieldTotal_!!
        }

    constructor() : this("", false, null)

    constructor(deobfuscator: Deobfuscator) : this("", false, deobfuscator)

    constructor(name: String, deobfuscator: Deobfuscator) : this(name, isClassName(name), deobfuscator)

    fun addMethodRef(method: MethodRef) {
        addInternal(descriptorToDot(method), 0, true, method)
    }

    fun addFieldRef(field: FieldRef) {
        addInternal(descriptorToDot(field), 0, false, field)
    }

    private fun addInternal(name: String, startIndex: Int, isMethod: Boolean, ref: HasDeclaringClass) {
        val ix = name.indexOf('.', startIndex)
        val segment = if (ix == -1) name.substring(startIndex) else name.substring(startIndex, ix)
        val child = children_.getOrPut(segment) { PackageTree(segment, deobfuscator_) }

        if (ix == -1) {
            if (isMethod) {
                child.methods_.add(ref as MethodRef)
            } else {
                child.fields_.add(ref as FieldRef)
            }
        } else {
            if (isMethod) {
                methodTotal_ = null
            } else {
                fieldTotal_ = null
            }
            child.addInternal(name, ix + 1, isMethod, ref)
        }
    }

    fun print(out: Appendable, format: OutputFormat, opts: PrintOptions) {
        when (format) {
            OutputFormat.LIST -> printPackageList(out, opts)
            OutputFormat.TREE -> printTree(out, opts)
            OutputFormat.JSON -> printJson(out, opts)
            OutputFormat.YAML -> printYaml(out, opts)
        }
    }

    fun printPackageList(out: Appendable, opts: PrintOptions) {
        val sb = StringBuilder(64)

        if (opts.includeTotalMethodCount) {
            out.appendln("Total methods: $methodCount")
        }

        if (opts.printHeader) {
            printPackageListHeader(out, opts)
        }

        getChildren(opts).forEach { it.printPackageListRecursively(out, sb, 0, opts) }
    }

    private fun printPackageListHeader(out: Appendable, opts: PrintOptions) {
        if (opts.includeClassCount) {
            out.append(String.format("%-8s ", "classes"))
        }

        if (opts.includeMethodCount) {
            out.append(String.format("%-8s ", "methods"))
        }

        if (opts.includeFieldCount) {
            out.append(String.format("%-8s ", "fields"))
        }

        out.append("package/class name")
        out.appendln()
    }

    private fun printPackageListRecursively(out: Appendable, sb: StringBuilder, depth: Int, opts: PrintOptions) {
        if (depth >= opts.maxTreeDepth) {
            return
        }

        val len = sb.length
        if (len > 0) {
            sb.append(".")
        }
        sb.append(name_)

        if (isPrintable(opts)) {
            if (opts.includeClassCount) {
                out.append(String.format("%-8d ", classCount))
            }

            if (opts.includeMethodCount) {
                out.append(String.format("%-8d ", methodCount))
            }

            if (opts.includeFieldCount) {
                out.append(String.format("%-8d ", fieldCount))
            }

            out.appendln(sb.toString())
        }

        getChildren(opts).forEach { it.printPackageListRecursively(out, sb, depth + 1, opts) }
        sb.setLength(len)
    }

    fun printTree(out: Appendable, opts: PrintOptions) {
        getChildren(opts).forEach { it.printTreeRecursively(out, 0, opts) }
    }

    private fun printTreeRecursively(out: Appendable, indent: Int, opts: PrintOptions) {
        // 'indent' here is equal to the current tree depth
        if (indent >= opts.maxTreeDepth) {
            return
        }

        out.append("  ".repeat(indent))
        out.append(name_)

        if (opts.includeFieldCount || opts.includeMethodCount || opts.includeClassCount) {
            out.append(" (")

            var appended = false
            if (opts.includeClassCount) {
                out.append("$classCount ${pluralizedClasses(classCount)}")
                appended = true
            }

            if (opts.includeMethodCount) {
                if (appended) {
                    out.append(", ")
                }
                out.append("$methodCount ${pluralizedMethods(methodCount)}")
                appended = true
            }

            if (opts.includeFieldCount) {
                if (appended) {
                    out.append(", ")
                }
                out.append("$fieldCount ${pluralizeFields(fieldCount)}")
            }

            out.append(")")
        }

        out.appendln()

        getChildren(opts).forEach { it.printTreeRecursively(out, indent + 1, opts) }
    }

    fun printJson(out: Appendable, opts: PrintOptions) {
        val json = JsonWriter(object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                val seq = CharBuffer.wrap(cbuf, off, len)
                out.append(seq)
            }

            override fun flush() {
                // nothing
            }

            override fun close() {
                // nothing
            }
        })

        // Setting an indentation enables pretty-printing
        json.setIndent("  ")

        printJsonRecursively(json, 0, opts)
    }

    private fun printJsonRecursively(json: JsonWriter, depth: Int, opts: PrintOptions) {
        if (depth >= opts.maxTreeDepth) {
            return
        }

        json.beginObject()

        json.name("name").value(name_)

        if (opts.includeClassCount) {
            json.name("classes").value(classCount)
        }

        if (opts.includeMethodCount) {
            json.name("methods").value(methodCount)
        }

        if (opts.includeFieldCount) {
            json.name("fields").value(fieldCount)
        }

        json.name("children")
        json.beginArray()

        getChildren(opts).forEach { it.printJsonRecursively(json, depth + 1, opts) }

        json.endArray()

        json.endObject()
    }

    fun printYaml(out: Appendable, opts: PrintOptions) {
        out.append("---\n")

        if (opts.includeClassCount) {
            out.append("classes: " + classCount + "\n")
        }

        if (opts.includeMethodCount) {
            out.append("methods: " + methodCount + "\n")
        }

        if (opts.includeFieldCount) {
            out.append("fields: " + fieldCount + "\n")
        }

        out.append("counts:\n")

        getChildren(opts).forEach { it.printYamlRecursively(out, 0, opts) }
    }

    private fun printYamlRecursively(out: Appendable, depth: Int, opts: PrintOptions) {
        if (depth > opts.maxTreeDepth) {
            return
        }

        var indentText = "  ".repeat((depth * 2) + 1)

        out.appendln("$indentText- name: $name_")

        indentText += "  "

        if (opts.includeClassCount) {
            out.appendln("${indentText}classes: $classCount")
        }

        if (opts.includeMethodCount) {
            out.appendln("${indentText}methods: $methodCount")
        }

        if (opts.includeFieldCount) {
            out.appendln("${indentText}fields: $fieldCount")
        }

        val children = if ((depth + 1) == opts.maxTreeDepth) emptyList() else getChildren(opts)
        if (children.isEmpty()) {
            out.appendln("${indentText}children: []")
        } else {
            out.appendln("${indentText}children:")
        }

        children.forEach { it.printYamlRecursively(out, depth + 1, opts) }
    }

    private fun getChildren(opts: PrintOptions): List<PackageTree> {
        val printableChildren = children_.values.filter {
            it.isPrintable(opts)
        }

        return if (opts.orderByMethodCount) {
            // Return the child nodes sorted in descending order by method count.
            printableChildren.sortedByDescending { it.methodCount }
        } else {
            printableChildren
        }
    }

    private fun isPrintable(opts: PrintOptions): Boolean {
        return opts.includeClasses || !isClass_
    }

    private fun pluralizedClasses(n: Int) = if (n == 1) "class" else "classes"

    private fun pluralizedMethods(n: Int) = if (n == 1) "method" else "methods"

    private fun pluralizeFields(n: Int) = if (n == 1) "field" else "fields"

    private fun descriptorToDot(ref: HasDeclaringClass): String {
        val descriptor = ref.declClassName
        val dot = Output.descriptorToDot(descriptor)
        val deobfuscated = deobfuscator_.deobfuscate(dot)
        return if (deobfuscated.indexOf('.') == -1) {
            // Classes in the unnamed package (e.g. primitive arrays)
            // will not appear in the output in the current PackageTree
            // implementation if classes are not included.  To work around,
            // we make an artificial package named "<unnamed>".
            "<unnamed>." + deobfuscated
        } else {
            deobfuscated
        }
    }
}
