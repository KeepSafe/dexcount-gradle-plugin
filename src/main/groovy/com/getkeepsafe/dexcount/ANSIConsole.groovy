/*
 * Copyright (C) 2016 KeepSafe Software
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

class ANSIConsole {
    private static final String CODE_PREFIX = "\u001b["
    private static final String CODE_SUFFIX = "m"
    private static final String CODE_CLEAR = CODE_PREFIX + CODE_SUFFIX

    public enum Color {
        BLACK(30),
        RED(31),
        GREEN(32),
        YELLOW(33),
        BLUE(34),
        MAGENTA(35),
        CYAN(36),
        WHITE(37),
        ;

        public int value

        Color(int value) {
            this.value = value
        }
    }

    static public String colorize(Color color, boolean bright = false, String str) {
        def brightOption = bright ? "1" : "0"
        return "$CODE_PREFIX$brightOption;$color.value$CODE_SUFFIX$str$CODE_CLEAR"
    }
}