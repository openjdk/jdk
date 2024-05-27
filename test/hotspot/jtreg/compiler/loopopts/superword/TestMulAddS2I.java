/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8310886 8325252 8320622
 * @summary Test MulAddS2I vectorization.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMulAddS2I
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

public class TestMulAddS2I {
    static final int RANGE = 1024*16;
    static final int ITER  = RANGE/2 - 1;

    static short[] sArr1 = new short[RANGE];
    static short[] sArr2 = new short[RANGE];
    static int[] ioutArr = new int[RANGE];
    static final int[] GOLDEN_A;
    static final int[] GOLDEN_B;
    static final int[] GOLDEN_C;
    static final int[] GOLDEN_D;
    static final int[] GOLDEN_E;
    static final int[] GOLDEN_F;
    static final int[] GOLDEN_G;
    static final int[] GOLDEN_H;

    static {
        for (int i = 0; i < RANGE; i++) {
            sArr1[i] = (short)(AbstractInfo.getRandom().nextInt());
            sArr2[i] = (short)(AbstractInfo.getRandom().nextInt());
        }
        GOLDEN_A = testa();
        GOLDEN_B = testb();
        GOLDEN_C = testc();
        GOLDEN_D = testd();
        GOLDEN_E = teste();
        GOLDEN_F = testf();
        GOLDEN_G = testg();
        GOLDEN_H = testh();
    }


    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+AlignVector");
        TestFramework.runWithFlags("-XX:-AlignVector");
    }

    @Run(test = {"testa", "testb", "testc", "testd", "teste", "testf", "testg", "testh"})
    @Warmup(0)
    public static void run() {
        compare(testa(), GOLDEN_A, "testa");
        compare(testb(), GOLDEN_B, "testb");
        compare(testc(), GOLDEN_C, "testc");
        compare(testd(), GOLDEN_D, "testd");
        compare(teste(), GOLDEN_E, "teste");
        compare(testf(), GOLDEN_F, "testf");
        compare(testg(), GOLDEN_G, "testg");
        compare(testh(), GOLDEN_H, "testh");
    }

    public static void compare(int[] out, int[] golden, String name) {
        for (int i = 0; i < ITER; i++) {
            Asserts.assertEQ(out[i], golden[i], "wrong result for '" + name + "' out[" + i + "]");
        }
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testa() {
        int[] out = new int[ITER];
        int[] out2 = new int[ITER];
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr1[2*i]) + (sArr1[2*i+1] * sArr1[2*i+1]));
            out2[i] += out[i];
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testb() {
        int[] out = new int[ITER];
        int[] out2 = new int[ITER];
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr2[2*i]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out2[i] += out[i];
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testc() {
        int[] out = new int[ITER];
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr2[2*i]) + (sArr1[2*i+1] * sArr2[2*i+1]));
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testd() {
        int[] out = ioutArr;
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with the same structure.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3]));
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] teste() {
        int[] out = ioutArr;
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+2] * sArr1[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3])); // swap(1 2)
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testf() {
        int[] out = ioutArr;
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+2] * sArr1[2*i+2]) + (sArr2[2*i+3] * sArr1[2*i+3])); // swap(1 2), swap(3 4)
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testg() {
        int[] out = ioutArr;
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr1[2*i+3] * sArr2[2*i+3]) + (sArr1[2*i+2] * sArr2[2*i+2])); // swap(1 3), swap(2 4)
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testh() {
        int[] out = ioutArr;
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+3] * sArr1[2*i+3]) + (sArr2[2*i+2] * sArr1[2*i+2])); // swap(1 4), swap(2 3)
        }
        return out;
    }
}
