/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang;

import java.util.Arrays;
import java.util.Locale;
import java.util.Spliterator;
import java.util.function.IntConsumer;
import jdk.internal.HotSpotIntrinsicCandidate;

import static java.lang.String.UTF16;
import static java.lang.String.LATIN1;
import static java.lang.String.checkIndex;
import static java.lang.String.checkOffset;
import static java.lang.String.checkBoundsOffCount;

final class StringUTF16 {

    public static byte[] newBytesFor(int len) {
        if (len < 0) {
            throw new NegativeArraySizeException();
        }
        if (len > MAX_LENGTH) {
            throw new OutOfMemoryError("UTF16 String size is " + len +
                                       ", should be less than " + MAX_LENGTH);
        }
        return new byte[len << 1];
    }

    @HotSpotIntrinsicCandidate
    public static void putChar(byte[] val, int index, int c) {
        index <<= 1;
        val[index++] = (byte)(c >> HI_BYTE_SHIFT);
        val[index]   = (byte)(c >> LO_BYTE_SHIFT);
    }

    @HotSpotIntrinsicCandidate
    public static char getChar(byte[] val, int index) {
        index <<= 1;
        return (char)(((val[index++] & 0xff) << HI_BYTE_SHIFT) |
                      ((val[index]   & 0xff) << LO_BYTE_SHIFT));
    }

    public static char charAt(byte[] value, int index) {
        if (index < 0 || index >= value.length >> 1) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return getChar(value, index);
    }

    public static int length(byte[] value) {
        return value.length >> 1;
    }

    public static int codePointAt(byte[] value, int index, int end) {
        char c1 = getChar(value, index);
        if (Character.isHighSurrogate(c1) && ++index < end) {
            char c2 = getChar(value, index);
            if (Character.isLowSurrogate(c2)) {
               return Character.toCodePoint(c1, c2);
            }
        }
        return c1;
    }

    public static int codePointBefore(byte[] value, int index) {
        char c2 = getChar(value, --index);
        if (Character.isLowSurrogate(c2) && index > 0) {
            char c1 = getChar(value, --index);
            if (Character.isHighSurrogate(c1)) {
               return Character.toCodePoint(c1, c2);
            }
        }
        return c2;
    }

    public static int codePointCount(byte[] value, int beginIndex, int endIndex) {
        int count = endIndex - beginIndex;
        for (int i = beginIndex; i < endIndex; ) {
            if (Character.isHighSurrogate(getChar(value, i++)) &&
                i < endIndex &&
                Character.isLowSurrogate(getChar(value, i))) {
                count--;
                i++;
            }
        }
        return count;
    }

    public static char[] toChars(byte[] value) {
        char[] dst = new char[value.length >> 1];
        getChars(value, 0, dst.length, dst, 0);
        return dst;
    }

    @HotSpotIntrinsicCandidate
    public static byte[] toBytes(char[] value, int off, int len) {
        byte[] val = newBytesFor(len);
        for (int i = 0; i < len; i++) {
            putChar(val, i, value[off]);
            off++;
        }
        return val;
    }

    public static byte[] compress(char[] val, int off, int len) {
        byte[] ret = new byte[len];
        if (compress(val, off, ret, 0, len) == len) {
            return ret;
        }
        return null;
    }

    public static byte[] compress(byte[] val, int off, int len) {
        byte[] ret = new byte[len];
        if (compress(val, off, ret, 0, len) == len) {
            return ret;
        }
        return null;
    }

    // compressedCopy char[] -> byte[]
    @HotSpotIntrinsicCandidate
    private static int compress(char[] src, int srcOff, byte[] dst, int dstOff, int len) {
        for (int i = 0; i < len; i++) {
            char c = src[srcOff];
            if (c > 0xFF) {
                len = 0;
                break;
            }
            dst[dstOff] = (byte)c;
            srcOff++;
            dstOff++;
        }
        return len;
    }

