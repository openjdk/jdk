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

import jdk.test.lib.Asserts;

import java.util.Arrays;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

/**
 * @test
 * @bug 8325520
 * @library /test/lib /
 * @summary Don't allow folding of load/store of vectors when using incompatible indices or mask
 * @modules jdk.incubator.vector
 *
 * @run main/othervm compiler.vectorapi.VectorGatherMaskFoldingTest
 */

public class VectorGatherMaskFoldingTest {
    private static final int NUM_ITER = 50000;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;

    private static LongVector testLongLoadOffsets(LongVector original, long[] array, int[] offsets) {
        original.intoArray(array, 0);
        return LongVector.fromArray(L_SPECIES, array, 0, offsets, 0);
    }

    private static LongVector testLongLoadMask(LongVector original, long[] array, VectorMask<Long> mask) {
        original.intoArray(array, 0);
        return LongVector.fromArray(L_SPECIES, array, 0, mask);
    }

    private static LongVector testLongLoadOffsetsMask(LongVector original,  long[] array, int[] offsets, VectorMask<Long> mask) {
        original.intoArray(array, 0);
        return LongVector.fromArray(L_SPECIES, array, 0, offsets, 0, mask);
    }

    private static IntVector testIntLoadOffsets(IntVector original, int[] array, int[] offsets) {
        original.intoArray(array, 0);
        return IntVector.fromArray(I_SPECIES, array, 0, offsets, 0);
    }

    private static IntVector testIntLoadMask(IntVector original, int[] array, VectorMask<Integer> mask) {
        original.intoArray(array, 0);
        return IntVector.fromArray(I_SPECIES, array, 0, mask);
    }

    private static IntVector testIntLoadOffsetsMask(IntVector original,  int[] array, int[] offsets, VectorMask<Integer> mask) {
        original.intoArray(array, 0);
        return IntVector.fromArray(I_SPECIES, array, 0, offsets, 0, mask);
    }

    private static void testLongStoreOffsets(long[] originalArray, long[] array, int[] offsets, int[] offsets2) {
        LongVector original = LongVector.fromArray(L_SPECIES, originalArray, 0);
        original.intoArray(array, 0, offsets, 0);
        original.intoArray(array, 0, offsets2, 0);
    }

    private static void testLongStoreMask(long[] originalArray, long[] array, VectorMask<Long> mask1, VectorMask<Long> mask2) {
        LongVector original = LongVector.fromArray(L_SPECIES, originalArray, 0);
        original.intoArray(array, 0, mask1);
        original.intoArray(array, 0, mask2);
    }

    private static void testIntStoreOffsets(int[] originalArray, int[] array, int[] offsets, int[] offsets2) {
        IntVector original = IntVector.fromArray(I_SPECIES, originalArray, 0);
        original.intoArray(array, 0, offsets, 0);
        original.intoArray(array, 0, offsets2, 0);
    }

    private static void testIntStoreMask(int[] originalArray, int[] array, VectorMask<Integer> mask1, VectorMask<Integer> mask2) {
        IntVector original = IntVector.fromArray(I_SPECIES, originalArray, 0);
        original.intoArray(array, 0, mask1);
        original.intoArray(array, 0, mask2);
    }

