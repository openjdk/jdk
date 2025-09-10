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

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8366588
 * @key randomness
 * @library /test/lib /
 * @summary VectorAPI: Re-intrinsify VectorMask.laneIsSet where the input index is a variable
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorMaskLaneIsSetTest
 */

public class VectorMaskLaneIsSetTest {
    static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    static final int LENGTH = 512;
    static boolean[] ma;
    static VectorMask<Byte> mask_b;
    static VectorMask<Short> mask_s;
    static VectorMask<Integer> mask_i;
    static VectorMask<Long> mask_l;
    static VectorMask<Float> mask_f;
    static VectorMask<Double> mask_d;

    static {
        ma = new boolean[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            ma[i] = i % 2 == 0;
        }
        mask_b = VectorMask.fromArray(B_SPECIES, ma, 0);
        mask_s = VectorMask.fromArray(S_SPECIES, ma, 0);
        mask_i = VectorMask.fromArray(I_SPECIES, ma, 0);
        mask_l = VectorMask.fromArray(L_SPECIES, ma, 0);
        mask_f = VectorMask.fromArray(F_SPECIES, ma, 0);
        mask_d = VectorMask.fromArray(D_SPECIES, ma, 0);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_LANE_IS_SET, "= 6" }, applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 6" }, applyIfCPUFeature = { "avx2", "true" })
    public static void testVectorMaskLaneIsSetByte_const() {
        Asserts.assertEquals(ma[0], mask_b.laneIsSet(0));
        Asserts.assertEquals(ma[0], mask_s.laneIsSet(0));
        Asserts.assertEquals(ma[0], mask_i.laneIsSet(0));
        Asserts.assertEquals(ma[0], mask_l.laneIsSet(0));
        Asserts.assertEquals(ma[0], mask_f.laneIsSet(0));
        Asserts.assertEquals(ma[0], mask_d.laneIsSet(0));
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_LANE_IS_SET, "= 1" }, applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 1" }, applyIfCPUFeature = { "avx", "true" })
    public static boolean testVectorMaskLaneIsSet_Byte_variable(int i) {
        return mask_b.laneIsSet(i);
    }

    @Run(test = "testVectorMaskLaneIsSet_Byte_variable")
    public static void testVectorMaskLaneIsSet_Byte_variable_runner() {
        Asserts.assertEquals(ma[0], testVectorMaskLaneIsSet_Byte_variable(0));
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_LANE_IS_SET, "= 1" }, applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 1" }, applyIfCPUFeature = { "avx", "true" })
    public static boolean testVectorMaskLaneIsSet_Short_variable(int i) {
        return mask_s.laneIsSet(i);
    }

    @Run(test = "testVectorMaskLaneIsSet_Short_variable")
    public static void testVectorMaskLaneIsSet_Short_variable_runner() {
        Asserts.assertEquals(ma[0], testVectorMaskLaneIsSet_Short_variable(0));
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_LANE_IS_SET, "= 1" }, applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 1" }, applyIfCPUFeature = { "avx", "true" })
    public static boolean testVectorMaskLaneIsSet_Int_variable(int i) {
        return mask_i.laneIsSet(i);
    }

    @Run(test = "testVectorMaskLaneIsSet_Int_variable")
    public static void testVectorMaskLaneIsSet_Int_variable_runner() {
        Asserts.assertEquals(ma[0], testVectorMaskLaneIsSet_Int_variable(0));
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_LANE_IS_SET, "= 1" }, applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 1" }, applyIfCPUFeature = { "avx2", "true" })
    public static boolean testVectorMaskLaneIsSet_Long_variable(int i) {
        return mask_l.laneIsSet(i);
    }

    @Run(test = "testVectorMaskLaneIsSet_Long_variable")
    public static void testVectorMaskLaneIsSet_Long_variable_runner() {
        Asserts.assertEquals(ma[0], testVectorMaskLaneIsSet_Long_variable(0));
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_LANE_IS_SET, "= 1" }, applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 1" }, applyIfCPUFeature = { "avx", "true" })
    public static boolean testVectorMaskLaneIsSet_Float_variable(int i) {
        return mask_f.laneIsSet(i);
    }

    @Run(test = "testVectorMaskLaneIsSet_Float_variable")
    public static void testVectorMaskLaneIsSet_Float_variable_runner() {
        Asserts.assertEquals(ma[0], testVectorMaskLaneIsSet_Float_variable(0));
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_LANE_IS_SET, "= 1" }, applyIfCPUFeature = { "asimd", "true" })
    @IR(counts = { IRNode.VECTOR_MASK_TO_LONG, "= 1" }, applyIfCPUFeature = { "avx2", "true" })
    public static boolean testVectorMaskLaneIsSet_Double_variable(int i) {
        return mask_d.laneIsSet(i);
    }

    @Run(test = "testVectorMaskLaneIsSet_Double_variable")
    public static void testVectorMaskLaneIsSet_Double_variable_runner() {
        Asserts.assertEquals(ma[0], testVectorMaskLaneIsSet_Double_variable(0));
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
