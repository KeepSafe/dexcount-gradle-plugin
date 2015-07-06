package com.getkeepsafe.dexcount

class PackageTree {
    // A cached sum of this node and all children's method ref counts.
    // -1 means that there is no cached value.  Set by `getCount()`, and
    // invalidated by adding new nodes.
    private int total = -1

    // The local count of method refs.  Will be zero for package nodes and
    // non-zero for class nodes.
    private int count = 0

    private boolean isClass
    private final String name
    private final SortedMap<String, PackageTree> children = new TreeMap<>()

    PackageTree() {
        name = ""
    }

    private PackageTree(name) {
        this.name = name
        this.isClass = name.charAt(0).isUpperCase()
    }

    public def add(String fullyQualifiedClassName) {
        def parts = fullyQualifiedClassName.split("\\.")
        def queue = new ArrayDeque<String>(parts.length)
        for (int i = 0; i < parts.length; ++i) {
            if (!parts[i].isEmpty()) {
                queue.add(parts[i])
            }
        }

        addInternal(queue)
    }

    private def addInternal(Queue<String> parts) {
        // Computed totals get invalidated
        total = -1

        if (parts.size() == 0) {
            isClass = true
            ++count
            return
        }

        def part = parts.remove()
        def child = children[part]
        if (child == null) {
            children[part] = child = new PackageTree(part)
        }
        child.addInternal(parts)
    }

    def getCount() {
        if (total == -1) {
            total = children.values().inject(count, { sum, child -> sum + child.getCount() })
        }
        return total
    }

    def printPackageList(PrintWriter out) {
        def sb = new StringBuilder(64)
        children.values().each { it -> it.printPackageListRecursively(out, sb, false) }
    }

    def printPackageListWithClasses(PrintWriter out) {
        def sb = new StringBuilder(64)
        children.values().each { it -> it.printPackageListRecursively(out, sb, true) }
    }

    private def printPackageListRecursively(PrintWriter out, StringBuilder sb, boolean includeClasses) {
        def len = sb.length()
        if (len > 0) {
            sb.append(".")
        }
        sb.append(name)

        if (!isClass || includeClasses) {
            out.format("%-8d %s\n", getCount(), sb.toString())
        }

        children.values().each { it -> it.printPackageListRecursively(out, sb, includeClasses) }
        sb.setLength(len)
    }

    def printTree(PrintWriter out) {
        children.values().each { it -> it.printTreeRecursively(out, 0) }
    }

    private def printTreeRecursively(PrintWriter out, int indent) {
        indent.times { out.print("  ") }
        out.print(name)
        out.print(" (")
        out.print(getCount())
        out.print(")")
        out.println()
        children.values().each { it -> it.printTreeRecursively(out, indent + 1) }
    }
}