    // compressedCopy byte[] -> byte[]
    @HotSpotIntrinsicCandidate
    public static int compress(byte[] src, int srcOff, byte[] dst, int dstOff, int len) {
        // We need a range check here because 'getChar' has no checks
        checkBoundsOffCount(srcOff, len, src.length);
        for (int i = 0; i < len; i++) {
            char c = getChar(src, srcOff);
            if (c > 0xFF) {
                len = 0;
                break;
            }
            dst[dstOff] = (byte)c;
            srcOff++;
            dstOff++;
        }
        return len;
    }

    public static byte[] toBytes(int[] val, int index, int len) {
        final int end = index + len;
        // Pass 1: Compute precise size of char[]
        int n = len;
        for (int i = index; i < end; i++) {
            int cp = val[i];
            if (Character.isBmpCodePoint(cp))
                continue;
            else if (Character.isValidCodePoint(cp))
                n++;
            else throw new IllegalArgumentException(Integer.toString(cp));
        }
        // Pass 2: Allocate and fill in <high, low> pair
        byte[] buf = newBytesFor(n);
        for (int i = index, j = 0; i < end; i++, j++) {
            int cp = val[i];
            if (Character.isBmpCodePoint(cp)) {
                putChar(buf, j, cp);
            } else {
                putChar(buf, j++, Character.highSurrogate(cp));
                putChar(buf, j, Character.lowSurrogate(cp));
            }
        }
        return buf;
    }

    public static byte[] toBytes(char c) {
        byte[] result = new byte[2];
        putChar(result, 0, c);
        return result;
    }

    @HotSpotIntrinsicCandidate
    public static void getChars(byte[] value, int srcBegin, int srcEnd, char dst[], int dstBegin) {
        // We need a range check here because 'getChar' has no checks
        checkBoundsOffCount(srcBegin, srcEnd - srcBegin, value.length);
        for (int i = srcBegin; i < srcEnd; i++) {
            dst[dstBegin++] = getChar(value, i);
        }
    }

    /* @see java.lang.String.getBytes(int, int, byte[], int) */
    public static void getBytes(byte[] value, int srcBegin, int srcEnd, byte dst[], int dstBegin) {
        srcBegin <<= 1;
        srcEnd <<= 1;
        for (int i = srcBegin + (1 >> LO_BYTE_SHIFT); i < srcEnd; i += 2) {
            dst[dstBegin++] = value[i];
        }
    }

