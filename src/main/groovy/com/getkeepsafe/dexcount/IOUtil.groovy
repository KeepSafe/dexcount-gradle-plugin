package com.getkeepsafe.dexcount

class IOUtil {
    static def drainToFile(InputStream stream, File file) {
        stream.withStream { input ->
            file.withOutputStream { output ->
                def buf = new byte[4096]
                def read
                while ((read = input.read(buf)) != -1) {
                    output.write(buf, 0, read)
                }
                output.flush()
            }
        }
    }
}
