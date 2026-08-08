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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.util.Arrays;
import java.util.Random;

/*
 * @test
 * @bug 8387472
 * @key randomness
 * @summary Test removal of fully covered stores and same-pattern vector stores.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          jdk.incubator.vector
 * @run driver ${test.main.class}
 */

public class TestRemoveFullyCoveredStores {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static final Random RANDOM = Utils.getRandomInstance();

    static final byte[] BYTES = new byte[16];
    static final long BYTE_BASE = UNSAFE.arrayBaseOffset(byte[].class);
    // Species
    static final VectorSpecies<Integer> I256 = IntVector.SPECIES_256;
    static final VectorSpecies<Integer> I512 = IntVector.SPECIES_512;
    static final VectorSpecies<Long> L256 = LongVector.SPECIES_256;
    // Arrays
    static final int[] intArray256Early = new int[I256.length()];
    static final int[] intArray256Middle = new int[I256.length()];
    static final int[] intArray256Late = new int[I256.length()];
    static final int[] intArray512 = new int[I512.length()];
    static final long[] longArray256Early = new long[L256.length()];
    static final long[] longArray256Middle = new long[L256.length()];
    static final long[] longArray256Late = new long[L256.length()];
    // Indices
    static int[] longIndices256A = new int[L256.length()];
    static int[] longIndices256B = new int[L256.length()];
    // Masks
    static VectorMask<Integer> intVectorMask256A;
    static VectorMask<Integer> intVectorMask256B;
    static VectorMask<Long> longVectorMask256A;
    static VectorMask<Long> longVectorMask256B;

    static boolean[] intMask256BitA;
    static boolean[] intMask256BitB;
    static boolean[] longMask256BitA;
    static boolean[] longMask256BitB;

    static {
        for (int i = 0; i < I256.length(); i++) {
            intArray256Early[i] = 256 + i;
            intArray256Middle[i] = 345 + i;
            intArray256Late[i] = 652 + i;
        }

        for (int i = 0; i < I512.length(); i++) {
            intArray512[i] = 512 + i;
        }

        for (int i = 0; i < L256.length(); i++) {
            longArray256Early[i] = 256L + i;
            longArray256Middle[i] = 468L + i;
            longArray256Late[i] = 652L + i;
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                                   "--add-modules", "jdk.incubator.vector",
                                   "-XX:-TieredCompilation");
    }

