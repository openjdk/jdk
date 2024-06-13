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
     * Insert the unsigned 2-byte integer into the buffer as 4 hexadecimal digit ASCII bytes,
     * only least significant 16 bits of {@code value} are used.
     * @param buffer byte buffer to copy into
     * @param index insert point
     * @param value to convert
     */
    public static void put4(byte[] buffer, int index, int value) {
        // Prepare an int value so C2 generates a 4-byte write instead of two 2-byte writes
        int v = (DIGITS[value & 0xff] << 16) | DIGITS[(value >> 8) & 0xff];
        buffer[index]     = (byte)  v;
        buffer[index + 1] = (byte) (v >> 8);
        buffer[index + 2] = (byte) (v >> 16);
        buffer[index + 3] = (byte) (v >> 24);
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
            JLA.putCharUTF16(buffer, --index, pair >> 8);
            JLA.putCharUTF16(buffer, --index, pair & 0xFF);
            value >>>= 8;
        }

        int digits = DIGITS[(int) (value & 0xFF)];
        JLA.putCharUTF16(buffer, --index, (byte) (digits >> 8));

        if (0xF < value) {
            JLA.putCharUTF16(buffer, --index, (byte) (digits & 0xFF));
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
}
