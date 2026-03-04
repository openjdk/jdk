/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
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
 * @bug 8345845
 * @summary Test merge loads for some Unsafe load address patterns.
 * @modules java.base/jdk.internal.misc
 * @requires vm.bits == 64
 * @requires os.maxMemory > 8G
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.c2.TestMergeLoadsUnsafeArrayPointer::test*
 *                   -Xbatch
 *                   -Xmx8g
 *                   compiler.c2.TestMergeLoadsUnsafeArrayPointer
 * @run main/othervm -Xmx8g
 *                   compiler.c2.TestMergeLoadsUnsafeArrayPointer
 */

package compiler.c2;
import jdk.internal.misc.Unsafe;

public class TestMergeLoadsUnsafeArrayPointer {
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

        // Initialize array with known pattern
        for (int i = 0; i < big.length; i++) {
            big[i] = i * 0x01010101;
        }

        // Each test is executed a few times, so that we can see the difference between
        // interpreter and compiler.
        int errors = 0;

        long val = 0;
        System.out.println("test1");
        for (int i = 0; i < 100_000; i++) {
            long sum = test1(big, ANCHOR);
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
            long sum = test2(big, ANCHOR);
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
            long sum = test3(big, ANCHOR);
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
            long sum = test4(big, ANCHOR);
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
            long sum = test5(big, ANCHOR);
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
            long sum = test6(big, ANCHOR);
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
            long sum = test7(big, ANCHOR);
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

    // Reference: expected to merge.
    static long test1(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        int i1 = UNSAFE.getInt(a, base + 0);
        int i2 = UNSAFE.getInt(a, base + 4);
        return i1 + i2;
    }

    // Test: if MergeLoads is applied this can lead to wrong results
    static long test2(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + ANCHOR;
        int i1 = UNSAFE.getInt(a, base + 0                 + (long)(four + Integer.MAX_VALUE));
        int i2 = UNSAFE.getInt(a, base + Integer.MAX_VALUE + (long)(four + 4                ));
        return i1 + i2;
    }

    // Test: if MergeLoads is applied this can lead to wrong results
    //  -> AddI needs overflow check.
    static long test3(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        int i1 = UNSAFE.getInt(a, base + (long)(max_int + 0));
        int i2 = UNSAFE.getInt(a, base + (long)(max_int + 4));
        return i1 + i2;
    }

    // Test: "max_int - four" cannot be parsed further, but would not make a difference here.
    static long test4(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        int i1 = UNSAFE.getInt(a, base + (long)(min_int - four) + 0);
        int i2 = UNSAFE.getInt(a, base + (long)(min_int - four) + 4);
        return i1 + i2;
    }

    // Test: if MergeLoads is applied this can lead to wrong results
    //  -> SubI needs overflow check.
    static long test5(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        int i1 = UNSAFE.getInt(a, base + (long)(min_int) - (long)(four) + 0); // no overflow
        int i2 = UNSAFE.getInt(a, base + (long)(min_int - four)         + 4); // overflow
        return i1 + i2;
    }

    // Test: if MergeLoads is applied this can lead to wrong results
    //  -> LShiftI needs overflow check.
    static long test6(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        int i1 = UNSAFE.getInt(a, base +  (long)(2 * val_2_to_30) + 0); // overflow
        int i2 = UNSAFE.getInt(a, base + 2L * (long)(val_2_to_30) + 4); // no overflow
        return i1 + i2;
    }

    // Test: if MergeLoads is applied this can lead to wrong results
    //  -> MulI needs overflow check.
    static long test7(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        int i1 = UNSAFE.getInt(a, base +  (long)(53 * large_by_53) + 0); // overflow
        int i2 = UNSAFE.getInt(a, base + 53L * (long)(large_by_53) + 4); // no overflow
        return i1 + i2;
    }

    // Test: check if large distance leads to assert
    static void test8a(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.getByte(a, base + (1L << 11) + 0);
        UNSAFE.getByte(a, base + (1L << 11) + (1L << 30));
    }

    // Test: check if large distance leads to assert
    static void test8b(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.getByte(a, base + (1L << 11) + (1L << 30));
        UNSAFE.getByte(a, base + (1L << 11) + 0);
    }

    // Test: check if large distance leads to assert
    static void test8c(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.getByte(a, base - (1L << 11) - 0);
        UNSAFE.getByte(a, base - (1L << 11) - (1L << 30));
    }

    // Test: check if large distance leads to assert
    static void test8d(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.getByte(a, base - (1L << 11) - (1L << 30));
        UNSAFE.getByte(a, base - (1L << 11) - 0);
    }

    // Test: check if large distance leads to assert
    //       case: bad distance: NaN
    static void test9a(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.getByte(a, base - 100);
        UNSAFE.getByte(a, base - 100  + (1L << 31));
    }

    // Test: check if large distance leads to assert
    //       case: just before NaN, it is still a valid distance for MemPointer aliasing.
    static void test9b(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.getByte(a, base - 100);
        UNSAFE.getByte(a, base - 100  + (1L << 31) - 1);
    }

    // Test: check if large distance leads to assert
    //       case: constant too large
    static void test9c(int[] a, long anchor) {
        long base = UNSAFE.ARRAY_INT_BASE_OFFSET + anchor;
        UNSAFE.getByte(a, base);
        UNSAFE.getByte(a, base  + (1L << 31));
    }
}
