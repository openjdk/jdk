/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package jdk.internal.math;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

import static java.lang.Math.multiplyHigh;

sealed class ToDecimal permits DoubleToDecimal, FloatToDecimal {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /* Used for left-to-tight digit extraction */
    static final int MASK_28 = (1 << 28) - 1;

    static final int NON_SPECIAL = 0;
    static final int PLUS_ZERO   = 0x100;
    static final int MINUS_ZERO  = 0x200;
    static final int PLUS_INF    = 0x300;
    static final int MINUS_INF   = 0x400;
    static final int NAN         = 0x500;

    static final byte LATIN1 = 0;
    static final byte UTF16  = 1;

    /**
     * The identifier of the encoding used to encode the bytes. The supported values in this implementation are
     *
     * LATIN1
     * UTF16
     *
     */
    private final byte coder;

    ToDecimal(byte coder) {
        this.coder = coder;
    }

    final void putChar(byte[] str, int index, int c) {
        if (coder == LATIN1) {
            str[index] = (byte) c;
        } else {
            JLA.putCharUTF16(str, index, (char) c);
        }
    }

    final void putCharsAt(byte[] str, int index, int c1, int c2) {
        if (coder == LATIN1) {
            str[index    ] = (byte) c1;
            str[index + 1] = (byte) c2;
        } else {
            JLA.putCharUTF16(str, index    , c1);
            JLA.putCharUTF16(str, index + 1, c2);
        }
    }

    final void putDigit(byte[] str, int index, int d) {
        putChar(str, index, (byte) ('0' + d));
    }

    final void put8Digits(byte[] str, int index, int m) {
        /*
         * Left-to-right digits extraction:
         * algorithm 1 in [3], with b = 10, k = 8, n = 28.
         */
        if (coder == LATIN1) {
            put8DigitsLatin1(str, index, m);
        } else {
            put8DigitsUTF16 (str, index, m);
        }
    }

    private static void put8DigitsLatin1(byte[] str, int index, int m) {
        int y = y(m);
        for (int i = 0; i < 8; ++i) {
            int t = 10 * y;
            str[index + i] = (byte) ('0' + (t >>> 28));
            y = t & MASK_28;
        }
    }

    private static void put8DigitsUTF16(byte[] str, int index, int m) {
        int y = y(m);
        for (int i = 0; i < 8; ++i) {
            int t = 10 * y;
            JLA.putCharUTF16(str, index + i, '0' + (t >>> 28));
            y = t & MASK_28;
        }
    }

    static int y(int a) {
        /*
         * Algorithm 1 in [3] needs computation of
         *     floor((a + 1) 2^n / b^k) - 1
         * with a < 10^8, b = 10, k = 8, n = 28.
         * Noting that
         *     (a + 1) 2^n <= 10^8 2^28 < 10^17
         * For n = 17, m = 8 the table in section 10 of [1] leads to:
         */
        return (int) (multiplyHigh(
                (long) (a + 1) << 28,
                193_428_131_138_340_668L) >>> 20) - 1;
    }

    final int removeTrailingZeroes(byte[] str, int index) {
        if (coder == LATIN1) {
            while (str[index - 1] == '0') {
                --index;
            }
            /* ... but do not remove the one directly to the right of '.' */
            if (str[index - 1] == '.') {
                ++index;
            }
        } else {
            while (JLA.getUTF16Char(str, index - 1) == '0') {
                --index;
            }
            /* ... but do not remove the one directly to the right of '.' */
            if (JLA.getUTF16Char(str, index - 1) == '.') {
                ++index;
            }
        }
        return index;
    }

    final int putSpecial(byte[] str, int index, int type) {
        String s = special(type);
        int length = s.length();
        if (coder == LATIN1) {
            for (int i = 0; i < length; ++i) {
                str[index + i] = (byte) s.charAt(i);
            }
        } else {
            for (int i = 0; i < length; ++i) {
                putChar(str, index + i, s.charAt(i));
            }
        }
        return index + length;
    }

    int length(byte[] str) {
        return str.length >> coder;
    }

    /* Using the deprecated constructor enhances performance */
    @SuppressWarnings("deprecation")
    static String asciiBytesToString(byte[] str, int index) {
        return new String(str, 0, 0, index);
    }

    static String special(int type) {
        return switch (type) {
            case PLUS_ZERO  -> "0.0";
            case MINUS_ZERO -> "-0.0";
            case PLUS_INF   -> "Infinity";
            case MINUS_INF  -> "-Infinity";
            default         -> "NaN";
        };
    }
}
