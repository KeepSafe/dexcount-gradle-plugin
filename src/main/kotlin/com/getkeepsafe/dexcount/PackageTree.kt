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
import com.getkeepsafe.dexcount.PackageTree.Type.DECLARED
import com.getkeepsafe.dexcount.PackageTree.Type.REFERENCED
import com.getkeepsafe.dexcount.thrift.FieldRef as ThriftFieldRef
import com.getkeepsafe.dexcount.thrift.MethodRef as ThriftMethodRef
import com.getkeepsafe.dexcount.thrift.PackageTree as ThriftPackageTree
import com.google.gson.stream.JsonWriter
import java.io.Writer
import java.nio.CharBuffer
import java.util.*
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap

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

        private fun methodRefToThrift(ref: MethodRef): ThriftMethodRef {
            return ThriftMethodRef(
                declaringClass = ref.declClassName,
                returnType = ref.returnTypeName,
                methodName = ref.name,
                argumentTypes = ref.argumentTypeNames.toList()
            )
        }

        private fun methodRefFromThrift(thrift: ThriftMethodRef): MethodRef {
            return MethodRef(thrift.declaringClass, thrift.argumentTypes?.toTypedArray(), thrift.returnType, thrift.methodName)
        }

        private fun fieldRefToThrift(ref: FieldRef): ThriftFieldRef {
            return ThriftFieldRef(
                declaringClass = ref.declClassName,
                fieldType = ref.typeName,
                fieldName = ref.name
            )
        }

        private fun fieldRefFromThrift(thrift: ThriftFieldRef): FieldRef {
            return FieldRef(thrift.declaringClass, thrift.fieldType, thrift.fieldName)
        }

        @JvmStatic fun toThrift(tree: PackageTree): ThriftPackageTree {
            val children = LinkedHashMap<String, ThriftPackageTree>()
            for ((name, child) in tree.children_) {
                children[name] = toThrift(child)
            }

            return ThriftPackageTree(
                name = tree.name_,
                isClass = tree.isClass_,
                children = children,
                declaredMethods = tree.methods_[DECLARED]?.map(::methodRefToThrift)?.toSet(),
                referencedMethods = tree.methods_[REFERENCED]?.map(::methodRefToThrift)?.toSet(),
                declaredFields = tree.fields_[DECLARED]?.map(::fieldRefToThrift)?.toSet(),
                referencedFields = tree.fields_[REFERENCED]?.map(::fieldRefToThrift)?.toSet()
            )
        }

        @JvmStatic fun fromThrift(thrift: ThriftPackageTree): PackageTree {
            val tree = PackageTree(thrift.name ?: "", thrift.isClass ?: false, Deobfuscator.EMPTY)
            if (thrift.children != null) {
                for ((name, node) in thrift.children) {
                    tree.children_[name] = fromThrift(node)
                }
            }

            if (thrift.declaredFields != null) {
                for (field in thrift.declaredFields) {
                    tree.fields_[DECLARED]!! += fieldRefFromThrift(field)
                }
            }

            if (thrift.referencedFields != null) {
                for (field in thrift.referencedFields) {
                    tree.fields_[REFERENCED]!! += fieldRefFromThrift(field)
                }
            }

            if (thrift.declaredMethods != null) {
                for (method in thrift.declaredMethods) {
                    tree.methods_[DECLARED]!! += methodRefFromThrift(method)
                }
            }

            if (thrift.referencedMethods != null) {
                for (method in thrift.referencedMethods) {
                    tree.methods_[REFERENCED]!! += methodRefFromThrift(method)
                }
            }

            return tree
        }
    }

    // A cached sum of classes.
    // Set by `getClassCount()`, and invalidated by adding new nodes.
    private var classTotal_ = mutableMapOf<Type, Int>()

    // A cached sum of this node and all children's method ref counts.
    // Set by `getMethodCount()`, and invalidated by adding new nodes.
    private var methodTotal_ = mutableMapOf<Type, Int>()

    // A cached sum of this node and all children's field-ref counts.
    // Same semantics as methodTotal_.
    private var fieldTotal_ = mutableMapOf<Type, Int>()

    private val deobfuscator_: Deobfuscator = deobfuscator ?: Deobfuscator.EMPTY
    private val children_: SortedMap<String, PackageTree> = TreeMap()

    // The set of methods declared on this node.  Will be empty for package
    // nodes and possibly non-empty for class nodes.
    private val methods_ = mutableMapOf<Type, HashSet<MethodRef>>()
        .apply { Type.values().forEach { put(it, HashSet()) } }

    // The set of fields declared on this node.  Will be empty for package
    // nodes and possibly non-empty for class nodes.
    private val fields_ = mutableMapOf<Type, HashSet<FieldRef>>()
        .apply { Type.values().forEach { put(it, HashSet()) } }

    val classCount: Int get() = classCount(REFERENCED)
    val methodCount: Int get() = methodCount(REFERENCED)
    val fieldCount: Int get() = fieldCount(REFERENCED)

    val classCountDeclared: Int get() = classCount(DECLARED)
    val methodCountDeclared: Int get() = methodCount(DECLARED)
    val fieldCountDeclared: Int get() = fieldCount(DECLARED)

    constructor() : this("", false, null)

    constructor(deobfuscator: Deobfuscator) : this("", false, deobfuscator)

    constructor(name: String, deobfuscator: Deobfuscator) : this(name, isClassName(name), deobfuscator)

    fun addMethodRef(method: MethodRef) {
        addInternal(descriptorToDot(method), 0, true, REFERENCED, method)
    }

    fun addFieldRef(field: FieldRef) {
        addInternal(descriptorToDot(field), 0, false, REFERENCED, field)
    }

    fun addDeclaredMethodRef(method: MethodRef) {
        addInternal(descriptorToDot(method), 0, true, DECLARED, method)
    }

    fun addDeclaredFieldRef(field: FieldRef) {
        addInternal(descriptorToDot(field), 0, false, DECLARED, field)
    }

    private fun addInternal(name: String, startIndex: Int, isMethod: Boolean, type: Type, ref: HasDeclaringClass) {
        val ix = name.indexOf('.', startIndex)
        val segment = if (ix == -1) name.substring(startIndex) else name.substring(startIndex, ix)
        val child = children_.getOrPut(segment) { PackageTree(segment, deobfuscator_) }

        if (ix == -1) {
            if (isMethod) {
                child.methods_[type]!!.add(ref as MethodRef)
            } else {
                child.fields_[type]!!.add(ref as FieldRef)
            }
        } else {
            if (isMethod) {
                methodTotal_.remove(type)
            } else {
                fieldTotal_.remove(type)
            }
            child.addInternal(name, ix + 1, isMethod, type, ref)
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
            if (opts.isAndroidProject) {
                out.appendln("Total methods: $methodCount")
            }

            if (opts.printDeclarations) {
                out.appendln("Total declared methods: $methodCountDeclared")
            }
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

        if (opts.isAndroidProject) {
            if (opts.includeMethodCount) {
                out.append(String.format("%-8s ", "methods"))
            }

            if (opts.includeFieldCount) {
                out.append(String.format("%-8s ", "fields"))
            }
        }

        if (opts.printDeclarations) {
            out.append(String.format("%-16s ", "declared methods"))
            out.append(String.format("%-16s ", "declared fields"))
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

            if (opts.isAndroidProject) {
                if (opts.includeMethodCount) {
                    out.append(String.format("%-8d ", methodCount))
                }

                if (opts.includeFieldCount) {
                    out.append(String.format("%-8d ", fieldCount))
                }
            }

            if (opts.printDeclarations) {
                if (opts.printHeader) {
                    // The header for the these two columns uses more space.
                    out.append(String.format("%-16d ", methodCountDeclared))
                    out.append(String.format("%-16d ", fieldCountDeclared))
                } else {
                    out.append(String.format("%-8d ", methodCountDeclared))
                    out.append(String.format("%-8d ", fieldCountDeclared))
                }
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

            if (opts.isAndroidProject) {
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
                    out.append("$fieldCount ${pluralizedFields(fieldCount)}")
                    appended = true
                }
            }

            if (opts.printDeclarations) {
                if (appended) {
                    out.append(", ")
                }
                out.append("$methodCountDeclared declared ${pluralizedMethods(methodCountDeclared)}")
                    .append(", ")
                    .append("$fieldCountDeclared declared ${pluralizedFields(fieldCountDeclared)}")
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

        if (opts.isAndroidProject) {
            if (opts.includeMethodCount) {
                json.name("methods").value(methodCount)
            }

            if (opts.includeFieldCount) {
                json.name("fields").value(fieldCount)
            }
        }

        if (opts.printDeclarations) {
            json.name("declared_methods").value(methodCountDeclared)
            json.name("declared_fields").value(fieldCountDeclared)
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
            out.append("classes: $classCount\n")
        }

        if (opts.isAndroidProject) {
            if (opts.includeMethodCount) {
                out.append("methods: $methodCount\n")
            }

            if (opts.includeFieldCount) {
                out.append("fields: $fieldCount\n")
            }
        }

        if (opts.printDeclarations) {
            out.append("declared_methods: $methodCountDeclared\n")
            out.append("declared_fields: $fieldCountDeclared\n")
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

        if (opts.printDeclarations) {
            out.appendln("${indentText}declared_methods: $methodCountDeclared")
            out.appendln("${indentText}declared_fields: $fieldCountDeclared")
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

    private fun pluralizedFields(n: Int) = if (n == 1) "field" else "fields"

    private fun descriptorToDot(ref: HasDeclaringClass): String {
        val descriptor = ref.declClassName
        val dot = Output.descriptorToDot(descriptor)
        val deobfuscated = deobfuscator_.deobfuscate(dot)
        return if (deobfuscated.indexOf('.') == -1) {
            // Classes in the unnamed package (e.g. primitive arrays)
            // will not appear in the output in the current PackageTree
            // implementation if classes are not included.  To work around,
            // we make an artificial package named "<unnamed>".
            "<unnamed>.$deobfuscated"
        } else {
            deobfuscated
        }
    }

    private fun classCount(type: Type): Int =
        classTotal_.getOrPut(type) {
            if (isClass_) {
                1
            } else {
                children_.values.sumBy {
                    when (type) {
                        REFERENCED -> it.classCount
                        DECLARED -> it.classCountDeclared
                    }
                }
            }
        }

    private fun methodCount(type: Type): Int =
        methodTotal_.getOrPut(type) { children_.values.sumBy(methods_[type]!!.size) {
            when (type) {
                REFERENCED -> it.methodCount
                DECLARED -> it.methodCountDeclared
            }
        } }

    private fun fieldCount(type: Type): Int =
        fieldTotal_.getOrPut(type) { children_.values.sumBy(fields_[type]!!.size) {
            when (type) {
                REFERENCED -> it.fieldCount
                DECLARED -> it.fieldCountDeclared
            }
        } }

    private enum class Type {
        DECLARED, REFERENCED
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PackageTree) return false

        if (this.name_ != other.name_) return false
        if (this.isClass_ != other.isClass_) return false
        if (this.methods_ != other.methods_) return false
        if (this.fields_ != other.fields_) return false
        if (this.children_ != other.children_) return false

        return true
    }

    override fun hashCode(): Int {
        var hashcode = 7
        hashcode = 31 * hashcode + name_.hashCode()
        hashcode = 31 * hashcode + (if (isClass_) 1 else 0)
        hashcode = 31 * hashcode + methods_.hashCode()
        hashcode = 31 * hashcode + fields_.hashCode()
        hashcode = 31 * hashcode + children_.hashCode()
        return hashcode
    }
}
