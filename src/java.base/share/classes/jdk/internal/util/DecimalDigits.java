/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

/**
 * Digits class for decimal digits.
 *
 * @since 21
 */
public final class DecimalDigits {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Each element of the array represents the packaging of two ascii characters based on little endian:<p>
     * <pre>
     *      00 -> '0' | ('0' << 8) -> 0x3030
     *      01 -> '0' | ('1' << 8) -> 0x3130
     *      02 -> '0' | ('2' << 8) -> 0x3230
     *
     *     ...
     *
     *      10 -> '1' | ('0' << 8) -> 0x3031
     *      11 -> '1' | ('1' << 8) -> 0x3131
     *      12 -> '1' | ('2' << 8) -> 0x3231
     *
     *     ...
     *
     *      97 -> '9' | ('7' << 8) -> 0x3739
     *      98 -> '9' | ('8' << 8) -> 0x3839
     *      99 -> '9' | ('9' << 8) -> 0x3939
     * </pre>
     */
    @Stable
    private static final short[] DIGITS;

    static {
        short[] digits = new short[128];

        for (int i = 0; i < 10; i++) {
            short hi = (short) (i + '0');
            for (int j = 0; j < 10; j++) {
                short lo = (short) ((j + '0') << 8);
                digits[i * 10 + j] = (short) (hi | lo);
            }
        }
        DIGITS = digits;
    }

    /**
     * Constructor.
     */
    private DecimalDigits() {
    }

    /**
     * Returns the string representation size for a given int value.
     *
     * @param x int value
     * @return string size
     *
     * @implNote There are other ways to compute this: e.g. binary search,
     * but values are biased heavily towards zero, and therefore linear search
     * wins. The iteration results are also routinely inlined in the generated
     * code after loop unrolling.
     */
    public static int stringSize(int x) {
        int d = 1;
        if (x >= 0) {
            d = 0;
            x = -x;
        }
        int p = -10;
        for (int i = 1; i < 10; i++) {
            if (x > p)
                return i + d;
            p = 10 * p;
        }
        return 10 + d;
    }

    /**
     * Returns the string representation size for a given long value.
     *
     * @param x long value
     * @return string size
     *
     * @implNote There are other ways to compute this: e.g. binary search,
     * but values are biased heavily towards zero, and therefore linear search
     * wins. The iteration results are also routinely inlined in the generated
     * code after loop unrolling.
     */
    public static int stringSize(long x) {
        int d = 1;
        if (x >= 0) {
            d = 0;
            x = -x;
        }
        long p = -10;
        for (int i = 1; i < 19; i++) {
            if (x > p)
                return i + d;
            p = 10 * p;
        }
        return 19 + d;
    }

    /**
     * Places characters representing the integer i into the
     * character array buf. The characters are placed into
     * the buffer backwards starting with the least significant
     * digit at the specified index (exclusive), and working
     * backwards from there.
     *
     * @implNote This method converts positive inputs into negative
     * values, to cover the Integer.MIN_VALUE case. Converting otherwise
     * (negative to positive) will expose -Integer.MIN_VALUE that overflows
     * integer.
     * <p>
     * <b>WARNING: This method does not perform any bound checks. </b>
     *
     * @param i     value to convert
     * @param index next index, after the least significant digit
     * @param buf   target buffer, Latin1-encoded
     * @return index of the most significant digit or minus sign, if present
     */
    public static int uncheckedGetCharsLatin1(int i, int index, byte[] buf) {
        int q;
        int charPos = index;

        boolean negative = i < 0;
        if (!negative) {
            i = -i;
        }

        // Generate two digits per iteration
        while (i <= -100) {
            q = i / 100;
            charPos -= 2;
            uncheckedPutPairLatin1(buf, charPos, (q * 100) - i);
            i = q;
        }

        // We know there are at most two digits left at this point.
        if (i <= -10) {
            charPos -= 2;
            uncheckedPutPairLatin1(buf, charPos, -i);
        } else {
            uncheckedPutCharLatin1(buf, --charPos, '0' - i);
        }

        if (negative) {
            uncheckedPutCharLatin1(buf, --charPos, '-');
        }
        return charPos;
    }


