package org.radarmc.link.core.text;

public final class Strings {
    private Strings() {}

    public static String strip(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && Character.isWhitespace(s.codePointAt(start))) {
            start += Character.charCount(s.codePointAt(start));
        }
        while (end > start && Character.isWhitespace(s.codePointBefore(end))) {
            end -= Character.charCount(s.codePointBefore(end));
        }
        return s.substring(start, end);
    }

    public static boolean isBlank(String s) {
        return s == null || strip(s).isEmpty();
    }

    public static String truncate(String s, int maxLength) {
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }
}
