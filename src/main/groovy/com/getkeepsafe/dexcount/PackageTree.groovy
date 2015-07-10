package com.getkeepsafe.dexcount

class PackageTree {
    // A cached sum of this node and all children's method ref counts.
    // -1 means that there is no cached value.  Set by `getCount()`, and
    // invalidated by adding new nodes.
    private int total_ = -1

    // The local count of method refs.  Will be zero for package nodes and
    // non-zero for class nodes.
    private int count_ = 0

    private final boolean isClass_
    private final String name_
    private final SortedMap<String, PackageTree> children_ = new TreeMap<>()

    PackageTree() {
        this.name_ = ""
        this.isClass_ = false
    }

    private PackageTree(name) {
        this.name_ = name
        this.isClass_ = name.charAt(0).isUpperCase()
    }

    public def add(String fullyQualifiedClassName) {
        addInternal(fullyQualifiedClassName, 0)
    }

    private def addInternal(String name, int startIndex) {
        def ix = name.indexOf('.', startIndex)
        def segment = ix == -1 ? name.substring(startIndex) : name.substring(startIndex, ix)
        def child = children_[segment]
        if (child == null) {
            child = children_[segment] = new PackageTree(segment)
        }

        if (ix == -1) {
            child.count_++
        } else {
            total_ = -1
            child.addInternal(name, ix + 1)
        }
    }

    def getCount() {
        if (total_ == -1) {
            total_ = children_.values().inject(count_, { sum, child -> sum + child.getCount() })
        }
        return total_
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
            out.append(String.format("%-8d %s\n", getCount(), sb.toString()))
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
            out.append(String.valueOf(getCount()))
            out.append(")\n")
        }
        children_.values().each { it -> it.printTreeRecursively(out, indent + 1, printClasses) }
    }
}
