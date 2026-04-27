/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=aggressive
 * @summary Verify correct object metadata for large arrays under Shenandoah GC stress (aggressive heuristics)
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m -Xms256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *      TestLargeArrayInitGCStress
 */

/*
 * @test id=generational-aggressive
 * @summary Verify correct object metadata for large arrays under Shenandoah generational mode with aggressive heuristics
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m -Xms256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive -XX:ShenandoahGCMode=generational
 *      TestLargeArrayInitGCStress
 */

/*
 * @test id=compact-on
 * @summary Verify correct object metadata for large arrays with compact object headers enabled
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m -Xms256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive

 *      -XX:+UseCompactObjectHeaders
 *      TestLargeArrayInitGCStress
 */

/*
 * @test id=compact-off
 * @summary Verify correct object metadata for large arrays with compact object headers disabled
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m -Xms256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *      -XX:-UseCompactObjectHeaders
 *      TestLargeArrayInitGCStress
 */

/**
 *
 * Allocates large arrays of various types under GC stress (aggressive heuristics,
 * tight 256MB heap) and verifies that after each allocation:
 *   - array.length matches the requested length
 *   - array.getClass() matches the expected array type
 *   - All elements are zero/null
 *
 * Arrays are sized to span multiple 64K-word segments to exercise the segmented
 * clearing path in ShenandoahObjArrayAllocator. The loop creates sustained GC
 * pressure so that safepoints are likely to occur during array initialization.
 */
public class TestLargeArrayInitGCStress {

    static final int ITERATIONS = 50;

    // Array sizes: small (within one segment), medium (a few segments), large (many segments)
    static final int[] BYTE_SIZES   = {1024, 256 * 1024, 2 * 1024 * 1024, 16 * 1024 * 1024};
    static final int[] INT_SIZES    = {1024, 64 * 1024, 512 * 1024, 4 * 1024 * 1024};
    static final int[] LONG_SIZES   = {1024, 32 * 1024, 256 * 1024, 2 * 1024 * 1024};
    static final int[] OBJ_SIZES    = {1024, 32 * 1024, 256 * 1024, 2 * 1024 * 1024};

    // Volatile sink to prevent dead code elimination
    static volatile Object sink;

    public static void main(String[] args) {
        for (int iter = 0; iter < ITERATIONS; iter++) {
            testByteArrays();
            testIntArrays();
            testLongArrays();
            testObjectArrays();
        }
        System.out.println("TestLargeArrayInitGCStress PASSED");
    }

    static void testByteArrays() {
        for (int len : BYTE_SIZES) {
            byte[] arr = new byte[len];
            sink = arr;
            verifyLength(arr.length, len, "byte[]");
            verifyClass(arr.getClass(), byte[].class, "byte[]");
            verifyByteZeros(arr, len);
        }
    }

    static void testIntArrays() {
        for (int len : INT_SIZES) {
            int[] arr = new int[len];
            sink = arr;
            verifyLength(arr.length, len, "int[]");
            verifyClass(arr.getClass(), int[].class, "int[]");
            verifyIntZeros(arr, len);
        }
    }

    static void testLongArrays() {
        for (int len : LONG_SIZES) {
            long[] arr = new long[len];
            sink = arr;
            verifyLength(arr.length, len, "long[]");
            verifyClass(arr.getClass(), long[].class, "long[]");
            verifyLongZeros(arr, len);
        }
    }

    static void testObjectArrays() {
        for (int len : OBJ_SIZES) {
            Object[] arr = new Object[len];
            sink = arr;
            verifyLength(arr.length, len, "Object[]");
            verifyClass(arr.getClass(), Object[].class, "Object[]");
            verifyObjectNulls(arr, len);
        }
    }

    static void verifyLength(int actual, int expected, String type) {
        if (actual != expected) {
            throw new RuntimeException(type + " length mismatch: expected " + expected + ", got " + actual);
        }
    }

    static void verifyClass(Class<?> actual, Class<?> expected, String type) {
        if (actual != expected) {
            throw new RuntimeException(type + " class mismatch: expected " + expected.getName() + ", got " + actual.getName());
        }
    }

    static void verifyByteZeros(byte[] arr, int len) {
        for (int i = 0; i < len; i++) {
            if (arr[i] != 0) {
                throw new RuntimeException("byte[] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void verifyIntZeros(int[] arr, int len) {
        for (int i = 0; i < len; i++) {
            if (arr[i] != 0) {
                throw new RuntimeException("int[] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void verifyLongZeros(long[] arr, int len) {
        for (int i = 0; i < len; i++) {
            if (arr[i] != 0L) {
                throw new RuntimeException("long[] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void verifyObjectNulls(Object[] arr, int len) {
        for (int i = 0; i < len; i++) {
            if (arr[i] != null) {
                throw new RuntimeException("Object[] not null at index " + i + ": " + arr[i]);
            }
        }
    }
}
