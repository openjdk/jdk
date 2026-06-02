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
 * @test id=adaptive
 * @summary Verify behavioral equivalence for small arrays under Shenandoah adaptive mode
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m -Xms256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive
 *      TestSmallArrayInit
 */

/*
 * @test id=generational
 * @summary Verify behavioral equivalence for small arrays under Shenandoah generational mode
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m -Xms256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive -XX:ShenandoahGCMode=generational
 *      TestSmallArrayInit
 */

/**
 *
 * For arrays with word_size &lt;= 64K words (the segment size threshold),
 * ShenandoahObjArrayAllocator delegates to ObjArrayAllocator::initialize().
 * This test verifies that small arrays of various types and sizes are correctly
 * zero-initialized, confirming no regression vs. the default allocator behavior.
 *
 * Test sizes:
 *   - Tiny:          10 elements
 *   - Small:         1000 elements
 *   - Near boundary: just under 64K words (65536 words = 524288 bytes on 64-bit)
 *       byte[]   -> 524288 elements  (524288 bytes = 64K words)
 *       int[]    -> 131072 elements  (524288 bytes = 64K words)
 *       long[]   -> 65536 elements   (524288 bytes = 64K words)
 *       Object[] -> 65536 elements   (524288 bytes = 64K words, 8 bytes/ref)
 */
public class TestSmallArrayInit {

    // Tiny sizes
    static final int TINY = 10;

    // Small sizes
    static final int SMALL = 1000;

    // Near 64K-word boundary sizes (just under 524288 bytes of element data)
    // 64K words = 65536 words = 524288 bytes on 64-bit
    static final int NEAR_BOUNDARY_BYTE = 524288;    // 524288 bytes
    static final int NEAR_BOUNDARY_INT  = 131072;    // 131072 * 4 = 524288 bytes
    static final int NEAR_BOUNDARY_LONG = 65536;     // 65536 * 8 = 524288 bytes
    static final int NEAR_BOUNDARY_OBJ  = 65536;     // 65536 * 8 = 524288 bytes (8 bytes/ref on 64-bit)

    public static void main(String[] args) {
        testByteArrays();
        testIntArrays();
        testLongArrays();
        testObjectArrays();
        System.out.println("TestSmallArrayInit PASSED");
    }

    static void testByteArrays() {
        verifyByteArray(new byte[TINY], "tiny");
        verifyByteArray(new byte[SMALL], "small");
        verifyByteArray(new byte[NEAR_BOUNDARY_BYTE], "near-boundary");
    }

    static void testIntArrays() {
        verifyIntArray(new int[TINY], "tiny");
        verifyIntArray(new int[SMALL], "small");
        verifyIntArray(new int[NEAR_BOUNDARY_INT], "near-boundary");
    }

    static void testLongArrays() {
        verifyLongArray(new long[TINY], "tiny");
        verifyLongArray(new long[SMALL], "small");
        verifyLongArray(new long[NEAR_BOUNDARY_LONG], "near-boundary");
    }

    static void testObjectArrays() {
        verifyObjectArray(new Object[TINY], "tiny");
        verifyObjectArray(new Object[SMALL], "small");
        verifyObjectArray(new Object[NEAR_BOUNDARY_OBJ], "near-boundary");
    }

    static void verifyByteArray(byte[] arr, String label) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != 0) {
                throw new RuntimeException("byte[" + label + "] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void verifyIntArray(int[] arr, String label) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != 0) {
                throw new RuntimeException("int[" + label + "] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void verifyLongArray(long[] arr, String label) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != 0L) {
                throw new RuntimeException("long[" + label + "] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void verifyObjectArray(Object[] arr, String label) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null) {
                throw new RuntimeException("Object[" + label + "] not null at index " + i + ": " + arr[i]);
            }
        }
    }
}
