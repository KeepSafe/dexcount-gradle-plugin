package com.getkeepsafe.dexcount

/**
 * Configuration properties for {@link DexMethodCountTask} instances.
 */
class DexMethodCountExtension {
    private boolean includeClasses
    private boolean orderByMethodCount
    private boolean includeFieldCount
    private boolean printAsTree
    private boolean verbose

    /**
     * When true, includes individual classes in task output.
     * When false, only packages are included.
     */
    public boolean getIncludeClasses() {
        return includeClasses
    }

    public void setIncludeClasses(boolean includeClasses) {
        this.includeClasses = includeClasses
    }

    /**
     * When true, the task output is formatted as a package tree, with
     * indentation indicating the package hierarchy.
     */
    public boolean getPrintAsTree() {
        return printAsTree
    }

    public void setPrintAsTree(boolean printAsTree) {
        this.printAsTree = printAsTree
    }

    /**
     * When true, the task's output file is printed to the build's standard
     * output.
     */
    public boolean getVerbose() {
        return verbose
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose
    }

    /**
     * When true, the number of fields in a package or a class is also included
     * in the task's output.
     */
    public boolean getIncludeFieldCount() {
        return includeFieldCount
    }

    public void setIncludeFieldCount(boolean countFields) {
        this.includeFieldCount = countFields
    }

    /**
     * When true, the task's output list is sorted in descending order by
     * method counts, with larger packages appearing higher in the list.
     */
    public boolean getOrderByMethodCount() {
        return this.orderByMethodCount
    }

    public void setOrderByMethodCount(boolean orderByMethodCount) {
        this.orderByMethodCount = orderByMethodCount
    }
}
