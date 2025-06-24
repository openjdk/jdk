/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.IntrinsicCandidate;

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
    @IntrinsicCandidate
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
     * Calculates the hash code for the subrange of an integer array.
     *
     * <p> This method does not perform type checks or bounds checks. It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * @param a the array
     * @param fromIndex the first index of the subrange of the array
     * @param length the number of elements in the subrange
     * @param initialValue the initial hash value, typically 0 or 1
     *
     * @return the calculated hash value
     */
    public static int hashCode(int[] a, int fromIndex, int length, int initialValue) {
        return switch (length) {
            case 0 -> initialValue;
            case 1 -> 31 * initialValue + a[fromIndex];
            default -> vectorizedHashCode(a, fromIndex, length, initialValue, T_INT);
        };
    }

    /**
     * Calculates the hash code for the subrange of a short array.
     *
     * <p> This method does not perform type checks or bounds checks. It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * @param a the array
     * @param fromIndex the first index of the subrange of the array
     * @param length the number of elements in the subrange
     * @param initialValue the initial hash value, typically 0 or 1
     *
     * @return the calculated hash value
     */
    public static int hashCode(short[] a, int fromIndex, int length, int initialValue) {
        return switch (length) {
            case 0 -> initialValue;
            case 1 -> 31 * initialValue + a[fromIndex];
            default -> vectorizedHashCode(a, fromIndex, length, initialValue, T_SHORT);
        };
    }

    /**
     * Calculates the hash code for the subrange of a char array.
     *
     * <p> This method does not perform type checks or bounds checks. It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * @param a the array
     * @param fromIndex the first index of the subrange of the array
     * @param length the number of elements in the subrange
     * @param initialValue the initial hash value, typically 0 or 1
     *
     * @return the calculated hash value
     */
    public static int hashCode(char[] a, int fromIndex, int length, int initialValue) {
        return switch (length) {
            case 0 -> initialValue;
            case 1 -> 31 * initialValue + a[fromIndex];
            default -> vectorizedHashCode(a, fromIndex, length, initialValue, T_CHAR);
        };
    }

    /**
     * Calculates the hash code for the subrange of a byte array.
     *
     * <p> This method does not perform type checks or bounds checks. It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * @param a the array
     * @param fromIndex the first index of the subrange of the array
     * @param length the number of elements in the subrange
     * @param initialValue the initial hash value, typically 0 or 1
     *
     * @return the calculated hash value
     */
    public static int hashCode(byte[] a, int fromIndex, int length, int initialValue) {
        return switch (length) {
            case 0 -> initialValue;
            case 1 -> 31 * initialValue + a[fromIndex];
            default -> vectorizedHashCode(a, fromIndex, length, initialValue, T_BYTE);
        };
    }

    /**
     * Calculates the hash code for the subrange of a byte array whose elements
     * are treated as unsigned bytes.
     *
     * <p> This method does not perform type checks or bounds checks. It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * @param a the array
     * @param fromIndex the first index of the subrange of the array
     * @param length the number of elements in the subrange
     * @param initialValue the initial hash value, typically 0 or 1
     *
     * @return the calculated hash value
     */
    public static int hashCodeOfUnsigned(byte[] a, int fromIndex, int length, int initialValue) {
        return switch (length) {
            case 0 -> initialValue;
            case 1 -> 31 * initialValue + Byte.toUnsignedInt(a[fromIndex]);
            default -> vectorizedHashCode(a, fromIndex, length, initialValue, T_BOOLEAN);
        };
    }

    /**
     * Calculates the hash code for the subrange of a byte array whose contents
     * are treated as UTF-16 chars.
     *
     * <p> This method does not perform type checks or bounds checks. It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * <p> {@code fromIndex} and {@code length} must be scaled down to char
     * indexes.
     *
     * @param a the array
     * @param fromIndex the first index of a char in the subrange of the array
     * @param length the number of chars in the subrange
     * @param initialValue the initial hash value, typically 0 or 1
     *
     * @return the calculated hash value
     */
    public static int hashCodeOfUTF16(byte[] a, int fromIndex, int length, int initialValue) {
        return switch (length) {
            case 0 -> initialValue;
            case 1 -> 31 * initialValue + JLA.uncheckedGetUTF16Char(a, fromIndex);
            default -> vectorizedHashCode(a, fromIndex, length, initialValue, T_CHAR);
        };
    }

    /**
     * Calculates the hash code for the subrange of an object array.
     *
     * <p> This method does not perform type checks or bounds checks. It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * @param a the array
     * @param fromIndex the first index of the subrange of the array
     * @param length the number of elements in the subrange
     * @param initialValue the initial hash value, typically 0 or 1
     *
     * @return the calculated hash value
     */
    public static int hashCode(Object[] a, int fromIndex, int length, int initialValue) {
        int result = initialValue;
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + Objects.hashCode(a[i]);
        }
        return result;
    }

    // Possible values for the type operand of the NEWARRAY instruction.
    // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html#jvms-6.5.newarray.

    private static final int T_BOOLEAN = 4;
    private static final int T_CHAR = 5;
    private static final int T_FLOAT = 6;
    private static final int T_DOUBLE = 7;
    private static final int T_BYTE = 8;
    private static final int T_SHORT = 9;
    private static final int T_INT = 10;
    private static final int T_LONG = 11;

    /**
     * Calculate the hash code for an array in a way that enables efficient
     * vectorization.
     *
     * <p>This method does not perform type checks or bounds checks.  It is the
     * responsibility of the caller to perform such checks before calling this
     * method.
     *
     * @param array for which to calculate hash code
     * @param fromIndex start index, scaled to basicType
     * @param length number of elements to include in the hash
     * @param initialValue the initial value for the hash (typically constant 0 or 1)
     * @param basicType type constant denoting how to interpret the array content.
     *                  T_BOOLEAN is used to signify unsigned bytes, and T_CHAR might be used
     *                  even if array is a byte[].
     * @implNote currently basicType must be constant at the call site for this method
     *           to be intrinsified.
     *
     * @return the calculated hash value
     */
    @IntrinsicCandidate
    private static int vectorizedHashCode(Object array, int fromIndex, int length, int initialValue,
                                          int basicType) {
        return switch (basicType) {
            case T_BOOLEAN -> unsignedHashCode(initialValue, (byte[]) array, fromIndex, length);
            case T_CHAR -> array instanceof byte[]
                    ? utf16hashCode(initialValue, (byte[]) array, fromIndex, length)
                    : hashCode(initialValue, (char[]) array, fromIndex, length);
            case T_BYTE -> hashCode(initialValue, (byte[]) array, fromIndex, length);
            case T_SHORT -> hashCode(initialValue, (short[]) array, fromIndex, length);
            case T_INT -> hashCode(initialValue, (int[]) array, fromIndex, length);
                default -> throw new IllegalArgumentException("unrecognized basic type: " + basicType);
        };
    }

    private static int unsignedHashCode(int result, byte[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + Byte.toUnsignedInt(a[i]);
        }
        return result;
    }

    private static int hashCode(int result, byte[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    private static int hashCode(int result, char[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    private static int hashCode(int result, short[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    private static int hashCode(int result, int[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    /*
     * fromIndex and length must be scaled to char indexes.
     */
    private static int utf16hashCode(int result, byte[] value, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + JLA.uncheckedGetUTF16Char(value, i);
        }
        return result;
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
            long aOffset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET + aFromIndex;
            long bOffset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET + bFromIndex;
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
            long aOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET + aFromIndex;
            long bOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET + bFromIndex;
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
            long aOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_CHAR_INDEX_SCALE);
            long bOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_CHAR_INDEX_SCALE);
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
            long aOffset = Unsafe.ARRAY_SHORT_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_SHORT_INDEX_SCALE);
            long bOffset = Unsafe.ARRAY_SHORT_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_SHORT_INDEX_SCALE);
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
            long aOffset = Unsafe.ARRAY_INT_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_INT_INDEX_SCALE);
            long bOffset = Unsafe.ARRAY_INT_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_INT_INDEX_SCALE);
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
                long aOffset = Unsafe.ARRAY_FLOAT_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_FLOAT_INDEX_SCALE);
                long bOffset = Unsafe.ARRAY_FLOAT_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_FLOAT_INDEX_SCALE);
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
        long aOffset = Unsafe.ARRAY_LONG_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_LONG_INDEX_SCALE);
        long bOffset = Unsafe.ARRAY_LONG_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_LONG_INDEX_SCALE);
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
            long aOffset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET + (aFromIndex << LOG2_ARRAY_DOUBLE_INDEX_SCALE);
            long bOffset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET + (bFromIndex << LOG2_ARRAY_DOUBLE_INDEX_SCALE);
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
     * A soft maximum array length imposed by array growth computations.
     * Some JVMs (such as HotSpot) have an implementation limit that will cause
     *
     *     OutOfMemoryError("Requested array size exceeds VM limit")
     *
     * to be thrown if a request is made to allocate an array of some length near
     * Integer.MAX_VALUE, even if there is sufficient heap available. The actual
     * limit might depend on some JVM implementation-specific characteristics such
     * as the object header size. The soft maximum value is chosen conservatively so
     * as to be smaller than any implementation limit that is likely to be encountered.
     */
    public static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * Computes a new array length given an array's current length, a minimum growth
     * amount, and a preferred growth amount. The computation is done in an overflow-safe
     * fashion.
     *
     * This method is used by objects that contain an array that might need to be grown
     * in order to fulfill some immediate need (the minimum growth amount) but would also
     * like to request more space (the preferred growth amount) in order to accommodate
     * potential future needs. The returned length is usually clamped at the soft maximum
     * length in order to avoid hitting the JVM implementation limit. However, the soft
     * maximum will be exceeded if the minimum growth amount requires it.
     *
     * If the preferred growth amount is less than the minimum growth amount, the
     * minimum growth amount is used as the preferred growth amount.
     *
     * The preferred length is determined by adding the preferred growth amount to the
     * current length. If the preferred length does not exceed the soft maximum length
     * (SOFT_MAX_ARRAY_LENGTH) then the preferred length is returned.
     *
     * If the preferred length exceeds the soft maximum, we use the minimum growth
     * amount. The minimum required length is determined by adding the minimum growth
     * amount to the current length. If the minimum required length exceeds Integer.MAX_VALUE,
     * then this method throws OutOfMemoryError. Otherwise, this method returns the greater of
     * the soft maximum or the minimum required length.
     *
     * Note that this method does not do any array allocation itself; it only does array
     * length growth computations. However, it will throw OutOfMemoryError as noted above.
     *
     * Note also that this method cannot detect the JVM's implementation limit, and it
     * may compute and return a length value up to and including Integer.MAX_VALUE that
     * might exceed the JVM's implementation limit. In that case, the caller will likely
     * attempt an array allocation with that length and encounter an OutOfMemoryError.
     * Of course, regardless of the length value returned from this method, the caller
     * may encounter OutOfMemoryError if there is insufficient heap to fulfill the request.
     *
     * @param oldLength   current length of the array (must be nonnegative)
     * @param minGrowth   minimum required growth amount (must be positive)
     * @param prefGrowth  preferred growth amount
     * @return the new array length
     * @throws OutOfMemoryError if the new length would exceed Integer.MAX_VALUE
     */
    public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        // preconditions not checked because of inlining
        // assert oldLength >= 0
        // assert minGrowth > 0

        int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
        if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
            return prefLength;
        } else {
            // put code cold in a separate method
            return hugeLength(oldLength, minGrowth);
        }
    }

    private static int hugeLength(int oldLength, int minGrowth) {
        int minLength = oldLength + minGrowth;
        if (minLength < 0) { // overflow
            throw new OutOfMemoryError(
                "Required array length " + oldLength + " + " + minGrowth + " is too large");
        } else if (minLength <= SOFT_MAX_ARRAY_LENGTH) {
            return SOFT_MAX_ARRAY_LENGTH;
        } else {
            return minLength;
        }
    }

    /**
     * Reverses the elements of an array in-place.
     *
     * @param <T> the array component type
     * @param a the array to be reversed
     * @return the reversed array, always the same array as the argument
     */
    public static <T> T[] reverse(T[] a) {
        int limit = a.length / 2;
        for (int i = 0, j = a.length - 1; i < limit; i++, j--) {
            T t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    /**
     * Dump the contents of the given collection into the given array, in reverse order.
     * This mirrors the semantics of Collection.toArray(T[]) in regard to reusing the given
     * array, appending null if necessary, or allocating a new array of the same component type.
     * <p>
     * A constraint is that this method should issue exactly one method call on the collection
     * to obtain the elements and the size. Having a separate size() call or using an Iterator
     * could result in errors if the collection changes size between calls. This implies that
     * the elements need to be obtained via a single call to one of the toArray() methods.
     * This further implies allocating memory proportional to the number of elements and
     * making an extra copy, but this seems unavoidable.
     * <p>
     * An obvious approach would be simply to call coll.toArray(array) and then reverse the
     * order of the elements. This doesn't work, because if given array is sufficiently long,
     * we cannot tell how many elements were copied into it and thus there is no way to reverse
     * the right set of elements while leaving the remaining array elements undisturbed.
     *
     * @throws ArrayStoreException if coll contains elements that can't be stored in the array
     */
    public static <T> T[] toArrayReversed(Collection<?> coll, T[] array) {
        T[] newArray = reverse(coll.toArray(Arrays.copyOfRange(array, 0, 0)));
        if (newArray.length > array.length) {
            return newArray;
        } else {
            System.arraycopy(newArray, 0, array, 0, newArray.length);
            if (array.length > newArray.length) {
                array[newArray.length] = null;
            }
            return array;
        }
    }
}
