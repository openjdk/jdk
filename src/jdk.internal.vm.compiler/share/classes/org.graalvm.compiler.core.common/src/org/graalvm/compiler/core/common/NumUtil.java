/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.core.common;

// JaCoCo Exclude

import jdk.vm.ci.code.CodeUtil;

/**
 * A collection of static utility functions that check ranges of numbers.
 */
public class NumUtil {

    public static boolean isShiftCount(int x) {
        return 0 <= x && x < 32;
    }

    /**
     * Determines if a given {@code int} value is the range of unsigned byte values.
     */
    public static boolean isUByte(int x) {
        return (x & 0xff) == x;
    }

    /**
     * Determines if a given {@code int} value is the range of signed byte values.
     */
    public static boolean isByte(int x) {
        return (byte) x == x;
    }

    /**
     * Determines if a given {@code long} value is the range of unsigned byte values.
     */
    public static boolean isUByte(long x) {
        return (x & 0xffL) == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed byte values.
     */
    public static boolean isByte(long l) {
        return (byte) l == l;
    }

    /**
     * Determines if a given {@code long} value is the range of unsigned int values.
     */
    public static boolean isUInt(long x) {
        return (x & 0xffffffffL) == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed int values.
     */
    public static boolean isInt(long l) {
        return (int) l == l;
    }

    /**
     * Determines if a given {@code int} value is the range of signed short values.
     */
    public static boolean isShort(int x) {
        return (short) x == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed short values.
     */
    public static boolean isShort(long x) {
        return (short) x == x;
    }

    public static boolean isUShort(int s) {
        return s == (s & 0xFFFF);
    }

    public static boolean isUShort(long s) {
        return s == (s & 0xFFFF);
    }

    public static boolean is32bit(long x) {
        return -0x80000000L <= x && x < 0x80000000L;
    }

    public static byte safeToByte(int v) {
        assert isByte(v);
        return (byte) v;
    }

    public static short safeToShort(int v) {
        assert isShort(v);
        return (short) v;
    }

    public static int safeToInt(long v) {
        assert isInt(v);
        return (int) v;
    }

    public static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    public static long roundUp(long number, long mod) {
        return ((number + mod - 1L) / mod) * mod;
    }

    public static int roundDown(int number, int mod) {
        return number / mod * mod;
    }

    public static long roundDown(long number, long mod) {
        return number / mod * mod;
    }

    public static int divideAndRoundUp(int number, int divisor) {
        return (number + divisor - 1) / divisor;
    }

    public static long divideAndRoundUp(long number, long divisor) {
        return (number + divisor - 1L) / divisor;
    }

    public static int log2Ceil(int val) {
        int x = 1;
        int log2 = 0;
        while (x < val) {
            log2++;
            x *= 2;
        }
        return log2;
    }

    public static boolean isUnsignedNbit(int n, int value) {
        assert n > 0 && n < 32;
        return 32 - Integer.numberOfLeadingZeros(value) <= n;
    }

    public static boolean isUnsignedNbit(int n, long value) {
        assert n > 0 && n < 64;
        return 64 - Long.numberOfLeadingZeros(value) <= n;
    }

    public static boolean isSignedNbit(int n, int value) {
        assert n > 0 && n < 32;
        int min = -(1 << (n - 1));
        int max = (1 << (n - 1)) - 1;
        return value >= min && value <= max;
    }

    public static boolean isSignedNbit(int n, long value) {
        assert n > 0 && n < 64;
        long min = -(1L << (n - 1));
        long max = (1L << (n - 1)) - 1;
        return value >= min && value <= max;
    }

    /**
     *
     * @param n Number of bits that should be set to 1. Must be between 0 and 32 (inclusive).
     * @return A number with n bits set to 1.
     */
    public static int getNbitNumberInt(int n) {
        assert n >= 0 && n <= 32 : "0 <= n <= 32; instead: " + n;
        if (n < 32) {
            return (1 << n) - 1;
        } else {
            return 0xFFFFFFFF;
        }
    }

    /**
     *
     * @param n Number of bits that should be set to 1. Must be between 0 and 64 (inclusive).
     * @return A number with n bits set to 1.
     */
    public static long getNbitNumberLong(int n) {
        assert n >= 0 && n <= 64;
        if (n < 64) {
            return (1L << n) - 1;
        } else {
            return 0xFFFFFFFFFFFFFFFFL;
        }
    }

    /**
     * Get the minimum value representable in a {@code bits} bit signed integer.
     */
    public static long minValue(int bits) {
        return CodeUtil.minValue(bits);
    }

    /**
     * Get the maximum value representable in a {@code bits} bit signed integer.
     */
    public static long maxValue(int bits) {
        return CodeUtil.maxValue(bits);
    }

    /**
     * Get the maximum value representable in a {@code bits} bit unsigned integer.
     */
    public static long maxValueUnsigned(int bits) {
        return getNbitNumberLong(bits);
    }

    public static long maxUnsigned(long a, long b) {
        if (Long.compareUnsigned(a, b) < 0) {
            return b;
        }
        return a;
    }

    public static long minUnsigned(long a, long b) {
        if (Long.compareUnsigned(a, b) < 0) {
            return a;
        }
        return b;
    }

    public static boolean sameSign(long a, long b) {
        return a < 0 == b < 0;
    }
}
