package com.getkeepsafe.dexcount

class DexMethodCountExtension {
    private boolean includeClasses
    private boolean orderBy
    private boolean countFields
    private boolean printAsTree
    private boolean verbose

    public boolean getIncludeClasses() {
        return includeClasses
    }

    public void setIncludeClasses(boolean includeClasses) {
        this.includeClasses = includeClasses
    }

    public boolean getPrintAsTree() {
        return printAsTree
    }

    public void setPrintAsTree(boolean printAsTree) {
        this.printAsTree = printAsTree
    }

    public boolean getVerbose() {
        return verbose
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose
    }

    public boolean getCountFields() {
        return countFields
    }

    public void setCountFields(boolean countFields) {
        this.countFields = countFields
    }
}
