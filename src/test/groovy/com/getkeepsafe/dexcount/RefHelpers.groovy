package com.getkeepsafe.dexcount

import com.android.dexdeps.FieldRef
import com.android.dexdeps.MethodRef

class RefHelpers {


    static MethodRef methodRef(String className, String methodName = null) {
        if (methodName == null) {
            methodName = randomName()
        }
        return new MethodRef(className, new String[0], "Object", methodName)
    }

    static FieldRef fieldRef(String className, String fieldName = null) {
        if (fieldName == null) {
            fieldName = randomName()
        }
        return new FieldRef(className, "Object", fieldName)
    }

    private static String randomName() {
        def rand = new Random()
        def alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890"
        def len = rand.nextInt(16)
        def sb = new StringBuilder(len)
        for (int i = 0; i < len; ++i) {
            sb.append(alphabet.charAt(rand.nextInt(alphabet.length())))
        }
        return sb.toString()
    }
}
