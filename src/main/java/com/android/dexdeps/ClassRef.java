/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.dexdeps;

import java.util.ArrayList;

public class ClassRef {
    private String mClassName;
    private ArrayList<FieldRef> mFieldRefs;
    private ArrayList<MethodRef> mMethodRefs;

    /**
     * Initializes a new class reference.
     * @param className name of class
     */
    public ClassRef(String className) {
        mClassName = className;
        mFieldRefs = new ArrayList<FieldRef>();
        mMethodRefs = new ArrayList<MethodRef>();
    }

    /**
     * Adds the field to the field list.
     * @param fref field reference
     */
    public void addField(FieldRef fref) {
        mFieldRefs.add(fref);
    }

    /**
     * @return Returns the field list as an array.
     */
    public FieldRef[] getFieldArray() {
        return mFieldRefs.toArray(new FieldRef[mFieldRefs.size()]);
    }

    /**
     * Adds the method to the method list.
     * @param mref method reference
     */
    public void addMethod(MethodRef mref) {
        mMethodRefs.add(mref);
    }

    /**
     * @return Returns the method list as an array.
     */
    public MethodRef[] getMethodArray() {
        return mMethodRefs.toArray(new MethodRef[mMethodRefs.size()]);
    }

    /**
     * Gets the class name.
     * @return class name
     */
    public String getName() {
        return mClassName;
    }
}
