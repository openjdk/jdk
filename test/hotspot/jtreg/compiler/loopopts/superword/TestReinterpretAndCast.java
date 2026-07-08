/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8366845
 * @summary Test Reinterpret with Cast cases, where the order of the src/dst types of Reinterpret matters.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestReinterpretAndCast
 */

package compiler.loopopts.superword;

import jdk.incubator.vector.Float16;

import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;

public class TestReinterpretAndCast {
    static int SIZE = 1028 * 8;

    public static final Generator<Float> GEN_FLOAT = Generators.G.floats();
    public static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();
    private static final Generator<Short> GEN_FLOAT16 = Generators.G.float16s();

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    static long[] fillWithDoubles(long[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Double.doubleToLongBits(GEN_DOUBLE.next());
        }
        return a;
    }

    static int[] fillWithFloats(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Float.floatToIntBits(GEN_FLOAT.next());
        }
        return a;
    }

    static short[] fillWithFloat16s(short[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = GEN_FLOAT16.next();
        }
        return a;
    }

    static void verify(long[] a, long[] b) {
        for (int i = 0; i < a.length; i++) {
            // Make sure we remove non-canonical NaN's.
            long aa = Double.doubleToLongBits(Double.longBitsToDouble(a[i]));
            long bb = Double.doubleToLongBits(Double.longBitsToDouble(b[i]));
            if (aa != bb) {
                throw new RuntimeException("Wrong value: " + aa + " vs " + bb + " - " + a[i] + " vs " + b[i]);
            }
        }
    }

    static void verify(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            // Make sure we remove non-canonical NaN's.
            int aa = Float.floatToIntBits(Float.intBitsToFloat(a[i]));
            int bb = Float.floatToIntBits(Float.intBitsToFloat(b[i]));
            if (aa != bb) {
                throw new RuntimeException("Wrong value: " + aa + " vs " + bb + " - " + a[i] + " vs " + b[i]);
            }
        }
    }

    static void verify(short[] a, short[] b) {
        for (int i = 0; i < a.length; i++) {
            // Make sure we remove non-canonical NaN's.
            int aa = Float.floatToIntBits(Float16.shortBitsToFloat16(a[i]).floatValue());
            int bb = Float.floatToIntBits(Float16.shortBitsToFloat16(b[i]).floatValue());
            if (aa != bb) {
                throw new RuntimeException("Wrong value: " + aa + " vs " + bb + " - " + a[i] + " vs " + b[i]);
            }
        }
    }

    // -------------- test1
    public static long[] test1_in   = fillWithDoubles(new long[SIZE]);
    public static int[]  test1_gold = new int[SIZE];
    public static int[]  test1_test = new int[SIZE];

    // Esecute in interpreter, to compare to compiled results later.
    static { test1(test1_in, test1_gold); }

    @Setup
    public static Object[] setup1() {
        return new Object[] {test1_in, test1_test};
    }

    @Test
    @Arguments(setup = "setup1")
    @IR(counts = {IRNode.LOAD_VECTOR_L,   IRNode.VECTOR_SIZE + "min(max_int, max_float, max_double, max_long)", "> 0",
                  IRNode.VECTOR_CAST_D2F, IRNode.VECTOR_SIZE + "min(max_int, max_float, max_double, max_long)", "> 0",
                  IRNode.STORE_VECTOR,       "> 0",
                  IRNode.VECTOR_REINTERPRET, "> 0"}, // We have both L2D and F2I
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public static void test1(long[] a, int[] b) {
        for (int i = 0; i < SIZE; i++) {
            long   v0 = a[i];
            double v1 = Double.longBitsToDouble(v0);
            // Reinterpret: long -> double
            // Before fix:  double -> long (no direct problem)
            float  v2 = (float)v1;
            // Cast:        double -> float
            // Before fix:  long -> float  (wrong!)
            int    v3 = Float.floatToRawIntBits(v2);
            b[i] = v3;
        }
    }

    @Check(test = "test1")
    public static void check1() {
        verify(test1_test, test1_gold);
    }

    // -------------- test2
    public static int[]   test2_in   = fillWithFloats(new int[SIZE]);
    public static short[] test2_gold = new short[SIZE];
    public static short[] test2_test = new short[SIZE];

    // Esecute in interpreter, to compare to compiled results later.
    static { test2(test2_in, test2_gold); }

    @Setup
    public static Object[] setup2() {
        return new Object[] {test2_in, test2_test};
    }

    @Test
    @Arguments(setup = "setup2")
    @IR(counts = {IRNode.LOAD_VECTOR_I,    IRNode.VECTOR_SIZE + "min(max_int, max_float, max_short)", "> 0",
                  IRNode.VECTOR_CAST_F2HF, IRNode.VECTOR_SIZE + "min(max_int, max_float, max_short)", "> 0",
                  IRNode.STORE_VECTOR,       "> 0",
                  IRNode.VECTOR_REINTERPRET, "> 0"}, // We have at least I2F
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureAnd = {"avx", "true", "f16c", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_I,    IRNode.VECTOR_SIZE + "min(max_int, max_float, max_short)", "> 0",
                  IRNode.VECTOR_CAST_F2HF, IRNode.VECTOR_SIZE + "min(max_int, max_float, max_short)", "> 0",
                  IRNode.STORE_VECTOR,       "> 0",
                  IRNode.VECTOR_REINTERPRET, "> 0"}, // We have at least I2F
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureAnd = {"asimd", "true", "fphp", "true", "asimdhp", "true"})
    public static void test2(int[] a, short[] b) {
        for (int i = 0; i < SIZE; i++) {
            int v0 = a[i];
            float v1 = Float.intBitsToFloat(v0);
            // Reinterpret: int -> float
            // Before fix:  float -> int (no direct problem)
            short v2 = Float.floatToFloat16(v1);
            // Cast:        float -> float16/short
            // Before fix:  int ->   float16/short (wrong!)
            b[i] = v2;
        }
    }

    @Check(test = "test2")
    public static void check2() {
        verify(test2_test, test2_gold);
    }

    // -------------- test3
    public static short[] test3_in   = fillWithFloat16s(new short[SIZE]);
    public static long[]  test3_gold = new long[SIZE];
    public static long[]  test3_test = new long[SIZE];

    // Esecute in interpreter, to compare to compiled results later.
    static { test3(test3_in, test3_gold); }

    @Setup
    public static Object[] setup3() {
        return new Object[] {test3_in, test3_test};
    }

    @Test
    @Arguments(setup = "setup3")
    @IR(counts = {IRNode.LOAD_VECTOR_S,    IRNode.VECTOR_SIZE + "min(max_float, max_short, max_long)", "> 0",
                  IRNode.VECTOR_CAST_HF2F, IRNode.VECTOR_SIZE + "min(max_float, max_short, max_long)", "> 0",
                  IRNode.VECTOR_CAST_I2L,  IRNode.VECTOR_SIZE + "min(max_float, max_short, max_long)", "> 0",
                  IRNode.STORE_VECTOR,       "> 0",
                  IRNode.VECTOR_REINTERPRET, "> 0"}, // We have at least F2I
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureAnd = {"avx", "true", "f16c", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_S,    IRNode.VECTOR_SIZE + "min(max_float, max_short, max_long)", "> 0",
                  IRNode.VECTOR_CAST_HF2F, IRNode.VECTOR_SIZE + "min(max_float, max_short, max_long)", "> 0",
                  IRNode.VECTOR_CAST_I2L,  IRNode.VECTOR_SIZE + "min(max_float, max_short, max_long)", "> 0",
                  IRNode.STORE_VECTOR,       "> 0",
                  IRNode.VECTOR_REINTERPRET, "> 0"}, // We have at least F2I
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureAnd = {"asimd", "true", "fphp", "true", "asimdhp", "true"})
    public static void test3(short[] a, long[] b) {
        for (int i = 0; i < SIZE; i++) {
            short v0 = a[i];
            Float16 v1 = Float16.shortBitsToFloat16(v0);
            float v2 = v1.floatValue();
            int v3 = Float.floatToRawIntBits(v2);
            // Reinterpret: float -> int
            // Before fix:  int -> float
            long v4 = v3;
            // Cast:        int -> long
            // Before fix:  float -> long (wrong!)
            b[i] = v4;
        }
    }

    @Check(test = "test3")
    public static void check3() {
        verify(test3_test, test3_gold);
    }
}
