/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.dexdeps;

public class FieldRef implements HasDeclaringClass {
    private String mDeclClass, mFieldType, mFieldName;

    /**
     * Initializes a new field reference.
     * @param declClass class name
     * @param fieldType field type
     * @param fieldName field name
     */
    public FieldRef(String declClass, String fieldType, String fieldName) {
        mDeclClass = declClass;
        mFieldType = fieldType;
        mFieldName = fieldName;
    }

    /**
     * Gets the name of the field's declaring class.
     * @return class name
     */
    public String getDeclClassName() {
        return mDeclClass;
    }

    /**
     * Gets the type name.  Examples: "Ljava/lang/String;", "[I".
     * @return type name
     */
    public String getTypeName() {
        return mFieldType;
    }

    /**
     * Gets the field name.
     * @return field name
     */
    public String getName() {
        return mFieldName;
    }
}
