/*
 * Copyright (C) 2015 KeepSafe Software
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

    public def addMethodRef(String fullyQualifiedClassName) {
        addInternal(fullyQualifiedClassName, 0, true)
    }

    public def addFieldRef(String fullyQualifiedClassName) {
        addInternal(fullyQualifiedClassName, 0, false)
    }

    private def addInternal(String name, int startIndex, boolean isMethod) {
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

    def getMethodCount() {
        if (methodTotal_ == -1) {
            methodTotal_ = children_.values().inject(methodCount_) { sum, child -> sum + child.getMethodCount() }
        }
        return methodTotal_
    }

    def getFieldCount() {
        if (fieldTotal_ == -1) {
            fieldTotal_ = children_.values().inject(fieldCount_) { sum, child -> sum + child.getFieldCount() }
        }
        return fieldTotal_
    }

    def printPackageList(Appendable out, PrintOptions opts) {
        def sb = new StringBuilder(64)

        if (opts.printHeader) {
            printPackageListHeader(out, opts)
        }

        getChildren(opts).each { it -> it.printPackageListRecursively(out, sb, opts) }
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

    private def printPackageListRecursively(Appendable out, StringBuilder sb, PrintOptions opts) {
        def len = sb.length()
        if (len > 0) {
            sb.append(".")
        }
        sb.append(name_)

        if (!isClass_ || opts.includeClasses) {
            if (opts.includeMethodCount) {
                out.append(String.format("%-8d ", getMethodCount()))
            }

            if (opts.includeFieldCount) {
                out.append(String.format("%-8d ", getFieldCount()))
            }

            out.append(sb.toString())
            out.append('\n')
        }

        getChildren(opts).each { it -> it.printPackageListRecursively(out, sb, opts) }
        sb.setLength(len)
    }

    def printTree(Appendable out, PrintOptions opts) {
        getChildren(opts).each { it -> it.printTreeRecursively(out, 0, opts) }
    }

    private def printTreeRecursively(Appendable out, int indent, PrintOptions opts) {
        if (opts.includeClasses || !isClass_) {
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
        }

        getChildren(opts).each { it -> it.printTreeRecursively(out, indent + 1, opts) }
    }

    private def getChildren(PrintOptions opts) {
        if (opts.orderByMethodCount) {
            // Return the child nodes sorted in descending order by method count.
            return children_.values().sort(false, { -it.getMethodCount() })
        } else {
            return children_.values()
        }
    }

    private static def pluralizedMethods(int n) {
        return n == 1 ? "method" : "methods"
    }

    private static def pluralizeFields(int n) {
        return n == 1 ? "field" : "fields"
    }
}
