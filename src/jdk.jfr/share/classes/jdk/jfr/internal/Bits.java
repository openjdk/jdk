/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import jdk.internal.misc.Unsafe;

public final class Bits {

    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final boolean unalignedAccess = unsafe.unalignedAccess();
    private static final boolean bigEndian = unsafe.isBigEndian();

    private Bits() { }

    // -- Swapping --

    private static int swap(int x) {
        return Integer.reverseBytes(x);
    }

    private static long swap(long x) {
        return Long.reverseBytes(x);
    }

    private static float swap(float x) {
        return Float.intBitsToFloat(swap(Float.floatToIntBits(x)));
    }

    private static double swap(double x) {
        return Double.longBitsToDouble(swap(Double.doubleToLongBits(x)));
    }

    // -- Alignment --

    private static boolean isAddressAligned(long a, int datumSize) {
        return (a & datumSize - 1) == 0;
    }

    // -- Primitives stored per byte

    private static byte int3(int x) { return (byte)(x >> 24); }
    private static byte int2(int x) { return (byte)(x >> 16); }
    private static byte int1(int x) { return (byte)(x >>  8); }
    private static byte int0(int x) { return (byte)(x      ); }

    private static byte long7(long x) { return (byte)(x >> 56); }
    private static byte long6(long x) { return (byte)(x >> 48); }
    private static byte long5(long x) { return (byte)(x >> 40); }
    private static byte long4(long x) { return (byte)(x >> 32); }
    private static byte long3(long x) { return (byte)(x >> 24); }
    private static byte long2(long x) { return (byte)(x >> 16); }
    private static byte long1(long x) { return (byte)(x >>  8); }
    private static byte long0(long x) { return (byte)(x      ); }

    private static void putIntBigEndianUnaligned(long a, int x) {
        putByte_(a    , int3(x));
        putByte_(a + 1, int2(x));
        putByte_(a + 2, int1(x));
        putByte_(a + 3, int0(x));
    }

    private static void putLongBigEndianUnaligned(long a, long x) {
        putByte_(a    , long7(x));
        putByte_(a + 1, long6(x));
        putByte_(a + 2, long5(x));
        putByte_(a + 3, long4(x));
        putByte_(a + 4, long3(x));
        putByte_(a + 5, long2(x));
        putByte_(a + 6, long1(x));
        putByte_(a + 7, long0(x));
    }

    private static void putFloatBigEndianUnaligned(long a, float x) {
        putIntBigEndianUnaligned(a, Float.floatToRawIntBits(x));
    }

    private static void putDoubleBigEndianUnaligned(long a, double x) {
        putLongBigEndianUnaligned(a, Double.doubleToRawLongBits(x));
    }

    private static void putByte_(long a, byte b) {
        unsafe.putByte(a, b);
    }

    private static void putInt_(long a, int x) {
        unsafe.putInt(a, bigEndian ? x : swap(x));
    }

    private static void putFloat_(long a, float x) {
        unsafe.putFloat(a, bigEndian ? x : swap(x));
    }

    private static void putDouble_(long a, double x) {
        unsafe.putDouble(a, bigEndian ? x : swap(x));
    }

    // external api
    public static int putInt(long a, int x) {
        if (unalignedAccess || isAddressAligned(a, Integer.BYTES)) {
            putInt_(a, x);
            return Integer.BYTES;
        }
        putIntBigEndianUnaligned(a, x);
        return Integer.BYTES;
    }

    public static int putFloat(long a, float x) {
        if (unalignedAccess || isAddressAligned(a, Float.BYTES)) {
            putFloat_(a, x);
            return Float.BYTES;
        }
        putFloatBigEndianUnaligned(a, x);
        return Float.BYTES;
    }

    public static int putDouble(long a, double x) {
        if (unalignedAccess || isAddressAligned(a, Double.BYTES)) {
            putDouble_(a, x);
            return Double.BYTES;
        }
        putDoubleBigEndianUnaligned(a, x);
        return Double.BYTES;
    }
}
