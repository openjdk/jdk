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
* @bug 8356760 8370863
* @library /test/lib /
* @summary test VectorMaskCast Identity() optimizations
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

    // The types before and after the cast sequence are the same,
    // so the casts will be eliminated.

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static int testOneCastToSameType() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        mInt128 = mInt128.cast(IntVector.SPECIES_128);
        // Insert a not() to prevent the casts being optimized by the optimization:
        // (VectorStoreMask (VectorMaskCast ... (VectorLoadMask x))) => x
        return mInt128.not().trueCount();
    }

    @Run(test = "testOneCastToSameType")
    public static void testOneCastToSameType_runner() {
        int count = testOneCastToSameType();
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        Asserts.assertEquals(count, mInt128.not().trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 0" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static int testTwoCastToSameType() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        VectorMask<Float> mFloat128 = mInt128.cast(FloatVector.SPECIES_128);
        VectorMask<Integer> mInt128_2 = mFloat128.cast(IntVector.SPECIES_128);
        return mInt128_2.not().trueCount();
    }

    @Run(test = "testTwoCastToSameType")
    public static void testTwoCastToSameType_runner() {
        int count = testTwoCastToSameType();
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        Asserts.assertEquals(count, mInt128.not().trueCount());
    }

    // The types before and after the cast sequence are different,
    // so the casts will not be eliminated.

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 1" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static int testOneCastToDifferentType() {
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        VectorMask<Short> mShort64 = mInt128.cast(ShortVector.SPECIES_64);
        return mShort64.not().trueCount();
    }

    @Run(test = "testOneCastToDifferentType")
    public static void testOneCastToDifferentType_runner() {
        int count = testOneCastToDifferentType();
        VectorMask<Integer> mInt128 = VectorMask.fromArray(IntVector.SPECIES_128, mr, 0);
        Asserts.assertEquals(count, mInt128.not().trueCount());
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "= 2" },
        applyIfCPUFeatureOr = { "asimd", "true", "avx2", "true" },
        applyIf = { "MaxVectorSize", ">= 16" })
    public static int testTwoCastToDifferentType() {
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mr, 0);
        VectorMask<Float> mFloat128 = mShort64.cast(FloatVector.SPECIES_128);
        VectorMask<Integer> mInt128 = mFloat128.cast(IntVector.SPECIES_128);
        return mInt128.not().trueCount();
    }

    @Run(test = "testTwoCastToDifferentType")
    public static void testTwoCastToDifferentType_runner() {
        int count = testTwoCastToDifferentType();
        VectorMask<Short> mShort64 = VectorMask.fromArray(ShortVector.SPECIES_64, mr, 0);
        Asserts.assertEquals(count, mShort64.not().trueCount());
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
