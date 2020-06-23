/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.Unsafe;

/**
 * Utility methods to work with arrays.  This includes a set of methods
 * to find a mismatch between two primitive arrays.  Also included is
 * a method to calculate the new length of an array to be reallocated.
 *
 * <p>Array equality and lexicographical comparison can be built on top of
 * array mismatch functionality.
 *
 * <p>The mismatch method implementation, {@link #vectorizedMismatch}, leverages
 * vector-based techniques to access and compare the contents of two arrays.
 * The Java implementation uses {@code Unsafe.getLongUnaligned} to access the
 * content of an array, thus access is supported on platforms that do not
 * support unaligned access.  For a byte[] array, 8 bytes (64 bits) can be
 * accessed and compared as a unit rather than individually, which increases
 * the performance when the method is compiled by the HotSpot VM.  On supported
 * platforms the mismatch implementation is intrinsified to leverage SIMD
 * instructions.  So for a byte[] array, 16 bytes (128 bits), 32 bytes
 * (256 bits), and perhaps in the future even 64 bytes (512 bits), platform
 * permitting, can be accessed and compared as a unit, which further increases
 * the performance over the Java implementation.
 *
 * <p>None of the mismatch methods perform array bounds checks.  It is the
 * responsibility of the caller (direct or otherwise) to perform such checks
 * before calling this method.
 */
public class ArraysSupport {
    static final Unsafe U = Unsafe.getUnsafe();

    private static final boolean BIG_ENDIAN = U.isBigEndian();

    public static final int LOG2_ARRAY_BOOLEAN_INDEX_SCALE = exactLog2(Unsafe.ARRAY_BOOLEAN_INDEX_SCALE);
    public static final int LOG2_ARRAY_BYTE_INDEX_SCALE = exactLog2(Unsafe.ARRAY_BYTE_INDEX_SCALE);
    public static final int LOG2_ARRAY_CHAR_INDEX_SCALE = exactLog2(Unsafe.ARRAY_CHAR_INDEX_SCALE);
    public static final int LOG2_ARRAY_SHORT_INDEX_SCALE = exactLog2(Unsafe.ARRAY_SHORT_INDEX_SCALE);
    public static final int LOG2_ARRAY_INT_INDEX_SCALE = exactLog2(Unsafe.ARRAY_INT_INDEX_SCALE);
    public static final int LOG2_ARRAY_LONG_INDEX_SCALE = exactLog2(Unsafe.ARRAY_LONG_INDEX_SCALE);
    public static final int LOG2_ARRAY_FLOAT_INDEX_SCALE = exactLog2(Unsafe.ARRAY_FLOAT_INDEX_SCALE);
    public static final int LOG2_ARRAY_DOUBLE_INDEX_SCALE = exactLog2(Unsafe.ARRAY_DOUBLE_INDEX_SCALE);

    private static final int LOG2_BYTE_BIT_SIZE = exactLog2(Byte.SIZE);

    private static int exactLog2(int scale) {
        if ((scale & (scale - 1)) != 0)
            throw new Error("data type scale not a power of two");
        return Integer.numberOfTrailingZeros(scale);
    }

    private ArraysSupport() {}

