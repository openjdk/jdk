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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;

import jdk.test.lib.Asserts;
import jdk.incubator.vector.*;
import java.util.Arrays;

/**
 * @test
 * @bug 8325520
 * @library /test/lib /
 * @summary Don't allow folding of Load/Store vectors when using incompatible indices or masks
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorGatherMaskFoldingTest
 */

public class VectorGatherMaskFoldingTest {
    // Species
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    // Vectors
    private static final LongVector longVector;
    private static final LongVector longVector2;
    private static final IntVector intVector;
    private static final IntVector intVector2;
    private static final DoubleVector doubleVector;
    private static final DoubleVector doubleVector2;
    private static final FloatVector floatVector;
    private static final FloatVector floatVector2;
    // Arrays
    private static final long[] longArray = new long[L_SPECIES.length()];
    private static final long[] longArray2 = new long[L_SPECIES.length()];
    private static final int[] intArray = new int[I_SPECIES.length()];
    private static final int[] intArray2 = new int[I_SPECIES.length()];
    private static final double[] doubleArray = new double[D_SPECIES.length()];
    private static final double[] doubleArray2 = new double[D_SPECIES.length()];
    private static final float[] floatArray = new float[F_SPECIES.length()];
    private static final float[] floatArray2 = new float[F_SPECIES.length()];
    // Offsets
    private static final int[] longOffsets = new int[L_SPECIES.length()];
    private static final int[] longOffsets2 = new int[L_SPECIES.length()];
    private static final int[] intOffsets = new int[I_SPECIES.length()];
    private static final int[] intOffsets2 = new int[I_SPECIES.length()];
    private static final int[] doubleOffsets = new int[D_SPECIES.length()];
    private static final int[] doubleOffsets2 = new int[D_SPECIES.length()];
    private static final int[] floatOffsets = new int[F_SPECIES.length()];
    private static final int[] floatOffsets2 = new int[F_SPECIES.length()];
    // Masks
    private static final boolean[] longMask = new boolean[L_SPECIES.length()];
    private static final boolean[] longMask2 = new boolean[L_SPECIES.length()];
    private static final boolean[] intMask = new boolean[I_SPECIES.length()];
    private static final boolean[] intMask2 = new boolean[I_SPECIES.length()];
    private static final boolean[] doubleMask = new boolean[D_SPECIES.length()];
    private static final boolean[] doubleMask2 = new boolean[D_SPECIES.length()];
    private static final boolean[] floatMask = new boolean[F_SPECIES.length()];
    private static final boolean[] floatMask2 = new boolean[F_SPECIES.length()];
    private static final VectorMask<Long> longVectorMask;
    private static final VectorMask<Long> longVectorMask2;
    private static final VectorMask<Integer> intVectorMask;
    private static final VectorMask<Integer> intVectorMask2;
    private static final VectorMask<Double> doubleVectorMask;
    private static final VectorMask<Double> doubleVectorMask2;
    private static final VectorMask<Float> floatVectorMask;
    private static final VectorMask<Float> floatVectorMask2;

    // Filling vectors/offsets/masks
    static {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            longArray[i] = i + 1;
            longArray2[i] = L_SPECIES.length() - i + 1;
            longMask[i] = i % 2 == 0;
            longMask2[i] = i >= L_SPECIES.length() / 2;
            longOffsets[i] = (i + L_SPECIES.length() / 2) % L_SPECIES.length();
            longOffsets2[i] = (L_SPECIES.length() - i) % L_SPECIES.length();
        }
        longVector = LongVector.fromArray(L_SPECIES, longArray, 0);
        longVector2 = LongVector.fromArray(L_SPECIES, longArray2, 0);
        longVectorMask = VectorMask.fromArray(L_SPECIES, longMask, 0);
        longVectorMask2 = VectorMask.fromArray(L_SPECIES, longMask2, 0);
        for (int i = 0; i < I_SPECIES.length(); i++) {
            intArray[i] = i + 1;
            intArray2[i] = I_SPECIES.length() - i + 1;
            intMask[i] = i % 2 == 0;
            intMask2[i] = i >= I_SPECIES.length() / 2;
            intOffsets[i] = (i + I_SPECIES.length() / 2) % I_SPECIES.length();
            intOffsets2[i] = (I_SPECIES.length() - i) % I_SPECIES.length();
        }
        intVector = IntVector.fromArray(I_SPECIES, intArray, 0);
        intVector2 = IntVector.fromArray(I_SPECIES, intArray2, 0);

