/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;
import static jdk.internal.util.ArraysSupport.U;

/**
 * Utility methods for packing/unpacking primitive values in/out of byte arrays.
 * <p>
 * All methods perform checked access, throwing NPE for null arrays or AIOOBE
 * if the start index of the access will cause out of bounds read/write.
 * <p>
 * Methods are grouped into 4 categories: BO for explicit byte order, BE for
 * big endian, LE for little endian, and there are boolean access methods that
 * does not depend on byte order.
 * <p>
 * Types supported including non-byte primitives numbers (char, short, int,
 * float, long, double), raw float/double writes with special NaN treatment,
 * unsigned shorts (u2), and single-byte boolean checking != 0.
 */
public final class ByteArray {

    private ByteArray() {
    }

    // Basic types with Byte Order

    public static char getCharBO(byte[] array, int index, boolean big) {
        Preconditions.checkIndex(index, array.length - Character.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        return U.getCharUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, big);
    }

    public static short getShortBO(byte[] array, int index, boolean big) {
        Preconditions.checkIndex(index, array.length - Short.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        return U.getShortUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, big);
    }

    public static int getIntBO(byte[] array, int index, boolean big) {
        Preconditions.checkIndex(index, array.length - Integer.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        return U.getIntUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, big);
    }

    public static long getLongBO(byte[] array, int index, boolean big) {
        Preconditions.checkIndex(index, array.length - Long.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        return U.getLongUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, big);
    }

    public static void setCharBO(byte[] array, int index, boolean big, char value) {
        Preconditions.checkIndex(index, array.length - Character.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        U.putCharUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, value, big);
    }

    public static void setShortBO(byte[] array, int index, boolean big, short value) {
        Preconditions.checkIndex(index, array.length - Short.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        U.putShortUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, value, big);
    }

    public static void setIntBO(byte[] array, int index, boolean big, int value) {
        Preconditions.checkIndex(index, array.length - Integer.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        U.putIntUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, value, big);
    }

    public static void setLongBO(byte[] array, int index, boolean big, long value) {
        Preconditions.checkIndex(index, array.length - Long.BYTES + 1, Preconditions.AIOOBE_FORMATTER);
        U.putLongUnaligned(array, ARRAY_BYTE_BASE_OFFSET + index, value, big);
    }

    // Derived types with Byte Order

    public static float getFloatBO(byte[] array, int index, boolean big) {
        return Float.intBitsToFloat(getIntBO(array, index, big));
    }

    public static double getDoubleBO(byte[] array, int index, boolean big) {
        return Double.longBitsToDouble(getLongBO(array, index, big));
    }

    // Retains custom NaNs
    public static void setFloatRawBO(byte[] array, int index, boolean big, float value) {
        setIntBO(array, index, big, Float.floatToRawIntBits(value));
    }

    public static void setDoubleRawBO(byte[] array, int index, boolean big, double value) {
        setLongBO(array, index, big, Double.doubleToRawLongBits(value));
    }

    // Collapses custom NaNs
    public static void setFloatBO(byte[] array, int index, boolean big, float value) {
        setIntBO(array, index, big, Float.floatToIntBits(value));
    }

    public static void setDoubleBO(byte[] array, int index, boolean big, double value) {
        setLongBO(array, index, big, Double.doubleToLongBits(value));
    }

    public static int getUnsignedShortBO(byte[] array, int index, boolean big) {
        return getCharBO(array, index, big);
    }

    // Truncates most significant bits
    public static void setUnsignedShortBO(byte[] array, int index, boolean big, int value) {
        setCharBO(array, index, big, (char) value);
    }

    // BE methods

    public static char getCharBE(byte[] array, int offset) {
        return getCharBO(array, offset, true);
    }

    public static short getShortBE(byte[] array, int offset) {
        return getShortBO(array, offset, true);
    }

    public static int getUnsignedShortBE(byte[] array, int offset) {
        return getUnsignedShortBO(array, offset, true);
    }

    public static int getIntBE(byte[] array, int offset) {
        return getIntBO(array, offset, true);
    }

    public static float getFloatBE(byte[] array, int offset) {
        return getFloatBO(array, offset, true);
    }

    public static long getLongBE(byte[] array, int offset) {
        return getLongBO(array, offset, true);
    }

    public static double getDoubleBE(byte[] array, int offset) {
        return getDoubleBO(array, offset, true);
    }

    public static void setCharBE(byte[] array, int offset, char value) {
        setCharBO(array, offset, true, value);
    }

    public static void setShortBE(byte[] array, int offset, short value) {
        setShortBO(array, offset, true, value);
    }

    public static void setUnsignedShortBE(byte[] array, int offset, int value) {
        setUnsignedShortBO(array, offset, true, value);
    }

    public static void setIntBE(byte[] array, int offset, int value) {
        setIntBO(array, offset, true, value);
    }

    public static void setFloatBE(byte[] array, int offset, float value) {
        setFloatBO(array, offset, true, value);
    }

    public static void setFloatRawBE(byte[] array, int offset, float value) {
        setFloatRawBO(array, offset, true, value);
    }

    public static void setLongBE(byte[] array, int offset, long value) {
        setLongBO(array, offset, true, value);
    }

    public static void setDoubleBE(byte[] array, int offset, double value) {
        setDoubleBO(array, offset, true, value);
    }

    public static void setDoubleRawBE(byte[] array, int offset, double value) {
        setDoubleRawBO(array, offset, true, value);
    }

    // LE

    public static char getCharLE(byte[] array, int offset) {
        return getCharBO(array, offset, false);
    }

    public static short getShortLE(byte[] array, int offset) {
        return getShortBO(array, offset, false);
    }

    public static int getUnsignedShortLE(byte[] array, int offset) {
        return getCharBO(array, offset, false);
    }

    public static int getIntLE(byte[] array, int offset) {
        return getIntBO(array, offset, false);
    }

    public static float getFloatLE(byte[] array, int offset) {
        return getFloatBO(array, offset, false);
    }

    public static long getLongLE(byte[] array, int offset) {
        return getLongBO(array, offset, false);
    }

    public static double getDoubleLE(byte[] array, int offset) {
        return getDoubleBO(array, offset, false);
    }

    public static void setCharLE(byte[] array, int offset, char value) {
        setCharBO(array, offset, false, value);
    }

    public static void setShortLE(byte[] array, int offset, short value) {
        setShortBO(array, offset, false, value);
    }

    public static void setUnsignedShortLE(byte[] array, int offset, int value) {
        setUnsignedShortBO(array, offset, false, value);
    }

    public static void setIntLE(byte[] array, int offset, int value) {
        setIntBO(array, offset, false, value);
    }

    public static void setFloatLE(byte[] array, int offset, float value) {
        setFloatBO(array, offset, false, value);
    }

    public static void setFloatRawLE(byte[] array, int offset, float value) {
        setFloatRawBO(array, offset, false, value);
    }

    public static void setLongLE(byte[] array, int offset, long value) {
        setLongBO(array, offset, false, value);
    }

    public static void setDoubleLE(byte[] array, int offset, double value) {
        setDoubleBO(array, offset, false, value);
    }

    public static void setDoubleRawLE(byte[] array, int offset, double value) {
        setDoubleRawBO(array, offset, false, value);
    }

    // boolean

    public static boolean getBoolean(byte[] array, int offset) {
        return array[offset] != 0;
    }

    public static void setBoolean(byte[] array, int offset, boolean value) {
        array[offset] = (byte) (value ? 1 : 0);
    }
}