    @HotSpotIntrinsicCandidate
    public static boolean equals(byte[] value, byte[] other) {
        if (value.length == other.length) {
            int len = value.length >> 1;
            for (int i = 0; i < len; i++) {
                if (getChar(value, i) != getChar(other, i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @HotSpotIntrinsicCandidate
    public static int compareTo(byte[] value, byte[] other) {
        int len1 = length(value);
        int len2 = length(other);
        int lim = Math.min(len1, len2);
        for (int k = 0; k < lim; k++) {
            char c1 = getChar(value, k);
            char c2 = getChar(other, k);
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return len1 - len2;
    }

    @HotSpotIntrinsicCandidate
    public static int compareToLatin1(byte[] value, byte[] other) {
        int len1 = length(value);
        int len2 = StringLatin1.length(other);
        int lim = Math.min(len1, len2);
        for (int k = 0; k < lim; k++) {
            char c1 = getChar(value, k);
            char c2 = StringLatin1.getChar(other, k);
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return len1 - len2;
    }

    public static int hashCode(byte[] value) {
        int h = 0;
        int length = value.length >> 1;
        for (int i = 0; i < length; i++) {
            h = 31 * h + getChar(value, i);
        }
        return h;
    }

    public static int indexOf(byte[] value, int ch, int fromIndex) {
        int max = value.length >> 1;
        if (fromIndex < 0) {
            fromIndex = 0;
        } else if (fromIndex >= max) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }
        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            // handle most cases here (ch is a BMP code point or a
            // negative value (invalid code point))
            return indexOfChar(value, ch, fromIndex, max);
        } else {
            return indexOfSupplementary(value, ch, fromIndex, max);
        }
    }

    @HotSpotIntrinsicCandidate
    public static int indexOf(byte[] value, byte[] str) {
        if (str.length == 0) {
            return 0;
        }
        if (value.length == 0) {
            return -1;
        }
        return indexOf(value, length(value), str, length(str), 0);
    }

    @HotSpotIntrinsicCandidate
    public static int indexOf(byte[] value, int valueCount, byte[] str, int strCount, int fromIndex) {
        char first = getChar(str, 0);
        int max = (valueCount - strCount);
        for (int i = fromIndex; i <= max; i++) {
            // Look for first character.
            if (getChar(value, i) != first) {
                while (++i <= max && getChar(value, i) != first);
            }
            // Found first character, now look at the rest of value
            if (i <= max) {
                int j = i + 1;
                int end = j + strCount - 1;
                for (int k = 1; j < end && getChar(value, j) == getChar(str, k); j++, k++);
                if (j == end) {
                    // Found whole string.
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Handles indexOf Latin1 substring in UTF16 string.
     */
    @HotSpotIntrinsicCandidate
    public static int indexOfLatin1(byte[] value, byte[] str) {
        if (str.length == 0) {
            return 0;
        }
        if (value.length == 0) {
            return -1;
        }
        return indexOfLatin1(value, length(value), str, str.length, 0);
    }

    @HotSpotIntrinsicCandidate
    public static int indexOfLatin1(byte[] src, int srcCount, byte[] tgt, int tgtCount, int fromIndex) {
        char first = (char)(tgt[0] & 0xff);
        int max = (srcCount - tgtCount);
        for (int i = fromIndex; i <= max; i++) {
            // Look for first character.
            if (getChar(src, i) != first) {
                while (++i <= max && getChar(src, i) != first);
            }
            // Found first character, now look at the rest of v2
            if (i <= max) {
                int j = i + 1;
                int end = j + tgtCount - 1;
                for (int k = 1;
                     j < end && getChar(src, j) == (tgt[k] & 0xff);
                     j++, k++);
                if (j == end) {
                    // Found whole string.
                    return i;
                }
            }
        }
        return -1;
    }

    @HotSpotIntrinsicCandidate
    private static int indexOfChar(byte[] value, int ch, int fromIndex, int max) {
        for (int i = fromIndex; i < max; i++) {
            if (getChar(value, i) == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Handles (rare) calls of indexOf with a supplementary character.
     */
    private static int indexOfSupplementary(byte[] value, int ch, int fromIndex, int max) {
        if (Character.isValidCodePoint(ch)) {
            final char hi = Character.highSurrogate(ch);
            final char lo = Character.lowSurrogate(ch);
            for (int i = fromIndex; i < max - 1; i++) {
                if (getChar(value, i) == hi && getChar(value, i + 1 ) == lo) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int lastIndexOf(byte[] src, int srcCount,
                                  byte[] tgt, int tgtCount, int fromIndex) {
        int min = tgtCount - 1;
        int i = min + fromIndex;
        int strLastIndex = tgtCount - 1;
        char strLastChar = getChar(tgt, strLastIndex);

    startSearchForLastChar:
        while (true) {
            while (i >= min && getChar(src, i) != strLastChar) {
                i--;
            }
            if (i < min) {
                return -1;
            }
            int j = i - 1;
            int start = j - strLastIndex;
            int k = strLastIndex - 1;
            while (j > start) {
                if (getChar(src, j--) != getChar(tgt, k--)) {
                    i--;
                    continue startSearchForLastChar;
                }
            }
            return start + 1;
        }
    }

    public static int lastIndexOf(byte[] value, int ch, int fromIndex) {
        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            // handle most cases here (ch is a BMP code point or a
            // negative value (invalid code point))
            int i = Math.min(fromIndex, (value.length >> 1) - 1);
            for (; i >= 0; i--) {
                if (getChar(value, i) == ch) {
                    return i;
                }
            }
            return -1;
        } else {
            return lastIndexOfSupplementary(value, ch, fromIndex);
        }
    }

    /**
     * Handles (rare) calls of lastIndexOf with a supplementary character.
     */
    private static int lastIndexOfSupplementary(final byte[] value, int ch, int fromIndex) {
        if (Character.isValidCodePoint(ch)) {
            char hi = Character.highSurrogate(ch);
            char lo = Character.lowSurrogate(ch);
            int i = Math.min(fromIndex, (value.length >> 1) - 2);
            for (; i >= 0; i--) {
                if (getChar(value, i) == hi && getChar(value, i + 1) == lo) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static String replace(byte[] value, char oldChar, char newChar) {
        int len = value.length >> 1;
        int i = -1;
        while (++i < len) {
            if (getChar(value, i) == oldChar) {
                break;
            }
        }
        if (i < len) {
            byte buf[] = new byte[value.length];
            for (int j = 0; j < i; j++) {
                putChar(buf, j, getChar(value, j)); // TBD:arraycopy?
            }
            while (i < len) {
                char c = getChar(value, i);
                putChar(buf, i, c == oldChar ? newChar : c);
                i++;
           }
           // Check if we should try to compress to latin1
           if (String.COMPACT_STRINGS &&
               !StringLatin1.canEncode(oldChar) &&
               StringLatin1.canEncode(newChar)) {
               byte[] val = compress(buf, 0, len);
               if (val != null) {
                   return new String(val, LATIN1);
               }
           }
           return new String(buf, UTF16);
        }
        return null;
    }

    public static boolean regionMatchesCI(byte[] value, int toffset,
                                          byte[] other, int ooffset, int len) {
        int last = toffset + len;
        while (toffset < last) {
            char c1 = getChar(value, toffset++);
            char c2 = getChar(other, ooffset++);
            if (c1 == c2) {
                continue;
            }
            // try converting both characters to uppercase.
            // If the results match, then the comparison scan should
            // continue.
            char u1 = Character.toUpperCase(c1);
            char u2 = Character.toUpperCase(c2);
            if (u1 == u2) {
                continue;
            }
            // Unfortunately, conversion to uppercase does not work properly
            // for the Georgian alphabet, which has strange rules about case
            // conversion.  So we need to make one last check before
            // exiting.
            if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static boolean regionMatchesCI_Latin1(byte[] value, int toffset,
                                                 byte[] other, int ooffset,
                                                 int len) {
        int last = toffset + len;
        while (toffset < last) {
            char c1 = getChar(value, toffset++);
            char c2 = (char)(other[ooffset++] & 0xff);
            if (c1 == c2) {
                continue;
            }
            char u1 = Character.toUpperCase(c1);
            char u2 = Character.toUpperCase(c2);
            if (u1 == u2) {
                continue;
            }
            if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static String toLowerCase(String str, byte[] value, Locale locale) {
        if (locale == null) {
            throw new NullPointerException();
        }
        int first;
        boolean hasSurr = false;
        final int len = value.length >> 1;

        // Now check if there are any characters that need to be changed, or are surrogate
        for (first = 0 ; first < len; first++) {
            int cp = (int)getChar(value, first);
            if (Character.isSurrogate((char)cp)) {
                hasSurr = true;
                break;
            }
            if (cp != Character.toLowerCase(cp)) {  // no need to check Character.ERROR
                break;
            }
        }
        if (first == len)
            return str;
        byte[] result = new byte[value.length];
        System.arraycopy(value, 0, result, 0, first << 1);  // Just copy the first few
                                                            // lowerCase characters.
        String lang = locale.getLanguage();
        if (lang == "tr" || lang == "az" || lang == "lt") {
            return toLowerCaseEx(str, value, result, first, locale, true);
        }
        if (hasSurr) {
            return toLowerCaseEx(str, value, result, first, locale, false);
        }
        int bits = 0;
        for (int i = first; i < len; i++) {
            int cp = (int)getChar(value, i);
            if (cp == '\u03A3' ||                       // GREEK CAPITAL LETTER SIGMA
                Character.isSurrogate((char)cp)) {
                return toLowerCaseEx(str, value, result, i, locale, false);
            }
            if (cp == '\u0130') {                       // LATIN CAPITAL LETTER I WITH DOT ABOVE
                return toLowerCaseEx(str, value, result, i, locale, true);
            }
            cp = Character.toLowerCase(cp);
            if (!Character.isBmpCodePoint(cp)) {
                return toLowerCaseEx(str, value, result, i, locale, false);
            }
            bits |= cp;
            putChar(result, i, cp);
        }
        if (bits > 0xFF) {
            return new String(result, UTF16);
        } else {
            return newString(result, 0, len);
        }
    }

    private static String toLowerCaseEx(String str, byte[] value,
                                        byte[] result, int first, Locale locale,
                                        boolean localeDependent) {
        int resultOffset = first;
        int length = value.length >> 1;
        int srcCount;
        for (int i = first; i < length; i += srcCount) {
            int srcChar = getChar(value, i);
            int lowerChar;
            char[] lowerCharArray;
            srcCount = 1;
            if (Character.isSurrogate((char)srcChar)) {
                srcChar = codePointAt(value, i, length);
                srcCount = Character.charCount(srcChar);
            }
            if (localeDependent ||
                srcChar == '\u03A3' ||  // GREEK CAPITAL LETTER SIGMA
                srcChar == '\u0130') {  // LATIN CAPITAL LETTER I WITH DOT ABOVE
                lowerChar = ConditionalSpecialCasing.toLowerCaseEx(str, i, locale);
            } else {
                lowerChar = Character.toLowerCase(srcChar);
            }
            if (Character.isBmpCodePoint(lowerChar)) {    // Character.ERROR is not a bmp
                putChar(result, resultOffset++, lowerChar);
            } else {
                if (lowerChar == Character.ERROR) {
                    lowerCharArray = ConditionalSpecialCasing.toLowerCaseCharArray(str, i, locale);
                } else {
                    lowerCharArray = Character.toChars(lowerChar);
                }
                /* Grow result if needed */
                int mapLen = lowerCharArray.length;
                if (mapLen > srcCount) {
                    byte[] result2 = newBytesFor((result.length >> 1) + mapLen - srcCount);
                    System.arraycopy(result, 0, result2, 0, resultOffset << 1);
                    result = result2;
                }
                for (int x = 0; x < mapLen; ++x) {
                    putChar(result, resultOffset++, lowerCharArray[x]);
                }
            }
        }
        return newString(result, 0, resultOffset);
    }

    public static String toUpperCase(String str, byte[] value, Locale locale) {
        if (locale == null) {
            throw new NullPointerException();
        }
        int first;
        boolean hasSurr = false;
        final int len = value.length >> 1;

        // Now check if there are any characters that need to be changed, or are surrogate
        for (first = 0 ; first < len; first++) {
            int cp = (int)getChar(value, first);
            if (Character.isSurrogate((char)cp)) {
                hasSurr = true;
                break;
            }
            if (cp != Character.toUpperCaseEx(cp)) {   // no need to check Character.ERROR
                break;
            }
        }
        if (first == len) {
            return str;
        }
        byte[] result = new byte[value.length];
        System.arraycopy(value, 0, result, 0, first << 1); // Just copy the first few
                                                           // upperCase characters.
        String lang = locale.getLanguage();
        if (lang == "tr" || lang == "az" || lang == "lt") {
            return toUpperCaseEx(str, value, result, first, locale, true);
        }
        if (hasSurr) {
            return toUpperCaseEx(str, value, result, first, locale, false);
        }
        int bits = 0;
        for (int i = first; i < len; i++) {
            int cp = (int)getChar(value, i);
            if (Character.isSurrogate((char)cp)) {
                return toUpperCaseEx(str, value, result, i, locale, false);
            }
            cp = Character.toUpperCaseEx(cp);
            if (!Character.isBmpCodePoint(cp)) {    // Character.ERROR is not bmp
                return toUpperCaseEx(str, value, result, i, locale, false);
            }
            bits |= cp;
            putChar(result, i, cp);
        }
        if (bits > 0xFF) {
            return new String(result, UTF16);
        } else {
            return newString(result, 0, len);
        }
    }

    private static String toUpperCaseEx(String str, byte[] value,
                                        byte[] result, int first,
                                        Locale locale, boolean localeDependent)
    {
        int resultOffset = first;
        int length = value.length >> 1;
        int srcCount;
        for (int i = first; i < length; i += srcCount) {
            int srcChar = getChar(value, i);
            int upperChar;
            char[] upperCharArray;
            srcCount = 1;
            if (Character.isSurrogate((char)srcChar)) {
                srcChar = codePointAt(value, i, length);
                srcCount = Character.charCount(srcChar);
            }
            if (localeDependent) {
                upperChar = ConditionalSpecialCasing.toUpperCaseEx(str, i, locale);
            } else {
                upperChar = Character.toUpperCaseEx(srcChar);
            }
            if (Character.isBmpCodePoint(upperChar)) {
                putChar(result, resultOffset++, upperChar);
            } else {
                if (upperChar == Character.ERROR) {
                    if (localeDependent) {
                        upperCharArray =
                            ConditionalSpecialCasing.toUpperCaseCharArray(str, i, locale);
                    } else {
                        upperCharArray = Character.toUpperCaseCharArray(srcChar);
                    }
                } else {
                    upperCharArray = Character.toChars(upperChar);
                }
                /* Grow result if needed */
                int mapLen = upperCharArray.length;
                if (mapLen > srcCount) {
                    byte[] result2 = newBytesFor((result.length >> 1) + mapLen - srcCount);
                    System.arraycopy(result, 0, result2, 0, resultOffset << 1);
                    result = result2;
                 }
                 for (int x = 0; x < mapLen; ++x) {
                    putChar(result, resultOffset++, upperCharArray[x]);
                 }
            }
        }
        return newString(result, 0, resultOffset);
    }

    public static String trim(byte[] value) {
        int length = value.length >> 1;
        int len = length;
        int st = 0;
        while (st < len && getChar(value, st) <= ' ') {
            st++;
        }
        while (st < len && getChar(value, len - 1) <= ' ') {
            len--;
        }
        return ((st > 0) || (len < length )) ?
            new String(Arrays.copyOfRange(value, st << 1, len << 1), UTF16) :
            null;
    }

    public static void putChars(byte[] val, int index, char[] str, int off, int end) {
        while (off < end) {
            putChar(val, index++, str[off++]);
        }
    }

    public static String newString(byte[] val, int index, int len) {
        if (String.COMPACT_STRINGS) {
            byte[] buf = compress(val, index, len);
            if (buf != null) {
                return new String(buf, LATIN1);
            }
        }
        int last = index + len;
        return new String(Arrays.copyOfRange(val, index << 1, last << 1), UTF16);
    }

    public static void fillNull(byte[] val, int index, int end) {
        Arrays.fill(val, index << 1, end << 1, (byte)0);
    }

    static class CharsSpliterator implements Spliterator.OfInt {
        private final byte[] array;
        private int index;        // current index, modified on advance/split
        private final int fence;  // one past last index
        private final int cs;

        CharsSpliterator(byte[] array, int acs) {
            this(array, 0, array.length >> 1, acs);
        }

        CharsSpliterator(byte[] array, int origin, int fence, int acs) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.cs = acs | Spliterator.ORDERED | Spliterator.SIZED
                      | Spliterator.SUBSIZED;
        }

        @Override
        public OfInt trySplit() {
            int lo = index, mid = (lo + fence) >>> 1;
            return (lo >= mid)
                   ? null
                   : new CharsSpliterator(array, lo, index = mid, cs);
        }

        @Override
        public void forEachRemaining(IntConsumer action) {
            byte[] a; int i, hi; // hoist accesses and checks from loop
            if (action == null)
                throw new NullPointerException();
            if (((a = array).length >> 1) >= (hi = fence) &&
                (i = index) >= 0 && i < (index = hi)) {
                do { action.accept(getChar(a, i)); } while (++i < hi);
            }
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (action == null)
                throw new NullPointerException();
            if (index >= 0 && index < fence) {
                action.accept(getChar(array, index++));
                return true;
            }
            return false;
        }

        @Override
        public long estimateSize() { return (long)(fence - index); }

        @Override
        public int characteristics() {
            return cs;
        }
    }

    static class CodePointsSpliterator implements Spliterator.OfInt {
        private final byte[] array;
        private int index;        // current index, modified on advance/split
        private final int fence;  // one past last index
        private final int cs;

        CodePointsSpliterator(byte[] array, int acs) {
            this(array, 0, array.length >> 1, acs);
        }

        CodePointsSpliterator(byte[] array, int origin, int fence, int acs) {
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.cs = acs | Spliterator.ORDERED;
        }

        @Override
        public OfInt trySplit() {
            int lo = index, mid = (lo + fence) >>> 1;
            if (lo >= mid)
                return null;

            int midOneLess;
            // If the mid-point intersects a surrogate pair
            if (Character.isLowSurrogate(getChar(array, mid)) &&
                Character.isHighSurrogate(getChar(array, midOneLess = (mid -1)))) {
                // If there is only one pair it cannot be split
                if (lo >= midOneLess)
                    return null;
                // Shift the mid-point to align with the surrogate pair
                return new CodePointsSpliterator(array, lo, index = midOneLess, cs);
            }
            return new CodePointsSpliterator(array, lo, index = mid, cs);
        }

        @Override
        public void forEachRemaining(IntConsumer action) {
            byte[] a; int i, hi; // hoist accesses and checks from loop
            if (action == null)
                throw new NullPointerException();
            if (((a = array).length >> 1) >= (hi = fence) &&
                (i = index) >= 0 && i < (index = hi)) {
                do {
                    i = advance(a, i, hi, action);
                } while (i < hi);
            }
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (action == null)
                throw new NullPointerException();
            if (index >= 0 && index < fence) {
                index = advance(array, index, fence, action);
                return true;
            }
            return false;
        }

        // Advance one code point from the index, i, and return the next
        // index to advance from
        private static int advance(byte[] a, int i, int hi, IntConsumer action) {
            char c1 = getChar(a, i++);
            int cp = c1;
            if (Character.isHighSurrogate(c1) && i < hi) {
                char c2 = getChar(a, i);
                if (Character.isLowSurrogate(c2)) {
                    i++;
                    cp = Character.toCodePoint(c1, c2);
                }
            }
            action.accept(cp);
            return i;
        }

        @Override
        public long estimateSize() { return (long)(fence - index); }

        @Override
        public int characteristics() {
            return cs;
        }
    }

    ////////////////////////////////////////////////////////////////

    public static void putCharSB(byte[] val, int index, int c) {
        checkIndex(index, val.length >> 1);
        putChar(val, index, c);
    }

    public static void putCharsSB(byte[] val, int index, char[] ca, int off, int end) {
        checkOffset(index + end - off, val.length >> 1);
        putChars(val, index, ca, off, end);
    }

    public static void putCharsSB(byte[] val, int index, CharSequence s, int off, int end) {
        checkOffset(index + end - off, val.length >> 1);
        for (int i = off; i < end; i++) {
            putChar(val, index++, s.charAt(i));
        }
    }

    public static int codePointAtSB(byte[] val, int index, int end) {
        checkOffset(end, val.length >> 1);
        return codePointAt(val, index, end);
    }

    public static int codePointBeforeSB(byte[] val, int index) {
        checkOffset(index, val.length >> 1);
        return codePointBefore(val, index);
    }

    public static int codePointCountSB(byte[] val, int beginIndex, int endIndex) {
        checkOffset(endIndex, val.length >> 1);
        return codePointCount(val, beginIndex, endIndex);
    }

    ////////////////////////////////////////////////////////////////

    private static native boolean isBigEndian();

    static final int HI_BYTE_SHIFT;
    static final int LO_BYTE_SHIFT;
    static {
        if (isBigEndian()) {
            HI_BYTE_SHIFT = 8;
            LO_BYTE_SHIFT = 0;
        } else {
            HI_BYTE_SHIFT = 0;
            LO_BYTE_SHIFT = 8;
        }
    }

    static final int MAX_LENGTH = Integer.MAX_VALUE >> 1;
}
