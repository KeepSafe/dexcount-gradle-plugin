/*
 * Copyright (C) 2015-2016 KeepSafe Software
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

import com.android.dexdeps.FieldRef
import com.android.dexdeps.MethodRef

final class RefHelpers {
    private RefHelpers() {
        throw new AssertionError('No instances')
    }

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

    static String randomName() {
        def rand = new Random()
        def alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890"
        def len = 16
        def sb = new StringBuilder(len)
        for (int i = 0; i < len; ++i) {
            sb.append(alphabet.charAt(rand.nextInt(alphabet.length())))
        }
        return sb.toString()
    }
}
