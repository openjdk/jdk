/*
 * Copyright 2026 Arm Limited and/or its affiliates.
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

package compiler.c2;

import compiler.lib.ir_framework.*;

import java.util.Arrays;

import jdk.incubator.vector.*;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

/**
 * @test
 * @library /test/lib /
 * @summary Test C2 postalloc deduplicates vector constant initializations
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.c2.TestPostAllocVectorConstDeduplication
 */

public class TestPostAllocVectorConstDeduplication {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    private static final int LENGTH = 512;
    private static final int IMMEDIATE = 123;

    private static final byte[] ba_dst1;
    private static final byte[] ba_dst2;
    private static final byte[] ba_dst3;

    private static final short[] sa_dst1;
    private static final short[] sa_dst2;
    private static final short[] sa_dst3;

    private static final int[] ia_dst1;
    private static final int[] ia_dst2;
    private static final int[] ia_dst3;

    private static final long[] la_dst1;
    private static final long[] la_dst2;
    private static final long[] la_dst3;

    private static final float[] fa_dst1;
    private static final float[] fa_dst2;
    private static final float[] fa_dst3;

    private static final double[] da_dst1;
    private static final double[] da_dst2;
    private static final double[] da_dst3;

    static {
        ba_dst1 = new byte[LENGTH];
        ba_dst2 = new byte[LENGTH];
        ba_dst3 = new byte[LENGTH];

        sa_dst1 = new short[LENGTH];
        sa_dst2 = new short[LENGTH];
        sa_dst3 = new short[LENGTH];

        ia_dst1 = new int[LENGTH];
        ia_dst2 = new int[LENGTH];
        ia_dst3 = new int[LENGTH];

        la_dst1 = new long[LENGTH];
        la_dst2 = new long[LENGTH];
        la_dst3 = new long[LENGTH];

        fa_dst1 = new float[LENGTH];
        fa_dst2 = new float[LENGTH];
        fa_dst3 = new float[LENGTH];

        da_dst1 = new double[LENGTH];
        da_dst2 = new double[LENGTH];
        da_dst3 = new double[LENGTH];
    }

    private static void noinline_call() {}

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testReplicateDeduplication_B() {
        ByteVector av = ByteVector.broadcast(B_SPECIES, (byte) IMMEDIATE);
        av.intoArray(ba_dst1, 0);
        noinline_call();
        av.intoArray(ba_dst2, 0);
        av.intoArray(ba_dst3, 0);
    }