        intVectorMask = VectorMask.fromArray(I_SPECIES, intMask, 0);
        intVectorMask2 = VectorMask.fromArray(I_SPECIES, intMask2, 0);
        for (int i = 0; i < D_SPECIES.length(); i++) {
            doubleArray[i] = (double) i + 1.0;
            doubleArray2[i] = (double) (D_SPECIES.length() - i) + 1.0;
            doubleMask[i] = i % 2 == 0;
            doubleMask2[i] = i >= D_SPECIES.length() / 2;
            doubleOffsets[i] = (i + D_SPECIES.length() / 2) % D_SPECIES.length();
            doubleOffsets2[i] = (D_SPECIES.length() - i) % D_SPECIES.length();
        }
        doubleVector = DoubleVector.fromArray(D_SPECIES, doubleArray, 0);
        doubleVector2 = DoubleVector.fromArray(D_SPECIES, doubleArray2, 0);
        doubleVectorMask = VectorMask.fromArray(D_SPECIES, doubleMask, 0);
        doubleVectorMask2 = VectorMask.fromArray(D_SPECIES, doubleMask2, 0);
        for (int i = 0; i < F_SPECIES.length(); i++) {
            floatArray[i] = i + 1.0f;
            floatArray2[i] = F_SPECIES.length() - i + 1.0f;
            floatMask[i] = i % 2 == 0;
            floatMask2[i] = i >= F_SPECIES.length() / 2;
            floatOffsets[i] = (i + F_SPECIES.length() / 2) % F_SPECIES.length();
            floatOffsets2[i] = (F_SPECIES.length() - i) % F_SPECIES.length();
        }
        floatVector = FloatVector.fromArray(F_SPECIES, floatArray, 0);
        floatVector2 = FloatVector.fromArray(F_SPECIES, floatArray2, 0);
        floatVectorMask = VectorMask.fromArray(F_SPECIES, floatMask, 0);
        floatVectorMask2 = VectorMask.fromArray(F_SPECIES, floatMask2, 0);
    }

    // LOAD TESTS

    // LongVector tests

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoLongVectorLoadGatherNotEqualArray() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray2, 0, longOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoLongVectorLoadGatherNotEqualOffsets() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets2, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">= 1", IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testOneLongVectorLoadGather() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoLongVectorLoadGatherEquals() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoLongVectorLoadMaskedEquals() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longVectorMask);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoLongVectorLoadMaskedNotEqualMask() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longVectorMask);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorLoadGatherMaskedEquals() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0, longVectorMask);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0, longVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorLoadGatherMaskedNotEqualMask() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0, longVectorMask);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0, longVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorLoadGatherMaskedNotEqualOffsets() {
        LongVector res = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets, 0, longVectorMask);
        LongVector res2 = LongVector.fromArray(L_SPECIES, longArray, 0, longOffsets2, 0, longVectorMask);
        Asserts.assertNotEquals(res, res2);
    }


    // IntVector tests

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoIntVectorLoadGatherNotEqualArray() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray2, 0, intOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoIntVectorLoadGatherNotEqualOffsets() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets2, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_I, ">= 1", IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testOneIntVectorLoadGather() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoIntVectorLoadGatherEquals() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoIntVectorLoadMaskedEquals() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intVectorMask);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoIntVectorLoadMaskedNotEqualMask() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intVectorMask);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorLoadGatherMaskedEquals() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0, intVectorMask);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0, intVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorLoadGatherMaskedNotEqualMask() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0, intVectorMask);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0, intVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorLoadGatherMaskedNotEqualOffsets() {
        IntVector res = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets, 0, intVectorMask);
        IntVector res2 = IntVector.fromArray(I_SPECIES, intArray, 0, intOffsets2, 0, intVectorMask);
        Asserts.assertNotEquals(res, res2);
    }


    // DoubleVector tests

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadGatherNotEqualArray() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray2, 0, doubleOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadGatherNotEqualOffsets() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets2, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_D, ">= 1", IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testOneDoubleVectorLoadGather() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadGatherEquals() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadMaskedEquals() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleVectorMask);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadMaskedNotEqualMask() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleVectorMask);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadGatherMaskedEquals() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0, doubleVectorMask);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0, doubleVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadGatherMaskedNotEqualMask() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0, doubleVectorMask);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0, doubleVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorLoadGatherMaskedNotEqualOffsets() {
        DoubleVector res = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets, 0, doubleVectorMask);
        DoubleVector res2 = DoubleVector.fromArray(D_SPECIES, doubleArray, 0, doubleOffsets2, 0, doubleVectorMask);
        Asserts.assertNotEquals(res, res2);
    }


    // FloatVector tests

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadGatherNotEqualArray() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray2, 0, floatOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadGatherNotEqualOffsets() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets2, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_F, ">= 1", IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testOneFloatVectorLoadGather() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadGatherEquals() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadMaskedEquals() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatVectorMask);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadMaskedNotEqualMask() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatVectorMask);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadGatherMaskedEquals() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0, floatVectorMask);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0, floatVectorMask);
        Asserts.assertEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadGatherMaskedNotEqualMask() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0, floatVectorMask);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0, floatVectorMask2);
        Asserts.assertNotEquals(res, res2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_GATHER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorLoadGatherMaskedNotEqualOffsets() {
        FloatVector res = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets, 0, floatVectorMask);
        FloatVector res2 = FloatVector.fromArray(F_SPECIES, floatArray, 0, floatOffsets2, 0, floatVectorMask);
        Asserts.assertNotEquals(res, res2);
    }


    // STORE TESTS

    // LongVector tests

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreScatterNotEqualVector() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longOffsets, 0);
        longVector2.intoArray(res2, 0, longOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreScatterNotEqualOffsets() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longOffsets, 0);
        longVector.intoArray(res2, 0, longOffsets2, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, ">= 1", IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testOneLongVectorStoreScatter() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0);
        longVector.intoArray(res2, 0, longOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreScatterEquals() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longOffsets, 0);
        longVector.intoArray(res2, 0, longOffsets, 0);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreMaskedEquals() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longVectorMask);
        longVector.intoArray(res2, 0, longVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreMaskedNotEqualMask() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longVectorMask);
        longVector.intoArray(res2, 0, longVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreScatterMaskedEquals() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longOffsets, 0, longVectorMask);
        longVector.intoArray(res2, 0, longOffsets, 0, longVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreScatterMaskedNotEqualMask() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longOffsets, 0, longVectorMask);
        longVector.intoArray(res2, 0, longOffsets, 0, longVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoLongVectorStoreScatterMaskedNotEqualOffsets() {
        long[] res = new long[L_SPECIES.length()];
        long[] res2 = new long[L_SPECIES.length()];
        longVector.intoArray(res, 0, longOffsets, 0, longVectorMask);
        longVector.intoArray(res2, 0, longOffsets2, 0, longVectorMask);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }


    // IntVector tests

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreScatterNotEqualVector() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intOffsets, 0);
        intVector2.intoArray(res2, 0, intOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreScatterNotEqualOffsets() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intOffsets, 0);
        intVector.intoArray(res2, 0, intOffsets2, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, ">= 1", IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testOneIntVectorStoreScatter() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0);
        intVector.intoArray(res2, 0, intOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreScatterEquals() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intOffsets, 0);
        intVector.intoArray(res2, 0, intOffsets, 0);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreMaskedEquals() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intVectorMask);
        intVector.intoArray(res2, 0, intVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreMaskedNotEqualMask() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intVectorMask);
        intVector.intoArray(res2, 0, intVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreScatterMaskedEquals() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intOffsets, 0, intVectorMask);
        intVector.intoArray(res2, 0, intOffsets, 0, intVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreScatterMaskedNotEqualMask() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intOffsets, 0, intVectorMask);
        intVector.intoArray(res2, 0, intOffsets, 0, intVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoIntVectorStoreScatterMaskedNotEqualOffsets() {
        int[] res = new int[I_SPECIES.length()];
        int[] res2 = new int[I_SPECIES.length()];
        intVector.intoArray(res, 0, intOffsets, 0, intVectorMask);
        intVector.intoArray(res2, 0, intOffsets2, 0, intVectorMask);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }


    // DoubleVector tests

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreScatterNotEqualVector() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleOffsets, 0);
        doubleVector2.intoArray(res2, 0, doubleOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreScatterNotEqualOffsets() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleOffsets, 0);
        doubleVector.intoArray(res2, 0, doubleOffsets2, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, ">= 1", IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testOneDoubleVectorStoreScatter() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0);
        doubleVector.intoArray(res2, 0, doubleOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreScatterEquals() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleOffsets, 0);
        doubleVector.intoArray(res2, 0, doubleOffsets, 0);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreMaskedEquals() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleVectorMask);
        doubleVector.intoArray(res2, 0, doubleVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreMaskedNotEqualMask() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleVectorMask);
        doubleVector.intoArray(res2, 0, doubleVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreScatterMaskedEquals() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleOffsets, 0, doubleVectorMask);
        doubleVector.intoArray(res2, 0, doubleOffsets, 0, doubleVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreScatterMaskedNotEqualMask() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleOffsets, 0, doubleVectorMask);
        doubleVector.intoArray(res2, 0, doubleOffsets, 0, doubleVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoDoubleVectorStoreScatterMaskedNotEqualOffsets() {
        double[] res = new double[D_SPECIES.length()];
        double[] res2 = new double[D_SPECIES.length()];
        doubleVector.intoArray(res, 0, doubleOffsets, 0, doubleVectorMask);
        doubleVector.intoArray(res2, 0, doubleOffsets2, 0, doubleVectorMask);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }


    // FloatVector tests

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreScatterNotEqualVector() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatOffsets, 0);
        floatVector2.intoArray(res2, 0, floatOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreScatterNotEqualOffsets() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatOffsets, 0);
        floatVector.intoArray(res2, 0, floatOffsets2, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR, ">= 1", IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testOneFloatVectorStoreScatter() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0);
        floatVector.intoArray(res2, 0, floatOffsets, 0);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreScatterEquals() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatOffsets, 0);
        floatVector.intoArray(res2, 0, floatOffsets, 0);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreMaskedEquals() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatVectorMask);
        floatVector.intoArray(res2, 0, floatVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreMaskedNotEqualMask() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatVectorMask);
        floatVector.intoArray(res2, 0, floatVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 1" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreScatterMaskedEquals() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatOffsets, 0, floatVectorMask);
        floatVector.intoArray(res2, 0, floatOffsets, 0, floatVectorMask);
        Asserts.assertTrue(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreScatterMaskedNotEqualMask() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatOffsets, 0, floatVectorMask);
        floatVector.intoArray(res2, 0, floatOffsets, 0, floatVectorMask2);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }

    @Test
    @IR(counts = { IRNode.STORE_VECTOR_SCATTER_MASKED, ">= 2" }, applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void testTwoFloatVectorStoreScatterMaskedNotEqualOffsets() {
        float[] res = new float[F_SPECIES.length()];
        float[] res2 = new float[F_SPECIES.length()];
        floatVector.intoArray(res, 0, floatOffsets, 0, floatVectorMask);
        floatVector.intoArray(res2, 0, floatOffsets2, 0, floatVectorMask);
        Asserts.assertFalse(Arrays.equals(res, res2));
    }


    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector", "-XX:+IncrementalInlineForceCleanup")
                     .start();
    }
}
