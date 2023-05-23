// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl.locale;

import jdk.internal.icu.impl.Utility;

public final class AsciiUtil {
    public static boolean caseIgnoreMatch(String s1, String s2) {
        if (Utility.sameObjects(s1, s2)) {
            return true;
        }
        int len = s1.length();
        if (len != s2.length()) {
            return false;
        }
        int i = 0;
        while (i < len) {
            char c1 = s1.charAt(i);
            char c2 = s2.charAt(i);
            if (c1 != c2 && toLower(c1) != toLower(c2)) {
                break;
            }
            i++;
        }
        return (i == len);
    }

    public static int caseIgnoreCompare(String s1, String s2) {
        if (Utility.sameObjects(s1, s2)) {
            return 0;
        }
        return AsciiUtil.toLowerString(s1).compareTo(AsciiUtil.toLowerString(s2));
    }


    public static char toUpper(char c) {
        if (c >= 'a' && c <= 'z') {
            c -= 0x20;
        }
        return c;
    }

    public static char toLower(char c) {
        if (c >= 'A' && c <= 'Z') {
            c += 0x20;
        }
        return c;
    }

    public static String toLowerString(String s) {
        int idx = 0;
        for (; idx < s.length(); idx++) {
            char c = s.charAt(idx);
            if (c >= 'A' && c <= 'Z') {
                break;
            }
        }
        if (idx == s.length()) {
            return s;
        }
        StringBuilder buf = new StringBuilder(s.substring(0, idx));
        for (; idx < s.length(); idx++) {
            buf.append(toLower(s.charAt(idx)));
        }
        return buf.toString();
    }

    public static String toUpperString(String s) {
        int idx = 0;
        for (; idx < s.length(); idx++) {
            char c = s.charAt(idx);
            if (c >= 'a' && c <= 'z') {
                break;
            }
        }
        if (idx == s.length()) {
            return s;
        }
        StringBuilder buf = new StringBuilder(s.substring(0, idx));
        for (; idx < s.length(); idx++) {
            buf.append(toUpper(s.charAt(idx)));
        }
        return buf.toString();
    }

    public static String toTitleString(String s) {
        if (s.length() == 0) {
            return s;
        }
        int idx = 0;
        char c = s.charAt(idx);
        if (!(c >= 'a' && c <= 'z')) {
            for (idx = 1; idx < s.length(); idx++) {
                if (c >= 'A' && c <= 'Z') {
                    break;
                }
            }
        }
        if (idx == s.length()) {
            return s;
        }
        StringBuilder buf = new StringBuilder(s.substring(0, idx));
        if (idx == 0) {
            buf.append(toUpper(s.charAt(idx)));
            idx++;
        }
        for (; idx < s.length(); idx++) {
            buf.append(toLower(s.charAt(idx)));
        }
        return buf.toString();
    }

    public static boolean isAlpha(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    public static boolean isAlphaString(String s) {
        boolean b = true;
        for (int i = 0; i < s.length(); i++) {
            if (!isAlpha(s.charAt(i))) {
                b = false;
                break;
            }
        }
        return b;
    }

    public static boolean isNumeric(char c) {
        return (c >= '0' && c <= '9');
    }

    public static boolean isNumericString(String s) {
        boolean b = true;
        for (int i = 0; i < s.length(); i++) {
            if (!isNumeric(s.charAt(i))) {
                b = false;
                break;
            }
        }
        return b;
    }

    public static boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isNumeric(c);
    }

    public static boolean isAlphaNumericString(String s) {
        boolean b = true;
        for (int i = 0; i < s.length(); i++) {
            if (!isAlphaNumeric(s.charAt(i))) {
                b = false;
                break;
            }
        }
        return b;
    }

    public static class CaseInsensitiveKey {
        private String _key;
        private int _hash;

        public CaseInsensitiveKey(String key) {
            _key = key;
            _hash = AsciiUtil.toLowerString(key).hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof CaseInsensitiveKey) {
                return AsciiUtil.caseIgnoreMatch(_key, ((CaseInsensitiveKey)o)._key);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return _hash;
        }
    }
}
