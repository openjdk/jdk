/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8335390
 * @summary Test merge stores for some Unsafe store address patterns.
 * @modules java.base/jdk.internal.misc
 * @requires vm.bits == 64
 * @requires os.maxMemory > 8G
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.c2.TestMergeStoresUnsafeArrayPointer::test*
 *                   -Xbatch
 *                   -Xmx8g
 *                   compiler.c2.TestMergeStoresUnsafeArrayPointer
 * @run main/othervm -Xmx8g
 *                   compiler.c2.TestMergeStoresUnsafeArrayPointer
 */

package compiler.c2;
import jdk.internal.misc.Unsafe;

public class TestMergeStoresUnsafeArrayPointer {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // We allocate a big int array of length:
    static final int SIZE = (1 << 30) + 100;

    // This gives us a memory region of 4x as many bytes:
    static final long BYTE_SIZE = 4L * SIZE; // = 1L << 32 + 400L

    // We set an "anchor" in the middle of this memory region, in bytes:
    static final long ANCHOR = BYTE_SIZE / 2;

    static int four = 4;

    public static void main(String[] args) {
        System.out.println("Allocate big array of SIZE = " + SIZE);
        int[] big = new int[SIZE];

        // Each test is executed a few times, so that we can see the difference between
        // interpreter and compiler.
        int errors = 0;

        long val = 0;
        System.out.println("test1");
        for (int i = 0; i < 100_000; i++) {
            testClear(big);
            test1(big, ANCHOR);
            long sum = testSum(big);
            if (i == 0) {
                val = sum;
            } else {
                if (sum != val) {
                    System.out.println("ERROR: test1 had wrong value: " + val + " != " + sum);
                    errors++;
                    break;
                }
            }
        }

        val = 0;
        System.out.println("test2");
        for (int i = 0; i < 100_000; i++) {
            testClear(big);
            test2(big, ANCHOR);
            long sum = testSum(big);
            if (i == 0) {
                val = sum;
            } else {
                if (sum != val) {
                    System.out.println("ERROR: test2 had wrong value: " + val + " != " + sum);
                    errors++;
                    break;
                }
            }
        }

        if (errors > 0) {
            throw new RuntimeException("ERRORS: " + errors);
        }
        System.out.println("PASSED");
    }

    // Only clear and sum over relevant parts of array to make the test fast.
    static void testClear(int[] a) {
        for (int j = 0               ; j <              100; j++) { a[j] = j; }
        for (int j = a.length/2 - 100; j < a.length/2 + 100; j++) { a[j] = j; }
        for (int j = a.length   - 100; j < a.length   +   0; j++) { a[j] = j; }
    }

    static long testSum(int[] a) {
        long sum = 0;
        for (int j = 0               ; j <              100; j++) { sum += a[j]; }
        for (int j = a.length/2 - 100; j < a.length/2 + 100; j++) { sum += a[j]; }
        for (int j = a.length   - 100; j < a.length   +   0; j++) { sum += a[j]; }
        return sum;
    }

    // Reference: expected to merge.
    static void test1(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putInt(a, base + 0, 0x42424242);
        UNSAFE.putInt(a, base + 4, 0x66666666);
    }

    // Test: if MergeStores is applied this can lead to wrong results
    static void test2(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + ANCHOR;
        UNSAFE.putInt(a, base + 0                 + (long)(four + Integer.MAX_VALUE), 0x42424242);
        UNSAFE.putInt(a, base + Integer.MAX_VALUE + (long)(four + 4                ), 0x66666666);
    }
}
