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
import java.util.Arrays;

/*
 * @test
 * @bug 8387472
 * @summary Test removal of fully covered stores and same-pattern vector stores.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          jdk.incubator.vector
 * @run driver ${test.main.class}
 */

public class TestRemoveFullyCoveredStores {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static final byte[] BYTES = new byte[16];
    static final long BYTE_BASE = UNSAFE.arrayBaseOffset(byte[].class);
    // Species
    static final VectorSpecies<Integer> I256 = IntVector.SPECIES_256;
    static final VectorSpecies<Integer> I512 = IntVector.SPECIES_512;
    static final VectorSpecies<Long> L256 = LongVector.SPECIES_256;
    // Arrays
    static final int[] intArray256Early = new int[I256.length()];
    static final int[] intArray256Late = new int[I256.length()];
    static final int[] intArray512 = new int[I512.length()];
    static final long[] longArray256Early = new long[L256.length()];
    static final long[] longArray256Late = new long[L256.length()];
    // Indices
    static final int[] longIndices256 = new int[L256.length()];
    // Masks
    static final boolean[] intMask256 = new boolean[I256.length()];
    static final boolean[] longMask256 = new boolean[L256.length()];
    static final VectorMask<Integer> intVectorMask256;
    static final VectorMask<Long> longVectorMask256;

    static {
        for (int i = 0; i < I256.length(); i++) {
            intArray256Early[i] = 256 + i;
            intArray256Late[i] = 652 + i;
            intMask256[i] = i % 2 == 0;
        }

        for (int i = 0; i < I512.length(); i++) {
            intArray512[i] = 512 + i;
        }

        for (int i = 0; i < L256.length(); i++) {
            longArray256Early[i] = 256L + i;
            longArray256Late[i] = 652L + i;
            longIndices256[i] = (i + L256.length() / 2) % L256.length();
            longMask256[i] = i % 2 == 0;
        }

        intVectorMask256 = VectorMask.fromArray(I256, intMask256, 0);
        longVectorMask256 = VectorMask.fromArray(L256, longMask256, 0);
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
     * StoreI [base + 4, base + 8)
     * StoreL [base + 0, base + 8)
     */
    @Test
    @IR(counts = {IRNode.STORE_I, "0", IRNode.STORE_L, "1"},
        phase = CompilePhase.BEFORE_MATCHING)
    public static void testStoreLongCoversStoreInt() {
        UNSAFE.putInt(BYTES, BYTE_BASE + 4, 0x12345678);
        UNSAFE.putLong(BYTES, BYTE_BASE, 0x1122334455667788L);
    }

    /*
     * Contiguous store fully covers non-contiguous store
     * even though they use different addresses.
     *
     * StoreVectorMasked256 res[1..8], mask = intVectorMask256
     * byte range: [base + 4, base + 36)
     *
     * StoreVector512 res[0..15]
     * byte range: [base + 0, base + 64)
     *
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_MASKED, "0", IRNode.STORE_VECTOR, "1"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 64"},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public static int[] testStoreVectorCoversMaskedStoreVector() {
        int[] res = new int[I512.length()];
        if (intVectorMask256.allTrue()){
            return res;
        }
        IntVector intVector256 = IntVector.fromArray(I256, intArray256Early, 0);
        IntVector intVector512 = IntVector.fromArray(I512, intArray512, 0);
        intVector256.intoArray(res, 1, intVectorMask256);
        intVector512.intoArray(res, 0);
        return res;
    }

    /*
     * StoreVectorMasked same pattern.
     *
     * Same address, same vector size, same mask. The later masked store fully
     * covers the earlier masked store.
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_MASKED, "1"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 32"},
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static int[] testMaskedStoreVectorSameMask() {
        int[] res = new int[I256.length()];
        if (intVectorMask256.allTrue()){
            return res;
        }
        IntVector intVector256Early = IntVector.fromArray(I256, intArray256Early, 0);
        IntVector intVector256Late = IntVector.fromArray(I256, intArray256Late, 0);
        intVector256Early.intoArray(res, 0, intVectorMask256);
        intVector256Late.intoArray(res, 0, intVectorMask256);
        return res;
    }

    /*
     * StoreVectorScatter same pattern.
     *
     * Same address, same vector size, same index map. The later scatter store
     * fully covers the earlier scatter store.
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_SCATTER, "1"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 32"},
        applyIfCPUFeatureOr = {"sve", "true"})
    public static long[] testScatterStoreVectorSameIndices() {
        long[] res = new long[L256.length()];
        LongVector longVector256Early = LongVector.fromArray(L256, longArray256Early, 0);
        LongVector longVector256Late = LongVector.fromArray(L256, longArray256Late, 0);
        longVector256Early.intoArray(res, 0, longIndices256, 0);
        longVector256Late.intoArray(res, 0, longIndices256, 0);
        return res;
    }

    /*
     * StoreVectorScatterMasked same pattern.
     *
     * Same address, same vector size, same index map, same mask. The later
     * scatter-masked store fully covers the earlier scatter-masked store.
     */
    @Test
    @IR(counts = {IRNode.STORE_VECTOR_SCATTER_MASKED, "1"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"MaxVectorSize", ">= 32"},
        applyIfCPUFeatureOr = {"sve", "true"})
    public static long[] testScatterMaskedStoreVectorSameIndicesAndMask() {
        long[] res = new long[L256.length()];
        LongVector longVector256Early = LongVector.fromArray(L256, longArray256Early, 0);
        LongVector longVector256Late = LongVector.fromArray(L256, longArray256Late, 0);
        longVector256Early.intoArray(res, 0, longIndices256, 0, longVectorMask256);
        longVector256Late.intoArray(res, 0, longIndices256, 0, longVectorMask256);
        return res;
    }

    @Run(test = {"testStoreLongCoversStoreInt",
                 "testStoreVectorCoversMaskedStoreVector",
                 "testMaskedStoreVectorSameMask",
                 "testScatterStoreVectorSameIndices",
                 "testScatterMaskedStoreVectorSameIndicesAndMask"})
    public static void runTest() {
        testStoreLongCoversStoreInt();
        Asserts.assertEQ(UNSAFE.getLong(BYTES, BYTE_BASE),
                         0x1122334455667788L);

        int[] res = testStoreVectorCoversMaskedStoreVector();
        Asserts.assertTrue(Arrays.equals(res, intArray512));

        res = testMaskedStoreVectorSameMask();
        for (int i = 0; i < I256.length(); i++) {
            int expected = intVectorMask256.laneIsSet(i) ? intArray256Late[i] : 0;
            Asserts.assertEQ(res[i], expected);
        }

        long[] res1 = testScatterStoreVectorSameIndices();
        for (int i = 0; i < L256.length(); i++) {
            int index = longIndices256[i];
            Asserts.assertEquals(res1[index], longArray256Late[i]);
        }

        res1 = testScatterMaskedStoreVectorSameIndicesAndMask();
        for (int i = 0; i < L256.length(); i++) {
            int index = longIndices256[i];
            long expected = longVectorMask256.laneIsSet(i) ? longArray256Late[i] : 0L;
            Asserts.assertEQ(res1[index], expected);
        }
    }
}
