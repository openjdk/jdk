/*
 * Copyright (c) 2021, 2022, Arm Limited. All rights reserved.
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

import java.util.Random;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8273264 8292898
 * @key randomness
 * @library /test/lib /
 * @summary Unify vector mask cast and add missing rules for VectorMaskCast
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorMaskCastTest
 */

// Current vector mask cast test cases at test/jdk/jdk/incubator/vector/*ConversionTests.java
// could not be intrinsfied, hence not able to verify compiler codegen, see [1]. As a
// supplement, we add more tests for vector mask cast operations, which could be intrinsified
// by c2 compiler to generate vector/mask instructions on supported targets.
//
// [1] https://bugs.openjdk.java.net/browse/JDK-8259610

public class VectorMaskCastTest {

    private static final Random rd = Utils.getRandomInstance();

    private static final boolean[] mask_arr;

    static {
        mask_arr = new boolean[64];
        for (int i = 0; i < 64; i++) {
            mask_arr[i] = rd.nextBoolean();
        }
    }

    // Byte
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static void testByte64ToShort128() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mByte64.cast(ShortVector.SPECIES_128).toString(), mByte64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testByte64ToInt256() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mByte64.cast(IntVector.SPECIES_256).toString(), mByte64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testByte64ToFloat256() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mByte64.cast(FloatVector.SPECIES_256).toString(), mByte64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testByte64ToLong512() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mByte64.cast(LongVector.SPECIES_512).toString(), mByte64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testByte64ToDouble512() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mByte64.cast(DoubleVector.SPECIES_512).toString(), mByte64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testByte128ToShort256() {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mByte128.cast(ShortVector.SPECIES_256).toString(), mByte128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testByte128ToInt512() {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mByte128.cast(IntVector.SPECIES_512).toString(), mByte128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testByte128ToFloat512() {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mByte128.cast(FloatVector.SPECIES_512).toString(), mByte128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testByte256ToShort512() {
        VectorMask<Byte> mByte256 = VectorMask.fromArray(ByteVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mByte256.cast(ShortVector.SPECIES_512).toString(), mByte256.toString());
    }

    // Short
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static void testShort64ToInt128() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mShort64.cast(IntVector.SPECIES_128).toString(), mShort64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static void testShort64ToFloat128() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mShort64.cast(FloatVector.SPECIES_128).toString(), mShort64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testShort64ToLong256() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mShort64.cast(LongVector.SPECIES_256).toString(), mShort64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testShort64ToDouble256() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mShort64.cast(DoubleVector.SPECIES_256).toString(), mShort64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static void testShort128ToByte64() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mShort128.cast(ByteVector.SPECIES_64).toString(), mShort128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testShort128ToInt256() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mShort128.cast(IntVector.SPECIES_256).toString(), mShort128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testShort128ToFloat256() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mShort128.cast(FloatVector.SPECIES_256).toString(), mShort128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testShort128ToLong512() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mShort128.cast(LongVector.SPECIES_512).toString(), mShort128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testShort128ToDouble512() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mShort128.cast(DoubleVector.SPECIES_512).toString(), mShort128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testShort256ToByte128() {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mShort256.cast(ByteVector.SPECIES_128).toString(), mShort256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testShort256ToInt512() {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mShort256.cast(IntVector.SPECIES_512).toString(), mShort256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testShort256ToFloat512() {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mShort256.cast(FloatVector.SPECIES_512).toString(), mShort256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testShort512ToByte256() {
        VectorMask<Short> mShort512 = VectorMask.fromArray(ShortVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mShort512.cast(ByteVector.SPECIES_256).toString(), mShort512.toString());
    }

    // Int
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testInt64ToLong128() {
        VectorMask<Integer> mInt64 = VectorMask.fromArray(IntVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mInt64.cast(LongVector.SPECIES_128).toString(), mInt64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testInt64ToDouble128() {
        VectorMask<Integer> mInt64 = VectorMask.fromArray(IntVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mInt64.cast(DoubleVector.SPECIES_128).toString(), mInt64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static void testInt128ToShort64() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mInt128.cast(ShortVector.SPECIES_64).toString(), mInt128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testInt128ToLong256() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mInt128.cast(LongVector.SPECIES_256).toString(), mInt128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testInt128ToDouble256() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mInt128.cast(DoubleVector.SPECIES_256).toString(), mInt128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testInt256ToShort128() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mInt256.cast(ShortVector.SPECIES_128).toString(), mInt256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testInt256ToByte64() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mInt256.cast(ByteVector.SPECIES_64).toString(), mInt256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testInt256ToLong512() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mInt256.cast(LongVector.SPECIES_512).toString(), mInt256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testInt256ToDouble512() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mInt256.cast(DoubleVector.SPECIES_512).toString(), mInt256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testInt512ToShort256() {
        VectorMask<Integer> mInt512 = VectorMask.fromArray(IntVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mInt512.cast(ShortVector.SPECIES_256).toString(), mInt512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testInt512ToByte128() {
        VectorMask<Integer> mInt512 = VectorMask.fromArray(IntVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mInt512.cast(ByteVector.SPECIES_128).toString(), mInt512.toString());
    }

    // Float
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testFloat64ToLong128() {
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mFloat64.cast(LongVector.SPECIES_128).toString(), mFloat64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testFloat64ToDouble128() {
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mask_arr, 0);
        Asserts.assertEquals(mFloat64.cast(DoubleVector.SPECIES_128).toString(), mFloat64.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static void testFloat128ToShort64() {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mFloat128.cast(ShortVector.SPECIES_64).toString(), mFloat128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testFloat128ToLong256() {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mFloat128.cast(LongVector.SPECIES_256).toString(), mFloat128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testFloat128ToDouble256() {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mFloat128.cast(DoubleVector.SPECIES_256).toString(), mFloat128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testFloat256ToShort128() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mFloat256.cast(ShortVector.SPECIES_128).toString(), mFloat256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testFloat256ToByte64() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mFloat256.cast(ByteVector.SPECIES_64).toString(), mFloat256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testFloat256ToLong512() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mFloat256.cast(LongVector.SPECIES_512).toString(), mFloat256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testFloat256ToDouble512() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mFloat256.cast(DoubleVector.SPECIES_512).toString(), mFloat256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testFloat512ToShort256() {
        VectorMask<Float> mFloat512 = VectorMask.fromArray(FloatVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mFloat512.cast(ShortVector.SPECIES_256).toString(), mFloat512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testFloat512ToByte128() {
        VectorMask<Float> mFloat512 = VectorMask.fromArray(FloatVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mFloat512.cast(ByteVector.SPECIES_128).toString(), mFloat512.toString());
    }

    // Long
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testLong128ToInt64() {
        VectorMask<Long> mLong128 = VectorMask.fromArray(LongVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mLong128.cast(IntVector.SPECIES_64).toString(), mLong128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testLong128ToFloat64() {
        VectorMask<Long> mLong128 = VectorMask.fromArray(LongVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mLong128.cast(FloatVector.SPECIES_64).toString(), mLong128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testLong256ToInt128() {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mLong256.cast(IntVector.SPECIES_128).toString(), mLong256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testLong256ToFloat128() {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mLong256.cast(FloatVector.SPECIES_128).toString(), mLong256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testLong256ToShort64() {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mLong256.cast(ShortVector.SPECIES_64).toString(), mLong256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testLong512ToInt256() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mLong512.cast(IntVector.SPECIES_256).toString(), mLong512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testLong512ToFloat256() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mLong512.cast(FloatVector.SPECIES_256).toString(), mLong512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testLong512ToShort128() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mLong512.cast(ShortVector.SPECIES_128).toString(), mLong512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testLong512ToByte64() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mLong512.cast(ByteVector.SPECIES_64).toString(), mLong512.toString());
    }

    // Double
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testDouble128ToInt64() {
        VectorMask<Double> mDouble128 = VectorMask.fromArray(DoubleVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mDouble128.cast(IntVector.SPECIES_64).toString(), mDouble128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static void testDouble128ToFloat64() {
        VectorMask<Double> mDouble128 = VectorMask.fromArray(DoubleVector.SPECIES_128, mask_arr, 0);
        Asserts.assertEquals(mDouble128.cast(FloatVector.SPECIES_64).toString(), mDouble128.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testDouble256ToInt128() {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mDouble256.cast(IntVector.SPECIES_128).toString(), mDouble256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testDouble256ToFloat128() {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mDouble256.cast(FloatVector.SPECIES_128).toString(), mDouble256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static void testDouble256ToShort64() {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        Asserts.assertEquals(mDouble256.cast(ShortVector.SPECIES_64).toString(), mDouble256.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testDouble512ToInt256() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mDouble512.cast(IntVector.SPECIES_256).toString(), mDouble512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testDouble512ToFloat256() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mDouble512.cast(FloatVector.SPECIES_256).toString(), mDouble512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testDouble512ToShort128() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mDouble512.cast(ShortVector.SPECIES_128).toString(), mDouble512.toString());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static void testDouble512ToByte64() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Asserts.assertEquals(mDouble512.cast(ByteVector.SPECIES_64).toString(), mDouble512.toString());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
