/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
 * @test 8371603
 * @key randomness
 * @library /test/lib /
 * @summary Test vector operations with vector size less than MaxVectorSize
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

public class TestVectorOperationsWithPartialSize {
    private static final int SIZE = 1024;
    private static final Generators random = Generators.G;

    private static final VectorSpecies<Integer> ISPEC_128 = IntVector.SPECIES_128;
    private static final VectorSpecies<Long> LSPEC_128 = LongVector.SPECIES_128;
    private static final VectorSpecies<Float> FSPEC_128 = FloatVector.SPECIES_128;
    private static final VectorSpecies<Double> DSPEC_128 = DoubleVector.SPECIES_128;
    private static final VectorSpecies<Integer> ISPEC_256 = IntVector.SPECIES_256;
    private static final VectorSpecies<Long> LSPEC_256 = LongVector.SPECIES_256;

    private static int[] ia;
    private static int[] ib;
    private static long[] la;
    private static long[] lb;
    private static float[] fa;
    private static float[] fb;
    private static double[] da;
    private static double[] db;
    private static boolean[] m;
    private static boolean[] mr;
    private static int[] indices;

    static {
        ia = new int[SIZE];
        ib = new int[SIZE];
        la = new long[SIZE];
        lb = new long[SIZE];
        fa = new float[SIZE];
        fb = new float[SIZE];
        da = new double[SIZE];
        db = new double[SIZE];
        m = new boolean[SIZE];
        mr = new boolean[SIZE];
        indices = new int[SIZE];

        random.fill(random.ints(), ia);
        random.fill(random.longs(), la);
        random.fill(random.floats(), fa);
        random.fill(random.doubles(), da);
        random.fill(random.uniformInts(0, ISPEC_128.length()), indices);
        for (int i = 0; i < SIZE; i++) {
            m[i] = i % 2 == 0;
        }
    }

    // ================ Load/Store/Gather/Scatter Tests ==================

    private static void verifyLoadStore(int[] expected, int[] actual, int vlen) {
        for (int i = 0; i < vlen; i++) {
            Asserts.assertEquals(expected[i], actual[i]);
        }
    }

