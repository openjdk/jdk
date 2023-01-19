/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility methods for packing/unpacking primitive valueues in/out of byte arrays
 * using {@linkplain ByteOrder#BIG_ENDIAN big endian order} (network order).
 * <p>
 * All methods in this class will throw an {@linkplain NullPointerException} if {@code null} is
 * passed in as a method parameter for a byte array.
 */
public final class ByteArray {

    private ByteArray() {
    }

    private static final VarHandle SHORT = create(short[].class);
    private static final VarHandle CHAR = create(char[].class);
    private static final VarHandle INT = create(int[].class);
    private static final VarHandle FLOAT = create(float[].class);
    private static final VarHandle LONG = create(long[].class);
    private static final VarHandle DOUBLE = create(double[].class);

    private static final VarHandle SHORT_AT_ZERO = createAtZeroOffset(short[].class);
    private static final VarHandle CHAR_AT_ZERO = createAtZeroOffset(char[].class);
    private static final VarHandle INT_AT_ZERO = createAtZeroOffset(int[].class);
    private static final VarHandle FLOAT_AT_ZERO = createAtZeroOffset(float[].class);
    private static final VarHandle LONG_AT_ZERO = createAtZeroOffset(long[].class);
    private static final VarHandle DOUBLE_AT_ZERO = createAtZeroOffset(double[].class);

    /*
     * Methods for unpacking primitive values from byte arrays starting at
     * a given offsets.
     */

    public static boolean getBoolean(byte[] array, int offset) {
        return array[offset] != 0;
    }

    public static char getChar(byte[] array, int offset) {
        return (char) CHAR.get(array, offset);
    }

    public static short getShort(byte[] array, int offset) {
        return (short) SHORT.get(array, offset);
    }

    public static int getUnsignedShort(byte[] array, int offset) {
        return Short.toUnsignedInt((short) SHORT.get(array, offset));
    }

    /**
     * Reads an {@code int} from the provided {@code array} at the given {@code offset} using big endian order.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to read a value from.
     * @param offset where extraction in the array should begin
     * @return an {@code int} from the array
     * @throws NullPointerException      if the provided {@code array} is {@code null}
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside the range [0, array.length - 4]
     * @see #setInt(byte[], int, int)
     */
    public static int getInt(byte[] array, int offset) {
        return (int) INT.get(array, offset);
    }

    public static float getFloat(byte[] array, int offset) {
        // Using Float.intBitsToFloat collapses NaN values to a single
        // "canonical" NaN value
        return Float.intBitsToFloat((int) INT.get(array, offset));
    }

    public static float getFloatRaw(byte[] array, int offset) {
        // Just gets the bits as they are
        return (float) FLOAT.get(array, offset);
    }

    public static long getLong(byte[] array, int offset) {
        return (long) LONG.get(array, offset);
    }

    public static double getDouble(byte[] array, int offset) {
        // Using Double.longBitsToDouble collapses NaN values to a single
        // "canonical" NaN value
        return Double.longBitsToDouble((long) LONG.get(array, offset));
    }

    public static double getDoubleRaw(byte[] array, int offset) {
        // Just gets the bits as they are
        return (double) DOUBLE.get(array, offset);
    }

    /*
     * Methods for unpacking primitive values from byte arrays starting at
     * offset zero.
     */

    public static boolean getBoolean(byte[] array) {
        return array[0] != 0;
    }

    public static char getChar(byte[] array) {
        return (char) CHAR_AT_ZERO.get(array);
    }

    public static short getShort(byte[] array) {
        return (short) SHORT_AT_ZERO.get(array);
    }

    public static int getUnsignedShort(byte[] array) {
        return Short.toUnsignedInt((short) SHORT_AT_ZERO.get(array));
    }

    public static int getInt(byte[] array) {
        return (int) INT_AT_ZERO.get(array);
    }

    public static float getFloat(byte[] array) {
        // Using Float.intBitsToFloat collapses NaN values to a single
        // "canonical" NaN value
        return Float.intBitsToFloat((int) INT_AT_ZERO.get(array));
    }

    public static float getFloatRaw(byte[] array) {
        // Just gets the bits as they are
        return (float) FLOAT_AT_ZERO.get(array);
    }

    public static long getLong(byte[] array) {
        return (long) LONG_AT_ZERO.get(array);
    }

    public static double getDouble(byte[] array) {
        // Using Double.longBitsToDouble collapses NaN values to a single
        // "canonical" NaN value
        return Double.longBitsToDouble((long) LONG_AT_ZERO.get(array));
    }

    public static double getDoubleRaw(byte[] array) {
        // Just gets the bits as they are
        return (double) DOUBLE_AT_ZERO.get(array);
    }

    /*
     * Methods for packing primitive values into byte arrays starting at a given
     * offset.
     */

    public static void setBoolean(byte[] array, int offset, boolean value) {
        array[offset] = (byte) (value ? 1 : 0);
    }

    public static void setChar(byte[] array, int offset, char value) {
        CHAR.set(array, offset, value);
    }

    public static void setShort(byte[] array, int offset, short value) {
        SHORT.set(array, offset, value);
    }

    public static void setUnsignedShort(byte[] array, int offset, int value) {
        SHORT.set(array, offset, (short) (char) value);
    }

    /**
     * Writes an {@code int} to the provided {@code array} at the given {@code offset} using big endian order.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to write a value to
     * @param offset where writing in the array should begin
     * @throws NullPointerException      if the provided {@code array} is {@code null}
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside the range [0, array.length - 4]
     * @see #setInt(byte[], int, int)
     */
    public static void setInt(byte[] array, int offset, int value) {
        INT.set(array, offset, value);
    }

    public static void setFloat(byte[] array, int offset, float value) {
        // Using Float.floatToIntBits collapses NaN values to a single
        // "canonical" NaN value
        INT.set(array, offset, Float.floatToIntBits(value));
    }

    public static void setFloatRaw(byte[] array, int offset, float value) {
        // Just sets the bits as they are
        FLOAT.set(array, offset, value);
    }

    public static void setLong(byte[] array, int offset, long value) {
        LONG.set(array, offset, value);
    }

    public static void setDouble(byte[] array, int offset, double value) {
        // Using Double.doubleToLongBits collapses NaN values to a single
        // "canonical" NaN value
        LONG.set(array, offset, Double.doubleToLongBits(value));
    }

    public static void setDoubleRaw(byte[] array, int offset, double value) {
        // Just sets the bits as they are
        DOUBLE.set(array, offset, value);
    }


    /*
     * Methods for packing primitive values into byte arrays starting at offset zero.
     */

    public static void setBoolean(byte[] array, boolean value) {
        array[0] = (byte) (value ? 1 : 0);
    }

    public static void setChar(byte[] array, char value) {
        CHAR_AT_ZERO.set(array, value);
    }

    public static void setShort(byte[] array, short value) {
        SHORT_AT_ZERO.set(array, value);
    }

    public static void setUnsignedShort(byte[] array, int value) {
        SHORT_AT_ZERO.set(array, (short) (char) value);
    }

    public static void setInt(byte[] array, int value) {
        INT_AT_ZERO.set(array, value);
    }

    public static void setFloat(byte[] array, float value) {
        // Using Float.floatToIntBits collapses NaN values to a single
        // "canonical" NaN value
        INT_AT_ZERO.set(array, Float.floatToIntBits(value));
    }

    public static void setFloatRaw(byte[] array, float value) {
        // Just sets the bits as they are
        FLOAT_AT_ZERO.set(array, value);
    }

    public static void setLong(byte[] array, long value) {
        LONG_AT_ZERO.set(array, value);
    }

    public static void setDouble(byte[] array, double value) {
        // Using Double.doubleToLongBits collapses NaN values to a single
        // "canonical" NaN value
        LONG_AT_ZERO.set(array, Double.doubleToLongBits(value));
    }

    public static void setDoubleRaw(byte[] array, double value) {
        // Just sets the bits as they are
        DOUBLE_AT_ZERO.set(array, value);
    }

    private static VarHandle createAtZeroOffset(Class<?> viewArrayClass) {
        var original = create(viewArrayClass);
        // (byte[] array, int offset, ...) -> { offset = 0 } -> (byte[], ...)
        return MethodHandles.insertCoordinates(original, 1, 0);
    }

    private static VarHandle create(Class<?> viewArrayClass) {
        return MethodHandles.byteArrayViewVarHandle(viewArrayClass, ByteOrder.BIG_ENDIAN);
    }

}
