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
        def url = Thread.currentThread().getContextClassLoader().getResource("./")
        def files = new File(url.toURI()).listFiles()
        for (file in files) {
            System.err.println(file.absolutePath)
        }
        if (!aarFile.exists()) {
            // couldn't read directly off file system if we're in a jar, so pull resources and drop them into a temp file
            // This is for TravisCI, specifically
            aarFile = File.createTempFile("test", ".aar")
            aarFile.deleteOnExit()
            def buf = new byte[4096]
            Thread.currentThread().getContextClassLoader().getResourceAsStream('android-beacon-library-2.7.aar').withStream { input ->
                aarFile.withOutputStream { output ->
                    def read
                    while ((read = input.read(buf)) != -1) {
                        output.write(buf, 0, read)
                    }
                    output.flush()
                    output.close()
                }
            }
        }
        def dexFiles = DexFile.extractDexData(aarFile)

        then:
        dexFiles != null
        dexFiles.size() == 1
        dexFiles[0].methodRefs.size() == 982
        dexFiles[0].fieldRefs.size() == 436
    }
}
