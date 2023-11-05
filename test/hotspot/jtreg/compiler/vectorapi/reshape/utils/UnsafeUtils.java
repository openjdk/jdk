/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi.reshape.utils;

import jdk.internal.misc.Unsafe;

/**
 * Unsafe to check for correctness of reinterpret operations. May be replaced with foreign API later.
 */
public class UnsafeUtils {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static long arrayBase(Class<?> etype) {
        return UNSAFE.arrayBaseOffset(etype.arrayType());
    }

    public static byte getByte(Object o, long base, int i) {
        // This technically leads to UB, what we need is UNSAFE.getByteUnaligned but they seem to be equivalent
        return UNSAFE.getByte(o, base + (long)i * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static void putByte(Object o, long base, int i, byte value) {
        // This technically leads to UB, what we need is UNSAFE.putByteUnaligned but they seem to be equivalent
        UNSAFE.putByte(o, base + (long)i * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    public static short getShort(Object o, long base, int i) {
        return UNSAFE.getShort(o, base + (long)i * Unsafe.ARRAY_SHORT_INDEX_SCALE);
    }

    public static void putShort(Object o, long base, int i, short value) {
        UNSAFE.putShort(o, base + (long)i * Unsafe.ARRAY_SHORT_INDEX_SCALE, value);
    }

    public static int getInt(Object o, long base, int i) {
        return UNSAFE.getInt(o, base + (long)i * Unsafe.ARRAY_INT_INDEX_SCALE);
    }

    public static void putInt(Object o, long base, int i, int value) {
        UNSAFE.putInt(o, base + (long)i * Unsafe.ARRAY_INT_INDEX_SCALE, value);
    }

    public static long getLong(Object o, long base, int i) {
        return UNSAFE.getLong(o, base + (long)i * Unsafe.ARRAY_LONG_INDEX_SCALE);
    }

    public static void putLong(Object o, long base, int i, long value) {
        UNSAFE.putLong(o, base + (long)i * Unsafe.ARRAY_LONG_INDEX_SCALE, value);
    }

    public static float getFloat(Object o, long base, int i) {
        return UNSAFE.getFloat(o, base + (long)i * Unsafe.ARRAY_FLOAT_INDEX_SCALE);
    }

    public static void putFloat(Object o, long base, int i, float value) {
        UNSAFE.putFloat(o, base + (long)i * Unsafe.ARRAY_FLOAT_INDEX_SCALE, value);
    }

    public static double getDouble(Object o, long base, int i) {
        return UNSAFE.getDouble(o, base + (long)i * Unsafe.ARRAY_DOUBLE_INDEX_SCALE);
    }

    public static void putDouble(Object o, long base, int i, double value) {
        UNSAFE.putDouble(o, base + (long)i * Unsafe.ARRAY_DOUBLE_INDEX_SCALE, value);
    }
}
