package com.getkeepsafe.dexcount;

import java.io.File;

public final class StringUtils {
    private StringUtils() {
        // no instances
    }

    public static String capitalize(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(text.length());
        sb.appendCodePoint(Character.toTitleCase(text.codePointAt(0)));
        text.codePoints().skip(1).forEachOrdered(sb::appendCodePoint);

        return sb.toString();
    }

    public static String removeExtension(String filePath) {
        int lastSeparator = filePath.lastIndexOf(File.separatorChar);
        int lastDot = filePath.lastIndexOf('.');

        if (lastDot >= 0 && lastDot > lastSeparator) {
            return filePath.substring(0, lastDot);
        } else {
            return filePath;
        }
    }
}
