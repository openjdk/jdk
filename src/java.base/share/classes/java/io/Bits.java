/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility methods for packing/unpacking primitive values in/out of byte arrays
 * using big-endian byte ordering (i.e. "Network Order").
 */
final class Bits {
    private Bits() {}

    private static final VarHandle SHORT = create(short[].class);
    private static final VarHandle INT = create(int[].class);
    private static final VarHandle LONG = create(long[].class);

    /*
     * Methods for unpacking primitive values from byte arrays starting at
     * given offsets.
     */

    static boolean getBoolean(byte[] b, int off) {
        return b[off] != 0;
    }

    static char getChar(byte[] b, int off) {
        return (char) (short) SHORT.get(b, off);
    }

    static short getShort(byte[] b, int off) {
        return (short) SHORT.get(b, off);
    }

    static int getInt(byte[] b, int off) {
        return (int) INT.get(b, off);
    }

    static float getFloat(byte[] b, int off) {
        // Using Float.intBitsToFloat collapses NaN values to a single
        // "canonical" NaN value
        return Float.intBitsToFloat((int) INT.get(b, off));
    }

    static long getLong(byte[] b, int off) {
        return (long) LONG.get(b, off);
    }

    static double getDouble(byte[] b, int off) {
        // Using Double.longBitsToDouble collapses NaN values to a single
        // "canonical" NaN value
        return Double.longBitsToDouble((long) LONG.get(b, off));
    }

    /*
     * Methods for packing primitive values into byte arrays starting at given
     * offsets.
     */

    static void putBoolean(byte[] b, int off, boolean val) {
        b[off] = (byte) (val ? 1 : 0);
    }

    static void putChar(byte[] b, int off, char val) {
        SHORT.set(b, off, (short) val);
    }

    static void putShort(byte[] b, int off, short val) {
        SHORT.set(b, off, val);
    }

    static void putInt(byte[] b, int off, int val) {
        INT.set(b, off, val);
    }

    static void putFloat(byte[] b, int off, float val) {
        // Using Float.floatToIntBits collapses NaN values to a single
        // "canonical" NaN value
        INT.set(b, off, Float.floatToIntBits(val));
    }

    static void putLong(byte[] b, int off, long val) {
        LONG.set(b, off, val);
    }

    static void putDouble(byte[] b, int off, double val) {
        // Using Double.doubleToLongBits collapses NaN values to a single
        // "canonical" NaN value
        LONG.set(b, off, Double.doubleToLongBits(val));
    }

    private static VarHandle create(Class<?> viewArrayClass) {
        return MethodHandles.byteArrayViewVarHandle(viewArrayClass, ByteOrder.BIG_ENDIAN);
    }
}
