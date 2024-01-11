/*
 * Copyright (c) 2021, 2023, Arm Limited. All rights reserved.
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
// [1] https://bugs.openjdk.org/browse/JDK-8259610

public class VectorMaskCastTest {

    private static final Random rd = Utils.getRandomInstance();

    private static final boolean[] mask_arr;

    static {
        mask_arr = new boolean[64];
        // Making sure atleast one of the elements in the mask is "true" to ensure the result of trueCount()
        // before and after the cast can be accurately verified.
        mask_arr[0] = true;
        for (int i = 1; i < 64; i++) {
            mask_arr[i] = rd.nextBoolean();
        }
    }

    // Byte
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static VectorMask<Short> testByte64ToShort128(VectorMask<Byte> v) {
        return v.cast(ShortVector.SPECIES_128);
    }

    @Run(test = "testByte64ToShort128")
    public static void testByte64ToShort128_runner() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        VectorMask<Short> res = testByte64ToShort128(mByte64);
        Asserts.assertEquals(res.toString(), mByte64.toString());
        Asserts.assertEquals(res.trueCount(), mByte64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Integer> testByte64ToInt256(VectorMask<Byte> v) {
        return v.cast(IntVector.SPECIES_256);
    }

    @Run(test = "testByte64ToInt256")
    public static void testByte64ToInt256_runner() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        VectorMask<Integer> res = testByte64ToInt256(mByte64);
        Asserts.assertEquals(res.toString(), mByte64.toString());
        Asserts.assertEquals(res.trueCount(), mByte64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Float> testByte64ToFloat256(VectorMask<Byte> v) {
        return v.cast(FloatVector.SPECIES_256);
    }

    @Run(test = "testByte64ToFloat256")
    public static void testByte64ToFloat256_runner() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        VectorMask<Float> res = testByte64ToFloat256(mByte64);
        Asserts.assertEquals(res.toString(), mByte64.toString());
        Asserts.assertEquals(res.trueCount(), mByte64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Long> testByte64ToLong512(VectorMask<Byte> v) {
        return v.cast(LongVector.SPECIES_512);
    }

    @Run(test = "testByte64ToLong512")
    public static void testByte64ToLong512_runner() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        VectorMask<Long> res = testByte64ToLong512(mByte64);
        Asserts.assertEquals(res.toString(), mByte64.toString());
        Asserts.assertEquals(res.trueCount(), mByte64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Double> testByte64ToDouble512(VectorMask<Byte> v) {
       return v.cast(DoubleVector.SPECIES_512);
    }

    @Run(test = "testByte64ToDouble512")
    public static void testByte64ToDouble512_runner() {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        VectorMask<Double> res = testByte64ToDouble512(mByte64);
        Asserts.assertEquals(res.toString(), mByte64.toString());
        Asserts.assertEquals(res.trueCount(), mByte64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Short> testByte128ToShort256(VectorMask<Byte> v) {
        return v.cast(ShortVector.SPECIES_256);
    }

    @Run(test = "testByte128ToShort256")
    public static void testByte128ToShort256_runner() {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        VectorMask<Short> res = testByte128ToShort256(mByte128);
        Asserts.assertEquals(res.toString(), mByte128.toString());
        Asserts.assertEquals(res.trueCount(), mByte128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Integer> testByte128ToInt512(VectorMask<Byte> v) {
        return v.cast(IntVector.SPECIES_512);
    }

    @Run(test = "testByte128ToInt512")
    public static void testByte128ToInt512_runner() {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        VectorMask<Integer> res = testByte128ToInt512(mByte128);
        Asserts.assertEquals(res.toString(), mByte128.toString());
        Asserts.assertEquals(res.trueCount(), mByte128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Float> testByte128ToFloat512(VectorMask<Byte> v) {
        return v.cast(FloatVector.SPECIES_512);
    }

    @Run(test = "testByte128ToFloat512")
    public static void testByte128ToFloat512_runner() {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        VectorMask<Float> res = testByte128ToFloat512(mByte128);
        Asserts.assertEquals(res.toString(), mByte128.toString());
        Asserts.assertEquals(res.trueCount(), mByte128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Short> testByte256ToShort512(VectorMask<Byte> v) {
        return v.cast(ShortVector.SPECIES_512);
    }

    @Run(test = "testByte256ToShort512")
    public static void testByte256ToShort512_runner() {
        VectorMask<Byte> mByte256 = VectorMask.fromArray(ByteVector.SPECIES_256, mask_arr, 0);
        VectorMask<Short> res = testByte256ToShort512(mByte256);
        Asserts.assertEquals(res.toString(), mByte256.toString());
        Asserts.assertEquals(res.trueCount(), mByte256.trueCount());
    }

    // Short
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static VectorMask<Integer> testShort64ToInt128(VectorMask<Short> v) {
        return v.cast(IntVector.SPECIES_128);
    }

    @Run(test = "testShort64ToInt128")
    public static void testShort64ToInt128_runner() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        VectorMask<Integer> res = testShort64ToInt128(mShort64);
        Asserts.assertEquals(res.toString(), mShort64.toString());
        Asserts.assertEquals(res.trueCount(), mShort64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static VectorMask<Float> testShort64ToFloat128(VectorMask<Short> v) {
        return v.cast(FloatVector.SPECIES_128);
    }

    @Run(test = "testShort64ToFloat128")
    public static void testShort64ToFloat128_runner() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        VectorMask<Float> res = testShort64ToFloat128(mShort64);
        Asserts.assertEquals(res.toString(), mShort64.toString());
        Asserts.assertEquals(res.trueCount(), mShort64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Long> testShort64ToLong256(VectorMask<Short> v) {
        return v.cast(LongVector.SPECIES_256);
    }

    @Run(test = "testShort64ToLong256")
    public static void testShort64ToLong256_runner() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        VectorMask<Long> res = testShort64ToLong256(mShort64);
        Asserts.assertEquals(res.toString(), mShort64.toString());
        Asserts.assertEquals(res.trueCount(), mShort64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Double> testShort64ToDouble256(VectorMask<Short> v) {
        return v.cast(DoubleVector.SPECIES_256);
    }

    @Run(test = "testShort64ToDouble256")
    public static void testShort64ToDouble256_runner() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        VectorMask<Double> res = testShort64ToDouble256(mShort64);
        Asserts.assertEquals(res.toString(), mShort64.toString());
        Asserts.assertEquals(res.trueCount(), mShort64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static VectorMask<Byte> testShort128ToByte64(VectorMask<Short> v) {
        return v.cast(ByteVector.SPECIES_64);
    }

    @Run(test = "testShort128ToByte64")
    public static void testShort128ToByte64_runner() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        VectorMask<Byte> res = testShort128ToByte64(mShort128);
        Asserts.assertEquals(res.toString(), mShort128.toString());
        Asserts.assertEquals(res.trueCount(), mShort128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Integer> testShort128ToInt256(VectorMask<Short> v) {
        return v.cast(IntVector.SPECIES_256);
    }

    @Run(test = "testShort128ToInt256")
    public static void testShort128ToInt256_runner() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        VectorMask<Integer> res = testShort128ToInt256(mShort128);
        Asserts.assertEquals(res.toString(), mShort128.toString());
        Asserts.assertEquals(res.trueCount(), mShort128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Float> testShort128ToFloat256(VectorMask<Short> v) {
        return v.cast(FloatVector.SPECIES_256);
    }

    @Run(test = "testShort128ToFloat256")
    public static void testShort128ToFloat256_runner() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        VectorMask<Float> res = testShort128ToFloat256(mShort128);
        Asserts.assertEquals(res.toString(), mShort128.toString());
        Asserts.assertEquals(res.trueCount(), mShort128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Long> testShort128ToLong512(VectorMask<Short> v) {
        return v.cast(LongVector.SPECIES_512);
    }

    @Run(test = "testShort128ToLong512")
    public static void testShort128ToLong512_runner() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        VectorMask<Long> res = testShort128ToLong512(mShort128);
        Asserts.assertEquals(res.toString(), mShort128.toString());
        Asserts.assertEquals(res.trueCount(), mShort128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Double> testShort128ToDouble512(VectorMask<Short> v) {
        return v.cast(DoubleVector.SPECIES_512);
    }

    @Run(test = "testShort128ToDouble512")
    public static void testShort128ToDouble512_runner() {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        VectorMask<Double> res = testShort128ToDouble512(mShort128);
        Asserts.assertEquals(res.toString(), mShort128.toString());
        Asserts.assertEquals(res.trueCount(), mShort128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Byte> testShort256ToByte128(VectorMask<Short> v) {
       return v.cast(ByteVector.SPECIES_128);
    }

    @Run(test = "testShort256ToByte128")
    public static void testShort256ToByte128_runner() {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        VectorMask<Byte> res = testShort256ToByte128(mShort256);
        Asserts.assertEquals(res.toString(), mShort256.toString());
        Asserts.assertEquals(res.trueCount(), mShort256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Integer> testShort256ToInt512(VectorMask<Short> v) {
        return v.cast(IntVector.SPECIES_512);
    }

    @Run(test = "testShort256ToInt512")
    public static void testShort256ToInt512_runner() {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        VectorMask<Integer> res = testShort256ToInt512(mShort256);
        Asserts.assertEquals(res.toString(), mShort256.toString());
        Asserts.assertEquals(res.trueCount(), mShort256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Float> testShort256ToFloat512(VectorMask<Short> v) {
        return v.cast(FloatVector.SPECIES_512);
    }

    @Run(test = "testShort256ToFloat512")
    public static void testShort256ToFloat512_runner() {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        VectorMask<Float> res = testShort256ToFloat512(mShort256);
        Asserts.assertEquals(res.toString(), mShort256.toString());
        Asserts.assertEquals(res.trueCount(), mShort256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Byte> testShort512ToByte256(VectorMask<Short> v) {
        return v.cast(ByteVector.SPECIES_256);
    }

    @Run(test = "testShort512ToByte256")
    public static void testShort512ToByte256_runner() {
        VectorMask<Short> mShort512 = VectorMask.fromArray(ShortVector.SPECIES_512, mask_arr, 0);
        VectorMask<Byte> res = testShort512ToByte256(mShort512);
        Asserts.assertEquals(res.toString(), mShort512.toString());
        Asserts.assertEquals(res.trueCount(), mShort512.trueCount());
    }

    // Int
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Long> testInt64ToLong128(VectorMask<Integer> v) {
        return v.cast(LongVector.SPECIES_128);
    }

    @Run(test = "testInt64ToLong128")
    public static void testInt64ToLong128_runner() {
        VectorMask<Integer> mInt64 = VectorMask.fromArray(IntVector.SPECIES_64, mask_arr, 0);
        VectorMask<Long> res = testInt64ToLong128(mInt64);
        Asserts.assertEquals(res.toString(), mInt64.toString());
        Asserts.assertEquals(res.trueCount(), mInt64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Double> testInt64ToDouble128(VectorMask<Integer> v) {
        return v.cast(DoubleVector.SPECIES_128);
    }

    @Run(test = "testInt64ToDouble128")
    public static void testInt64ToDouble128_runner() {
        VectorMask<Integer> mInt64 = VectorMask.fromArray(IntVector.SPECIES_64, mask_arr, 0);
        VectorMask<Double> res = testInt64ToDouble128(mInt64);
        Asserts.assertEquals(res.toString(), mInt64.toString());
        Asserts.assertEquals(res.trueCount(), mInt64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static VectorMask<Short> testInt128ToShort64(VectorMask<Integer> v) {
        return v.cast(ShortVector.SPECIES_64);
    }

    @Run(test = "testInt128ToShort64")
    public static void testInt128ToShort64_runner() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        VectorMask<Short> res = testInt128ToShort64(mInt128);
        Asserts.assertEquals(res.toString(), mInt128.toString());
        Asserts.assertEquals(res.trueCount(), mInt128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Long> testInt128ToLong256(VectorMask<Integer> v) {
        return v.cast(LongVector.SPECIES_256);
    }

    @Run(test = "testInt128ToLong256")
    public static void testInt128ToLong256_runner() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        VectorMask<Long> res = testInt128ToLong256(mInt128);
        Asserts.assertEquals(res.toString(), mInt128.toString());
        Asserts.assertEquals(res.trueCount(), mInt128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Double> testInt128ToDouble256(VectorMask<Integer> v) {
        return v.cast(DoubleVector.SPECIES_256);
    }

    @Run(test = "testInt128ToDouble256")
    public static void testInt128ToDouble256_runner() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        VectorMask<Double> res = testInt128ToDouble256(mInt128);
        Asserts.assertEquals(res.toString(), mInt128.toString());
        Asserts.assertEquals(res.trueCount(), mInt128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Short> testInt256ToShort128(VectorMask<Integer> v) {
        return v.cast(ShortVector.SPECIES_128);
    }

    @Run(test = "testInt256ToShort128")
    public static void testInt256ToShort128_runner() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        VectorMask<Short> res = testInt256ToShort128(mInt256);
        Asserts.assertEquals(res.toString(), mInt256.toString());
        Asserts.assertEquals(res.trueCount(), mInt256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Byte> testInt256ToByte64(VectorMask<Integer> v) {
    return v.cast(ByteVector.SPECIES_64);
    }

    @Run(test = "testInt256ToByte64")
    public static void testInt256ToByte64_runner() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        VectorMask<Byte> res = testInt256ToByte64(mInt256);
        Asserts.assertEquals(res.toString(), mInt256.toString());
        Asserts.assertEquals(res.trueCount(), mInt256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Long> testInt256ToLong512(VectorMask<Integer> v) {
        return v.cast(LongVector.SPECIES_512);
    }

    @Run(test = "testInt256ToLong512")
    public static void testInt256ToLong512_runner() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        VectorMask<Long> res = testInt256ToLong512(mInt256);
        Asserts.assertEquals(res.toString(), mInt256.toString());
        Asserts.assertEquals(res.trueCount(), mInt256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Double> testInt256ToDouble512(VectorMask<Integer> v) {
        return v.cast(DoubleVector.SPECIES_512);
    }

    @Run(test = "testInt256ToDouble512")
    public static void testInt256ToDouble512_runner() {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        VectorMask<Double> res = testInt256ToDouble512(mInt256);
        Asserts.assertEquals(res.toString(), mInt256.toString());
        Asserts.assertEquals(res.trueCount(), mInt256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Short> testInt512ToShort256(VectorMask<Integer> v) {
        return v.cast(ShortVector.SPECIES_256);
    }

    @Run(test = "testInt512ToShort256")
    public static void testInt512ToShort256_runner() {
        VectorMask<Integer> mInt512 = VectorMask.fromArray(IntVector.SPECIES_512, mask_arr, 0);
        VectorMask<Short> res = testInt512ToShort256(mInt512);
        Asserts.assertEquals(res.toString(), mInt512.toString());
        Asserts.assertEquals(res.trueCount(), mInt512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Byte> testInt512ToByte128(VectorMask<Integer> v) {
        return v.cast(ByteVector.SPECIES_128);
    }

    @Run(test = "testInt512ToByte128")
    public static void testInt512ToByte128_runner() {
        VectorMask<Integer> mInt512 = VectorMask.fromArray(IntVector.SPECIES_512, mask_arr, 0);
        VectorMask<Byte> res = testInt512ToByte128(mInt512);
        Asserts.assertEquals(res.toString(), mInt512.toString());
        Asserts.assertEquals(res.trueCount(), mInt512.trueCount());
    }

    // Float
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Long> testFloat64ToLong128(VectorMask<Float> v) {
        return v.cast(LongVector.SPECIES_128);
    }

    @Run(test = "testFloat64ToLong128")
    public static void testFloat64ToLong128_runner() {
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mask_arr, 0);
        VectorMask<Long> res = testFloat64ToLong128(mFloat64);
        Asserts.assertEquals(res.toString(), mFloat64.toString());
        Asserts.assertEquals(res.trueCount(), mFloat64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Double> testFloat64ToDouble128(VectorMask<Float> v) {
        return v.cast(DoubleVector.SPECIES_128);
    }

    @Run(test = "testFloat64ToDouble128")
    public static void testFloat64ToDouble128_runner() {
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mask_arr, 0);
        VectorMask<Double> res = testFloat64ToDouble128(mFloat64);
        Asserts.assertEquals(res.toString(), mFloat64.toString());
        Asserts.assertEquals(res.trueCount(), mFloat64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static VectorMask<Short> testFloat128ToShort64(VectorMask<Float> v) {
        return v.cast(ShortVector.SPECIES_64);
    }

    @Run(test = "testFloat128ToShort64")
    public static void testFloat128ToShort64_runner() {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        VectorMask<Short> res = testFloat128ToShort64(mFloat128);
        Asserts.assertEquals(res.toString(), mFloat128.toString());
        Asserts.assertEquals(res.trueCount(), mFloat128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Long> testFloat128ToLong256(VectorMask<Float> v) {
        return v.cast(LongVector.SPECIES_256);
    }

    @Run(test = "testFloat128ToLong256")
    public static void testFloat128ToLong256_runner() {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        VectorMask<Long> res = testFloat128ToLong256(mFloat128);
        Asserts.assertEquals(res.toString(), mFloat128.toString());
        Asserts.assertEquals(res.trueCount(), mFloat128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Double> testFloat128ToDouble256(VectorMask<Float> v) {
        return v.cast(DoubleVector.SPECIES_256);
    }

    @Run(test = "testFloat128ToDouble256")
    public static void testFloat128ToDouble256_runner() {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        VectorMask<Double> res = testFloat128ToDouble256(mFloat128);
        Asserts.assertEquals(res.toString(), mFloat128.toString());
        Asserts.assertEquals(res.trueCount(), mFloat128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Short> testFloat256ToShort128(VectorMask<Float> v) {
        return v.cast(ShortVector.SPECIES_128);
    }

    @Run(test = "testFloat256ToShort128")
    public static void testFloat256ToShort128_runner() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        VectorMask<Short> res = testFloat256ToShort128(mFloat256);
        Asserts.assertEquals(res.toString(), mFloat256.toString());
        Asserts.assertEquals(res.trueCount(), mFloat256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Byte> testFloat256ToByte64(VectorMask<Float> v) {
        return v.cast(ByteVector.SPECIES_64);
    }

    @Run(test = "testFloat256ToByte64")
    public static void testFloat256ToByte64_runner() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        VectorMask<Byte> res = testFloat256ToByte64(mFloat256);
        Asserts.assertEquals(res.toString(), mFloat256.toString());
        Asserts.assertEquals(res.trueCount(), mFloat256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Long> testFloat256ToLong512(VectorMask<Float> v) {
        return v.cast(LongVector.SPECIES_512);
    }

    @Run(test = "testFloat256ToLong512")
    public static void testFloat256ToLong512_runner() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        VectorMask<Long> res = testFloat256ToLong512(mFloat256);
        Asserts.assertEquals(res.toString(), mFloat256.toString());
        Asserts.assertEquals(res.trueCount(), mFloat256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Double> testFloat256ToDouble512(VectorMask<Float> v) {
        return v.cast(DoubleVector.SPECIES_512);
    }

    @Run(test = "testFloat256ToDouble512")
    public static void testFloat256ToDouble512_runner() {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        VectorMask<Double> res = testFloat256ToDouble512(mFloat256);
        Asserts.assertEquals(res.toString(), mFloat256.toString());
        Asserts.assertEquals(res.trueCount(), mFloat256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Short> testFloat512ToShort256(VectorMask<Float> v) {
        return v.cast(ShortVector.SPECIES_256);
    }

    @Run(test = "testFloat512ToShort256")
    public static void testFloat512ToShort256_runner() {
        VectorMask<Float> mFloat512 = VectorMask.fromArray(FloatVector.SPECIES_512, mask_arr, 0);
        VectorMask<Short> res = testFloat512ToShort256(mFloat512);
        Asserts.assertEquals(res.toString(), mFloat512.toString());
        Asserts.assertEquals(res.trueCount(), mFloat512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Byte> testFloat512ToByte128(VectorMask<Float> v) {
        return v.cast(ByteVector.SPECIES_128);
    }

    @Run(test = "testFloat512ToByte128")
    public static void testFloat512ToByte128_runner() {
        VectorMask<Float> mFloat512 = VectorMask.fromArray(FloatVector.SPECIES_512, mask_arr, 0);
        VectorMask<Byte> res = testFloat512ToByte128(mFloat512);
        Asserts.assertEquals(res.toString(), mFloat512.toString());
        Asserts.assertEquals(res.trueCount(), mFloat512.trueCount());
    }

    // Long
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Integer> testLong128ToInt64(VectorMask<Long> v) {
        return v.cast(IntVector.SPECIES_64);
    }

    @Run(test = "testLong128ToInt64")
    public static void testLong128ToInt64_runner() {
        VectorMask<Long> mLong128 = VectorMask.fromArray(LongVector.SPECIES_128, mask_arr, 0);
        VectorMask<Integer> res = testLong128ToInt64(mLong128);
        Asserts.assertEquals(res.toString(), mLong128.toString());
        Asserts.assertEquals(res.trueCount(), mLong128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Float> testLong128ToFloat64(VectorMask<Long> v) {
        return v.cast(FloatVector.SPECIES_64);
    }

    @Run(test = "testLong128ToFloat64")
    public static void testLong128ToFloat64_runner() {
        VectorMask<Long> mLong128 = VectorMask.fromArray(LongVector.SPECIES_128, mask_arr, 0);
        VectorMask<Float> res = testLong128ToFloat64(mLong128);
        Asserts.assertEquals(res.toString(), mLong128.toString());
        Asserts.assertEquals(res.trueCount(), mLong128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Integer> testLong256ToInt128(VectorMask<Long> v) {
        return v.cast(IntVector.SPECIES_128);
    }

    @Run(test = "testLong256ToInt128")
    public static void testLong256ToInt128_runner() {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        VectorMask<Integer> res = testLong256ToInt128(mLong256);
        Asserts.assertEquals(res.toString(), mLong256.toString());
        Asserts.assertEquals(res.trueCount(), mLong256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Float> testLong256ToFloat128(VectorMask<Long> v) {
        return v.cast(FloatVector.SPECIES_128);
    }

    @Run(test = "testLong256ToFloat128")
    public static void testLong256ToFloat128_runner() {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        VectorMask<Float> res = testLong256ToFloat128(mLong256);
        Asserts.assertEquals(res.toString(), mLong256.toString());
        Asserts.assertEquals(res.trueCount(), mLong256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Short> testLong256ToShort64(VectorMask<Long> v) {
       return v.cast(ShortVector.SPECIES_64);
    }

    @Run(test = "testLong256ToShort64")
    public static void testLong256ToShort64_runner() {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        VectorMask<Short> res = testLong256ToShort64(mLong256);
        Asserts.assertEquals(res.toString(), mLong256.toString());
        Asserts.assertEquals(res.trueCount(), mLong256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Integer> testLong512ToInt256(VectorMask<Long> v) {
        return v.cast(IntVector.SPECIES_256);
    }

    @Run(test = "testLong512ToInt256")
    public static void testLong512ToInt256_runner() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        VectorMask<Integer> res = testLong512ToInt256(mLong512);
        Asserts.assertEquals(res.toString(), mLong512.toString());
        Asserts.assertEquals(res.trueCount(), mLong512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Float> testLong512ToFloat256(VectorMask<Long> v) {
        return v.cast(FloatVector.SPECIES_256);
    }

    @Run(test = "testLong512ToFloat256")
    public static void testLong512ToFloat256_runner() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        VectorMask<Float> res = testLong512ToFloat256(mLong512);
        Asserts.assertEquals(res.toString(), mLong512.toString());
        Asserts.assertEquals(res.trueCount(), mLong512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Short> testLong512ToShort128(VectorMask<Long> v) {
        return v.cast(ShortVector.SPECIES_128);
    }

    @Run(test = "testLong512ToShort128")
    public static void testLong512ToShort128_runner() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        VectorMask<Short> res = testLong512ToShort128(mLong512);
        Asserts.assertEquals(res.toString(), mLong512.toString());
        Asserts.assertEquals(res.trueCount(), mLong512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Byte> testLong512ToByte64(VectorMask<Long> v) {
        return v.cast(ByteVector.SPECIES_64);
    }

    @Run(test = "testLong512ToByte64")
    public static void testLong512ToByte64_runner() {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        VectorMask<Byte> res = testLong512ToByte64(mLong512);
        Asserts.assertEquals(res.toString(), mLong512.toString());
        Asserts.assertEquals(res.trueCount(), mLong512.trueCount());
    }

    // Double
    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Integer> testDouble128ToInt64(VectorMask<Double> v) {
        return v.cast(IntVector.SPECIES_64);
    }

    @Run(test = "testDouble128ToInt64")
    public static void testDouble128ToInt64_runner() {
        VectorMask<Double> mDouble128 = VectorMask.fromArray(DoubleVector.SPECIES_128, mask_arr, 0);
        VectorMask<Integer> res = testDouble128ToInt64(mDouble128);
        Asserts.assertEquals(res.toString(), mDouble128.toString());
        Asserts.assertEquals(res.trueCount(), mDouble128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"asimd", "true"})
    public static VectorMask<Float> testDouble128ToFloat64(VectorMask<Double> v) {
        return v.cast(FloatVector.SPECIES_64);
    }

    @Run(test = "testDouble128ToFloat64")
    public static void testDouble128ToFloat64_runner() {
        VectorMask<Double> mDouble128 = VectorMask.fromArray(DoubleVector.SPECIES_128, mask_arr, 0);
        VectorMask<Float> res = testDouble128ToFloat64(mDouble128);
        Asserts.assertEquals(res.toString(), mDouble128.toString());
        Asserts.assertEquals(res.trueCount(), mDouble128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Integer> testDouble256ToInt128(VectorMask<Double> v) {
        return v.cast(IntVector.SPECIES_128);
    }

    @Run(test = "testDouble256ToInt128")
    public static void testDouble256ToInt128_runner() {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        VectorMask<Integer> res = testDouble256ToInt128(mDouble256);
        Asserts.assertEquals(res.toString(), mDouble256.toString());
        Asserts.assertEquals(res.trueCount(), mDouble256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Float> testDouble256ToFloat128(VectorMask<Double> v) {
        return v.cast(FloatVector.SPECIES_128);
    }

    @Run(test = "testDouble256ToFloat128")
    public static void testDouble256ToFloat128_runner() {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        VectorMask<Float> res = testDouble256ToFloat128(mDouble256);
        Asserts.assertEquals(res.toString(), mDouble256.toString());
        Asserts.assertEquals(res.trueCount(), mDouble256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx2", "true"})
    public static VectorMask<Short> testDouble256ToShort64(VectorMask<Double> v) {
        return v.cast(ShortVector.SPECIES_64);
    }

    @Run(test = "testDouble256ToShort64")
    public static void testDouble256ToShort64_runner() {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        VectorMask<Short> res = testDouble256ToShort64(mDouble256);
        Asserts.assertEquals(res.toString(), mDouble256.toString());
        Asserts.assertEquals(res.trueCount(), mDouble256.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Integer> testDouble512ToInt256(VectorMask<Double> v) {
        return v.cast(IntVector.SPECIES_256);
    }

    @Run(test = "testDouble512ToInt256")
    public static void testDouble512ToInt256_runner() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        VectorMask<Integer> res = testDouble512ToInt256(mDouble512);
        Asserts.assertEquals(res.toString(), mDouble512.toString());
        Asserts.assertEquals(res.trueCount(), mDouble512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Float> testDouble512ToFloat256(VectorMask<Double> v) {
        return v.cast(FloatVector.SPECIES_256);
    }

    @Run(test = "testDouble512ToFloat256")
    public static void testDouble512ToFloat256_runner() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        VectorMask<Float> res = testDouble512ToFloat256(mDouble512);
        Asserts.assertEquals(res.toString(), mDouble512.toString());
        Asserts.assertEquals(res.trueCount(), mDouble512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Short> testDouble512ToShort128(VectorMask<Double> v) {
        return v.cast(ShortVector.SPECIES_128);
    }

    @Run(test = "testDouble512ToShort128")
    public static void testDouble512ToShort128_runner() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        VectorMask<Short> res = testDouble512ToShort128(mDouble512);
        Asserts.assertEquals(res.toString(), mDouble512.toString());
        Asserts.assertEquals(res.trueCount(), mDouble512.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeature = {"avx512vl", "true"})
    public static VectorMask<Byte> testDouble512ToByte64(VectorMask<Double> v) {
        return v.cast(ByteVector.SPECIES_64);
    }

    @Run(test = "testDouble512ToByte64")
    public static void testDouble512ToByte64_runner() {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        VectorMask<Byte> res = testDouble512ToByte64(mDouble512);
        Asserts.assertEquals(res.toString(), mDouble512.toString());
        Asserts.assertEquals(res.trueCount(), mDouble512.trueCount());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
