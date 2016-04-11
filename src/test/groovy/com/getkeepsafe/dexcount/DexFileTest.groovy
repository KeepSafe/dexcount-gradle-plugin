package com.getkeepsafe.dexcount;

import spock.lang.Specification

class DexFileTest extends Specification {
    def "test AAR dexcount"() {
        setup:
        def currentDir = new File(".").getAbsolutePath()

        when:
        def aarFile = new File(currentDir + File.separatorChar + "src"
                + File.separatorChar + "test" + File.separatorChar + "resources"
                + File.separatorChar + "android-beacon-library-2.7.aar")
        def dexFiles = DexFile.extractDexData(aarFile)

        then:
        dexFiles != null
        dexFiles.size() == 1
        dexFiles[0].methodRefs.size() == 982
        dexFiles[0].fieldRefs.size() == 436
    }
}