    private static void verifyLoadGatherStoreScatter(int[] expected, int[] actual, int[] indices, int vlen) {
        for (int i = 0; i < vlen; i++) {
            Asserts.assertEquals(expected[indices[i]], actual[indices[i]]);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "1",
                  IRNode.STORE_VECTOR, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public void testLoadStore_128() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0);
        v.intoArray(ib, 0);
        verifyLoadStore(ia, ib, ISPEC_128.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.LOAD_VECTOR_MASKED, "1",
                  IRNode.STORE_VECTOR_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=64"})
    public void testLoadStore_256() {
        IntVector v = IntVector.fromArray(ISPEC_256, ia, 0);
        v.intoArray(ib, 0);
        verifyLoadStore(ia, ib, ISPEC_256.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.LOAD_VECTOR_GATHER_MASKED, "1",
                  IRNode.STORE_VECTOR_SCATTER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public void testLoadGatherStoreScatter_128() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0, indices, 0);
        v.intoArray(ib, 0, indices, 0);
        verifyLoadGatherStoreScatter(ia, ib, indices, ISPEC_128.length());
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.LOAD_VECTOR_GATHER_MASKED, "1",
                  IRNode.STORE_VECTOR_SCATTER_MASKED, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=64"})
    public void testLoadGatherStoreScatter_256() {
        IntVector v = IntVector.fromArray(ISPEC_256, ia, 0, indices, 0);
        v.intoArray(ib, 0, indices, 0);
        verifyLoadGatherStoreScatter(ia, ib, indices, ISPEC_256.length());
    }

    // ===================== Reduction Tests - Add =====================

    interface binOpInt {
        int apply(int a, int b);
    }

    interface binOpLong {
        long apply(long a, long b);
    }

    private static int reduceLanes(int init, int[] arr, int vlen, binOpInt f) {
        int result = init;
        for (int i = 0; i < vlen; i++) {
            result = f.apply(arr[i], result);
        }
        return result;
    }

    private static long reduceLanes(long init, long[] arr, int vlen, binOpLong f) {
        long result = init;
        for (int i = 0; i < vlen; i++) {
            result = f.apply(arr[i], result);
        }
        return result;
    }

    // Reduction add operations with integer types are implemented with NEON SIMD instructions
    // when the vector size is less than or equal to 128-bit.
    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "0",
                  IRNode.ADD_REDUCTION_VI, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public int testAddReductionInt_128() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0);
        int result = v.reduceLanes(VectorOperators.ADD);
        Asserts.assertEquals(reduceLanes(0, ia, ISPEC_128.length(), (a, b) -> (a + b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.ADD_REDUCTION_VI, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=64"})
    public int testAddReductionInt_256() {
        IntVector v = IntVector.fromArray(ISPEC_256, ia, 0);
        int result = v.reduceLanes(VectorOperators.ADD);
        Asserts.assertEquals(reduceLanes(0, ia, ISPEC_256.length(), (a, b) -> (a + b)), result);
        return result;
    }

    // Reduction add operations with long types are implemented with NEON SIMD instructions
    // when the vector size is less than or equal to 128-bit.
    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "0",
                  IRNode.ADD_REDUCTION_VL, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public long testAddReductionLong_128() {
        LongVector v = LongVector.fromArray(LSPEC_128, la, 0);
        long result = v.reduceLanes(VectorOperators.ADD);
        Asserts.assertEquals(reduceLanes(0L, la, LSPEC_128.length(), (a, b) -> (a + b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.ADD_REDUCTION_VL, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=64"})
    public long testAddReductionLong_256() {
        LongVector v = LongVector.fromArray(LSPEC_256, la, 0);
        long result = v.reduceLanes(VectorOperators.ADD);
        Asserts.assertEquals(reduceLanes(0L, la, LSPEC_256.length(), (a, b) -> (a + b)), result);
        return result;
    }

    // Because the evaluation order of floating point reduction addition in the Vector
    // API is not guaranteed, it is difficult to choose a single tolerance that reliably
    // validates results for randomly generated floating‑point inputs. Given that there
    // are already extensive jtreg tests under "test/jdk/jdk/incubator/vector" that verify
    // the API’s numerical correctness, this test is instead focused solely on checking
    // the generated IRs, and deliberately does not assert on the computed result.
    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.ADD_REDUCTION_VF, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public float testAddReductionFloat() {
        FloatVector v = FloatVector.fromArray(FSPEC_128, fa, 0);
        return v.reduceLanes(VectorOperators.ADD);
    }

    // Same with above test for float type, this test does not validate the numerical
    // result and focuses solely on checking the correctness of the generated IR.
    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.ADD_REDUCTION_VD, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public double testAddReductionDouble() {
        DoubleVector v = DoubleVector.fromArray(DSPEC_128, da, 0);
        return v.reduceLanes(VectorOperators.ADD);
    }

    // ============== Reduction Tests - Logical ==============

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.AND_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public int testAndReduction() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0);
        int result = v.reduceLanes(VectorOperators.AND);
        Asserts.assertEquals(reduceLanes(-1, ia, ISPEC_128.length(), (a, b) -> (a & b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.OR_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public int testOrReduction() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0);
        int result = v.reduceLanes(VectorOperators.OR);
        Asserts.assertEquals(reduceLanes(0, ia, ISPEC_128.length(), (a, b) -> (a | b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.XOR_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">=32"})
    public int testXorReduction() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0);
        int result = v.reduceLanes(VectorOperators.XOR);
        Asserts.assertEquals(reduceLanes(0, ia, ISPEC_128.length(), (a, b) -> (a ^ b)), result);
        return result;
    }

    // ===================== Reduction Tests - Min/Max =====================

    // Reduction min operations with non-long types are implemented with NEON SIMD instructions
    // when the vector size is less than or equal to 128-bit.
    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "0",
                  IRNode.MIN_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public int testMinReductionInt_128() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0);
        int result = v.reduceLanes(VectorOperators.MIN);
        Asserts.assertEquals(reduceLanes(Integer.MAX_VALUE, ia, ISPEC_128.length(), (a, b) -> Math.min(a, b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.MIN_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 64"})
    public int testMinReductionInt_256() {
        IntVector v = IntVector.fromArray(ISPEC_256, ia, 0);
        int result = v.reduceLanes(VectorOperators.MIN);
        Asserts.assertEquals(reduceLanes(Integer.MAX_VALUE, ia, ISPEC_256.length(), (a, b) -> Math.min(a, b)), result);
        return result;
    }

    // Reduction max operations with non-long types are implemented with NEON SIMD instructions
    // when the vector size is less than or equal to 128-bit.
    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "0",
                  IRNode.MAX_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public int testMaxReductionInt_128() {
        IntVector v = IntVector.fromArray(ISPEC_128, ia, 0);
        int result = v.reduceLanes(VectorOperators.MAX);
        Asserts.assertEquals(reduceLanes(Integer.MIN_VALUE, ia, ISPEC_128.length(), (a, b) -> Math.max(a, b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.MAX_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 64"})
    public int testMaxReductionInt_256() {
        IntVector v = IntVector.fromArray(ISPEC_256, ia, 0);
        int result = v.reduceLanes(VectorOperators.MAX);
        Asserts.assertEquals(reduceLanes(Integer.MIN_VALUE, ia, ISPEC_256.length(), (a, b) -> Math.max(a, b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.MIN_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public static long testMinReductionLong() {
        LongVector v = LongVector.fromArray(LSPEC_128, la, 0);
        long result = v.reduceLanes(VectorOperators.MIN);
        Asserts.assertEquals(reduceLanes(Long.MAX_VALUE, la, LSPEC_128.length(), (a, b) -> Math.min(a, b)), result);
        return result;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.MAX_REDUCTION_V, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public static long testMaxReductionLong() {
        LongVector v = LongVector.fromArray(LSPEC_128, la, 0);
        long result = v.reduceLanes(VectorOperators.MAX);
        Asserts.assertEquals(reduceLanes(Long.MIN_VALUE, la, LSPEC_128.length(), (a, b) -> Math.max(a, b)), result);
        return result;
    }

    // ====================== VectorMask Tests ======================

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.VECTOR_LOAD_MASK, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public static void testLoadMask() {
        VectorMask<Integer> vm = VectorMask.fromArray(ISPEC_128, m, 0);
        vm.not().intoArray(mr, 0);
        // Verify that the mask is loaded correctly.
        for (int i = 0; i < ISPEC_128.length(); i++) {
            Asserts.assertEquals(!m[i], mr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.VECTOR_MASK_CMP, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public static void testVectorMaskCmp() {
        IntVector v1 = IntVector.fromArray(ISPEC_128, ia, 0);
        IntVector v2 = IntVector.fromArray(ISPEC_128, ib, 0);
        VectorMask<Integer> vm = v1.compare(VectorOperators.LT, v2);
        vm.intoArray(mr, 0);
        // Verify that the mask is generated correctly.
        for (int i = 0; i < ISPEC_128.length(); i++) {
            Asserts.assertEquals(ia[i] < ib[i], mr[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_GEN, "1",
                  IRNode.VECTOR_MASK_FIRST_TRUE, "1"},
        applyIfCPUFeature = {"sve", "true"}, applyIf = {"MaxVectorSize", ">= 32"})
    public static int testFirstTrue() {
        VectorMask<Integer> vm = ISPEC_128.maskAll(false);
        int result = vm.firstTrue();
        // The result is the vector length if no lane is true.
        // This is the default behavior of the firstTrue method.
        Asserts.assertEquals(ISPEC_128.length(), result);
        return result;
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
