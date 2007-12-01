/*
 * Portions Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2005 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

// This class contains utility functions so testing not needed
///CLOVER:OFF
public final class Utility {

    /**
     * Convert characters outside the range U+0020 to U+007F to
     * Unicode escapes, and convert backslash to a double backslash.
     */
    public static final String escape(String s) {
        StringBuffer buf = new StringBuffer();
        for (int i=0; i<s.length(); ) {
            int c = UTF16.charAt(s, i);
            i += UTF16.getCharCount(c);
            if (c >= ' ' && c <= 0x007F) {
                if (c == '\\') {
                    buf.append("\\\\"); // That is, "\\"
                } else {
                    buf.append((char)c);
                }
            } else {
                boolean four = c <= 0xFFFF;
                buf.append(four ? "\\u" : "\\U");
                hex(c, four ? 4 : 8, buf);
            }
        }
        return buf.toString();
    }

    /* This map must be in ASCENDING ORDER OF THE ESCAPE CODE */
    static private final char[] UNESCAPE_MAP = {
        /*"   0x22, 0x22 */
        /*'   0x27, 0x27 */
        /*?   0x3F, 0x3F */
        /*\   0x5C, 0x5C */
        /*a*/ 0x61, 0x07,
        /*b*/ 0x62, 0x08,
        /*e*/ 0x65, 0x1b,
        /*f*/ 0x66, 0x0c,
        /*n*/ 0x6E, 0x0a,
        /*r*/ 0x72, 0x0d,
        /*t*/ 0x74, 0x09,
        /*v*/ 0x76, 0x0b
    };

    /**
     * Convert an escape to a 32-bit code point value.  We attempt
     * to parallel the icu4c unescapeAt() function.
     * @param offset16 an array containing offset to the character
     * <em>after</em> the backslash.  Upon return offset16[0] will
     * be updated to point after the escape sequence.
     * @return character value from 0 to 10FFFF, or -1 on error.
     */
    public static int unescapeAt(String s, int[] offset16) {
        int c;
        int result = 0;
        int n = 0;
        int minDig = 0;
        int maxDig = 0;
        int bitsPerDigit = 4;
        int dig;
        int i;
        boolean braces = false;

        /* Check that offset is in range */
        int offset = offset16[0];
        int length = s.length();
        if (offset < 0 || offset >= length) {
            return -1;
        }

        /* Fetch first UChar after '\\' */
        c = UTF16.charAt(s, offset);
        offset += UTF16.getCharCount(c);

        /* Convert hexadecimal and octal escapes */
        switch (c) {
        case 'u':
            minDig = maxDig = 4;
            break;
        case 'U':
            minDig = maxDig = 8;
            break;
        case 'x':
            minDig = 1;
            if (offset < length && UTF16.charAt(s, offset) == 0x7B /*{*/) {
                ++offset;
                braces = true;
                maxDig = 8;
            } else {
                maxDig = 2;
            }
            break;
        default:
            dig = UCharacter.digit(c, 8);
            if (dig >= 0) {
                minDig = 1;
                maxDig = 3;
                n = 1; /* Already have first octal digit */
                bitsPerDigit = 3;
                result = dig;
            }
            break;
        }
        if (minDig != 0) {
            while (offset < length && n < maxDig) {
                c = UTF16.charAt(s, offset);
                dig = UCharacter.digit(c, (bitsPerDigit == 3) ? 8 : 16);
                if (dig < 0) {
                    break;
                }
                result = (result << bitsPerDigit) | dig;
                offset += UTF16.getCharCount(c);
                ++n;
            }
            if (n < minDig) {
                return -1;
            }
            if (braces) {
                if (c != 0x7D /*}*/) {
                    return -1;
                }
                ++offset;
            }
            if (result < 0 || result >= 0x110000) {
                return -1;
            }
            // If an escape sequence specifies a lead surrogate, see
            // if there is a trail surrogate after it, either as an
            // escape or as a literal.  If so, join them up into a
            // supplementary.
            if (offset < length &&
                UTF16.isLeadSurrogate((char) result)) {
                int ahead = offset+1;
                c = s.charAt(offset); // [sic] get 16-bit code unit
                if (c == '\\' && ahead < length) {
                    int o[] = new int[] { ahead };
                    c = unescapeAt(s, o);
                    ahead = o[0];
                }
                if (UTF16.isTrailSurrogate((char) c)) {
                    offset = ahead;
                result = UCharacterProperty.getRawSupplementary(
                                  (char) result, (char) c);
                }
            }
            offset16[0] = offset;
            return result;
        }

        /* Convert C-style escapes in table */
        for (i=0; i<UNESCAPE_MAP.length; i+=2) {
            if (c == UNESCAPE_MAP[i]) {
                offset16[0] = offset;
                return UNESCAPE_MAP[i+1];
            } else if (c < UNESCAPE_MAP[i]) {
                break;
            }
        }

        /* Map \cX to control-X: X & 0x1F */
        if (c == 'c' && offset < length) {
            c = UTF16.charAt(s, offset);
            offset16[0] = offset + UTF16.getCharCount(c);
            return 0x1F & c;
        }

        /* If no special forms are recognized, then consider
         * the backslash to generically escape the next character. */
        offset16[0] = offset;
        return c;
    }

    /**
     * Convert a integer to size width hex uppercase digits.
     * E.g., hex('a', 4, str) => "0041".
     * Append the output to the given StringBuffer.
     * If width is too small to fit, nothing will be appended to output.
     */
    public static StringBuffer hex(int ch, int width, StringBuffer output) {
        return appendNumber(output, ch, 16, width);
    }

    /**
     * Convert a integer to size width (minimum) hex uppercase digits.
     * E.g., hex('a', 4, str) => "0041".  If the integer requires more
     * than width digits, more will be used.
     */
    public static String hex(int ch, int width) {
        StringBuffer buf = new StringBuffer();
        return appendNumber(buf, ch, 16, width).toString();
    }

    /**
     * Skip over a sequence of zero or more white space characters
     * at pos.  Return the index of the first non-white-space character
     * at or after pos, or str.length(), if there is none.
     */
    public static int skipWhitespace(String str, int pos) {
        while (pos < str.length()) {
            int c = UTF16.charAt(str, pos);
            if (!UCharacterProperty.isRuleWhiteSpace(c)) {
                break;
            }
            pos += UTF16.getCharCount(c);
        }
        return pos;
    }

    static final char DIGITS[] = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z'
    };

    /**
     * Append the digits of a positive integer to the given
     * <code>StringBuffer</code> in the given radix. This is
     * done recursively since it is easiest to generate the low-
     * order digit first, but it must be appended last.
     *
     * @param result is the <code>StringBuffer</code> to append to
     * @param n is the positive integer
     * @param radix is the radix, from 2 to 36 inclusive
     * @param minDigits is the minimum number of digits to append.
     */
    private static void recursiveAppendNumber(StringBuffer result, int n,
                                                int radix, int minDigits)
    {
        int digit = n % radix;

        if (n >= radix || minDigits > 1) {
            recursiveAppendNumber(result, n / radix, radix, minDigits - 1);
        }

        result.append(DIGITS[digit]);
    }

    /**
     * Append a number to the given StringBuffer in the given radix.
     * Standard digits '0'-'9' are used and letters 'A'-'Z' for
     * radices 11 through 36.
     * @param result the digits of the number are appended here
     * @param n the number to be converted to digits; may be negative.
     * If negative, a '-' is prepended to the digits.
     * @param radix a radix from 2 to 36 inclusive.
     * @param minDigits the minimum number of digits, not including
     * any '-', to produce.  Values less than 2 have no effect.  One
     * digit is always emitted regardless of this parameter.
     * @return a reference to result
     */
    public static StringBuffer appendNumber(StringBuffer result, int n,
                                             int radix, int minDigits)
        throws IllegalArgumentException
    {
        if (radix < 2 || radix > 36) {
            throw new IllegalArgumentException("Illegal radix " + radix);
        }


        int abs = n;

        if (n < 0) {
            abs = -n;
            result.append("-");
        }

        recursiveAppendNumber(result, abs, radix, minDigits);

        return result;
    }

    /**
     * Return true if the character is NOT printable ASCII.  The tab,
     * newline and linefeed characters are considered unprintable.
     */
    public static boolean isUnprintable(int c) {
        return !(c >= 0x20 && c <= 0x7E);
    }

    /**
     * Escape unprintable characters using <backslash>uxxxx notation
     * for U+0000 to U+FFFF and <backslash>Uxxxxxxxx for U+10000 and
     * above.  If the character is printable ASCII, then do nothing
     * and return FALSE.  Otherwise, append the escaped notation and
     * return TRUE.
     */
    public static boolean escapeUnprintable(StringBuffer result, int c) {
        if (isUnprintable(c)) {
            result.append('\\');
            if ((c & ~0xFFFF) != 0) {
                result.append('U');
                result.append(DIGITS[0xF&(c>>28)]);
                result.append(DIGITS[0xF&(c>>24)]);
                result.append(DIGITS[0xF&(c>>20)]);
                result.append(DIGITS[0xF&(c>>16)]);
            } else {
                result.append('u');
            }
            result.append(DIGITS[0xF&(c>>12)]);
            result.append(DIGITS[0xF&(c>>8)]);
            result.append(DIGITS[0xF&(c>>4)]);
            result.append(DIGITS[0xF&c]);
            return true;
        }
        return false;
    }

    //// for StringPrep
    /**
    * Similar to StringBuffer.getChars, version 1.3.
    * Since JDK 1.2 implements StringBuffer.getChars differently, this method
    * is here to provide consistent results.
    * To be removed after JDK 1.2 ceased to be the reference platform.
    * @param src source string buffer
    * @param srcBegin offset to the start of the src to retrieve from
    * @param srcEnd offset to the end of the src to retrieve from
    * @param dst char array to store the retrieved chars
    * @param dstBegin offset to the start of the destination char array to
    *                 store the retrieved chars
    * @draft since ICU4J 2.0
    */
    public static void getChars(StringBuffer src, int srcBegin, int srcEnd,
                                char dst[], int dstBegin)
    {
        if (srcBegin == srcEnd) {
            return;
        }
        src.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    /**
     * Convenience utility to compare two char[]s.
     * @param len the length to compare.
     * The start indices and start+len must be valid.
     */
    public final static boolean arrayRegionMatches(char[] source, int sourceStart,
                                            char[] target, int targetStart,
                                            int len)
    {
        int sourceEnd = sourceStart + len;
        int delta = targetStart - sourceStart;
        for (int i = sourceStart; i < sourceEnd; i++) {
            if (source[i] != target[i + delta])
            return false;
        }
        return true;
    }

}
///CLOVER:ON