    public static void main(String[] args) {
        // Long vector init
        int[] longOffsets = new int[L_SPECIES.length()];
        int[] longOffsets2 = new int[L_SPECIES.length()];
        long[] longArray = new long[L_SPECIES.length()];
        long[] longArray2, longArray3;
        boolean[] longMask = new boolean[L_SPECIES.length()];
        boolean[] longMask2 = new boolean[L_SPECIES.length()];
        for (int i = 0; i < L_SPECIES.length(); i++) {
            longArray[i] = i;
            longOffsets[i] = (i + L_SPECIES.length() / 2) % L_SPECIES.length();
            longOffsets2[i] = L_SPECIES.length() - i - 1;
            longMask[i] = i % 2 == 0;
            longMask2[i] = i < L_SPECIES.length() / 2;
        }
        LongVector longVector = LongVector.fromArray(L_SPECIES, longArray, 0);
        VectorMask<Long> longVectorMask = VectorMask.fromArray(L_SPECIES, longMask, 0);
        VectorMask<Long> longVectorMask2 = VectorMask.fromArray(L_SPECIES, longMask2, 0);
        // Integer vector init
        int[] intOffsets = new int[I_SPECIES.length()];
        int[] intOffsets2 = new int[I_SPECIES.length()];
        int[] intArray = new int[I_SPECIES.length()];
        int[] intArray2, intArray3;
        boolean[] intMask = new boolean[I_SPECIES.length()];
        boolean[] intMask2 = new boolean[I_SPECIES.length()];
        for (int i = 0; i < I_SPECIES.length(); i++) {
            intArray[i] = i;
            intOffsets[i] = (i + I_SPECIES.length() / 2) % I_SPECIES.length();
            intOffsets2[i] = I_SPECIES.length() - i - 1;
            intMask[i] = i % 2 == 0;
            intMask2[i] = i < I_SPECIES.length() / 2;
        }
        IntVector intVector = IntVector.fromArray(I_SPECIES, intArray, 0);
        VectorMask<Integer> intVectorMask = VectorMask.fromArray(I_SPECIES, intMask, 0);
        VectorMask<Integer> intVectorMask2 = VectorMask.fromArray(I_SPECIES, intMask2, 0);

        for (int i = 0; i < NUM_ITER; i++) {
            // Test long loading with offsets;
            Asserts.assertNotEquals(longVector, testLongLoadOffsets(longVector, longArray, longOffsets));
            // Test long loading with mask
            Asserts.assertNotEquals(longVector, testLongLoadMask(longVector, longArray, longVectorMask));
            // Test long loading with offsets and mask
            Asserts.assertNotEquals(longVector, testLongLoadOffsetsMask(longVector, longArray, longOffsets, longVectorMask));
            // Test int loading with offsets;
            Asserts.assertNotEquals(intVector, testIntLoadOffsets(intVector, intArray, intOffsets));
            // Test int loading with mask
            Asserts.assertNotEquals(intVector, testIntLoadMask(intVector, intArray, intVectorMask));
            // Test int loading with offsets and mask
            Asserts.assertNotEquals(intVector, testIntLoadOffsetsMask(intVector, intArray, intOffsets, intVectorMask));

            // Test long storing with offsets
            longArray2 = new long[L_SPECIES.length()];
            longArray3 = new long[L_SPECIES.length()];
            testLongStoreOffsets(longArray, longArray2, longOffsets, longOffsets2);
            longVector.intoArray(longArray3, 0, longOffsets, 0);
            Asserts.assertFalse(Arrays.equals(longArray2, longArray3));
            // Test long storing with mask
            longArray2 = new long[L_SPECIES.length()];
            longArray3 = new long[L_SPECIES.length()];
            testLongStoreMask(longArray, longArray2, longVectorMask, longVectorMask2);
            longVector.intoArray(longArray3, 0, longVectorMask);
            Asserts.assertFalse(Arrays.equals(longArray2, longArray3));

            // Test int storing with offsets
            intArray2 = new int[I_SPECIES.length()];
            intArray3 = new int[I_SPECIES.length()];
            testIntStoreOffsets(intArray, intArray2, intOffsets, intOffsets2);
            intVector.intoArray(intArray3, 0, intOffsets, 0);
            Asserts.assertFalse(Arrays.equals(intArray2, intArray3));
            // Test int storing with mask
            intArray2 = new int[I_SPECIES.length()];
            intArray3 = new int[I_SPECIES.length()];
            testIntStoreMask(intArray, intArray2, intVectorMask, intVectorMask2);
            intVector.intoArray(intArray3, 0, intVectorMask);
            Asserts.assertFalse(Arrays.equals(intArray2, intArray3));
        }
    }

}
