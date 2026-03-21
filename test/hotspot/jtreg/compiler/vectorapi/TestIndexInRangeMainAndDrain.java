/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8378315
 * @summary C2 should optimize indexInRange loops into unmasked main loop and masked drain loop
 * @library /test/lib /
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestIndexInRangeMainAndDrain
 */
public class TestIndexInRangeMainAndDrain {
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> LONG_SPECIES = LongVector.SPECIES_PREFERRED;
    private static final int SIZE = 1027;
    private static final int STRIDE = Math.max(1, SPECIES.length() * 2);

    private static final int[] SRC = new int[SIZE];
    private static final int[] DST = new int[SIZE];
    private static final int[] DST_STRIDE = new int[SIZE];
    private static final int[] DST_SHARED1 = new int[SIZE];
    private static final int[] DST_SHARED2 = new int[SIZE];
    private static final long[] LSRC = new long[SIZE];
    private static final long[] LDST = new long[SIZE];

    static {
        for (int i = 0; i < SIZE; i++) {
            SRC[i] = i * 3 + 17;
            LSRC[i] = i * 5L + 13L;
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">= 1",
                  IRNode.STORE_VECTOR, ">= 1",
                  IRNode.LOAD_VECTOR_MASKED, ">= 1",
                  IRNode.STORE_VECTOR_MASKED, ">= 1"},
        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void maskedLoopWithIndexInRange() {
        for (int i = 0; i < SIZE; i += SPECIES.length()) {
            VectorMask<Integer> m = SPECIES.indexInRange(i, SIZE);
            IntVector v = IntVector.fromArray(SPECIES, SRC, i, m);
            v.add(1).intoArray(DST, i, m);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">= 1",
                  IRNode.STORE_VECTOR, ">= 1",
                  IRNode.LOAD_VECTOR_MASKED, ">= 1",
                  IRNode.STORE_VECTOR_MASKED, ">= 1"},
        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void maskedLoopWithIndexInRangeNonUnitStride() {
        for (int i = 0; i < SIZE; i += STRIDE) {
            VectorMask<Integer> m = SPECIES.indexInRange(i, SIZE);
            IntVector v = IntVector.fromArray(SPECIES, SRC, i, m);
            v.add(2).intoArray(DST_STRIDE, i, m);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">= 1",
                  IRNode.LOAD_VECTOR_L, ">= 1",
                  IRNode.STORE_VECTOR, ">= 2",
                  IRNode.LOAD_VECTOR_MASKED, ">= 2",
                  IRNode.STORE_VECTOR_MASKED, ">= 2"},
        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void maskedLoopWithMultipleVectorLengths() {
        for (int i = 0; i < SIZE; i += SPECIES.length()) {
            VectorMask<Integer> im = SPECIES.indexInRange(i, SIZE);
            IntVector iv = IntVector.fromArray(SPECIES, SRC, i, im);
            iv.add(3).intoArray(DST, i, im);

            VectorMask<Long> lm = LONG_SPECIES.indexInRange(i, SIZE);
            LongVector lv = LongVector.fromArray(LONG_SPECIES, LSRC, i, lm);
            lv.add(7L).intoArray(LDST, i, lm);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">= 1",
                  IRNode.STORE_VECTOR, ">= 1",
                  IRNode.LOAD_VECTOR_MASKED, ">= 2",
                  IRNode.STORE_VECTOR_MASKED, ">= 2"},
        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public static void maskedLoopSharedMaskNodeSafety() {
        for (int i = 0; i < SIZE; i += SPECIES.length()) {
            VectorMask<Integer> m = SPECIES.indexInRange(i, SIZE);

            IntVector v1 = IntVector.fromArray(SPECIES, SRC, i, m);
            v1.add(11).intoArray(DST_SHARED1, i, m);

            IntVector v2 = IntVector.fromArray(SPECIES, SRC, i, m);
            v2.mul(3).intoArray(DST_SHARED2, i, m);
        }
    }

    @Check(test = "maskedLoopWithIndexInRange")
    public static void checkMaskedLoopWithIndexInRange() {
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(DST[i], SRC[i] + 1);
        }
    }

    @Check(test = "maskedLoopWithIndexInRangeNonUnitStride")
    public static void checkMaskedLoopWithIndexInRangeNonUnitStride() {
        int[] expected = new int[SIZE];
        for (int i = 0; i < SIZE; i += STRIDE) {
            int upper = Math.min(i + SPECIES.length(), SIZE);
            for (int j = i; j < upper; j++) {
                expected[j] = SRC[j] + 2;
            }
        }
        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(DST_STRIDE[i], expected[i]);
        }
    }

    @Check(test = "maskedLoopWithMultipleVectorLengths")
    public static void checkMaskedLoopWithMultipleVectorLengths() {
        int[] expectedInt = new int[SIZE];
        long[] expectedLong = new long[SIZE];

        for (int i = 0; i < SIZE; i += SPECIES.length()) {
            int intUpper = Math.min(i + SPECIES.length(), SIZE);
            for (int j = i; j < intUpper; j++) {
                expectedInt[j] = SRC[j] + 3;
            }

            int longUpper = Math.min(i + LONG_SPECIES.length(), SIZE);
            for (int j = i; j < longUpper; j++) {
                expectedLong[j] = LSRC[j] + 7L;
            }
        }

        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(DST[i], expectedInt[i]);
            Asserts.assertEquals(LDST[i], expectedLong[i]);
        }
    }

    @Check(test = "maskedLoopSharedMaskNodeSafety")
    public static void checkMaskedLoopSharedMaskNodeSafety() {
        int[] expected1 = new int[SIZE];
        int[] expected2 = new int[SIZE];

        for (int i = 0; i < SIZE; i += SPECIES.length()) {
            int upper = Math.min(i + SPECIES.length(), SIZE);
            for (int j = i; j < upper; j++) {
                expected1[j] = SRC[j] + 11;
                expected2[j] = SRC[j] * 3;
            }
        }

        for (int i = 0; i < SIZE; i++) {
            Asserts.assertEquals(DST_SHARED1[i], expected1[i]);
            Asserts.assertEquals(DST_SHARED2[i], expected2[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector")
                     .setDefaultWarmup(5000)
                     .start();
    }
}
