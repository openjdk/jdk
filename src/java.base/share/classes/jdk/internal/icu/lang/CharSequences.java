// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ********************************************************************************
 * Copyright (C) 2010-2014, Google, International Business Machines Corporation *
 * and others. All Rights Reserved.                                                 *
 ********************************************************************************
 */
package jdk.internal.icu.lang;


/**
 * A number of utilities for dealing with CharSequences and related classes.
 * For accessing codepoints with a CharSequence, also see
 * <ul>
 * <li>{@link java.lang.Character#codePointAt(CharSequence, int)}</li>
 * <li>{@link java.lang.Character#codePointBefore(CharSequence, int)}</li>
 * <li>{@link java.lang.Character#codePointCount(CharSequence, int, int)}</li>
 * <li>{@link java.lang.Character#charCount(int)}</li>
 * <li>{@link java.lang.Character#offsetByCodePoints(CharSequence, int, int)}</li>
 * <li>{@link java.lang.Character#toChars(int, char[], int)}</li>
 * <li>{@link java.lang.Character#toCodePoint(char, char)}</li>
 * </ul>
 * @author markdavis
 * @internal
 * <p>This API is ICU internal only.
 */
public class CharSequences {
    // TODO
    // compareTo(a, b);
    // compareToIgnoreCase(a, b)
    // contentEquals(a, b)
    // contentEqualsIgnoreCase(a, b)

    // contains(a, b) => indexOf >= 0
    // endsWith(a, b)
    // startsWith(a, b)

    // lastIndexOf(a, b, fromIndex)
    // indexOf(a, ch, fromIndex)
    // lastIndexOf(a, ch, fromIndex);

    // s.trim() => UnicodeSet.trim(CharSequence s); return a subsequence starting with the first character not in the set to the last character not in the set.
    // add UnicodeSet.split(CharSequence s);

