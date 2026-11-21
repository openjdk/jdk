/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.arraycopy;

import java.lang.reflect.Array;
import java.util.Objects;

/*
 * @test id=avx2
 * @bug 8394825
 * @summary Check array copies with the AVX2 non-temporal store path enabled,
 *          disabled, and with the vectorized fallback disabled.
 * @requires os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*"
 *
 * @run main/othervm/timeout=600 -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=100
 *      -XX:UseAVX=2 -XX:+UnlockDiagnosticVMOptions -XX:ArrayCopyNonTemporalThreshold=65536
 *      ${test.main.class}
 * @run main/othervm/timeout=600 -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=100
 *      -XX:UseAVX=2 -XX:+UnlockDiagnosticVMOptions -XX:ArrayCopyNonTemporalThreshold=0
 *      ${test.main.class}
 * @run main/othervm/timeout=600 -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=100
 *      -XX:UseAVX=2 -XX:-UseUnalignedLoadStores -XX:+UnlockDiagnosticVMOptions
 *      -XX:ArrayCopyNonTemporalThreshold=65536
 *      ${test.main.class}
 */

/*
 * @test id=avx3
 * @bug 8394825
 * @summary Check array copies with the AVX-512 path forced.
 * @requires os.simpleArch == "x64" & vm.cpu.features ~= ".*avx512.*"
 *
 * @run main/othervm/timeout=600 -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=100
 *      -XX:UseAVX=3
 *      ${test.main.class}
 */

/*
 * @test id=non-x64
 * @bug 8394825
 * @summary Check array copies on platforms without the x86 intrinsics.
 * @requires os.simpleArch != "x64"
 *
 * @run main/othervm/timeout=600 -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=100
 *      ${test.main.class}
 */

public class TestArrayCopyNonTemporal {

    // Must match -XX:ArrayCopyNonTemporalThreshold in the @run lines that
    // enable non-temporal stores.
    private static final int THRESHOLD = 64 * 1024;

    private static final Class<?>[] PRIMITIVE_TYPES = {
        byte.class, short.class, char.class, int.class,
        long.class, float.class, double.class, boolean.class
    };

    private static final int[] OVERLAP_POSITIONS = {0, 1, 2, 8};

    public static void main(String[] args) {
        for (Class<?> type : PRIMITIVE_TYPES) {
            int thresholdElements = THRESHOLD / elementSize(type);

            testOffsets(type, thresholdElements);
            testLengths(type, new int[] {
                0, 1, 2, 7, 8, 9,
                31, 32, 33, 63, 64, 65,
                127, 128, 129, 4096,
                thresholdElements - 1,
                thresholdElements,
                thresholdElements + 1,
                thresholdElements + 7,
                thresholdElements + 8,
                4 * thresholdElements
            });
            testOverlapping(type, new int[] {
                thresholdElements,
                thresholdElements + 1,
                thresholdElements + 7,
                thresholdElements + 8
            });
        }
        testObjectArrays();
    }

    // Test all destination element offsets within a 32-byte period,
    // using two source offsets to exercise different alignments.
    private static void testOffsets(Class<?> type, int thresholdElements) {
        int capacity = thresholdElements + 32 + 8;
        Object src = newArray(type, capacity, 1);
        for (int offset = 0; offset < 32 / elementSize(type); offset++) {
            checkDisjoint(type, src, 0, newArray(type, capacity, 2), offset, thresholdElements);
            checkDisjoint(type, src, 1, newArray(type, capacity, 2), offset, thresholdElements);
        }
    }

    private static void testLengths(Class<?> type, int[] lengths) {
        for (int len : lengths) {
            Object src = newArray(type, len + 64, 3);
            for (int offset : new int[] {0, 1}) {
                checkDisjoint(type, src, 0, newArray(type, len + 64, 4), offset, len);
            }
        }
    }

    // Forward and backward overlapping copies on the same array.
    private static void testOverlapping(Class<?> type, int[] lengths) {
        for (int len : lengths) {
            Object array = newArray(type, len + 32, 5);
            for (int pos : OVERLAP_POSITIONS) {
                checkOverlapping(type, array, pos, 0, len);
                checkOverlapping(type, array, 0, pos, len);
            }
        }
    }

    // Reference arrays never take the non-temporal store path, but they share
    // the same entry point; check reference identity is preserved.
    private static void testObjectArrays() {
        for (int len : new int[] {0, 1, 2, 4096, 65536}) {
            Object[] src = newObjectArray(len + 32, 6);
            for (int offset : new int[] {0, 1, 2, 3}) {
                Object[] dst = newObjectArray(len + 32, 7);
                Object[] expected = objectCopyOf(dst);
                for (int i = 0; i < len; i++) {
                    expected[offset + i] = src[i];
                }
                copy(src, 0, dst, offset, len);
                verifyObjectArray(expected, dst, "Object: length=" + len + ", dstPos=" + offset);
            }
            for (int pos : OVERLAP_POSITIONS) {
                checkObjectOverlap(src, pos, 0, len);
                checkObjectOverlap(src, 0, pos, len);
            }
        }
    }

    private static void checkDisjoint(Class<?> type, Object src, int srcPos,
                                      Object dst, int dstPos, int len) {
        Object expected = elementCopyOf(dst);
        for (int i = 0; i < len; i++) {
            Array.set(expected, dstPos + i, Array.get(src, srcPos + i));
        }
        copy(src, srcPos, dst, dstPos, len);
        verify(type, expected, dst, failure(type, len, srcPos, dstPos, false));
    }

