/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Utility methods for packing/unpacking primitive values in/out of byte arrays
 * using {@linkplain java.nio.ByteOrder#BIG_ENDIAN big endian order} (aka. "network order").
 * <p>
 * All methods in this class will throw an {@linkplain NullPointerException} if {@code null} is
 * passed in as a method parameter for a byte array.
 */
public final class ByteArray {

    private ByteArray() {
    }

    /**
     * The {@code Unsafe} can be functionality replaced by
     * {@linkplain java.lang.invoke.MethodHandles#byteArrayViewVarHandle byteArrayViewVarHandle},
     * but it's not feasible in practices, because {@code ByteArray} and {@code ByteArrayLittleEndian}
     * can be used in fundamental classes, {@code VarHandle} exercise many other
     * code at VM startup, this could lead a recursive calls when fundamental
     * classes is used in {@code VarHandle}.
     */
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @ForceInline
    static long arrayOffset(byte[] array, int typeBytes, int offset) {
        return (long) Preconditions.checkIndex(offset, array.length - typeBytes + 1, Preconditions.AIOOBE_FORMATTER)
                + Unsafe.ARRAY_BYTE_BASE_OFFSET;
    }

    /*
     * Methods for unpacking primitive values from byte arrays starting at
     * a given offset.
     */

    /**
     * {@return a {@code boolean} from the provided {@code array} at the given {@code offset}}.
     *
     * @param array  to read a value from.
     * @param offset where extraction in the array should begin
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 1]
     * @see #setBoolean(byte[], int, boolean)
     */
    @ForceInline
    public static boolean getBoolean(byte[] array, int offset) {
        return array[offset] != 0;
    }

    /**
     * {@return a {@code char} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #setChar(byte[], int, char)
     */
    @ForceInline
    public static char getChar(byte[] array, int offset) {
        return UNSAFE.getCharUnaligned(
            array,
            arrayOffset(array, Character.BYTES, offset),
            true);
    }

    /**
     * {@return a {@code short} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @return a {@code short} from the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #setShort(byte[], int, short)
     */
    @ForceInline
    public static short getShort(byte[] array, int offset) {
        return UNSAFE.getShortUnaligned(
            array,
            arrayOffset(array, Short.BYTES, offset),
            true);
    }

    /**
     * {@return an {@code unsigned short} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @return an {@code int} representing an unsigned short from the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #setUnsignedShort(byte[], int, int)
     */
    @ForceInline
    public static int getUnsignedShort(byte[] array, int offset) {
        return Short.toUnsignedInt(getShort(array, offset));
    }

    /**
     * {@return an {@code int} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 4]
     * @see #setInt(byte[], int, int)
     */
    @ForceInline
    public static int getInt(byte[] array, int offset) {
        return UNSAFE.getIntUnaligned(
            array,
            arrayOffset(array, Integer.BYTES, offset),
            true);
    }

    /**
     * {@return an {@code unsigned int} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @return an {@code long} representing an unsigned int from the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 4]
     * @see #setUnsignedInt(byte[], int, long)
     */
    @ForceInline
    public static long getUnsignedInt(byte[] array, int offset) {
        return Integer.toUnsignedLong(getInt(array, offset));
    }

    /**
     * {@return a {@code float} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 4]
     * @see #setFloat(byte[], int, float)
     */
    @ForceInline
    public static float getFloat(byte[] array, int offset) {
        return Float.intBitsToFloat(getInt(array, offset));
    }

    /**
     * {@return a {@code long} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 8]
     * @see #setLong(byte[], int, long)
     */
    @ForceInline
    public static long getLong(byte[] array, int offset) {
        return UNSAFE.getLongUnaligned(
            array,
            arrayOffset(array, Long.BYTES, offset),
            true);
    }

    /**
     * {@return a {@code double} from the provided {@code array} at the given {@code offset}
     * using big endian order}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to get a value from.
     * @param offset where extraction in the array should begin
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 8]
     * @see #setDouble(byte[], int, double)
     */
    @ForceInline
    public static double getDouble(byte[] array, int offset) {
        return Double.longBitsToDouble(getLong(array, offset));
    }

    /*
     * Methods for packing primitive values into byte arrays starting at a given
     * offset.
     */

    /**
     * Sets (writes) the provided {@code value} into
     * the provided {@code array} beginning at the given {@code offset}.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length]
     * @see #getBoolean(byte[], int)
     */
    @ForceInline
    public static void setBoolean(byte[] array, int offset, boolean value) {
        array[offset] = (byte) (value ? 1 : 0);
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #getChar(byte[], int)
     */
    @ForceInline
    public static void setChar(byte[] array, int offset, char value) {
        UNSAFE.putCharUnaligned(
                array,
                arrayOffset(array, Character.BYTES, offset),
                value,
                true);
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #getShort(byte[], int)
     */
    @ForceInline
    public static void setShort(byte[] array, int offset, short value) {
        UNSAFE.putShortUnaligned(
                array,
                arrayOffset(array, Short.BYTES, offset),
                value,
                true);
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #getUnsignedShort(byte[], int)
     */
    @ForceInline
    public static void setUnsignedShort(byte[] array, int offset, int value) {
        setShort(array, offset, (short) (char) value);
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 4]
     * @see #getInt(byte[], int)
     */
    @ForceInline
    public static void setInt(byte[] array, int offset, int value) {
        UNSAFE.putIntUnaligned(
                array,
                arrayOffset(array, Integer.BYTES, offset),
                value,
                true);
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 4]
     * @see #getUnsignedInt(byte[], int)
     */
    @ForceInline
    public static void setUnsignedInt(byte[] array, int offset, long value) {
        setInt(array, offset, (int) value);
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * Variants of {@linkplain Float#NaN } values are canonized to a single NaN value.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #getFloat(byte[], int)
     */
    @ForceInline
    public static void setFloat(byte[] array, int offset, float value) {
        // Using Float.floatToIntBits collapses NaN values to a single
        // "canonical" NaN value
        setInt(array, offset, Float.floatToIntBits(value));
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * Variants of {@linkplain Float#NaN } values are silently written according to
     * their bit patterns.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #getFloat(byte[], int)
     */
    @ForceInline
    public static void setFloatRaw(byte[] array, int offset, float value) {
        // Just sets the bits as they are
        setInt(array, offset, Float.floatToRawIntBits(value));
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 4]
     * @see #getLong(byte[], int)
     */
    @ForceInline
    public static void setLong(byte[] array, int offset, long value) {
        UNSAFE.putLongUnaligned(
                array,
                arrayOffset(array, Long.BYTES, offset),
                value,
                true);
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * Variants of {@linkplain Double#NaN } values are canonized to a single NaN value.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #getDouble(byte[], int)
     */
    @ForceInline
    public static void setDouble(byte[] array, int offset, double value) {
        // Using Double.doubleToLongBits collapses NaN values to a single
        // "canonical" NaN value
        setLong(array, offset, Double.doubleToLongBits(value));
    }

    /**
     * Sets (writes) the provided {@code value} using big endian order into
     * the provided {@code array} beginning at the given {@code offset}.
     * <p>
     * Variants of {@linkplain Double#NaN } values are silently written according to
     * their bit patterns.
     * <p>
     * There are no access alignment requirements.
     *
     * @param array  to set (write) a value into
     * @param offset where setting (writing) in the array should begin
     * @param value  value to set in the array
     * @throws IndexOutOfBoundsException if the provided {@code offset} is outside
     *                                   the range [0, array.length - 2]
     * @see #getDouble(byte[], int)
     */
    @ForceInline
    public static void setDoubleRaw(byte[] array, int offset, double value) {
        // Just sets the bits as they are
        setLong(array, offset, Double.doubleToRawLongBits(value));
    }

}