    /*
     * Contiguous store fully covers contiguous store even though
     * they use different addresses.
     *
     * Before optimization:
     *
     * StoreB  byte [base + 1, base + 2)
     * LOAD_US byte [base + 1, base + 3)
     * StoreB  byte [base + 1, base + 2)
     * StoreI  byte [base + 4, base + 8)
     * StoreL  byte [base + 0, base + 8)
     *
     * After optimization:
     *
     * StoreB  byte [base + 1, base + 2)
     * LOAD_US byte [base + 1, base + 3)
     * StoreL  byte [base + 0, base + 8)
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "1",
                  IRNode.LOAD_US,  "1",
                  IRNode.STORE_I, "0",
                  IRNode.STORE_L, "1"},
        phase = CompilePhase.BEFORE_MATCHING)
    public static char testStoreLongCoversStoreInt() {
        UNSAFE.putByte(BYTES, BYTE_BASE + 1, (byte)0x15);
        char val = UNSAFE.getChar(BYTES, BYTE_BASE + 1);
        UNSAFE.putByte(BYTES, BYTE_BASE + 1, (byte)0x18);
        UNSAFE.putInt(BYTES, BYTE_BASE + 4, 0x12345678);
        UNSAFE.putLong(BYTES, BYTE_BASE, 0x1122334455667788L);
        return val;
    }

    /*
     * Contiguous store fully covers non-contiguous store
     * even though they use different addresses.
     *
     * Before optimization:
     *
     * StoreVectorMasked256 int [base + i + 1, base + i + 9) intVectorMask256A
     * StoreVector256       int [base + i + 4, base + i + 12)
     * StoreVector512       int [base + i, base + i + 16)
     *
     * After optimization:
     *
     * StoreVector512       int [base + i, base + i + 16)
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_MASKED, "0", IRNode.STORE_VECTOR, "1"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 64"},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public static int[] testStoreVectorCoversMaskedStoreVector(int index) {
        int[] res = new int[I512.length() + 8];
        int i = index & 7;

        if (intVectorMask256A.allTrue()) {
            return res;
        }

        IntVector earlyVector256 = IntVector.fromArray(I256, intArray256Early, 0);
        IntVector middleVector256 = IntVector.fromArray(I256, intArray256Middle, 0);
        IntVector lateVector512 = IntVector.fromArray(I512, intArray512, 0);

        earlyVector256.intoArray(res, i + 1, intVectorMask256A);
        middleVector256.intoArray(res, i + 4);
        lateVector512.intoArray(res, i);
        return res;
    }

    /*
     * Combined masked-store patterns
     *
     * Before optimization:
     *
     * StoreVectorMasked int [base + i,     base + i + 8) intVectorMask256A
     * StoreVectorMasked int [base + i,     base + i + 8) intVectorMask256B
     * StoreVectorMasked int [base + i,     base + i + 8) intVectorMask256B
     * StoreVectorMasked int [base + i + 1, base + i + 9) intVectorMask256A
     * StoreVectorMasked int [base + i,     base + i + 8) intVectorMask256A
     * StoreVectorMasked int [base + i,     base + i + 8) intVectorMask256A
     *
     * After optimization:
     *
     * StoreVectorMasked int [base + i,     base + i + 8) intVectorMask256B
     * StoreVectorMasked int [base + i + 1, base + i + 9) intVectorMask256A
     * StoreVectorMasked int [base + i,     base + i + 8) intVectorMask256A
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_MASKED, "3"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 32"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static int[] testStoreVectorMasked(int index,
                                              boolean[] intMask256BitA,
                                              boolean[] intMask256BitB) {
        int[] res = new int[I256.length() + 8];
        int i = index & 7;

        VectorMask<Integer> intVectorMask256A = VectorMask.fromArray(I256, intMask256BitA, 0);
        VectorMask<Integer> intVectorMask256B = VectorMask.fromArray(I256, intMask256BitB, 0);
        if (intVectorMask256A.allTrue() ||
            intVectorMask256B.allTrue()) {
            return res;
        }

        IntVector earlyVector256 = IntVector.fromArray(I256, intArray256Early, 0);
        IntVector middleVector256 = IntVector.fromArray(I256, intArray256Middle, 0);
        IntVector lateVector256 = IntVector.fromArray(I256, intArray256Late, 0);
        // Different mask, same offset.
        earlyVector256.intoArray(res, i, intVectorMask256A);
        middleVector256.intoArray(res, i, intVectorMask256B);
        lateVector256.intoArray(res, i, intVectorMask256B);
        // Different offset, same mask.
        earlyVector256.intoArray(res, i + 1, intVectorMask256A);
        middleVector256.intoArray(res, i, intVectorMask256A);
        lateVector256.intoArray(res, i, intVectorMask256A);
        return res;
    }

    /*
     * Combined and chained StoreVectorScatter patterns.
     *
     * Before optimization:
     *
     * StoreVectorScatter offset i     longIndices256A
     * StoreVectorScatter offset i     longIndices256B
     * StoreVectorScatter offset i     longIndices256B
     * StoreVectorScatter offset i + 1 longIndices256A
     * StoreVectorScatter offset i     longIndices256A
     * StoreVectorScatter offset i     longIndices256A
     *
     * After optimization:
     *
     * StoreVectorScatter offset i     longIndices256A
     * StoreVectorScatter offset i     longIndices256B
     * StoreVectorScatter offset i + 1 longIndices256A
     * StoreVectorScatter offset i     longIndices256A
     *
     * A new indice or offset introduces a range check. The preceding store
     * then has two outputs and is not considered for elimination.
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_SCATTER, "4"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 32"},
        applyIfCPUFeatureOr = {"sve", "true", "avx512vl", "true", "rvv", "true"})
    public static long[] testStoreVectorScatter(int index) {
        long[] res = new long[L256.length() + 8];
        int i = index & 7;

        LongVector longVector256Early = LongVector.fromArray(L256, longArray256Early, 0);
        LongVector longVector256Middle = LongVector.fromArray(L256, longArray256Middle, 0);
        LongVector longVector256Late = LongVector.fromArray(L256, longArray256Late, 0);
        // Different indice, same offset.
        longVector256Early.intoArray(res, i, longIndices256A, 0);
        longVector256Middle.intoArray(res, i, longIndices256B, 0);
        longVector256Late.intoArray(res, i, longIndices256B, 0);
        // Different offset, same indice.
        longVector256Early.intoArray(res, i + 1, longIndices256A, 0);
        longVector256Middle.intoArray(res, i, longIndices256A, 0);
        longVector256Late.intoArray(res, i, longIndices256A, 0);
        return res;
    }

    /*
     * Combined and chained StoreVectorScatterMasked patterns.
     *
     * Before optimization:
     *
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256B longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256B longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256B
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256B
     * StoreVectorScatterMasked offset i + 1 longIndices256A longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256B longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256B longVectorMask256A
     *
     * After optimization:
     *
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256B longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256B
     * StoreVectorScatterMasked offset i + 1 longIndices256A longVectorMask256A
     * StoreVectorScatterMasked offset i     longIndices256A longVectorMask256A
     *
     * A new indice or offset introduces a range check. The preceding store
     * then has two outputs and is not considered for elimination.
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_SCATTER_MASKED, "6"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 32"},
        applyIfCPUFeatureOr = {"sve", "true", "avx512vl", "true", "rvv", "true"})
    public static long[] testStoreVectorScatterMasked(int index,
                                                      boolean[] longMask256BitA,
                                                      boolean[] longMask256BitB) {
        long[] res = new long[L256.length() + 8];
        int i = index & 7;

        VectorMask<Long> longVectorMask256A = VectorMask.fromArray(L256, longMask256BitA, 0);
        VectorMask<Long> longVectorMask256B = VectorMask.fromArray(L256, longMask256BitB, 0);

        if (longVectorMask256A.allTrue() ||
            longVectorMask256B.allTrue()) {
            return res;
        }

        LongVector longVector256Early = LongVector.fromArray(L256, longArray256Early, 0);
        LongVector longVector256Middle = LongVector.fromArray(L256, longArray256Middle, 0);
        LongVector longVector256Late = LongVector.fromArray(L256, longArray256Late, 0);
        // Different indice, same offset and mask.
        longVector256Early.intoArray(res, i, longIndices256A, 0, longVectorMask256A);
        longVector256Middle.intoArray(res, i, longIndices256B, 0, longVectorMask256A);
        longVector256Late.intoArray(res, i, longIndices256B, 0, longVectorMask256A);
        // Different mask, same offset and indice.
        longVector256Early.intoArray(res, i, longIndices256A, 0, longVectorMask256A);
        longVector256Middle.intoArray(res, i, longIndices256A, 0, longVectorMask256B);
        longVector256Late.intoArray(res, i, longIndices256A, 0, longVectorMask256B);
        // Different offset, same indice and mask.
        longVector256Early.intoArray(res, i + 1, longIndices256A, 0, longVectorMask256A);
        longVector256Middle.intoArray(res, i, longIndices256A, 0, longVectorMask256A);
        longVector256Late.intoArray(res, i, longIndices256A, 0, longVectorMask256A);
        return res;
    }

    @Run(test = {"testStoreLongCoversStoreInt",
                 "testStoreVectorCoversMaskedStoreVector",
                 "testStoreVectorMasked",
                 "testStoreVectorScatter",
                 "testStoreVectorScatterMasked"})
    public static void runTest() {
        testStoreLongCoversStoreInt();
        Asserts.assertEQ(UNSAFE.getLong(BYTES, BYTE_BASE),
                         0x1122334455667788L);

        intMask256BitA = getRandomMask(I256.length(), true);
        intMask256BitB = getRandomMask(I256.length(), false);
        longMask256BitA = getRandomMask(L256.length(), true);
        longMask256BitB = getRandomMask(L256.length(), false);

        intVectorMask256A = VectorMask.fromArray(I256, intMask256BitA, 0);
        intVectorMask256B = VectorMask.fromArray(I256, intMask256BitB, 0);
        longVectorMask256A = VectorMask.fromArray(L256, longMask256BitA, 0);
        longVectorMask256B = VectorMask.fromArray(L256, longMask256BitB, 0);

        longIndices256A = getRandomIndice(L256.length());
        longIndices256B = getRandomIndice(L256.length());

        if (Arrays.equals(longIndices256A, longIndices256B)) {
            int tmp = longIndices256B[0];
            longIndices256B[0] = longIndices256B[1];
            longIndices256B[1] = tmp;
        }

        int index = RANDOM.nextInt(8);
        int[] res = testStoreVectorCoversMaskedStoreVector(index);
        int[] expectedArr = new int[I512.length() + 8];
        System.arraycopy(intArray512, 0, expectedArr, index, I512.length());
        Asserts.assertTrue(Arrays.equals(res, expectedArr));

        index = RANDOM.nextInt(8);
        res = testStoreVectorMasked(index, intMask256BitA, intMask256BitB);
        expectedArr = expectedStoreVectorMasked(index);
        Asserts.assertTrue(Arrays.equals(res, expectedArr));

        index = RANDOM.nextInt(8);
        long[] res1 = testStoreVectorScatter(index);
        long[] expectedLong = expectedStoreVectorScatter(index);
        Asserts.assertTrue(Arrays.equals(res1, expectedLong));


        index = RANDOM.nextInt(8);
        res1 = testStoreVectorScatterMasked(index, longMask256BitA, longMask256BitB);
        expectedLong = expectedScatterVectorStoreMasked(index);
        Asserts.assertTrue(Arrays.equals(res1, expectedLong));
    }

    @DontInline
    static boolean[] getRandomMask(int length, boolean first) {
        boolean[] mask = new boolean[length];
        for (int i = 0; i < length; i++) {
            mask[i] = RANDOM.nextBoolean();
        }
        // avoid all true.
        if (length >= 2) {
            mask[0] = first;
            mask[1] = !first;
        }
        return mask;
    }

    @DontInline
    static int[] getRandomIndice(int length) {
        int[] indice = new int[length];
        for (int i = 0; i < length; i++) {
            indice[i] = i;
        }

        for (int i = length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            int tmp = indice[i];
            indice[i] = indice[j];
            indice[j] = tmp;
        }
        return indice;
    }

    @DontInline
    static int[] expectedStoreVectorMasked(int index) {
        int[] expected = new int[I256.length() + 8];
        int offset = index & 7;

        applyMaskedStore(expected, offset,
                         intArray256Early, intVectorMask256A);
        applyMaskedStore(expected, offset,
                         intArray256Middle, intVectorMask256B);
        applyMaskedStore(expected, offset,
                         intArray256Late, intVectorMask256B);
        applyMaskedStore(expected, offset + 1,
                         intArray256Early, intVectorMask256A);
        applyMaskedStore(expected, offset,
                        intArray256Middle, intVectorMask256A);
        applyMaskedStore(expected, offset,
                         intArray256Late, intVectorMask256A);

        return expected;
    }

    @DontInline
    static void applyMaskedStore(int[] dst, int offset, int[] values,
                                 VectorMask<Integer> mask) {
        for (int lane = 0; lane < I256.length(); lane++) {
            if (mask.laneIsSet(lane)) {
                dst[offset + lane] = values[lane];
            }
        }
    }

    @DontInline
    static long[] expectedStoreVectorScatter(int index) {
        long[] expected = new long[L256.length() + 8];
        int offset = index & 7;

        applyScatterStore(expected, offset,
                          longArray256Early, longIndices256A, null);
        applyScatterStore(expected, offset,
                          longArray256Middle, longIndices256B, null);
        applyScatterStore(expected, offset,
                          longArray256Late, longIndices256B, null);
        applyScatterStore(expected, offset + 1,
                          longArray256Early, longIndices256A, null);
        applyScatterStore(expected, offset,
                          longArray256Middle, longIndices256A, null);
        applyScatterStore(expected, offset,
                          longArray256Late, longIndices256A, null);

        return expected;
    }

    @DontInline
    static long[] expectedScatterVectorStoreMasked(int index) {
        long[] expected = new long[L256.length() + 8];
        int offset = index & 7;

        applyScatterStore(expected, offset, longArray256Early,
                          longIndices256A, longVectorMask256A);
        applyScatterStore(expected, offset, longArray256Middle,
                          longIndices256B, longVectorMask256A);
        applyScatterStore(expected, offset, longArray256Late,
                          longIndices256B, longVectorMask256A);
        applyScatterStore(expected, offset, longArray256Early,
                          longIndices256A, longVectorMask256A);
        applyScatterStore(expected, offset, longArray256Middle,
                          longIndices256A, longVectorMask256B);
        applyScatterStore(expected, offset, longArray256Late,
                          longIndices256A, longVectorMask256B);
        applyScatterStore(expected, offset + 1, longArray256Early,
                          longIndices256A, longVectorMask256A);
        applyScatterStore(expected, offset, longArray256Middle,
                          longIndices256A, longVectorMask256A);
        applyScatterStore(expected, offset, longArray256Late,
                          longIndices256A, longVectorMask256A);

        return expected;
    }

    @DontInline
    static void applyScatterStore(long[] dst, int offset,
                                  long[] values, int[] indices,
                                  VectorMask<Long> mask) {
        for (int lane = 0; lane < L256.length(); lane++) {
            if (mask == null || mask.laneIsSet(lane)) {
                dst[offset + indices[lane]] = values[lane];
            }
        }
    }
}
