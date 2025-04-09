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
    static final int[] GOLDEN_A;
    static final int[] GOLDEN_B;
    static final int[] GOLDEN_C;
    static final int[] GOLDEN_D;
    static final int[] GOLDEN_E;
    static final int[] GOLDEN_F;
    static final int[] GOLDEN_G;
    static final int[] GOLDEN_H;
    static final int[] GOLDEN_I;
    static final int[] GOLDEN_J;
    static final int[] GOLDEN_K;
    static final int[] GOLDEN_L;
    static final int[] GOLDEN_M;

    static {
        for (int i = 0; i < RANGE; i++) {
            sArr1[i] = (short)(AbstractInfo.getRandom().nextInt());
            sArr2[i] = (short)(AbstractInfo.getRandom().nextInt());
        }
        GOLDEN_A = testa();
        GOLDEN_B = testb();
        GOLDEN_C = testc(new int[ITER]);
        GOLDEN_D = testd(new int[ITER]);
        GOLDEN_E = teste(new int[ITER]);
        GOLDEN_F = testf(new int[ITER]);
        GOLDEN_G = testg(new int[ITER]);
        GOLDEN_H = testh(new int[ITER]);
        GOLDEN_I = testi(new int[ITER]);
        GOLDEN_J = testj(new int[ITER]);
        GOLDEN_K = testk(new int[ITER]);
        GOLDEN_L = testl(new int[ITER]);
        GOLDEN_M = testm(new int[ITER]);
    }


    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:-AlignVector", "-XX:-UseCompactObjectHeaers");
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AlignVector", "-XX:-UseCompactObjectHeaers");
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:-AlignVector", "-XX:+UseCompactObjectHeaers");
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AlignVector", "-XX:+UseCompactObjectHeaers");
    }

    @Run(test = {"testa", "testb", "testc", "testd", "teste", "testf", "testg", "testh",
                 "testi", "testj", "testk", "testl", "testm"})
    @Warmup(0)
    public static void run() {
        compare(testa(), GOLDEN_A, "testa");
        compare(testb(), GOLDEN_B, "testb");
        compare(testc(new int[ITER]), GOLDEN_C, "testc");
        compare(testd(new int[ITER]), GOLDEN_D, "testd");
        compare(teste(new int[ITER]), GOLDEN_E, "teste");
        compare(testf(new int[ITER]), GOLDEN_F, "testf");
        compare(testg(new int[ITER]), GOLDEN_G, "testg");
        compare(testh(new int[ITER]), GOLDEN_H, "testh");
        compare(testi(new int[ITER]), GOLDEN_I, "testi");
        compare(testj(new int[ITER]), GOLDEN_J, "testj");
        compare(testk(new int[ITER]), GOLDEN_K, "testk");
        compare(testl(new int[ITER]), GOLDEN_L, "testl");
        compare(testm(new int[ITER]), GOLDEN_M, "testm");
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
    public static int[] testc(int[] out) {
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr2[2*i]) + (sArr1[2*i+1] * sArr2[2*i+1]));
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIfAnd = {"MaxVectorSize", "16", "UseCompactObjectHeaders", "false"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testd(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with the same structure.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3]));
            // Hand-unrolling can mess with AlignVector and UseCompactObjectHeaders.
            // We need all addresses 8-byte aligned.
            //
            // out:
            //   adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 8*iter
            //                = 16 (or 12 if UseCompactObjectHeaders=true)
            // -> never aligned!
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIfAnd = {"MaxVectorSize", "16", "UseCompactObjectHeaders", "false" }, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] teste(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+2] * sArr1[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3])); // swap(1 2)
            // Hand-unrolling can mess with AlignVector and UseCompactObjectHeaders.
            // We need all addresses 8-byte aligned.
            //
            // out:
            //   adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 8*iter
            //                = 16 (or 12 if UseCompactObjectHeaders=true)
            // -> never aligned!
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIfAnd = {"MaxVectorSize", "16", "UseCompactObjectHeaders", "false" }, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testf(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+2] * sArr1[2*i+2]) + (sArr2[2*i+3] * sArr1[2*i+3])); // swap(1 2), swap(3 4)
            // Hand-unrolling can mess with AlignVector and UseCompactObjectHeaders.
            // We need all addresses 8-byte aligned.
            //
            // out:
            //   adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 8*iter
            //                = 16 (or 12 if UseCompactObjectHeaders=true)
            // -> never aligned!
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIfAnd = {"MaxVectorSize", "16", "UseCompactObjectHeaders", "false" }, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testg(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr1[2*i+3] * sArr2[2*i+3]) + (sArr1[2*i+2] * sArr2[2*i+2])); // swap(1 3), swap(2 4)
            // Hand-unrolling can mess with AlignVector and UseCompactObjectHeaders.
            // We need all addresses 8-byte aligned.
            //
            // out:
            //   adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 8*iter
            //                = 16 (or 12 if UseCompactObjectHeaders=true)
            // -> never aligned!
        }
        return out;
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIfAnd = {"MaxVectorSize", "16", "UseCompactObjectHeaders", "false" }, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false" },
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] testh(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1]));
            out[i+1] += ((sArr2[2*i+3] * sArr1[2*i+3]) + (sArr2[2*i+2] * sArr1[2*i+2])); // swap(1 4), swap(2 3)
            // Hand-unrolling can mess with AlignVector and UseCompactObjectHeaders.
            // We need all addresses 8-byte aligned.
            //
            // out:
            //   adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 8*iter
            //                = 16 (or 12 if UseCompactObjectHeaders=true)
            // -> never aligned!
        }
        return out;
    }

    @Test
    @IR(counts = {IRNode.MUL_ADD_S2I, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    @IR(counts = {IRNode.MUL_ADD_VS2VI, "= 0"})
    public static int[] testi(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+0]) + (sArr1[2*i+1] * sArr2[2*i+1])); // ok
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+3]) + (sArr1[2*i+3] * sArr2[2*i+2])); // bad
        }
        return out;
    }

    @Test
    @IR(counts = {IRNode.MUL_ADD_S2I, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    @IR(counts = {IRNode.MUL_ADD_VS2VI, "= 0"})
    public static int[] testj(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+1]) + (sArr1[2*i+1] * sArr2[2*i+0])); // bad
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+3]) + (sArr1[2*i+3] * sArr2[2*i+2])); // bad
        }
        return out;
    }

    @Test
    @IR(counts = {IRNode.MUL_ADD_S2I, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    @IR(counts = {IRNode.MUL_ADD_VS2VI, "= 0"})
    public static int[] testk(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+1]) + (sArr1[2*i+1] * sArr2[2*i+0])); // bad
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3])); // ok
        }
        return out;
    }

    @Test
    @IR(counts = {IRNode.MUL_ADD_S2I, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    @IR(counts = {IRNode.MUL_ADD_VS2VI, "= 0"})
    public static int[] testl(int[] out) {
        for (int i = 0; i < ITER-2; i+=2) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+1] * sArr2[2*i+1]) + (sArr1[2*i+0] * sArr2[2*i+0])); // ok
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+3]) + (sArr1[2*i+3] * sArr2[2*i+2])); // bad
        }
        return out;
    }

    @Test
    @IR(counts = {IRNode.MUL_ADD_S2I, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    @IR(counts = {IRNode.MUL_ADD_VS2VI, "= 0"})
    public static int[] testm(int[] out) {
        for (int i = 0; i < ITER-4; i+=4) {
            // Unrolled, with some swaps that prevent vectorization.
            out[i+0] += ((sArr1[2*i+0] * sArr2[2*i+1]) + (sArr1[2*i+1] * sArr2[2*i+0])); // bad
            out[i+1] += ((sArr1[2*i+2] * sArr2[2*i+2]) + (sArr1[2*i+3] * sArr2[2*i+3])); // ok
            // 2-element gap
        }
        return out;
    }
}