    /**
     * Find the longest n such that a[aIndex,n] = b[bIndex,n], and n is on a character boundary.
     * @internal
     * This API is ICU internal only.
     */
    public static int matchAfter(CharSequence a, CharSequence b, int aIndex, int bIndex) {
        int i = aIndex, j = bIndex;
        int alen = a.length();
        int blen = b.length();
        for (; i < alen && j < blen; ++i, ++j) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (ca != cb) {
                break;
            }
        }
        // if we failed a match make sure that we didn't match half a character
        int result = i - aIndex;
        if (result != 0 && !onCharacterBoundary(a, i) && !onCharacterBoundary(b, j)) {
            --result; // backup
        }
        return result;
    }

    /**
     * Count the code point length. Unpaired surrogates count as 1.
     * @internal
     * This API is ICU internal only.
     */
    public int codePointLength(CharSequence s) {
        return Character.codePointCount(s, 0, s.length());
//        int length = s.length();
//        int result = length;
//        for (int i = 1; i < length; ++i) {
//            char ch = s.charAt(i);
//            if (0xDC00 <= ch && ch <= 0xDFFF) {
//                char ch0 = s.charAt(i-1);
//                if (0xD800 <= ch && ch <= 0xDbFF) {
//                    --result;
//                }
//            }
//        }
    }

    /**
     * Utility function for comparing codepoint to string without generating new
     * string.
     *
     * @internal
     * This API is ICU internal only.
     */
    public static final boolean equals(int codepoint, CharSequence other) {
        if (other == null) {
            return false;
        }
        switch (other.length()) {
        case 1: return codepoint == other.charAt(0);
        case 2: return codepoint > 0xFFFF && codepoint == Character.codePointAt(other, 0);
        default: return false;
        }
    }

    /**
     * @internal
     * This API is ICU internal only.
     */
    public static final boolean equals(CharSequence other, int codepoint) {
        return equals(codepoint, other);
    }

    /**
     * Utility to compare a string to a code point.
     * Same results as turning the code point into a string (with the [ugly] new StringBuilder().appendCodePoint(codepoint).toString())
     * and comparing, but much faster (no object creation).
     * Actually, there is one difference; a null compares as less.
     * Note that this (=String) order is UTF-16 order -- <i>not</i> code point order.
     *
     * @internal
     * This API is ICU internal only.
     */
    public static int compare(CharSequence string, int codePoint) {
        if (codePoint < Character.MIN_CODE_POINT || codePoint > Character.MAX_CODE_POINT) {
            throw new IllegalArgumentException();
        }
        int stringLength = string.length();
        if (stringLength == 0) {
            return -1;
        }
        char firstChar = string.charAt(0);
        int offset = codePoint - Character.MIN_SUPPLEMENTARY_CODE_POINT;

        if (offset < 0) { // BMP codePoint
            int result = firstChar - codePoint;
            if (result != 0) {
                return result;
            }
            return stringLength - 1;
        }
        // non BMP
        char lead = (char)((offset >>> 10) + Character.MIN_HIGH_SURROGATE);
        int result = firstChar - lead;
        if (result != 0) {
            return result;
        }
        if (stringLength > 1) {
            char trail = (char)((offset & 0x3ff) + Character.MIN_LOW_SURROGATE);
            result = string.charAt(1) - trail;
            if (result != 0) {
                return result;
            }
        }
        return stringLength - 2;
    }

    /**
     * Utility to compare a string to a code point.
     * Same results as turning the code point into a string and comparing, but much faster (no object creation).
     * Actually, there is one difference; a null compares as less.
     * Note that this (=String) order is UTF-16 order -- <i>not</i> code point order.
     *
     * @internal
     * This API is ICU internal only.
     */
    public static int compare(int codepoint, CharSequence a) {
        int result = compare(a, codepoint);
        return result > 0 ? -1 : result < 0 ? 1 : 0; // Reverse the order.
    }

    /**
     * Return the value of the first code point, if the string is exactly one code point. Otherwise return Integer.MAX_VALUE.
     *
     * @internal
     * This API is ICU internal only.
     */
    public static int getSingleCodePoint(CharSequence s) {
        int length = s.length();
        if (length < 1 || length > 2) {
            return Integer.MAX_VALUE;
        }
        int result = Character.codePointAt(s, 0);
        return (result < 0x10000) == (length == 1) ? result : Integer.MAX_VALUE;
    }

    /**
     * Utility function for comparing objects that may be null
     * string.
     *
     * @internal
     * This API is ICU internal only.
     */
    public static final <T extends Object> boolean equals(T a, T b) {
        return a == null ? b == null
                : b == null ? false
                        : a.equals(b);
    }

    /**
     * Utility for comparing the contents of CharSequences
     *
     * @internal
     * This API is ICU internal only.
     */
    public static int compare(CharSequence a, CharSequence b) {
        int alength = a.length();
        int blength = b.length();
        int min = alength <= blength ? alength : blength;
        for (int i = 0; i < min; ++i) {
            int diff = a.charAt(i) - b.charAt(i);
            if (diff != 0) {
                return diff;
            }
        }
        return alength - blength;
    }

    /**
     * Utility for comparing the contents of CharSequences
     *
     * @internal
     * This API is ICU internal only.
     */
    public static boolean equalsChars(CharSequence a, CharSequence b) {
        // do length test first for fast path
        return a.length() == b.length() && compare(a,b) == 0;
    }

    /**
     * Are we on a character boundary?
     *
     * @internal
     * This API is ICU internal only.
     */
    public static boolean onCharacterBoundary(CharSequence s, int i) {
        return i <= 0
        || i >= s.length()
        || !Character.isHighSurrogate(s.charAt(i-1))
        || !Character.isLowSurrogate(s.charAt(i));
    }

    /**
     * Find code point in string.
     *
     * @internal
     * This API is ICU internal only.
     */
    public static int indexOf(CharSequence s, int codePoint) {
        int cp;
        for (int i = 0; i < s.length(); i += Character.charCount(cp)) {
            cp = Character.codePointAt(s, i);
            if (cp == codePoint) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Utility function for simplified, more robust loops, such as:
     * <pre>
     *   for (int codePoint : CharSequences.codePoints(string)) {
     *     doSomethingWith(codePoint);
     *   }
     * </pre>
     *
     * @internal
     * This API is ICU internal only.
     */
    public static int[] codePoints(CharSequence s) {
        int[] result = new int[s.length()]; // in the vast majority of cases, the length is the same
        int j = 0;
        for (int i = 0; i < s.length(); ++i) {
            char cp = s.charAt(i);
            if (cp >= 0xDC00 && cp <= 0xDFFF && i != 0 ) { // hand-code for speed
                char last = (char) result[j-1];
                if (last >= 0xD800 && last <= 0xDBFF) {
                    // Note: j-1 is safe, because j can only be zero if i is zero. But i!=0 in this block.
                    result[j-1] = Character.toCodePoint(last, cp);
                    continue;
                }
            }
            result[j++] = cp;
        }
        if (j == result.length) {
            return result;
        }
        int[] shortResult = new int[j];
        System.arraycopy(result, 0, shortResult, 0, j);
        return shortResult;
    }

    private CharSequences() {
    }
}