    private static void checkOverlapping(Class<?> type, Object original, int srcPos,
                                         int dstPos, int len) {
        Object array = elementCopyOf(original);
        Object expected = elementCopyOf(original);
        for (int i = 0; i < len; i++) {
            Array.set(expected, dstPos + i, Array.get(original, srcPos + i));
        }
        copy(array, srcPos, array, dstPos, len);
        verify(type, expected, array, failure(type, len, srcPos, dstPos, true));
    }

    private static void checkObjectOverlap(Object[] original, int srcPos, int dstPos, int len) {
        Object[] array = objectCopyOf(original);
        Object[] expected = objectCopyOf(original);
        for (int i = 0; i < len; i++) {
            expected[dstPos + i] = original[srcPos + i];
        }
        copy(array, srcPos, array, dstPos, len);
        verifyObjectArray(expected, array, "Object: length=" + len
                + ", srcPos=" + srcPos + ", dstPos=" + dstPos);
    }

    private static void verify(Class<?> type, Object expected, Object actual, String context) {
        int length = Array.getLength(expected);
        for (int i = 0; i < length; i++) {
            if (!elementsEqual(type, expected, actual, i)) {
                throw new RuntimeException(context + ", index=" + i);
            }
        }
    }

    // Compares raw bit patterns for float and double so that NaN, infinities
    // and signed zero are checked exactly.
    private static boolean elementsEqual(Class<?> type, Object expected, Object actual, int i) {
        Object e = Array.get(expected, i);
        Object a = Array.get(actual, i);
        if (type == float.class) {
            return Float.floatToRawIntBits((Float) e) == Float.floatToRawIntBits((Float) a);
        }
        if (type == double.class) {
            return Double.doubleToRawLongBits((Double) e) == Double.doubleToRawLongBits((Double) a);
        }
        return Objects.equals(e, a);
    }

    private static void verifyObjectArray(Object[] expected, Object[] actual, String context) {
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new RuntimeException(context + ", index=" + i);
            }
        }
    }

    private static String failure(Class<?> type, int len, int srcPos, int dstPos, boolean overlap) {
        return type + ": length=" + len + ", srcPos=" + srcPos + ", dstPos=" + dstPos
                + ", overlap=" + overlap;
    }

    // Keep the arraycopy call separate to avoid specializing the test
    // directly on constant copy lengths and array types.
    private static void copy(Object src, int srcPos, Object dst, int dstPos, int len) {
        System.arraycopy(src, srcPos, dst, dstPos, len);
    }

    private static int elementSize(Class<?> type) {
        if (type == byte.class || type == boolean.class) {
            return 1;
        }
        if (type == short.class || type == char.class) {
            return 2;
        }
        if (type == int.class || type == float.class) {
            return 4;
        }
        return 8; // long, double
    }

    private static Object newArray(Class<?> type, int length, int seed) {
        Object array = Array.newInstance(type, length);
        for (int i = 0; i < length; i++) {
            int value = i * 17 + seed;
            Object element;
            if (type == byte.class) {
                element = (byte) value;
            } else if (type == short.class) {
                element = (short) value;
            } else if (type == char.class) {
                element = (char) value;
            } else if (type == int.class) {
                element = value;
            } else if (type == long.class) {
                element = ((long) value << 32) ^ (value * 31L);
            } else if (type == float.class) {
                element = floatElement(i, value);
            } else if (type == double.class) {
                element = doubleElement(i, value);
            } else {
                // Non-periodic pattern: a periodic one can hide a missing
                // copy, e.g. all-False arrays compare equal after a shift.
                element = (mixed(i, seed) & 1) == 0;
            }
            Array.set(array, i, element);
        }
        return array;
    }

    private static Object floatElement(int i, int value) {
        switch (i & 15) {
            case 0: return Float.NaN;
            case 1: return Float.POSITIVE_INFINITY;
            case 2: return Float.NEGATIVE_INFINITY;
            case 3: return -0.0f;
            default: return (float) value;
        }
    }

    private static Object doubleElement(int i, int value) {
        switch (i & 15) {
            case 0: return Double.NaN;
            case 1: return Double.POSITIVE_INFINITY;
            case 2: return Double.NEGATIVE_INFINITY;
            case 3: return -0.0d;
            default: return (double) value;
        }
    }

    private static int mixed(int i, int seed) {
        int h = i * 0x9E3779B9 + seed;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    // Builds the expected result without System.arraycopy.
    private static Object elementCopyOf(Object array) {
        int length = Array.getLength(array);
        Object copy = Array.newInstance(array.getClass().getComponentType(), length);
        for (int i = 0; i < length; i++) {
            Array.set(copy, i, Array.get(array, i));
        }
        return copy;
    }

    private static Object[] newObjectArray(int length, int seed) {
        Object[] array = new Object[length];
        for (int i = 0; i < length; i++) {
            // Values above the Integer cache range so that identity and
            // equality agree only if the same reference was copied.
            array[i] = Integer.valueOf(1000 + i * 17 + seed);
        }
        return array;
    }

    private static Object[] objectCopyOf(Object[] array) {
        Object[] copy = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[i];
        }
        return copy;
    }
}
