/*
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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

import java.util.Random;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.test.lib.Utils;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8273264
 * @key randomness
 * @library /test/lib
 * @summary AArch64: [vector] Add missing rules for VectorMaskCast
 * @modules jdk.incubator.vector
 *
 * @run testng/othervm -XX:-TieredCompilation -XX:CompileThreshold=100 compiler.vectorapi.VectorMaskCastTest
 */


// Current vector mask cast test cases at test/jdk/jdk/incubator/vector/*ConversionTests.java
// could not be intrinsfied, hence not able to verify compiler codegen, see [1]. As a
// supplement, we add more tests for vector mask cast operations, which could be intrinsified
// by c2 compiler to generate vector/mask instructions on supported targets.
//
// [1] https://bugs.openjdk.java.net/browse/JDK-8259610

public class VectorMaskCastTest{

    private static final int NUM_ITER = 5000;
    private static final Random rd = Utils.getRandomInstance();

    public static boolean[] genMask() {
        boolean[] mask = new boolean[64];
        for (int i = 0; i < 64; i ++) {
            mask[i] = rd.nextBoolean();
        }
        return mask;
    }

    // Byte
    private static void testByte64ToShort128(boolean[] mask_arr) {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mByte64.cast(ShortVector.SPECIES_128).toString(), mByte64.toString());
    }

    private static void testByte64ToInt256(boolean[] mask_arr) {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mByte64.cast(IntVector.SPECIES_256).toString(), mByte64.toString());
    }

    private static void testByte64ToFloat256(boolean[] mask_arr) {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mByte64.cast(FloatVector.SPECIES_256).toString(), mByte64.toString());
    }

    private static void testByte64ToLong512(boolean[] mask_arr) {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mByte64.cast(LongVector.SPECIES_512).toString(), mByte64.toString());
    }

    private static void testByte64ToDouble512(boolean[] mask_arr) {
        VectorMask<Byte> mByte64 = VectorMask.fromArray(ByteVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mByte64.cast(DoubleVector.SPECIES_512).toString(), mByte64.toString());
    }

    private static void testByte128ToShort256(boolean[] mask_arr) {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mByte128.cast(ShortVector.SPECIES_256).toString(), mByte128.toString());
    }

    private static void testByte128ToInt512(boolean[] mask_arr) {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mByte128.cast(IntVector.SPECIES_512).toString(), mByte128.toString());
    }

    private static void testByte128ToFloat512(boolean[] mask_arr) {
        VectorMask<Byte> mByte128 = VectorMask.fromArray(ByteVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mByte128.cast(FloatVector.SPECIES_512).toString(), mByte128.toString());
    }

    private static void testByte256ToShort512(boolean[] mask_arr) {
        VectorMask<Byte> mByte256 = VectorMask.fromArray(ByteVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mByte256.cast(ShortVector.SPECIES_512).toString(), mByte256.toString());
    }

    // Short
    private static void testShort64ToInt128(boolean[] mask_arr) {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mShort64.cast(IntVector.SPECIES_128).toString(), mShort64.toString());
    }

    private static void testShort64ToFloat128(boolean[] mask_arr) {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mShort64.cast(FloatVector.SPECIES_128).toString(), mShort64.toString());
    }

    private static void testShort64ToLong256(boolean[] mask_arr) {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mShort64.cast(LongVector.SPECIES_256).toString(), mShort64.toString());
    }

    private static void testShort64ToDouble256(boolean[] mask_arr) {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mShort64.cast(DoubleVector.SPECIES_256).toString(), mShort64.toString());
    }

    private static void testShort128ToByte64(boolean[] mask_arr) {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mShort128.cast(ByteVector.SPECIES_64).toString(), mShort128.toString());
    }

    private static void testShort128ToInt256(boolean[] mask_arr) {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mShort128.cast(IntVector.SPECIES_256).toString(), mShort128.toString());
    }

    private static void testShort128ToFloat256(boolean[] mask_arr) {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mShort128.cast(FloatVector.SPECIES_256).toString(), mShort128.toString());
    }

    private static void testShort128ToLong512(boolean[] mask_arr) {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mShort128.cast(LongVector.SPECIES_512).toString(), mShort128.toString());
    }

    private static void testShort128ToDouble512(boolean[] mask_arr) {
        VectorMask<Short> mShort128 = VectorMask.fromArray(ShortVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mShort128.cast(DoubleVector.SPECIES_512).toString(), mShort128.toString());
    }

    private static void testShort256ToByte128(boolean[] mask_arr) {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mShort256.cast(ByteVector.SPECIES_128).toString(), mShort256.toString());
    }

    private static void testShort256ToInt512(boolean[] mask_arr) {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mShort256.cast(IntVector.SPECIES_512).toString(), mShort256.toString());
    }

    private static void testShort256ToFloat512(boolean[] mask_arr) {
        VectorMask<Short> mShort256 = VectorMask.fromArray(ShortVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mShort256.cast(FloatVector.SPECIES_512).toString(), mShort256.toString());
    }

    private static void testShort512ToByte256(boolean[] mask_arr) {
        VectorMask<Short> mShort512 = VectorMask.fromArray(ShortVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mShort512.cast(ByteVector.SPECIES_256).toString(), mShort512.toString());
    }

    // Int
    private static void testInt64ToLong128(boolean[] mask_arr) {
        VectorMask<Integer> mInt64 = VectorMask.fromArray(IntVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mInt64.cast(LongVector.SPECIES_128).toString(), mInt64.toString());
    }

    private static void testInt64ToDouble128(boolean[] mask_arr) {
        VectorMask<Integer> mInt64 = VectorMask.fromArray(IntVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mInt64.cast(DoubleVector.SPECIES_128).toString(), mInt64.toString());
    }

    private static void testInt128ToShort64(boolean[] mask_arr) {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mInt128.cast(ShortVector.SPECIES_64).toString(), mInt128.toString());
    }

    private static void testInt128ToLong256(boolean[] mask_arr) {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mInt128.cast(LongVector.SPECIES_256).toString(), mInt128.toString());
    }

    private static void testInt128ToDouble256(boolean[] mask_arr) {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mInt128.cast(DoubleVector.SPECIES_256).toString(), mInt128.toString());
    }

    private static void testInt256ToShort128(boolean[] mask_arr) {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mInt256.cast(ShortVector.SPECIES_128).toString(), mInt256.toString());
    }

    private static void testInt256ToByte64(boolean[] mask_arr) {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mInt256.cast(ByteVector.SPECIES_64).toString(), mInt256.toString());
    }

    private static void testInt256ToLong512(boolean[] mask_arr) {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mInt256.cast(LongVector.SPECIES_512).toString(), mInt256.toString());
    }

    private static void testInt256ToDouble512(boolean[] mask_arr) {
        VectorMask<Integer> mInt256 = VectorMask.fromArray(IntVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mInt256.cast(DoubleVector.SPECIES_512).toString(), mInt256.toString());
    }

    private static void testInt512ToShort256(boolean[] mask_arr) {
        VectorMask<Integer> mInt512 = VectorMask.fromArray(IntVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mInt512.cast(ShortVector.SPECIES_256).toString(), mInt512.toString());
    }

    private static void testInt512ToByte128(boolean[] mask_arr) {
        VectorMask<Integer> mInt512 = VectorMask.fromArray(IntVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mInt512.cast(ByteVector.SPECIES_128).toString(), mInt512.toString());
    }

    // Float
    private static void testFloat64ToLong128(boolean[] mask_arr) {
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mFloat64.cast(LongVector.SPECIES_128).toString(), mFloat64.toString());
    }

    private static void testFloat64ToDouble128(boolean[] mask_arr) {
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mask_arr, 0);
        Assert.assertEquals(mFloat64.cast(DoubleVector.SPECIES_128).toString(), mFloat64.toString());
    }

    private static void testFloat128ToShort64(boolean[] mask_arr) {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mFloat128.cast(ShortVector.SPECIES_64).toString(), mFloat128.toString());
    }

    private static void testFloat128ToLong256(boolean[] mask_arr) {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mFloat128.cast(LongVector.SPECIES_256).toString(), mFloat128.toString());
    }

    private static void testFloat128ToDouble256(boolean[] mask_arr) {
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mFloat128.cast(DoubleVector.SPECIES_256).toString(), mFloat128.toString());
    }

    private static void testFloat256ToShort128(boolean[] mask_arr) {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mFloat256.cast(ShortVector.SPECIES_128).toString(), mFloat256.toString());
    }

    private static void testFloat256ToByte64(boolean[] mask_arr) {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mFloat256.cast(ByteVector.SPECIES_64).toString(), mFloat256.toString());
    }

    private static void testFloat256ToLong512(boolean[] mask_arr) {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mFloat256.cast(LongVector.SPECIES_512).toString(), mFloat256.toString());
    }

    private static void testFloat256ToDouble512(boolean[] mask_arr) {
        VectorMask<Float> mFloat256 = VectorMask.fromArray(FloatVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mFloat256.cast(DoubleVector.SPECIES_512).toString(), mFloat256.toString());
    }

    private static void testFloat512ToShort256(boolean[] mask_arr) {
        VectorMask<Float> mFloat512 = VectorMask.fromArray(FloatVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mFloat512.cast(ShortVector.SPECIES_256).toString(), mFloat512.toString());
    }

    private static void testFloat512ToByte128(boolean[] mask_arr) {
        VectorMask<Float> mFloat512 = VectorMask.fromArray(FloatVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mFloat512.cast(ByteVector.SPECIES_128).toString(), mFloat512.toString());
    }

    // Long
    private static void testLong128ToInt64(boolean[] mask_arr) {
        VectorMask<Long> mLong128 = VectorMask.fromArray(LongVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mLong128.cast(IntVector.SPECIES_64).toString(), mLong128.toString());
    }

    private static void testLong128ToFloat64(boolean[] mask_arr) {
        VectorMask<Long> mLong128 = VectorMask.fromArray(LongVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mLong128.cast(FloatVector.SPECIES_64).toString(), mLong128.toString());
    }

    private static void testLong256ToInt128(boolean[] mask_arr) {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mLong256.cast(IntVector.SPECIES_128).toString(), mLong256.toString());
    }

    private static void testLong256ToFloat128(boolean[] mask_arr) {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mLong256.cast(FloatVector.SPECIES_128).toString(), mLong256.toString());
    }

    private static void testLong256ToShort64(boolean[] mask_arr) {
        VectorMask<Long> mLong256 = VectorMask.fromArray(LongVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mLong256.cast(ShortVector.SPECIES_64).toString(), mLong256.toString());
    }

    private static void testLong512ToInt256(boolean[] mask_arr) {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mLong512.cast(IntVector.SPECIES_256).toString(), mLong512.toString());
    }

    private static void testLong512ToFloat256(boolean[] mask_arr) {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mLong512.cast(FloatVector.SPECIES_256).toString(), mLong512.toString());
    }

    private static void testLong512ToShort128(boolean[] mask_arr) {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mLong512.cast(ShortVector.SPECIES_128).toString(), mLong512.toString());
    }

    private static void testLong512ToByte64(boolean[] mask_arr) {
        VectorMask<Long> mLong512 = VectorMask.fromArray(LongVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mLong512.cast(ByteVector.SPECIES_64).toString(), mLong512.toString());
    }

    // Double
    private static void testDouble128ToInt64(boolean[] mask_arr) {
        VectorMask<Double> mDouble128 = VectorMask.fromArray(DoubleVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mDouble128.cast(IntVector.SPECIES_64).toString(), mDouble128.toString());
    }

    private static void testDouble128ToFloat64(boolean[] mask_arr) {
        VectorMask<Double> mDouble128 = VectorMask.fromArray(DoubleVector.SPECIES_128, mask_arr, 0);
        Assert.assertEquals(mDouble128.cast(FloatVector.SPECIES_64).toString(), mDouble128.toString());
    }

    private static void testDouble256ToInt128(boolean[] mask_arr) {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mDouble256.cast(IntVector.SPECIES_128).toString(), mDouble256.toString());
    }

    private static void testDouble256ToFloat128(boolean[] mask_arr) {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mDouble256.cast(FloatVector.SPECIES_128).toString(), mDouble256.toString());
    }

    private static void testDouble256ToShort64(boolean[] mask_arr) {
        VectorMask<Double> mDouble256 = VectorMask.fromArray(DoubleVector.SPECIES_256, mask_arr, 0);
        Assert.assertEquals(mDouble256.cast(ShortVector.SPECIES_64).toString(), mDouble256.toString());
    };

    private static void testDouble512ToInt256(boolean[] mask_arr) {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mDouble512.cast(IntVector.SPECIES_256).toString(), mDouble512.toString());
    }

    private static void testDouble512ToFloat256(boolean[] mask_arr) {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mDouble512.cast(FloatVector.SPECIES_256).toString(), mDouble512.toString());
    }

    private static void testDouble512ToShort128(boolean[] mask_arr) {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mDouble512.cast(ShortVector.SPECIES_128).toString(), mDouble512.toString());
    }

    private static void testDouble512ToByte64(boolean[] mask_arr) {
        VectorMask<Double> mDouble512 = VectorMask.fromArray(DoubleVector.SPECIES_512, mask_arr, 0);
        Assert.assertEquals(mDouble512.cast(ByteVector.SPECIES_64).toString(), mDouble512.toString());
    }


    @Test
    public static void testMaskCast() {
        for (int i = 0; i < NUM_ITER; i++) {
            boolean[] mask = genMask();
            // Byte
            testByte64ToShort128(mask);
            testByte64ToInt256(mask);
            testByte64ToFloat256(mask);
            testByte64ToLong512(mask);
            testByte64ToDouble512(mask);
            testByte128ToShort256(mask);
            testByte128ToInt512(mask);
            testByte128ToFloat512(mask);
            testByte256ToShort512(mask);

            // Short
            testShort64ToInt128(mask);
            testShort64ToFloat128(mask);
            testShort64ToLong256(mask);
            testShort64ToDouble256(mask);
            testShort128ToByte64(mask);
            testShort128ToInt256(mask);
            testShort128ToFloat256(mask);
            testShort128ToLong512(mask);
            testShort128ToDouble512(mask);
            testShort256ToByte128(mask);
            testShort256ToInt512(mask);
            testShort256ToFloat512(mask);
            testShort512ToByte256(mask);

            // Int
            testInt64ToLong128(mask);
            testInt64ToDouble128(mask);
            testInt128ToShort64(mask);
            testInt128ToLong256(mask);
            testInt128ToDouble256(mask);
            testInt256ToShort128(mask);
            testInt256ToByte64(mask);
            testInt256ToLong512(mask);
            testInt256ToDouble512(mask);
            testInt512ToShort256(mask);
            testInt512ToByte128(mask);

            // Float
            testFloat64ToLong128(mask);
            testFloat64ToDouble128(mask);
            testFloat128ToShort64(mask);
            testFloat128ToLong256(mask);
            testFloat128ToDouble256(mask);
            testFloat256ToShort128(mask);
            testFloat256ToByte64(mask);
            testFloat256ToLong512(mask);
            testFloat256ToDouble512(mask);
            testFloat512ToShort256(mask);
            testFloat512ToByte128(mask);

            // Long
            testLong128ToInt64(mask);
            testLong128ToFloat64(mask);
            testLong256ToInt128(mask);
            testLong256ToFloat128(mask);
            testLong256ToShort64(mask);
            testLong512ToInt256(mask);
            testLong512ToFloat256(mask);
            testLong512ToShort128(mask);
            testLong512ToByte64(mask);

            // Double
            testDouble128ToInt64(mask);
            testDouble128ToFloat64(mask);
            testDouble256ToInt128(mask);
            testDouble256ToFloat128(mask);
            testDouble256ToShort64(mask);
            testDouble512ToInt256(mask);
            testDouble512ToFloat256(mask);
            testDouble512ToShort128(mask);
            testDouble512ToByte64(mask);
        }
    }
}
