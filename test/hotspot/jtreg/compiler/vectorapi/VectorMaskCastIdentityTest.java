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

/*
* @test
* @bug 8356760
* @key randomness
* @library /test/lib /
* @summary Optimize VectorMask.fromLong for all-true/all-false cases
* @modules jdk.incubator.vector
*
* @run driver compiler.vectorapi.VectorMaskCastIdentityTest
*/

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class VectorMaskCastIdentityTest {
    private static final boolean[] mr = new boolean[128]; // 128 is large enough
    private static final Random rd = Utils.getRandomInstance();
    static {
        for (int i = 0; i < mr.length; i++) {
            mr[i] = rd.nextBoolean();
        }
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 2" }, applyIfCPUFeatureOr = {"asimd", "true"})
    public static int testTwoCastToDifferentType() {
        // The types before and after the two casts are not the same, so the cast cannot be eliminated.
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mr, 0);
        VectorMask<Double> mDouble128 = mFloat64.cast(DoubleVector.SPECIES_128);
        VectorMask<Integer> mInt64 = mDouble128.cast(IntVector.SPECIES_64);
        return mInt64.trueCount();
    }

    @Run(test = "testTwoCastToDifferentType")
    public static void testTwoCastToDifferentType_runner() {
        int count = testTwoCastToDifferentType();
        VectorMask<Float> mFloat64 = VectorMask.fromArray(FloatVector.SPECIES_64, mr, 0);
        Asserts.assertEquals(count, mFloat64.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 2" }, applyIfCPUFeatureOr = {"avx2", "true"})
    public static int testTwoCastToDifferentType2() {
        // The types before and after the two casts are not the same, so the cast cannot be eliminated.
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        VectorMask<Double> mDouble256 = mInt128.cast(DoubleVector.SPECIES_256);
        VectorMask<Short>  mShort64 = mDouble256.cast(ShortVector.SPECIES_64);
        return mShort64.trueCount();
    }

    @Run(test = "testTwoCastToDifferentType2")
    public static void testTwoCastToDifferentType2_runner() {
        int count = testTwoCastToDifferentType2();
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        Asserts.assertEquals(count, mInt128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 0" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static int testTwoCastToSameType() {
        // The types before and after the two casts are the same, so the cast will be eliminated.
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        VectorMask<Float> mFloat128 = mInt128.cast(FloatVector.SPECIES_128);
        VectorMask<Integer> mInt128_2 = mFloat128.cast(IntVector.SPECIES_128);
        return mInt128_2.trueCount();
    }

    @Run(test = "testTwoCastToSameType")
    public static void testTwoCastToSameType_runner() {
        int count = testTwoCastToSameType();
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        Asserts.assertEquals(count, mInt128.trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 1" }, applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    public static int testOneCastToDifferentType() {
        // The types before and after the only cast are different, the cast will not be eliminated.
        VectorMask<Float> mFloat128 = VectorMask.fromArray(FloatVector.SPECIES_128, mr, 0).not();
        VectorMask<Integer> mInt128 = mFloat128.cast(IntVector.SPECIES_128);
        return mInt128.trueCount();
    }

    @Run(test = "testOneCastToDifferentType")
    public static void testOneCastToDifferentType_runner() {
        int count = testOneCastToDifferentType();
        VectorMask<Float> mInt128 = VectorMask.fromArray(FloatVector.SPECIES_128, mr, 0).not();
        Asserts.assertEquals(count, mInt128.trueCount());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