    @Check(test = "testReplicateDeduplication_B")
    public static void checkReplicateDeduplication_B() {
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals(ba_dst1[i], (byte) IMMEDIATE);
            Asserts.assertEquals(ba_dst2[i], (byte) IMMEDIATE);
            Asserts.assertEquals(ba_dst3[i], (byte) IMMEDIATE);
        }
        Arrays.fill(ba_dst1, (byte) 0);
        Arrays.fill(ba_dst2, (byte) 0);
        Arrays.fill(ba_dst3, (byte) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testReplicateDeduplication_S() {
        ShortVector av = ShortVector.broadcast(S_SPECIES, (short) IMMEDIATE);
        av.intoArray(sa_dst1, 0);
        noinline_call();
        av.intoArray(sa_dst2, 0);
        av.intoArray(sa_dst3, 0);
    }

    @Check(test = "testReplicateDeduplication_S")
    public static void checkReplicateDeduplication_S() {
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals(sa_dst1[i], (short) IMMEDIATE);
            Asserts.assertEquals(sa_dst2[i], (short) IMMEDIATE);
            Asserts.assertEquals(sa_dst3[i], (short) IMMEDIATE);
        }
        Arrays.fill(sa_dst1, (short) 0);
        Arrays.fill(sa_dst2, (short) 0);
        Arrays.fill(sa_dst3, (short) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testReplicateDeduplication_I() {
        IntVector av = IntVector.broadcast(I_SPECIES, (int) IMMEDIATE);
        av.intoArray(ia_dst1, 0);
        noinline_call();
        av.intoArray(ia_dst2, 0);
        av.intoArray(ia_dst3, 0);
    }

    @Check(test = "testReplicateDeduplication_I")
    public static void checkReplicateDeduplication_I() {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia_dst1[i], (int) IMMEDIATE);
            Asserts.assertEquals(ia_dst2[i], (int) IMMEDIATE);
            Asserts.assertEquals(ia_dst3[i], (int) IMMEDIATE);
        }
        Arrays.fill(ia_dst1, (int) 0);
        Arrays.fill(ia_dst2, (int) 0);
        Arrays.fill(ia_dst3, (int) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateL_imm_128b",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateL_imm_128b",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateL_imm_128b",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplL_imm",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplL_imm",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplL_imm",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testReplicateDeduplication_L() {
        LongVector av = LongVector.broadcast(L_SPECIES, (long) IMMEDIATE);
        av.intoArray(la_dst1, 0);
        noinline_call();
        av.intoArray(la_dst2, 0);
        av.intoArray(la_dst3, 0);
    }

    @Check(test = "testReplicateDeduplication_L")
    public static void checkReplicateDeduplication_L() {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(la_dst1[i], (long) IMMEDIATE);
            Asserts.assertEquals(la_dst2[i], (long) IMMEDIATE);
            Asserts.assertEquals(la_dst3[i], (long) IMMEDIATE);
        }
        Arrays.fill(la_dst1, (long) 0);
        Arrays.fill(la_dst2, (long) 0);
        Arrays.fill(la_dst3, (long) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplF_imm",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplF_imm",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplF_imm",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testReplicateDeduplication_F() {
        FloatVector av = FloatVector.broadcast(F_SPECIES, (float) IMMEDIATE);
        av.intoArray(fa_dst1, 0);
        noinline_call();
        av.intoArray(fa_dst2, 0);
        av.intoArray(fa_dst3, 0);
    }

    @Check(test = "testReplicateDeduplication_F")
    public static void checkReplicateDeduplication_F() {
        for (int i = 0; i < F_SPECIES.length(); i++) {
            Asserts.assertEquals(fa_dst1[i], (float) IMMEDIATE);
            Asserts.assertEquals(fa_dst2[i], (float) IMMEDIATE);
            Asserts.assertEquals(fa_dst3[i], (float) IMMEDIATE);
        }
        Arrays.fill(fa_dst1, (float) 0);
        Arrays.fill(fa_dst2, (float) 0);
        Arrays.fill(fa_dst3, (float) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplD_imm",  " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplD_imm",  " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplD_imm",  " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testReplicateDeduplication_D() {
        DoubleVector av = DoubleVector.broadcast(D_SPECIES, (double) IMMEDIATE);
        av.intoArray(da_dst1, 0);
        noinline_call();
        av.intoArray(da_dst2, 0);
        av.intoArray(da_dst3, 0);
    }

    @Check(test = "testReplicateDeduplication_D")
    public static void checkReplicateDeduplication_D() {
        for (int i = 0; i < D_SPECIES.length(); i++) {
            Asserts.assertEquals(da_dst1[i], (double) IMMEDIATE);
            Asserts.assertEquals(da_dst2[i], (double) IMMEDIATE);
            Asserts.assertEquals(da_dst3[i], (double) IMMEDIATE);
        }
        Arrays.fill(da_dst1, (double) 0);
        Arrays.fill(da_dst2, (double) 0);
        Arrays.fill(da_dst3, (double) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testVectorLoadConstDeduplication_B() {
        ByteVector av = ByteVector.broadcast(B_SPECIES, 0);
        ByteVector bv = av.addIndex(1);
        bv.intoArray(ba_dst1, 0);
        noinline_call();
        bv.intoArray(ba_dst2, 0);
        bv.intoArray(ba_dst3, 0);
    }

    @Check(test = "testVectorLoadConstDeduplication_B")
    public static void checkVectorLoadConstDeduplication_B() {
        for (int i = 0; i < B_SPECIES.length(); i++) {
            Asserts.assertEquals(ba_dst1[i], (byte) i);
            Asserts.assertEquals(ba_dst2[i], (byte) i);
            Asserts.assertEquals(ba_dst3[i], (byte) i);
        }
        Arrays.fill(ba_dst1, (byte) 0);
        Arrays.fill(ba_dst2, (byte) 0);
        Arrays.fill(ba_dst3, (byte) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testVectorLoadConstDeduplication_S() {
        ShortVector av = ShortVector.broadcast(S_SPECIES, 0);
        ShortVector bv = av.addIndex(1);
        bv.intoArray(sa_dst1, 0);
        noinline_call();
        bv.intoArray(sa_dst2, 0);
        bv.intoArray(sa_dst3, 0);
    }

    @Check(test = "testVectorLoadConstDeduplication_S")
    public static void checkVectorLoadConstDeduplication_S() {
        for (int i = 0; i < S_SPECIES.length(); i++) {
            Asserts.assertEquals(sa_dst1[i], (short) i);
            Asserts.assertEquals(sa_dst2[i], (short) i);
            Asserts.assertEquals(sa_dst3[i], (short) i);
        }
        Arrays.fill(sa_dst1, (short) 0);
        Arrays.fill(sa_dst2, (short) 0);
        Arrays.fill(sa_dst3, (short) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testVectorLoadConstDeduplication_I() {
        IntVector av = IntVector.broadcast(I_SPECIES, 0);
        IntVector bv = av.addIndex(1);
        bv.intoArray(ia_dst1, 0);
        noinline_call();
        bv.intoArray(ia_dst2, 0);
        bv.intoArray(ia_dst3, 0);
    }

    @Check(test = "testVectorLoadConstDeduplication_I")
    public static void checkVectorLoadConstDeduplication_I() {
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia_dst1[i], (int) i);
            Asserts.assertEquals(ia_dst2[i], (int) i);
            Asserts.assertEquals(ia_dst3[i], (int) i);
        }
        Arrays.fill(ia_dst1, (int) 0);
        Arrays.fill(ia_dst2, (int) 0);
        Arrays.fill(ia_dst3, (int) 0);
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon", " =2 "}, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " >2 "}, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices", " =2 "}, phase = CompilePhase.FINAL_CODE)
    public static void testVectorLoadConstDeduplication_L() {
        LongVector av = LongVector.broadcast(L_SPECIES, 0);
        LongVector bv = av.addIndex(1);
        bv.intoArray(la_dst1, 0);
        noinline_call();
        bv.intoArray(la_dst2, 0);
        bv.intoArray(la_dst3, 0);
    }

    @Check(test = "testVectorLoadConstDeduplication_L")
    public static void checkVectorLoadConstDeduplication_L() {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Asserts.assertEquals(la_dst1[i], (long) i);
            Asserts.assertEquals(la_dst2[i], (long) i);
            Asserts.assertEquals(la_dst3[i], (long) i);
        }
        Arrays.fill(la_dst1, (long) 0);
        Arrays.fill(la_dst2, (long) 0);
        Arrays.fill(la_dst3, (long) 0);
    }

    public static void main(String[] args) {
        TestFramework tfw = new TestFramework();

        tfw.addFlags("--add-modules=jdk.incubator.vector",
            "-XX:-TieredCompilation",
            "-XX:CompileCommand=dontinline,compiler.c2.TestPostAllocVectorConstDeduplication::noinline_call");

        if (Platform.isAArch64()) {
            tfw.addFlags("-XX:UseSVE=0");
        }

        tfw.start();
    }
}
