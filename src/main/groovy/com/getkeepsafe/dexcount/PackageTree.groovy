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

    PackageTree(name) {
        this(name, name.charAt(0).isUpperCase())
    }

    private PackageTree(name, isClass) {
        this.name_ = name
        this.isClass_ = isClass
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

    def printPackageListWithoutClasses(Appendable out) {
        printPackageList(out, false)
    }

    def printPackageListWithClasses(Appendable out) {
        printPackageList(out, true)
    }

    def printPackageList(Appendable out, boolean printClasses) {
        def sb = new StringBuilder(64)
        children_.values().each { it -> it.printPackageListRecursively(out, sb, printClasses) }
    }

    private def printPackageListRecursively(Appendable out, StringBuilder sb, boolean includeClasses) {
        def len = sb.length()
        if (len > 0) {
            sb.append(".")
        }
        sb.append(name_)

        if (!isClass_ || includeClasses) {
            out.append(String.format("%-8d %s\n", getMethodCount(), sb.toString()))
        }

        children_.values().each { it -> it.printPackageListRecursively(out, sb, includeClasses) }
        sb.setLength(len)
    }

    def printTree(Appendable out, boolean printClasses) {
        children_.values().each { it -> it.printTreeRecursively(out, 0, printClasses) }
    }

    private def printTreeRecursively(Appendable out, int indent, boolean printClasses) {
        if (printClasses || !isClass_) {
            indent.times { out.append("  ") }
            out.append(name_)
            out.append(" (")
            out.append(String.valueOf(getMethodCount()))
            out.append(")\n")
        }
        children_.values().each { it -> it.printTreeRecursively(out, indent + 1, printClasses) }
    }
}
