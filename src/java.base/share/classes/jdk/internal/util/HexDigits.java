/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.Stable;

/**
 * Digits provides a fast methodology for converting integers and longs to
 * hexadecimal digits ASCII strings.
 *
 * @since 21
 */
public final class HexDigits {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /**
     * Each element of the array represents the ascii encoded
     * hex relative to its index, for example:<p>
     * <pre>
     *       0 -> '00' -> '0' | ('0' << 8) -> 0x3030
     *       1 -> '01' -> '0' | ('1' << 8) -> 0x3130
     *       2 -> '02' -> '0' | ('2' << 8) -> 0x3230
     *
     *     ...
     *
     *      10 -> '0a' -> '0' | ('a' << 8) -> 0x6130
     *      11 -> '0b' -> '0' | ('b' << 8) -> 0x6230
     *      12 -> '0c' -> '0' | ('b' << 8) -> 0x6330
     *
     *     ...
     *
     *      26 -> '1a' -> '1' | ('a' << 8) -> 0x6131
     *      27 -> '1b' -> '1' | ('b' << 8) -> 0x6231
     *      28 -> '1c' -> '1' | ('c' << 8) -> 0x6331
     *
     *     ...
     *
     *     253 -> 'fd' -> 'f' | ('d' << 8) -> 0x6466
     *     254 -> 'fe' -> 'f' | ('e' << 8) -> 0x6566
     *     255 -> 'ff' -> 'f' | ('f' << 8) -> 0x6666
     * </pre>
     */
    @Stable
    private static final short[] DIGITS;

    static {
        short[] digits = new short[16 * 16];

        for (int i = 0; i < 16; i++) {
            short lo = (short) (i < 10 ? i + '0' : i - 10 + 'a');

            for (int j = 0; j < 16; j++) {
                short hi = (short) ((j < 10 ? j + '0' : j - 10 + 'a') << 8);
                digits[(i << 4) + j] = (short) (hi | lo);
            }
        }

        DIGITS = digits;
    }

    /**
     * Constructor.
     */
    private HexDigits() {
    }

    /**
     * For values from 0 to 255 return a short encoding a pair of hex ASCII-encoded digit characters in little-endian
     * @param i value to convert
     * @param ucase true uppper case, false lower case
     * @return a short encoding a pair of hex ASCII-encoded digit characters
     */
    public static short digitPair(int i, boolean ucase) {
        /*
         * 0b0100_0000_0100_0000 is a selector that selects letters (1 << 6),
         * uppercase or not, and shifting it right by 1 bit incidentally
         * becomes a bit offset between cases (1 << 5).
         *
         *  ([0-9] & 0b100_0000) >> 1 => 0
         *  ([a-f] & 0b100_0000) >> 1 => 32
         *
         *  [0-9] -  0 => [0-9]
         *  [a-f] - 32 => [A-F]
         */
        short v = DIGITS[i & 0xff];
        return ucase
                ? (short) (v - ((v & 0b0100_0000_0100_0000) >> 1))
                : v;
    }

    /**
     * Insert digits for long value in buffer from high index to low index.
     *
     * @param value      value to convert
     * @param index      insert point + 1
     * @param buffer     byte buffer to copy into
     *
     * @return the last index used
     */
    public static int getCharsLatin1(long value, int index, byte[] buffer) {
        while ((value & ~0xFF) != 0) {
            short pair = DIGITS[((int) value) & 0xFF];
            buffer[--index] = (byte)(pair >> 8);
            buffer[--index] = (byte)(pair);
            value >>>= 8;
        }

        int digits = DIGITS[(int) (value & 0xFF)];
        buffer[--index] = (byte) (digits >> 8);

        if (0xF < value) {
            buffer[--index] = (byte) (digits & 0xFF);
        }

        return index;
    }

    /**
     * Insert digits for long value in buffer from high index to low index.
     *
     * @param value      value to convert
     * @param index      insert point + 1
     * @param buffer     byte buffer to copy into
     *
     * @return the last index used
     */
    public static int getCharsUTF16(long value, int index, byte[] buffer) {
        while ((value & ~0xFF) != 0) {
            int pair = (int) DIGITS[((int) value) & 0xFF];
            JLA.uncheckedPutCharUTF16(buffer, --index, pair >> 8);
            JLA.uncheckedPutCharUTF16(buffer, --index, pair & 0xFF);
            value >>>= 8;
        }

        int digits = DIGITS[(int) (value & 0xFF)];
        JLA.uncheckedPutCharUTF16(buffer, --index, (byte) (digits >> 8));

        if (0xF < value) {
            JLA.uncheckedPutCharUTF16(buffer, --index, (byte) (digits & 0xFF));
        }

        return index;
    }

    /**
     * Calculate the number of digits required to represent the long.
     *
     * @param value value to convert
     *
     * @return number of digits
     */
    public static int stringSize(long value) {
        return value == 0 ? 1 :
                67 - Long.numberOfLeadingZeros(value) >> 2;
    }

    /**
     * Extract the least significant 4 bytes from the input integer i, convert each byte into its corresponding 2-digit
     * hexadecimal representation, concatenate these hexadecimal strings into one continuous string, and then interpret
     * this string as a hexadecimal number to form and return a long value.
     */
    public static long hex8(long i) {
        long x = Long.expand(i, 0x0F0F_0F0F_0F0F_0F0FL);
        /*
            Use long to simulate vector operations and generate 8 hexadecimal characters at a time.
            ------------
            0  = 0b0000_0000 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '0'
            1  = 0b0000_0001 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '1'
            2  = 0b0000_0010 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '2'
            3  = 0b0000_0011 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '3'
            4  = 0b0000_0100 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '4'
            5  = 0b0000_0101 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '5'
            6  = 0b0000_0110 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '6'
            7  = 0b0000_0111 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '7'
            8  = 0b0000_1000 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '8'
            9  = 0b0000_1001 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 0  + 0x30 + (i & 0xF) => '9'
            10 = 0b0000_1010 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 39 + 0x30 + (i & 0xF) => 'a'
            11 = 0b0000_1011 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 39 + 0x30 + (i & 0xF) => 'b'
            12 = 0b0000_1100 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 39 + 0x30 + (i & 0xF) => 'c'
            13 = 0b0000_1101 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 39 + 0x30 + (i & 0xF) => 'd'
            14 = 0b0000_1110 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 39 + 0x30 + (i & 0xF) => 'e'
            15 = 0b0000_1111 => m = ((i + 6) & 0x10); (m << 1) + (m >> 1) - (m >> 4) => 39 + 0x30 + (i & 0xF) => 'f'
         */
        long m = (x + 0x0606_0606_0606_0606L) & 0x1010_1010_1010_1010L;
        return ((m << 1) + (m >> 1) - (m >> 4))
                + 0x3030_3030_3030_3030L
                + x;
    }
}
