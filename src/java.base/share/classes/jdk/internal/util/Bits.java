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

import org.openjdk.bench.java.lang.invoke.MethodTypeAcquire;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility methods for packing/unpacking primitive values in/out of byte arrays
 * using various byte ordering.
 */
public final class Bits {

    private Bits() {
    }

    public static final class BigEndian {

        private BigEndian() {
        }

        private static final VarHandle SHORT = create(short[].class, ByteOrder.BIG_ENDIAN);
        private static final VarHandle CHAR = create(char[].class, ByteOrder.BIG_ENDIAN);
        private static final VarHandle INT = create(int[].class, ByteOrder.BIG_ENDIAN);
        private static final VarHandle FLOAT = create(float[].class, ByteOrder.BIG_ENDIAN);
        private static final VarHandle LONG = create(long[].class, ByteOrder.BIG_ENDIAN);
        private static final VarHandle DOUBLE = create(double[].class, ByteOrder.BIG_ENDIAN);

        /*
         * Methods for unpacking primitive values from byte arrays starting at
         * given offsets.
         */

        public static boolean getBoolean(byte[] b, int off) {
            return b[off] != 0;
        }

        public static char getChar(byte[] b, int off) {
            return (char) CHAR.get(b, off);
            // return (char) (short) SHORT.get(b, off);
        }

        public static short getShort(byte[] b, int off) {
            return (short) SHORT.get(b, off);
        }

        public static int getUnsignedShort(byte[] b, int off) {
            return Short.toUnsignedInt((short) SHORT.get(b, off));
        }

        public static int getInt(byte[] b, int off) {
            return (int) INT.get(b, off);
        }

        public static float getFloat(byte[] b, int off) {
            // Using Float.intBitsToFloat collapses NaN values to a single
            // "canonical" NaN value
            return Float.intBitsToFloat((int) INT.get(b, off));
        }

        public static float getFloatRaw(byte[] b, int off) {
            // Just gets the bits as they are
            return (float) FLOAT.get(b, off);
        }

        public static long getLong(byte[] b, int off) {
            return (long) LONG.get(b, off);
        }

        public static double getDouble(byte[] b, int off) {
            // Using Double.longBitsToDouble collapses NaN values to a single
            // "canonical" NaN value
            return Double.longBitsToDouble((long) LONG.get(b, off));
        }

        public static double getDoubleRaw(byte[] b, int off) {
            // Just gets the bits as they are
            return (double) DOUBLE.get(b, off);
        }

        /*
         * Methods for packing primitive values into byte arrays starting at given
         * offsets.
         */

        public static void putBoolean(byte[] b, int off, boolean val) {
            b[off] = (byte) (val ? 1 : 0);
        }

        public static void putChar(byte[] b, int off, char val) {
            CHAR.set(b, off, val);
            //SHORT.set(b, off, (short) val);
        }

        public static void putShort(byte[] b, int off, short val) {
            SHORT.set(b, off, val);
        }

        public static void putUnsignedShort(byte[] b, int off, int val) {
            SHORT.set(b, off, (short) (char) val);
        }

        public static void putInt(byte[] b, int off, int val) {
            INT.set(b, off, val);
        }

        public static void putFloat(byte[] b, int off, float val) {
            // Using Float.floatToIntBits collapses NaN values to a single
            // "canonical" NaN value
            INT.set(b, off, Float.floatToIntBits(val));
        }

        public static void putFloatRaw(byte[] b, int off, float val) {
            // Just sets the bits as they are
            FLOAT.set(b, off, val);
        }

        public static void putLong(byte[] b, int off, long val) {
            LONG.set(b, off, val);
        }

        public static void putDouble(byte[] b, int off, double val) {
            // Using Double.doubleToLongBits collapses NaN values to a single
            // "canonical" NaN value
            LONG.set(b, off, Double.doubleToLongBits(val));
        }

        public static void putDoubleRaw(byte[] b, int off, double val) {
            // Just sets the bits as they are
            DOUBLE.set(b, off, val);
        }

    }

    public static final class BigEndianAtZero {

        private BigEndianAtZero() {
        }

        private static final VarHandle SHORT = createAtZeroOffset(short[].class);
        private static final VarHandle CHAR = createAtZeroOffset(char[].class);
        private static final VarHandle INT = createAtZeroOffset(int[].class);
        private static final VarHandle FLOAT = createAtZeroOffset(float[].class);
        private static final VarHandle LONG = createAtZeroOffset(long[].class);
        private static final VarHandle DOUBLE = createAtZeroOffset(double[].class);

        /*
         * Methods for unpacking primitive values from byte arrays starting at
         * zero offsets.
         */

        public static boolean getBoolean(byte[] b) {
            return b[0] != 0;
        }

        public static char getChar(byte[] b) {
            return (char) CHAR.get(b);
        }

        public static short getShort(byte[] b) {
            return (short) SHORT.get(b);
        }

        public static int getUnsignedShort(byte[] b) {
            return Short.toUnsignedInt((short) SHORT.get(b));
        }

        public static int getInt(byte[] b) {
            return (int) INT.get(b);
        }

