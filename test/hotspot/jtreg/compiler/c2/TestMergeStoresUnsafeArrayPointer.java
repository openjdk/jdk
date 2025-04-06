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
    static int max_int = Integer.MAX_VALUE;
    static int min_int = Integer.MIN_VALUE;
    static int val_2_to_30 = (1 << 30);
    static int large_by_53 = (int)((1L << 31) / 53L + 1L);

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

        val = 0;
        System.out.println("test3");
        for (int i = 0; i < 100_000; i++) {
            testClear(big);
            test3(big, ANCHOR);
            long sum = testSum(big);
            if (i == 0) {
                val = sum;
            } else {
                if (sum != val) {
                    System.out.println("ERROR: test3 had wrong value: " + val + " != " + sum);
                    errors++;
                    break;
                }
            }
        }

        val = 0;
        System.out.println("test4");
        for (int i = 0; i < 100_000; i++) {
            testClear(big);
            test4(big, ANCHOR);
            long sum = testSum(big);
            if (i == 0) {
                val = sum;
            } else {
                if (sum != val) {
                    System.out.println("ERROR: test4 had wrong value: " + val + " != " + sum);
                    errors++;
                    break;
                }
            }
        }

        val = 0;
        System.out.println("test5");
        for (int i = 0; i < 100_000; i++) {
            testClear(big);
            test5(big, ANCHOR);
            long sum = testSum(big);
            if (i == 0) {
                val = sum;
            } else {
                if (sum != val) {
                    System.out.println("ERROR: test5 had wrong value: " + val + " != " + sum);
                    errors++;
                    break;
                }
            }
        }

        val = 0;
        System.out.println("test6");
        for (int i = 0; i < 100_000; i++) {
            testClear(big);
            test6(big, ANCHOR);
            long sum = testSum(big);
            if (i == 0) {
                val = sum;
            } else {
                if (sum != val) {
                    System.out.println("ERROR: test6 had wrong value: " + val + " != " + sum);
                    errors++;
                    break;
                }
            }
        }

        val = 0;
        System.out.println("test7");
        for (int i = 0; i < 100_000; i++) {
            testClear(big);
            test7(big, ANCHOR);
            long sum = testSum(big);
            if (i == 0) {
                val = sum;
            } else {
                if (sum != val) {
                    System.out.println("ERROR: test7 had wrong value: " + val + " != " + sum);
                    errors++;
                    break;
                }
            }
        }

        // No result verification here. We only want to make sure we do not hit asserts.
        System.out.println("test8 and test9");
        for (int i = 0; i < 100_000; i++) {
            test8a(big, ANCHOR);
            test8b(big, ANCHOR);
            test8c(big, ANCHOR);
            test8d(big, ANCHOR);
            test9a(big, ANCHOR);
            test9b(big, ANCHOR);
            test9c(big, ANCHOR);
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

    // Test: if MergeStores is applied this can lead to wrong results
    //  -> AddI needs overflow check.
    static void test3(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putInt(a, base + (long)(max_int + 0), 0x42424242);
        UNSAFE.putInt(a, base + (long)(max_int + 4), 0x66666666);
    }

    // Test: "max_int - four" cannot be parsed further, but would not make a difference here.
    static void test4(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putInt(a, base + (long)(min_int - four) + 0, 0x42424242);
        UNSAFE.putInt(a, base + (long)(min_int - four) + 4, 0x66666666);
    }

    // Test: if MergeStores is applied this can lead to wrong results
    //  -> SubI needs overflow check.
    static void test5(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putInt(a, base + (long)(min_int) - (long)(four) + 0, 0x42424242); // no overflow
        UNSAFE.putInt(a, base + (long)(min_int - four)         + 4, 0x66666666); // overflow
    }

    // Test: if MergeStores is applied this can lead to wrong results
    //  -> LShiftI needs overflow check.
    static void test6(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putInt(a, base +  (long)(2 * val_2_to_30) + 0, 0x42424242); // overflow
        UNSAFE.putInt(a, base + 2L * (long)(val_2_to_30) + 4, 0x66666666); // no overflow
    }

    // Test: if MergeStores is applied this can lead to wrong results
    //  -> MulI needs overflow check.
    static void test7(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putInt(a, base +  (long)(53 * large_by_53) + 0, 0x42424242); // overflow
        UNSAFE.putInt(a, base + 53L * (long)(large_by_53) + 4, 0x66666666); // no overflow
    }

    // Test: check if large distance leads to assert
    static void test8a(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putByte(a, base + (1L << 11) + 0,          (byte)42);
        UNSAFE.putByte(a, base + (1L << 11) + (1L << 30), (byte)11);
    }

    // Test: check if large distance leads to assert
    static void test8b(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putByte(a, base + (1L << 11) + (1L << 30), (byte)11);
        UNSAFE.putByte(a, base + (1L << 11) + 0,          (byte)42);
    }

    // Test: check if large distance leads to assert
    static void test8c(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putByte(a, base - (1L << 11) - 0,          (byte)42);
        UNSAFE.putByte(a, base - (1L << 11) - (1L << 30), (byte)11);
    }

    // Test: check if large distance leads to assert
    static void test8d(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putByte(a, base - (1L << 11) - (1L << 30), (byte)11);
        UNSAFE.putByte(a, base - (1L << 11) - 0,          (byte)42);
    }

    // Test: check if large distance leads to assert
    //       case: bad distance: NaN
    static void test9a(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putByte(a, base - 100,               (byte)42);
        UNSAFE.putByte(a, base - 100  + (1L << 31), (byte)11);
    }

    // Test: check if large distance leads to assert
    //       case: just before NaN, it is still a valid distance for MemPointer aliasing.
    static void test9b(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putByte(a, base - 100,                   (byte)42);
        UNSAFE.putByte(a, base - 100  + (1L << 31) - 1, (byte)11);
    }

    // Test: check if large distance leads to assert
    //       case: constant too large
    static void test9c(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.putByte(a, base,               (byte)42);
        UNSAFE.putByte(a, base  + (1L << 31), (byte)11);
    }
}
