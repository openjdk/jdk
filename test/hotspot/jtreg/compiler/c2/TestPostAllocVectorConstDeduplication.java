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

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

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
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final int LENGTH = 512;
    private static final int IMMEDIATE = 123;

    private static final int[] ia_dst1;
    private static final int[] ia_dst2;
    private static final int[] ia_dst3;

    static {
        ia_dst1 = new int[LENGTH];
        ia_dst2 = new int[LENGTH];
        ia_dst3 = new int[LENGTH];
    }

    private static void noinline_call() {}

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " >2 " }, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 " }, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"replicateI_imm_le128b",  " =2 " }, phase = CompilePhase.FINAL_CODE)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " >2 " }, phase = CompilePhase.AFTER_ITERATIVE_SPILLING)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 " }, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL)
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"ReplI_imm",  " =2 " }, phase = CompilePhase.FINAL_CODE)
    public static void testReplicateDeduplication() {
        IntVector av = IntVector.broadcast(I_SPECIES, IMMEDIATE);
        av.intoArray(ia_dst1, 0);
        noinline_call();
        av.intoArray(ia_dst2, 0);
        av.intoArray(ia_dst3, 0);
    }

    @Check(test="testReplicateDeduplication")
    public static void checkReplicateDeduplication() {
        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia_dst1[i], IMMEDIATE);
            Asserts.assertEquals(ia_dst2[i], IMMEDIATE);
            Asserts.assertEquals(ia_dst3[i], IMMEDIATE);
        }
    }

    @Test
    @Warmup(10000)
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon",  " >2 " }, phase = CompilePhase.AFTER_ITERATIVE_SPILLING )
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon",  " =2 " }, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL )
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = {"vloadcon",  " =2 " }, phase = CompilePhase.FINAL_CODE )
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices",  " >2 " }, phase = CompilePhase.AFTER_ITERATIVE_SPILLING )
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices",  " =2 " }, phase = CompilePhase.POST_ALLOCATION_COPY_REMOVAL )
    @IR(applyIfPlatform = {"x64", "true"}, counts = {"loadIotaIndices",  " =2 " }, phase = CompilePhase.FINAL_CODE )
    public static void testVectorLoadConstDeduplication() {
        IntVector av = IntVector.broadcast(I_SPECIES, 0);
        IntVector bv = av.addIndex(1);
        bv.intoArray(ia_dst1, 0);
        noinline_call();
        bv.intoArray(ia_dst2, 0);
        bv.intoArray(ia_dst3, 0);
    }

    @Check(test="testVectorLoadConstDeduplication")
    public static void checkVectorLoadConstDeduplication() {
        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Asserts.assertEquals(ia_dst1[i], i);
            Asserts.assertEquals(ia_dst2[i], i);
            Asserts.assertEquals(ia_dst3[i], i);
        }
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