    /**
     * Places characters representing the long i into the
     * character array buf. The characters are placed into
     * the buffer backwards starting with the least significant
     * digit at the specified index (exclusive), and working
     * backwards from there.
     *
     * @implNote This method converts positive inputs into negative
     * values, to cover the Long.MIN_VALUE case. Converting otherwise
     * (negative to positive) will expose -Long.MIN_VALUE that overflows
     * long.
     * <p>
     * <b>WARNING: This method does not perform any bound checks. </b>
     *
     * @param i     value to convert
     * @param index next index, after the least significant digit
     * @param buf   target buffer, Latin1-encoded
     * @return index of the most significant digit or minus sign, if present
     */
    public static int uncheckedGetCharsLatin1(long i, int index, byte[] buf) {
        long q;
        int charPos = index;

        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i < Integer.MIN_VALUE) {
            q = i / 100;
            charPos -= 2;
            uncheckedPutPairLatin1(buf, charPos, (int)((q * 100) - i));
            i = q;
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int)i;
        while (i2 <= -100) {
            q2 = i2 / 100;
            charPos -= 2;
            uncheckedPutPairLatin1(buf, charPos, (q2 * 100) - i2);
            i2 = q2;
        }

        // We know there are at most two digits left at this point.
        if (i2 <= -10) {
            charPos -= 2;
            uncheckedPutPairLatin1(buf, charPos, -i2);
        } else {
            uncheckedPutCharLatin1(buf, --charPos, '0' - i2);
        }