        public static float getFloat(byte[] b) {
            // Using Float.intBitsToFloat collapses NaN values to a single
            // "canonical" NaN value
            return Float.intBitsToFloat((int) INT.get(b));
        }

        public static float getFloatRaw(byte[] b) {
            // Just gets the bits as they are
            return (float) FLOAT.get(b);
        }

        public static long getLong(byte[] b) {
            return (long) LONG.get(b);
        }

        public static double getDouble(byte[] b) {
            // Using Double.longBitsToDouble collapses NaN values to a single
            // "canonical" NaN value
            return Double.longBitsToDouble((long) LONG.get(b));
        }

        public static double getDoubleRaw(byte[] b) {
            // Just gets the bits as they are
            return (double) DOUBLE.get(b);
        }

        /*
         * Methods for packing primitive values into byte arrays starting at zero
         * offsets.
         */

        public static void putBoolean(byte[] b, boolean val) {
            b[0] = (byte) (val ? 1 : 0);
        }

        public static void putChar(byte[] b, char val) {
            CHAR.set(b, val);
        }

        public static void putShort(byte[] b, short val) {
            SHORT.set(b, val);
        }

        public static void putUnsignedShort(byte[] b, int val) {
            SHORT.set(b, (short) (char) val);
        }

        public static void putInt(byte[] b, int val) {
            INT.set(b, val);
        }

        public static void putFloat(byte[] b, float val) {
            // Using Float.floatToIntBits collapses NaN values to a single
            // "canonical" NaN value
            INT.set(b, Float.floatToIntBits(val));
        }

        public static void putFloatRaw(byte[] b, float val) {
            // Just sets the bits as they are
            FLOAT.set(b, val);
        }

        public static void putLong(byte[] b, long val) {
            LONG.set(b, val);
        }

        public static void putDouble(byte[] b, double val) {
            // Using Double.doubleToLongBits collapses NaN values to a single
            // "canonical" NaN value
            LONG.set(b, Double.doubleToLongBits(val));
        }

        public static void putDoubleRaw(byte[] b, double val) {
            // Just sets the bits as they are
            DOUBLE.set(b, val);
        }

        private static VarHandle createAtZeroOffset(Class<?> viewArrayClass) {
            var original = create(viewArrayClass, ByteOrder.BIG_ENDIAN);
            // (byte[] array, int off, ...) -> { off = 0 } -> (byte[], ...)
            return MethodHandles.insertCoordinates(original, 1, 0);
        }

    }

    // Alternative with an internal buffer fixed att offset zero.
    public static final class BigEndianAtZeroBuffer {

        private final byte[] buffer;

        public BigEndianAtZeroBuffer() {
            this.buffer = new byte[8];
        }

        public byte[] buffer() {
            return buffer;
        }

        /*
         * Methods for unpacking primitive values from byte arrays starting at
         * zero offsets.
         */

        public boolean getBoolean() {
            return buffer[0] != 0;
        }

        public char getChar() {
            return BigEndianAtZero.getChar(buffer);
        }

        public short getShort() {
            return BigEndianAtZero.getShort(buffer);
        }

        public int getUnsignedShort() {
            return BigEndianAtZero.getUnsignedShort(buffer);
        }

        public int getInt() {
            return BigEndianAtZero.getInt(buffer);
        }

        public float getFloat() {
            return BigEndianAtZero.getFloat(buffer);
        }

        public float getFloatRaw() {
            return BigEndianAtZero.getFloatRaw(buffer);
        }

        public long getLong() {
            return BigEndianAtZero.getLong(buffer);
        }

        public double getDouble() {
            return BigEndianAtZero.getDouble(buffer);
        }

        public double getDoubleRaw() {
            return BigEndianAtZero.getDoubleRaw(buffer);
        }

        /*
         * Methods for packing primitive values into byte arrays starting at zero
         * offsets.
         */

        public void putBoolean(boolean val) {
            buffer[0] = (byte) (val ? 1 : 0);
        }

        public void putChar(char val) {
            BigEndianAtZero.putChar(buffer, val);
        }

        public void putShort(short val) {
            BigEndianAtZero.putShort(buffer, val);
        }

        public void putUnsignedShort(int val) {
            BigEndianAtZero.putUnsignedShort(buffer, val);
        }

        public void putInt(int val) {
            BigEndianAtZero.putInt(buffer, val);
        }

        public void putFloat(float val) {
            BigEndianAtZero.putFloat(buffer, val);
        }

        public void putFloatRaw(float val) {
            BigEndianAtZero.putFloatRaw(buffer, val);
        }

        public void putLong(long val) {
            BigEndianAtZero.putLong(buffer, val);
        }

        public void putDouble(double val) {
            BigEndianAtZero.putDouble(buffer, val);
        }

        public void putDoubleRaw(double val) {
            BigEndianAtZero.putDoubleRaw(buffer, val);
        }

    }

    private static VarHandle create(Class<?> viewArrayClass, ByteOrder byteOrder) {
        return MethodHandles.byteArrayViewVarHandle(viewArrayClass, byteOrder);
    }

}
