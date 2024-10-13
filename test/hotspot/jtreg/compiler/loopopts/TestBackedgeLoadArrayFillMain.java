/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8299179
 * @summary ArrayFill: if store is on backedge, last iteration is not to be executed.
 * @library /test/lib
 * @compile TestBackedgeLoadArrayFill.jasm
 * @requires vm.compiler2.enabled
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,TestBackedgeLoadArrayFill*::test*
 *      -XX:-TieredCompilation -Xcomp -XX:+OptimizeFill
 *      TestBackedgeLoadArrayFillMain
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,TestBackedgeLoadArrayFill*::test*
 *      -XX:-TieredCompilation -Xcomp -XX:+OptimizeFill
 *      -XX:LoopUnrollLimit=1
 *      TestBackedgeLoadArrayFillMain
 */

import jdk.test.lib.Asserts;

public class TestBackedgeLoadArrayFillMain {
    static long[]  longA;
    static int[]   intA;
    static short[] shortA;
    static byte[]  byteA;

    static class Data {
        long longValue;
        int intValue;
        short shortValue;
        byte byteValue;

        Data(int value) {
            longValue  = (long)  value;
            intValue   = (int)   value;
            shortValue = (short) value;
            longValue  = (byte)  value;
        }
    }

    public static long longSum() {
        long s = 0;
        for (long v : longA) { s += v; }
        return s;
    }

    public static int intSum() {
        int s = 0;
        for (int v : intA) { s += v; }
        return s;
    }

    public static short shortSum() {
        short s = 0;
        for (short v : shortA) { s += v; }
        return s;
    }

    public static byte byteSum() {
        byte s = 0;
        for (byte v : byteA) { s += v; }
        return s;
    }

    static void test_001() {
        // long seems not yet supported
        int i = 6;
        long arr[] = new long[22];
        do {
            arr[i] = 1;
            try {
                arr[i] = arr[i];
            } catch (Exception e) {
            }
        } while (++i < 20);
        longA = arr;
    }

    static void test_002() {
        // jint_fill
        int i = 6;
        int arr[] = new int[22];
        do {
            arr[i] = 1;
            try {
                arr[i] = arr[i];
            } catch (Exception e) {
            }
        } while (++i < 20);
        intA = arr;
    }

    static void test_003() {
        // jshort_fill
        int i = 6;
        short arr[] = new short[22];
        do {
            // first block of loop: copied before loop, and onto backedge -> store on backedge
            arr[i] = 1;
            // second block of loop
            try {
                arr[i] = arr[i];
            } catch (Exception e) {
            }
        } while (++i < 20);
        shortA = arr;
    }

    static void test_004() {
        // jbyte_fill
        int i = 6;
        byte arr[] = new byte[22];
        do {
            arr[i] = 1;
            try {
                arr[i] = arr[i];
            } catch (Exception e) {
            }
        } while (++i < 20);
        byteA = arr;
    }

    static void test_005() {
        // Note: currently unrolled, not intrinsified (unless -XX:LoopUnrollLimit=1)
        int arr[] = new int[22];
        for (int i = 6; i < 20; i++) {
            arr[i] = 1;
        }
        intA = arr;
    }

    static void test_006() {
        // Note: currently unrolled, not intrinsified (unless -XX:LoopUnrollLimit=1)
        // Load in normal body, because not moved to backedge during parsing.
        int i = 6;
        int arr[] = new int[22];
        do {
            arr[i] = 1;
        } while (++i < 20);
        intA = arr;
    }

    static void test_007() {
        int i = 6;
        int arr[] = new int[22];
        do {
            // still not on backedge [7,20) partial peel
            arr[i] = 1;
            try { int x = arr[i]; } catch (Exception e) {}
        } while (++i < 20);
        intA = arr;
    }

    static void test_008(Data data) {
        // Because of conditional in loop, at first not intrinsified, and also not unrolled.
        // After unswitching both loops are intrinsified.
        // I stole this idea from TestOptimizeFillWithStripMinedLoop.java
        int i = 6;
        int arr[] = new int[22];
        do {
            arr[i] = (data == null) ? 1 : data.intValue;
        } while (++i < 20);
        intA = arr;
    }

    static void test_009() {
        // Cast to int leads to "missing use of index", not intrinsified
        int arr[] = new int[22];
        for (long i = 6; i < 20; i++) {
            arr[(int)i] = 1;
        }
        intA = arr;
    }


    public static void main(String[] strArr) {
        test_001();
        Asserts.assertEQ(longSum(),  (long)14);
        test_002();
        Asserts.assertEQ(intSum(),   14);
        test_003();
        Asserts.assertEQ(shortSum(), (short)14);
        test_004();
        Asserts.assertEQ(byteSum(),  (byte)14);
        test_005();
        Asserts.assertEQ(intSum(),   14);
        test_006();
        Asserts.assertEQ(intSum(),   14);
        test_007();
        Asserts.assertEQ(intSum(),   14);
        test_008(new Data(1));
        Asserts.assertEQ(intSum(),   14);
        test_008(null);
        Asserts.assertEQ(intSum(),   14);
        test_009();
        Asserts.assertEQ(intSum(),   14);
        TestBackedgeLoadArrayFill t = new TestBackedgeLoadArrayFill();
        t.test_101();
        Asserts.assertEQ(intSum(),   15);
        t.test_102();
        Asserts.assertEQ(intSum(),   16);
        t.test_103();
        Asserts.assertEQ(intSum(),   14);
        t.test_104();
        Asserts.assertEQ(intSum(),   12);
    }
}

