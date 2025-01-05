/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

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
     *      01 -> '1' | ('0' << 8) -> 0x3130
     *      02 -> '2' | ('0' << 8) -> 0x3230
     *
     *     ...
     *
     *      10 -> '0' | ('1' << 8) -> 0x3031
     *      11 -> '1' | ('1' << 8) -> 0x3131
     *      12 -> '2' | ('1' << 8) -> 0x3231
     *
     *     ...
     *
     *      97 -> '7' | ('9' << 8) -> 0x3739
     *      98 -> '8' | ('9' << 8) -> 0x3839
     *      99 -> '9' | ('9' << 8) -> 0x3939
     * </pre>
     */
    @Stable
    private static final short[] DIGITS;

    static {
        short[] digits = new short[10 * 10];

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
     * For values from 0 to 99 return a short encoding a pair of ASCII-encoded digit characters in little-endian
     * @param i value to convert
     * @return a short encoding a pair of ASCII-encoded digit characters
     */
    public static short digitPair(int i) {
        return DIGITS[i];
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
     * Determine whether the two strings in bytes are both numbers. If they are, return d0 * 10 + d1, otherwise return -1
     * @param str The input LATIN1 encoded String value
     * @param offset the offset
     * @return If both characters are numbers, return d0 * 10 + d1, otherwise return -1
     */
    @ForceInline
    public static int digit2(byte[] str, int offset) {
        /*
            Here we are doing a 2-Byte Vector operation on the short type.

            x & 0xF0 != 0xC0
            ---------------
            0 0b0011_0000 & 0b1111_0000 = 0b0011_0000
            1 0b0011_0001 & 0b1111_0000 = 0b0011_0000
            2 0b0011_0010 & 0b1111_0000 = 0b0011_0000
            3 0b0011_0011 & 0b1111_0000 = 0b0011_0000
            4 0b0011_0100 & 0b1111_0000 = 0b0011_0000
            5 0b0011_0101 & 0b1111_0000 = 0b0011_0000
            6 0b0011_0110 & 0b1111_0000 = 0b0011_0000
            7 0b0011_0111 & 0b1111_0000 = 0b0011_0000
            8 0b0011_1000 & 0b1111_0000 = 0b0011_0000
            9 0b0011_1001 & 0b1111_0000 = 0b0011_0000

            (((d = x & 0x0F) + 0x06) & 0xF0) != 0
            ---------------
            0 ((0b0011_0000) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            1 ((0b0011_0001) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            2 ((0b0011_0010) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            3 ((0b0011_0011) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            4 ((0b0011_0100) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            5 ((0b0011_0101) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            6 ((0b0011_0110) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            7 ((0b0011_0111) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            8 ((0b0011_1000) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
            9 ((0b0011_1001) & 0b0000_1111 + 0b0110_0000) & 0b1111_0000 = 0b0110_0000
         */
        int d;
        short x = UNSAFE.getShortUnaligned(str, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, false);
        if ((((x & 0xF0F0) - 0x3030)
                | (((d = x & 0x0F0F) + 0x0606) & 0xF0F0)) != 0
        ) {
            return -1;
        }
        return ((d & 0xF) << 3) + ((d & 0xF) << 1)  // (d & 0xF) * 10
                + (d >> 8);
    }
}