        if (negative) {
            uncheckedPutCharLatin1(buf, --charPos, '-');
        }
        return charPos;
    }


    /**
     * This is a variant of {@link DecimalDigits#uncheckedGetCharsLatin1(int, int, byte[])}, but for
     * UTF-16 coder.
     * <p>
     * <b>WARNING: This method does not perform any bound checks.</b>
     *
     * @param i     value to convert
     * @param index next index, after the least significant digit
     * @param buf   target buffer, UTF16-coded.
     * @return index of the most significant digit or minus sign, if present
     */
    public static int uncheckedGetCharsUTF16(int i, int index, byte[] buf) {
        int q;
        int charPos = index;

        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }

        // Get 2 digits/iteration using ints
        while (i <= -100) {
            q = i / 100;
            charPos -= 2;
            uncheckedPutPairUTF16(buf, charPos, (q * 100) - i);
            i = q;
        }

        // We know there are at most two digits left at this point.
        if (i <= -10) {
            charPos -= 2;
            uncheckedPutPairUTF16(buf, charPos, -i);
        } else {
            uncheckedPutCharUTF16(buf, --charPos, '0' - i);
        }

        if (negative) {
            uncheckedPutCharUTF16(buf, --charPos, '-');
        }
        return charPos;
    }


    /**
     * This is a variant of {@link DecimalDigits#uncheckedGetCharsLatin1(long, int, byte[])}, but for
     * UTF-16 coder.
     * <p>
     * <b>WARNING: This method does not perform any bound checks.</b>
     *
     * @param i     value to convert
     * @param index next index, after the least significant digit
     * @param buf   target buffer, UTF16-coded.
     * @return index of the most significant digit or minus sign, if present
     */
    public static int uncheckedGetCharsUTF16(long i, int index, byte[] buf) {
        long q;
        int charPos = index;

        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i < Integer.MIN_VALUE) {
            q = i / 100;
            charPos -= 2;
            uncheckedPutPairUTF16(buf, charPos, (int)((q * 100) - i));
            i = q;
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int)i;
        while (i2 <= -100) {
            q2 = i2 / 100;
            charPos -= 2;
            uncheckedPutPairUTF16(buf, charPos, (q2 * 100) - i2);
            i2 = q2;
        }

        // We know there are at most two digits left at this point.
        if (i2 <= -10) {
            charPos -= 2;
            uncheckedPutPairUTF16(buf, charPos, -i2);
        } else {
            uncheckedPutCharUTF16(buf, --charPos, '0' - i2);
        }

        if (negative) {
            uncheckedPutCharUTF16(buf, --charPos, '-');
        }
        return charPos;
    }

    /**
     * This is a variant of {@link DecimalDigits#uncheckedGetCharsUTF16(long, int, byte[])}, but for
     * UTF-16 coder.
     *
     * @param i     value to convert
     * @param index next index, after the least significant digit
     * @param buf   target buffer, UTF16-coded.
     * @return index of the most significant digit or minus sign, if present
     */
    public static int getChars(long i, int index, char[] buf) {
        long q;
        int charPos = index;

        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i < Integer.MIN_VALUE) {
            q = i / 100;
            charPos -= 2;
            putPair(buf, charPos, (int)((q * 100) - i));
            i = q;
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int)i;
        while (i2 <= -100) {
            q2 = i2 / 100;
            charPos -= 2;
            putPair(buf, charPos, (q2 * 100) - i2);
            i2 = q2;
        }

        // We know there are at most two digits left at this point.
        if (i2 <= -10) {
            charPos -= 2;
            putPair(buf, charPos, -i2);
        } else {
            buf[--charPos] = (char) ('0' - i2);
        }

        if (negative) {
            buf[--charPos] = '-';
        }
        return charPos;
    }

    /**
     * Insert the 2-chars integer into the buf as 2 decimal digit ASCII chars,
     * only least significant 16 bits of {@code v} are used.
     * @param buf byte buffer to copy into
     * @param charPos insert point
     * @param v to convert
     */
    public static void putPair(char[] buf, int charPos, int v) {
        int packed = DIGITS[v & 0x7f];
        buf[charPos    ] = (char) (packed & 0xFF);
        buf[charPos + 1] = (char) (packed >> 8);
    }

    /**
     * Insert the 2-bytes integer into the buf as 2 decimal digit ASCII bytes,
     * only least significant 16 bits of {@code v} are used.
     * <p>
     * <b>WARNING: This method does not perform any bound checks.</b>
     *
     * @param buf byte buffer to copy into
     * @param charPos insert point
     * @param v to convert
     */
    public static void uncheckedPutPairLatin1(byte[] buf, int charPos, int v) {
        int packed = DIGITS[v & 0x7f];
        uncheckedPutCharLatin1(buf, charPos, packed & 0xFF);
        uncheckedPutCharLatin1(buf, charPos + 1, packed >> 8);
    }

    /**
     * Insert the 2-chars integer into the buf as 2 decimal digit UTF16 bytes,
     * only least significant 16 bits of {@code v} are used.
     * <p>
     * <b>WARNING: This method does not perform any bound checks.</b>
     *
     * @param buf byte buffer to copy into
     * @param charPos insert point
     * @param v to convert
     */
    public static void uncheckedPutPairUTF16(byte[] buf, int charPos, int v) {
        int packed = DIGITS[v & 0x7f];
        uncheckedPutCharUTF16(buf, charPos, packed & 0xFF);
        uncheckedPutCharUTF16(buf, charPos + 1, packed >> 8);
    }

    private static void uncheckedPutCharLatin1(byte[] buf, int charPos, int c) {
        assert charPos >= 0 && charPos < buf.length;
        UNSAFE.putByte(buf, ARRAY_BYTE_BASE_OFFSET + charPos, (byte) c);
    }

    private static void uncheckedPutCharUTF16(byte[] buf, int charPos, int c) {
        assert charPos >= 0 && charPos < (buf.length >> 1);
        UNSAFE.putCharUnaligned(buf, ARRAY_BYTE_BASE_OFFSET + ((long) charPos << 1), (char) c);
    }
}