    /**
     * Find the relative index of the first mismatching pair of elements in two
     * primitive arrays of the same component type.  Pairs of elements will be
     * tested in order relative to given offsets into both arrays.
     *
     * <p>This method does not perform type checks or bounds checks.  It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * <p>The given offsets, in bytes, need not be aligned according to the
     * given log<sub>2</sub> size the array elements.  More specifically, an
     * offset modulus the size need not be zero.
     *
     * @param a the first array to be tested for mismatch, or {@code null} for
     * direct memory access
     * @param aOffset the relative offset, in bytes, from the base address of
     * the first array to test from, otherwise if the first array is
     * {@code null}, an absolute address pointing to the first element to test.
     * @param b the second array to be tested for mismatch, or {@code null} for
     * direct memory access
     * @param bOffset the relative offset, in bytes, from the base address of
     * the second array to test from, otherwise if the second array is
     * {@code null}, an absolute address pointing to the first element to test.
     * @param length the number of array elements to test
     * @param log2ArrayIndexScale log<sub>2</sub> of the array index scale, that
     * corresponds to the size, in bytes, of an array element.
     * @return if a mismatch is found a relative index, between 0 (inclusive)
     * and {@code length} (exclusive), of the first mismatching pair of elements
     * in the two arrays.  Otherwise, if a mismatch is not found the bitwise
     * compliment of the number of remaining pairs of elements to be checked in
     * the tail of the two arrays.
     */
    @HotSpotIntrinsicCandidate
    public static int vectorizedMismatch(Object a, long aOffset,
                                         Object b, long bOffset,
                                         int length,
                                         int log2ArrayIndexScale) {
        // assert a.getClass().isArray();
        // assert b.getClass().isArray();
        // assert 0 <= length <= sizeOf(a)
        // assert 0 <= length <= sizeOf(b)
        // assert 0 <= log2ArrayIndexScale <= 3

        int log2ValuesPerWidth = LOG2_ARRAY_LONG_INDEX_SCALE - log2ArrayIndexScale;
        int wi = 0;
        for (; wi < length >> log2ValuesPerWidth; wi++) {
            long bi = ((long) wi) << LOG2_ARRAY_LONG_INDEX_SCALE;
            long av = U.getLongUnaligned(a, aOffset + bi);
            long bv = U.getLongUnaligned(b, bOffset + bi);
            if (av != bv) {
                long x = av ^ bv;
                int o = BIG_ENDIAN
                        ? Long.numberOfLeadingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale)
                        : Long.numberOfTrailingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale);
                return (wi << log2ValuesPerWidth) + o;
            }
        }

        // Calculate the tail of remaining elements to check
        int tail = length - (wi << log2ValuesPerWidth);

        if (log2ArrayIndexScale < LOG2_ARRAY_INT_INDEX_SCALE) {
            int wordTail = 1 << (LOG2_ARRAY_INT_INDEX_SCALE - log2ArrayIndexScale);
            // Handle 4 bytes or 2 chars in the tail using int width
            if (tail >= wordTail) {
                long bi = ((long) wi) << LOG2_ARRAY_LONG_INDEX_SCALE;
                int av = U.getIntUnaligned(a, aOffset + bi);
                int bv = U.getIntUnaligned(b, bOffset + bi);
                if (av != bv) {
                    int x = av ^ bv;
                    int o = BIG_ENDIAN
                            ? Integer.numberOfLeadingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale)
                            : Integer.numberOfTrailingZeros(x) >> (LOG2_BYTE_BIT_SIZE + log2ArrayIndexScale);
                    return (wi << log2ValuesPerWidth) + o;
                }
                tail -= wordTail;
            }
            return ~tail;
        }
        else {
            return ~tail;
        }
    }

    /**
     * Mismatch over long lengths.
     */
    public static long vectorizedMismatchLargeForBytes(Object a, long aOffset,
                                                       Object b, long bOffset,
                                                       long length) {
        long off = 0;
        long remaining = length;
        int i, size;
        boolean lastSubRange = false;
        while (remaining > 7 && !lastSubRange) {
            if (remaining > Integer.MAX_VALUE) {
                size = Integer.MAX_VALUE;
            } else {
                size = (int) remaining;
                lastSubRange = true;
            }
            i = vectorizedMismatch(
                    a, aOffset + off,
                    b, bOffset + off,
                    size, LOG2_ARRAY_BYTE_INDEX_SCALE);
            if (i >= 0)
                return off + i;

            i = size - ~i;
            off += i;
            remaining -= i;
        }
        return ~remaining;
    }

    // Booleans
    // Each boolean element takes up one byte

    public static int mismatch(boolean[] a,
                               boolean[] b,
                               int length) {
        int i = 0;
        if (length > 7) {
            if (a[0] != b[0])
                return 0;
            i = vectorizedMismatch(
                    a, Unsafe.ARRAY_BOOLEAN_BASE_OFFSET,
                    b, Unsafe.ARRAY_BOOLEAN_BASE_OFFSET,
                    length, LOG2_ARRAY_BOOLEAN_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[i] != b[i])
                return i;
        }
        return -1;
    }

    public static int mismatch(boolean[] a, int aFromIndex,
                               boolean[] b, int bFromIndex,
                               int length) {
        int i = 0;
        if (length > 7) {
            if (a[aFromIndex] != b[bFromIndex])
                return 0;
            int aOffset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET + aFromIndex;
            int bOffset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET + bFromIndex;
            i = vectorizedMismatch(
                    a, aOffset,
                    b, bOffset,
                    length, LOG2_ARRAY_BOOLEAN_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i])
                return i;
        }
        return -1;
    }


    // Bytes

    /**
     * Find the index of a mismatch between two arrays.
     *
     * <p>This method does not perform bounds checks. It is the responsibility
     * of the caller to perform such bounds checks before calling this method.
     *
     * @param a the first array to be tested for a mismatch
     * @param b the second array to be tested for a mismatch
     * @param length the number of bytes from each array to check
     * @return the index of a mismatch between the two arrays, otherwise -1 if
     * no mismatch.  The index will be within the range of (inclusive) 0 to
     * (exclusive) the smaller of the two array lengths.
     */
    public static int mismatch(byte[] a,
                               byte[] b,
                               int length) {
        // ISSUE: defer to index receiving methods if performance is good
        // assert length <= a.length
        // assert length <= b.length

        int i = 0;
        if (length > 7) {
            if (a[0] != b[0])
                return 0;
            i = vectorizedMismatch(
                    a, Unsafe.ARRAY_BYTE_BASE_OFFSET,
                    b, Unsafe.ARRAY_BYTE_BASE_OFFSET,
                    length, LOG2_ARRAY_BYTE_INDEX_SCALE);
            if (i >= 0)
                return i;
            // Align to tail
            i = length - ~i;
//            assert i >= 0 && i <= 7;
        }
        // Tail < 8 bytes
        for (; i < length; i++) {
            if (a[i] != b[i])
                return i;
        }
        return -1;
    }

    /**
     * Find the relative index of a mismatch between two arrays starting from
     * given indexes.
     *
     * <p>This method does not perform bounds checks. It is the responsibility
     * of the caller to perform such bounds checks before calling this method.
     *
     * @param a the first array to be tested for a mismatch
     * @param aFromIndex the index of the first element (inclusive) in the first
     * array to be compared
     * @param b the second array to be tested for a mismatch
     * @param bFromIndex the index of the first element (inclusive) in the
     * second array to be compared
     * @param length the number of bytes from each array to check
     * @return the relative index of a mismatch between the two arrays,
     * otherwise -1 if no mismatch.  The index will be within the range of
     * (inclusive) 0 to (exclusive) the smaller of the two array bounds.
     */
    public static int mismatch(byte[] a, int aFromIndex,
                               byte[] b, int bFromIndex,
                               int length) {
        // assert 0 <= aFromIndex < a.length
        // assert 0 <= aFromIndex + length <= a.length
        // assert 0 <= bFromIndex < b.length
        // assert 0 <= bFromIndex + length <= b.length
        // assert length >= 0

        int i = 0;
        if (length > 7) {
            if (a[aFromIndex] != b[bFromIndex])
                return 0;
            int aOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET + aFromIndex;
            int bOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET + bFromIndex;
            i = vectorizedMismatch(
                    a, aOffset,
                    b, bOffset,
                    length, LOG2_ARRAY_BYTE_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i])
                return i;
        }
        return -1;
    }


    // Chars

    public static int mismatch(char[] a,
                               char[] b,
                               int length) {
        int i = 0;
        if (length > 3) {
            if (a[0] != b[0])
                return 0;
            i = vectorizedMismatch(
                    a, Unsafe.ARRAY_CHAR_BASE_OFFSET,
                    b, Unsafe.ARRAY_CHAR_BASE_OFFSET,
                    length, LOG2_ARRAY_CHAR_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[i] != b[i])
                return i;
        }
        return -1;
    }

    public static int mismatch(char[] a, int aFromIndex,
                               char[] b, int bFromIndex,
                               int length) {
        int i = 0;
        if (length > 3) {
            if (a[aFromIndex] != b[bFromIndex])
                return 0;
            int aOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_CHAR_INDEX_SCALE);
            int bOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_CHAR_INDEX_SCALE);
            i = vectorizedMismatch(
                    a, aOffset,
                    b, bOffset,
                    length, LOG2_ARRAY_CHAR_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i])
                return i;
        }
        return -1;
    }


    // Shorts

    public static int mismatch(short[] a,
                               short[] b,
                               int length) {
        int i = 0;
        if (length > 3) {
            if (a[0] != b[0])
                return 0;
            i = vectorizedMismatch(
                    a, Unsafe.ARRAY_SHORT_BASE_OFFSET,
                    b, Unsafe.ARRAY_SHORT_BASE_OFFSET,
                    length, LOG2_ARRAY_SHORT_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[i] != b[i])
                return i;
        }
        return -1;
    }

    public static int mismatch(short[] a, int aFromIndex,
                               short[] b, int bFromIndex,
                               int length) {
        int i = 0;
        if (length > 3) {
            if (a[aFromIndex] != b[bFromIndex])
                return 0;
            int aOffset = Unsafe.ARRAY_SHORT_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_SHORT_INDEX_SCALE);
            int bOffset = Unsafe.ARRAY_SHORT_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_SHORT_INDEX_SCALE);
            i = vectorizedMismatch(
                    a, aOffset,
                    b, bOffset,
                    length, LOG2_ARRAY_SHORT_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i])
                return i;
        }
        return -1;
    }


    // Ints

    public static int mismatch(int[] a,
                               int[] b,
                               int length) {
        int i = 0;
        if (length > 1) {
            if (a[0] != b[0])
                return 0;
            i = vectorizedMismatch(
                    a, Unsafe.ARRAY_INT_BASE_OFFSET,
                    b, Unsafe.ARRAY_INT_BASE_OFFSET,
                    length, LOG2_ARRAY_INT_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[i] != b[i])
                return i;
        }
        return -1;
    }

    public static int mismatch(int[] a, int aFromIndex,
                               int[] b, int bFromIndex,
                               int length) {
        int i = 0;
        if (length > 1) {
            if (a[aFromIndex] != b[bFromIndex])
                return 0;
            int aOffset = Unsafe.ARRAY_INT_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_INT_INDEX_SCALE);
            int bOffset = Unsafe.ARRAY_INT_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_INT_INDEX_SCALE);
            i = vectorizedMismatch(
                    a, aOffset,
                    b, bOffset,
                    length, LOG2_ARRAY_INT_INDEX_SCALE);
            if (i >= 0)
                return i;
            i = length - ~i;
        }
        for (; i < length; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i])
                return i;
        }
        return -1;
    }


    // Floats

    public static int mismatch(float[] a,
                               float[] b,
                               int length) {
        return mismatch(a, 0, b, 0, length);
    }

    public static int mismatch(float[] a, int aFromIndex,
                               float[] b, int bFromIndex,
                               int length) {
        int i = 0;
        if (length > 1) {
            if (Float.floatToRawIntBits(a[aFromIndex]) == Float.floatToRawIntBits(b[bFromIndex])) {
                int aOffset = Unsafe.ARRAY_FLOAT_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_FLOAT_INDEX_SCALE);
                int bOffset = Unsafe.ARRAY_FLOAT_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_FLOAT_INDEX_SCALE);
                i = vectorizedMismatch(
                        a, aOffset,
                        b, bOffset,
                        length, LOG2_ARRAY_FLOAT_INDEX_SCALE);
            }
            // Mismatched
            if (i >= 0) {
                // Check if mismatch is not associated with two NaN values
                if (!Float.isNaN(a[aFromIndex + i]) || !Float.isNaN(b[bFromIndex + i]))
                    return i;

                // Mismatch on two different NaN values that are normalized to match
                // Fall back to slow mechanism
                // ISSUE: Consider looping over vectorizedMismatch adjusting ranges
                // However, requires that returned value be relative to input ranges
                i++;
            }
            // Matched
            else {
                i = length - ~i;
            }
        }
        for (; i < length; i++) {
            if (Float.floatToIntBits(a[aFromIndex + i]) != Float.floatToIntBits(b[bFromIndex + i]))
                return i;
        }
        return -1;
    }

    // 64 bit sizes

    // Long

    public static int mismatch(long[] a,
                               long[] b,
                               int length) {
        if (length == 0) {
            return -1;
        }
        if (a[0] != b[0])
            return 0;
        int i = vectorizedMismatch(
                a, Unsafe.ARRAY_LONG_BASE_OFFSET,
                b, Unsafe.ARRAY_LONG_BASE_OFFSET,
                length, LOG2_ARRAY_LONG_INDEX_SCALE);
        return i >= 0 ? i : -1;
    }

    public static int mismatch(long[] a, int aFromIndex,
                               long[] b, int bFromIndex,
                               int length) {
        if (length == 0) {
            return -1;
        }
        if (a[aFromIndex] != b[bFromIndex])
            return 0;
        int aOffset = Unsafe.ARRAY_LONG_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_LONG_INDEX_SCALE);
        int bOffset = Unsafe.ARRAY_LONG_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_LONG_INDEX_SCALE);
        int i = vectorizedMismatch(
                a, aOffset,
                b, bOffset,
                length, LOG2_ARRAY_LONG_INDEX_SCALE);
        return i >= 0 ? i : -1;
    }


    // Double

    public static int mismatch(double[] a,
                               double[] b,
                               int length) {
        return mismatch(a, 0, b, 0, length);
    }

    public static int mismatch(double[] a, int aFromIndex,
                               double[] b, int bFromIndex,
                               int length) {
        if (length == 0) {
            return -1;
        }
        int i = 0;
        if (Double.doubleToRawLongBits(a[aFromIndex]) == Double.doubleToRawLongBits(b[bFromIndex])) {
            int aOffset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_DOUBLE_INDEX_SCALE);
            int bOffset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_DOUBLE_INDEX_SCALE);
            i = vectorizedMismatch(
                    a, aOffset,
                    b, bOffset,
                    length, LOG2_ARRAY_DOUBLE_INDEX_SCALE);
        }
        if (i >= 0) {
            // Check if mismatch is not associated with two NaN values
            if (!Double.isNaN(a[aFromIndex + i]) || !Double.isNaN(b[bFromIndex + i]))
                return i;

            // Mismatch on two different NaN values that are normalized to match
            // Fall back to slow mechanism
            // ISSUE: Consider looping over vectorizedMismatch adjusting ranges
            // However, requires that returned value be relative to input ranges
            i++;
            for (; i < length; i++) {
                if (Double.doubleToLongBits(a[aFromIndex + i]) != Double.doubleToLongBits(b[bFromIndex + i]))
                    return i;
            }
        }

        return -1;
    }

    /**
     * The maximum length of array to allocate (unless necessary).
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * {@code OutOfMemoryError: Requested array size exceeds VM limit}
     */
    public static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * Calculates a new array length given an array's current length, a preferred
     * growth value, and a minimum growth value.  If the preferred growth value
     * is less than the minimum growth value, the minimum growth value is used in
     * its place.  If the sum of the current length and the preferred growth
     * value does not exceed {@link #MAX_ARRAY_LENGTH}, that sum is returned.
     * If the sum of the current length and the minimum growth value does not
     * exceed {@code MAX_ARRAY_LENGTH}, then {@code MAX_ARRAY_LENGTH} is returned.
     * If the sum does not overflow an int, then {@code Integer.MAX_VALUE} is
     * returned.  Otherwise, {@code OutOfMemoryError} is thrown.
     *
     * @param oldLength   current length of the array (must be non negative)
     * @param minGrowth   minimum required growth of the array length (must be
     *                    positive)
     * @param prefGrowth  preferred growth of the array length (ignored, if less
     *                    then {@code minGrowth})
     * @return the new length of the array
     * @throws OutOfMemoryError if increasing {@code oldLength} by
     *                    {@code minGrowth} overflows.
     */
    public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        // assert oldLength >= 0
        // assert minGrowth > 0

        int newLength = Math.max(minGrowth, prefGrowth) + oldLength;
        if (newLength - MAX_ARRAY_LENGTH <= 0) {
            return newLength;
        }
        return hugeLength(oldLength, minGrowth);
    }

    private static int hugeLength(int oldLength, int minGrowth) {
        int minLength = oldLength + minGrowth;
        if (minLength < 0) { // overflow
            throw new OutOfMemoryError("Required array length too large");
        }
        if (minLength <= MAX_ARRAY_LENGTH) {
            return MAX_ARRAY_LENGTH;
        }
        return Integer.MAX_VALUE;
    }
}
