/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8302139
 * @summary Test various reductions, verify results and IR
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestReductionIR
 */

// TODO:
// int / long: min, max - seems to be broken?
// SumRedAbsNeg_Double.java, SumRedAbsNeg_Float.java - TODO WIP
// Vec_MulAddS2I.java ?
// Split scenarios into different runs/tests?
// Lower than SVE? Rules for other platforms?
// Remove the unroll 2 scenario, add another one instead?

package compiler.loopopts.superword;
import compiler.lib.ir_framework.*;

public class TestReductionIR {
    static final int RANGE = 512;
    static final int REPETITIONS = 100;

    static int[] iArrA  = new int[RANGE];
    static int[] iArrB  = new int[RANGE];
    static int[] iArrC  = new int[RANGE];

    static long[] lArrA  = new long[RANGE];
    static long[] lArrB  = new long[RANGE];
    static long[] lArrC  = new long[RANGE];

    static float[] fArrA  = new float[RANGE];
    static float[] fArrB  = new float[RANGE];
    static float[] fArrC  = new float[RANGE];

    static double[] dArrA  = new double[RANGE];
    static double[] dArrB  = new double[RANGE];
    static double[] dArrC  = new double[RANGE];
    static double[] dArrR0 = new double[RANGE];
    static double[] dArrR1 = new double[RANGE];

    public static void main(String args[]) {
        TestFramework framework = new TestFramework();
        String compileonly0 = "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestReductionIR::test*";
        String compileonly1 = "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestReductionIR::verify*";
        String compileonly2 = "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestReductionIR::fill*";
        String unrollLimit  = "-XX:LoopUnrollLimit=250";
        String tieredcomp   = "-XX:-TieredCompilation";
        Scenario s0 = new Scenario(0, "-XX:+SuperWordReductions", "-XX:LoopMaxUnroll=2",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        Scenario s1 = new Scenario(1, "-XX:-SuperWordReductions", "-XX:LoopMaxUnroll=2",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        Scenario s2 = new Scenario(2, "-XX:+SuperWordReductions", "-XX:LoopMaxUnroll=4",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        Scenario s3 = new Scenario(3, "-XX:-SuperWordReductions", "-XX:LoopMaxUnroll=4",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        Scenario s4 = new Scenario(4, "-XX:+SuperWordReductions", "-XX:LoopMaxUnroll=8",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        Scenario s5 = new Scenario(5, "-XX:-SuperWordReductions", "-XX:LoopMaxUnroll=8",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        Scenario s6 = new Scenario(6, "-XX:+SuperWordReductions", "-XX:LoopMaxUnroll=16",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        Scenario s7 = new Scenario(7, "-XX:-SuperWordReductions", "-XX:LoopMaxUnroll=16",
                                      compileonly0, compileonly1, compileonly2, unrollLimit, tieredcomp);
        framework.addScenarios(s0, s1, s2, s3, s4, s5, s6, s7);
        framework.start();
    }

    // ------------------------------------ ReductionAddInt --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VI, "= 0", IRNode.MUL_VI, "= 0",
                  IRNode.ADD_REDUCTION_VI, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VI, "> 0", IRNode.MUL_VI, "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public int testReductionAddInt(int[] a, int[] b, int[] c, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum += (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
        }
        return sum;
    }

    // Not compiled.
    public int referenceReductionAddInt(int[] a, int[] b, int[] c, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum += (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
        }
        return sum;
    }

    @Run(test = "testReductionAddInt")
    @Warmup(0)
    public void runTestReductionAddInt() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(iArrA, iArrB, iArrC);
            int init = RunInfo.getRandom().nextInt();
            int s0 = testReductionAddInt(iArrA, iArrB, iArrC, init);
            int s1 = referenceReductionAddInt(iArrA, iArrB, iArrC, init);
            verify("testReductionAddInt sum", s0, s1);
        }
    }

    // ------------------------------------ ReductionMulInt --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.SUB_VI, "= 0", IRNode.MUL_REDUCTION_VI, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.SUB_VI, "> 0", IRNode.MUL_REDUCTION_VI, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public int testReductionMulInt(int[] a, int[] b, int mul) {
        for (int i = 0; i < RANGE; i++) {
            mul *= a[i] - b[i];
        }
        return mul;
    }

    // Not compiled.
    public int referenceReductionMulInt(int[] a, int[] b, int mul) {
        for (int i = 0; i < RANGE; i++) {
            mul *= a[i] - b[i];
        }
        return mul;
    }

    @Run(test = "testReductionMulInt")
    @Warmup(0)
    public void runTestReductionMulInt() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillSmallPrimeDiff(iArrA, iArrB);
            int init = fillSmallPrime();
            int s0 = testReductionMulInt(iArrA, iArrB, init);
            int s1 = referenceReductionMulInt(iArrA, iArrB, init);
            verify("testReductionMulInt sum", s0, s1);
            if (s0 == 0) {
                throw new RuntimeException("Primes should not multiply to zero in int-ring.");
            }
        }
    }

    // ------------------------------------ ReductionXorInt --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VI, "= 0", IRNode.XOR_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VI, "> 0", IRNode.XOR_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public int testReductionXorInt(int[] a, int[] b, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum ^= a[i] + b[i];
        }
        return sum;
    }

    // Not compiled.
    public int referenceReductionXorInt(int[] a, int[] b, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum ^= a[i] + b[i];
        }
        return sum;
    }

    @Run(test = "testReductionXorInt")
    @Warmup(0)
    public void runTestReductionXorInt() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(iArrA, iArrB, iArrC);
            int init = RunInfo.getRandom().nextInt();
            int s0 = testReductionXorInt(iArrA, iArrB, init);
            int s1 = referenceReductionXorInt(iArrA, iArrB, init);
            verify("testReductionXorInt sum", s0, s1);
        }
    }

    // ------------------------------------ ReductionAndInt --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.SUB_VI, "= 0", IRNode.AND_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.SUB_VI, "> 0", IRNode.AND_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public int testReductionAndInt(int[] a, int[] b, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum &= a[i] - b[i];
        }
        return sum;
    }

    // Not compiled.
    public int referenceReductionAndInt(int[] a, int[] b, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum &= a[i] - b[i];
        }
        return sum;
    }

    @Run(test = "testReductionAndInt")
    @Warmup(0)
    public void runTestReductionAndInt() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillSpecialBytes(iArrA, iArrB, 0xFFFFFFFF);
            int init = 0xFFFFFFFF; // start with all bits
            int s0 = testReductionAndInt(iArrA, iArrB, init);
            int s1 = referenceReductionAndInt(iArrA, iArrB, init);
            verify("testReductionAndInt sum", s0, s1);
            if (s0 == 0 || s0 == 0xFFFFFFFF) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
	}
    }

    // ------------------------------------ ReductionOrInt --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.SUB_VI, "= 0", IRNode.OR_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.SUB_VI, "> 0", IRNode.OR_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public int testReductionOrInt(int[] a, int[] b, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum |= a[i] - b[i];
        }
        return sum;
    }

    // Not compiled.
    public int referenceReductionOrInt(int[] a, int[] b, int sum) {
        for (int i = 0; i < RANGE; i++) {
            sum |= a[i] - b[i];
        }
        return sum;
    }

    @Run(test = "testReductionOrInt")
    @Warmup(0)
    public void runTestReductionOrInt() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillSpecialBytes(iArrA, iArrB, 0);
            int init = 0; // start with no bits
            int s0 = testReductionOrInt(iArrA, iArrB, init);
            int s1 = referenceReductionOrInt(iArrA, iArrB, init);
            verify("testReductionOrInt sum", s0, s1);
            if (s0 == 0 || s0 == 0xFFFFFFFF) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
	}
    }

// TODO Add once it works
//     // ------------------------------------ ReductionMinInt --------------------------------------------------
// 
//     @Test
//     @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.MUL_VI, "= 0",
//                   IRNode.MIN_REDUCTION_VI, "= 0"},
//         applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
//     @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_VI, "> 0",
//                   IRNode.MIN_REDUCTION_VI, "> 0"},
//         applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
//         applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
//     public int testReductionMinInt(int[] a, int sum) {
//         for (int i = 0; i < RANGE; i++) {
//             sum = Math.min(sum, a[i] * 11);
//         }
//         return sum;
//     }
// 
//     // Not compiled.
//     public int referenceReductionMinInt(int[] a, int sum) {
//         for (int i = 0; i < RANGE; i++) {
//             sum = Math.min(sum, a[i] * 11);
//         }
//         return sum;
//     }
// 
//     @Run(test = "testReductionMinInt")
//     @Warmup(0)
//     public void runTestReductionMinInt() {
//         for (int j = 0; j < REPETITIONS; j++) {
//             fillRandom(iArrA);
//             int init = RunInfo.getRandom().nextInt();
//             int s0 = testReductionMinInt(iArrA, init);
//             int s1 = referenceReductionMinInt(iArrA, init);
//             verify("testReductionMinInt sum", s0, s1);
//         }
//     }

    // ------------------------------------ ReductionAddLong --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VL, "= 0", IRNode.MUL_VL, "= 0",
                  IRNode.ADD_REDUCTION_VL, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VL, "> 0", IRNode.MUL_VL, "> 0",
                  IRNode.ADD_REDUCTION_VL, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    public long testReductionAddLong(long[] a, long[] b, long[] c, long sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: need at least AVX2 to have more than 2 longs in vector.
            sum += (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
        }
        return sum;
    }

    // Not compiled.
    public long referenceReductionAddLong(long[] a, long[] b, long[] c, long sum) {
        for (int i = 0; i < RANGE; i++) {
            sum += (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
        }
        return sum;
    }

    @Run(test = "testReductionAddLong")
    @Warmup(0)
    public void runTestReductionAddLong() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(lArrA, lArrB, lArrC);
            long init = RunInfo.getRandom().nextLong();
            long s0 = testReductionAddLong(lArrA, lArrB, lArrC, init);
            long s1 = referenceReductionAddLong(lArrA, lArrB, lArrC, init);
            verify("testReductionAddLong sum", s0, s1);
        }
    }

    // ------------------------------------ ReductionMulLong --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.SUB_VL, "= 0",
                  IRNode.MUL_REDUCTION_VL, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.SUB_VL, "> 0",
                  IRNode.MUL_REDUCTION_VL, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx512dq", "true", "sve", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    public long testReductionMulLong(long[] a, long[] b, long[] c, long mul) {
        for (int i = 0; i < RANGE; i++) {
            // Note: requires AVX3 to vectorize.
            mul *= a[i] - b[i];
        }
        return mul;
    }

    // Not compiled.
    public long referenceReductionMulLong(long[] a, long[] b, long[] c, long mul) {
        for (int i = 0; i < RANGE; i++) {
            mul *= a[i] - b[i];
        }
        return mul;
    }

    @Run(test = "testReductionMulLong")
    @Warmup(0)
    public void runTestReductionMulLong() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillSmallPrimeDiff(lArrA, lArrB);
            long init = fillSmallPrime();
            long s0 = testReductionMulLong(lArrA, lArrB, lArrC, init);
            long s1 = referenceReductionMulLong(lArrA, lArrB, lArrC, init);
            verify("testReductionMulLong mul", s0, s1);
            if (s0 == 0) {
                throw new RuntimeException("Primes should not multiply to zero in long-ring.");
            }
	}
    }

    // ------------------------------------ ReductionXorLong --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VL, "= 0",
                  IRNode.XOR_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VL, "> 0",
                  IRNode.XOR_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    public long testReductionXorLong(long[] a, long[] b, long sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: need at least AVX2 to have more than 2 longs in vector.
            sum ^= a[i] + b[i];
        }
        return sum;
    }

    // Not compiled.
    public long referenceReductionXorLong(long[] a, long[] b, long sum) {
        for (int i = 0; i < RANGE; i++) {
            sum ^= a[i] + b[i];
        }
        return sum;
    }

    @Run(test = "testReductionXorLong")
    @Warmup(0)
    public void runTestReductionXorLong() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(lArrA, lArrB, lArrC);
            long init = RunInfo.getRandom().nextLong();
            long s0 = testReductionXorLong(lArrA, lArrB, init);
            long s1 = referenceReductionXorLong(lArrA, lArrB, init);
            verify("testReductionXorLong sum", s0, s1);
        }
    }

    // ------------------------------------ ReductionAndLong --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.SUB_VL, "= 0",
                  IRNode.AND_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.SUB_VL, "> 0",
                  IRNode.AND_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    public long testReductionAndLong(long[] a, long[] b, long sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: need at least AVX2 to have more than 2 longs in vector.
            sum &= a[i] - b[i];
        }
        return sum;
    }

    // Not compiled.
    public long referenceReductionAndLong(long[] a, long[] b, long sum) {
        for (int i = 0; i < RANGE; i++) {
            sum &= a[i] - b[i];
        }
        return sum;
    }

    @Run(test = "testReductionAndLong")
    @Warmup(0)
    public void runTestReductionAndLong() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillSpecialBytes(lArrA, lArrB, 0xFFFFFFFFFFFFFFFFL);
            long init = 0xFFFFFFFFFFFFFFFFL; // start with all bits
            long s0 = testReductionAndLong(lArrA, lArrB, init);
            long s1 = referenceReductionAndLong(lArrA, lArrB, init);
            verify("testReductionAndLong sum", s0, s1);
            if (s0 == 0 || s0 == 0xFFFFFFFFFFFFFFFFL) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
        }
    }

    // ------------------------------------ ReductionOrLong --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.SUB_VL, "= 0",
                  IRNode.OR_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.SUB_VL, "> 0",
                  IRNode.OR_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    public long testReductionOrLong(long[] a, long[] b, long sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: need at least AVX2 to have more than 2 longs in vector.
            sum |= a[i] - b[i];
        }
        return sum;
    }

    // Not compiled.
    public long referenceReductionOrLong(long[] a, long[] b, long sum) {
        for (int i = 0; i < RANGE; i++) {
            sum |= a[i] - b[i];
        }
        return sum;
    }

    @Run(test = "testReductionOrLong")
    @Warmup(0)
    public void runTestReductionOrLong() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillSpecialBytes(lArrA, lArrB, 0);
            long init = 0; // start with no bits
            long s0 = testReductionOrLong(lArrA, lArrB, init);
            long s1 = referenceReductionOrLong(lArrA, lArrB, init);
            verify("testReductionOrLong sum", s0, s1);
            if (s0 == 0 || s0 == 0xFFFFFFFFFFFFFFFFL) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
        }
    }

    // ------------------------------------ ReductionAddFloat --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VF, "= 0", IRNode.MUL_VF, "= 0",
                  IRNode.ADD_REDUCTION_VF, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VF, "> 0", IRNode.MUL_VF, "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public float testReductionAddFloat(float[] a, float[] b, float[] c, float sum) {
        for (int i = 0; i < RANGE; i++) {
            sum += (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
        }
        return sum;
    }

    // Not compiled.
    public float referenceReductionAddFloat(float[] a, float[] b, float[] c, float sum) {
        for (int i = 0; i < RANGE; i++) {
            sum += (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
        }
        return sum;
    }

    @Run(test = "testReductionAddFloat")
    @Warmup(0)
    public void runTestReductionAddFloat() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(fArrA, fArrB, fArrC);
            float init = RunInfo.getRandom().nextFloat();
            float s0 = testReductionAddFloat(fArrA, fArrB, fArrC, init);
            // // Comment: reduction order for floats matters. Swapping order leads to wrong results.
            // // To verify: uncomment code below.
            // float tmpA = fArrA[50];
            // float tmpB = fArrB[50];
            // float tmpC = fArrC[50];
            // fArrA[50] = fArrA[51];
            // fArrB[50] = fArrB[51];
            // fArrC[50] = fArrC[51];
            // fArrA[51] = tmpA;
            // fArrB[51] = tmpB;
            // fArrC[51] = tmpC;
            float s1 = referenceReductionAddFloat(fArrA, fArrB, fArrC, init);
            verify("testReductionAddFloat sum", s0, s1);
            if (s0 == 0.0f || Float.isNaN(s0) || Float.isInfinite(s0)) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
        }
    }

    // ------------------------------------ ReductionMulFloat --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VF, "= 0", IRNode.MUL_VF, "= 0",
                  IRNode.MUL_REDUCTION_VF, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VF, "> 0", IRNode.MUL_VF, "> 0",
                  IRNode.MUL_REDUCTION_VF, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public float testReductionMulFloat(float[] a, float[] b, float[] c, float mul) {
        for (int i = 0; i < RANGE; i++) {
            // Ensure to have enough ops to make mul-reduction profitable.
            // Ensure that product does not collapse to zero or infinity.
            mul *= a[i] * 0.05f + b[i] * 0.07f + c[i] * 0.08f + 0.9f; // [0.9..1.1]
        }
        return mul;
    }

    // Not compiled.
    public float referenceReductionMulFloat(float[] a, float[] b, float[] c, float mul) {
        for (int i = 0; i < RANGE; i++) {
            mul *= a[i] * 0.05f + b[i] * 0.07f + c[i] * 0.08f + 0.9f; // [0.9..1.1]
        }
        return mul;
    }

    @Run(test = "testReductionMulFloat")
    @Warmup(0)
    public void runTestReductionMulFloat() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(fArrA, fArrB, fArrC);
            float init = RunInfo.getRandom().nextFloat() + 1.0f; // avoid zero
            float s0 = testReductionMulFloat(fArrA, fArrB, fArrC, init);
            // // Comment: reduction order for floats matters. Swapping order leads to wrong results.
            // // To verify: uncomment code below.
            // float tmpA = fArrA[50];
            // float tmpB = fArrB[50];
            // float tmpC = fArrC[50];
            // fArrA[50] = fArrA[51];
            // fArrB[50] = fArrB[51];
            // fArrC[50] = fArrC[51];
            // fArrA[51] = tmpA;
            // fArrB[51] = tmpB;
            // fArrC[51] = tmpC;
            float s1 = referenceReductionMulFloat(fArrA, fArrB, fArrC, init);
            verify("testReductionMulFloat sum", s0, s1);
            if (s0 == 0.0f || Float.isNaN(s0) || Float.isInfinite(s0)) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
        }
    }

    // ------------------------------------ ReductionMinFloat --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.MUL_VF, "= 0", IRNode.MIN_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_VF, "> 0", IRNode.MIN_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx", "true", "sve", "true"})
    public float testReductionMinFloat(float[] a, float sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: MinReductionV requires at least AVX for float.
            sum = Math.min(sum, a[i] * 5.5f);
        }
        return sum;
    }

    // Not compiled.
    public float referenceReductionMinFloat(float[] a, float sum) {
        for (int i = 0; i < RANGE; i++) {
            sum = Math.min(sum, a[i] * 5.5f);
        }
        return sum;
    }

    @Run(test = "testReductionMinFloat")
    @Warmup(0)
    public void runTestReductionMinFloat() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(fArrA);
            float init = RunInfo.getRandom().nextFloat();
            float s0 = testReductionMinFloat(fArrA, init);
            float s1 = referenceReductionMinFloat(fArrA, init);
            verify("testReductionMinFloat sum", s0, s1);
        }
    }

    // ------------------------------------ ReductionMaxFloat --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.MUL_VF, "= 0", IRNode.MAX_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_VF, "> 0", IRNode.MAX_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx", "true", "sve", "true"})
    public float testReductionMaxFloat(float[] a, float sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: MaxReductionV requires at least AVX for float.
            sum = Math.max(sum, a[i] * 5.5f);
        }
        return sum;
    }

    // Not compiled.
    public float referenceReductionMaxFloat(float[] a, float sum) {
        for (int i = 0; i < RANGE; i++) {
            sum = Math.max(sum, a[i] * 5.5f);
        }
        return sum;
    }

    @Run(test = "testReductionMaxFloat")
    @Warmup(0)
    public void runTestReductionMaxFloat() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(fArrA);
            float init = RunInfo.getRandom().nextFloat();
            float s0 = testReductionMaxFloat(fArrA, init);
            float s1 = referenceReductionMaxFloat(fArrA, init);
            verify("testReductionMaxFloat sum", s0, s1);
        }
    }

    // ------------------------------------ ReductionAddAbsNegFloat --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VF, "= 0", IRNode.MUL_VF, "= 0",
                  IRNode.ADD_REDUCTION_VF, "= 0", IRNode.ABS_V, "= 0", IRNode.NEG_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VF, "> 0", IRNode.MUL_VF, "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0", IRNode.ABS_V, "> 0", IRNode.NEG_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public float testReductionAddAbsNegFloat(float[] a, float[] b, float[] c, float sum) {
        for (int i = 0; i < RANGE; i++) {
            sum += Math.abs(-a[i] * -b[i]) + Math.abs(-a[i] * -c[i]) + Math.abs(-b[i] * -c[i]);
        }
        return sum;
    }

    // Not compiled.
    public float referenceReductionAddAbsNegFloat(float[] a, float[] b, float[] c, float sum) {
        for (int i = 0; i < RANGE; i++) {
            sum += Math.abs(-a[i] * -b[i]) + Math.abs(-a[i] * -c[i]) + Math.abs(-b[i] * -c[i]);
        }
        return sum;
    }

    @Run(test = "testReductionAddAbsNegFloat")
    @Warmup(0)
    public void runTestReductionAddAbsNegFloat() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(fArrA, fArrB, fArrC);
            float init = RunInfo.getRandom().nextFloat();
            float s0 = testReductionAddAbsNegFloat(fArrA, fArrB, fArrC, init);
            float s1 = referenceReductionAddAbsNegFloat(fArrA, fArrB, fArrC, init);
            verify("testReductionAddAbsNegFloat sum", s0, s1);
        }
    }

    // ------------------------------------ ReductionAddDouble --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VD, "= 0", IRNode.MUL_VD, "= 0", IRNode.STORE_VECTOR, "= 0",
                  IRNode.ADD_REDUCTION_VD, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VD, "> 0", IRNode.MUL_VD, "> 0", IRNode.STORE_VECTOR, "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public double testReductionAddDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            sum += r[i]; // TODO: Store is required for double reduction.
        }
        return sum;
    }

    // Not compiled.
    public double referenceReductionAddDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            sum += r[i]; // TODO: Store is required for double reduction.
        }
        return sum;
    }

    @Run(test = "testReductionAddDouble")
    @Warmup(0)
    public void runTestReductionAddDouble() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(dArrA, dArrB, dArrC);
            double init = RunInfo.getRandom().nextDouble();
            double s0 = testReductionAddDouble(dArrA, dArrB, dArrC, dArrR0, init);
            // // Comment: reduction order for doubles matters. Swapping order leads to wrong results.
            // // To verify: uncomment code below, and comment out the r verification.
            // double tmpA = dArrA[50];
            // double tmpB = dArrB[50];
            // double tmpC = dArrC[50];
            // dArrA[50] = dArrA[51];
            // dArrB[50] = dArrB[51];
            // dArrC[50] = dArrC[51];
            // dArrA[51] = tmpA;
            // dArrB[51] = tmpB;
            // dArrC[51] = tmpC;
            double s1 = referenceReductionAddDouble(dArrA, dArrB, dArrC, dArrR1, init);
            verify("testReductionAddDouble sum", s0, s1);
            verify("testReductionAddDouble r", dArrR0, dArrR1);
            if (s0 == 0.0f || Double.isNaN(s0) || Double.isInfinite(s0)) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
	}
    }

    // ------------------------------------ ReductionAddDoubleSqrt --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VD, "= 0", IRNode.MUL_VD, "= 0", IRNode.STORE_VECTOR, "= 0",
                  IRNode.ADD_REDUCTION_VD, "= 0", IRNode.SQRT_VD, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VD, "> 0", IRNode.MUL_VD, "> 0", IRNode.STORE_VECTOR, "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0", IRNode.SQRT_VD, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx", "true", "sve", "true"})
    public double testReductionAddDoubleSqrt(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = Math.sqrt(a[i] * b[i]) + Math.sqrt(a[i] * c[i]) + Math.sqrt(b[i] * c[i]);
            sum += r[i]; // TODO: Store is required for double reduction.
        }
        return sum;
    }

    // Not compiled.
    public double referenceReductionAddDoubleSqrt(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = Math.sqrt(a[i] * b[i]) + Math.sqrt(a[i] * c[i]) + Math.sqrt(b[i] * c[i]);
            sum += r[i]; // TODO: Store is required for double reduction.
        }
        return sum;
    }

    @Run(test = "testReductionAddDoubleSqrt")
    @Warmup(0)
    public void runTestReductionAddDoubleSqrt() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(dArrA, dArrB, dArrC);
            double init = RunInfo.getRandom().nextDouble();
            double s0 = testReductionAddDoubleSqrt(dArrA, dArrB, dArrC, dArrR0, init);
            // // Comment: reduction order for doubles matters. Swapping order leads to wrong results.
            // // To verify: uncomment code below, and comment out the r verification.
            // double tmpA = dArrA[50];
            // double tmpB = dArrB[50];
            // double tmpC = dArrC[50];
            // dArrA[50] = dArrA[51];
            // dArrB[50] = dArrB[51];
            // dArrC[50] = dArrC[51];
            // dArrA[51] = tmpA;
            // dArrB[51] = tmpB;
            // dArrC[51] = tmpC;
            double s1 = referenceReductionAddDoubleSqrt(dArrA, dArrB, dArrC, dArrR1, init);
            verify("testReductionAddDoubleSqrt sum", s0, s1);
            verify("testReductionAddDoubleSqrt r", dArrR0, dArrR1);
            if (s0 == 0.0f || Double.isNaN(s0) || Double.isInfinite(s0)) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
	}
    }

    // ------------------------------------ ReductionMulDouble --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VD, "= 0", IRNode.MUL_VD, "= 0", IRNode.STORE_VECTOR, "= 0",
                  IRNode.MUL_REDUCTION_VD, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VD, "> 0", IRNode.MUL_VD, "> 0", IRNode.STORE_VECTOR, "> 0",
                  IRNode.MUL_REDUCTION_VD, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
    public double testReductionMulDouble(double[] a, double[] b, double[] c, double[] r, double mul) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = a[i] * 0.05 + b[i] * 0.07 + c[i] * 0.08 + 0.9; // [0.9..1.1]
            mul *= r[i]; // TODO: Store is required for double reduction.
        }
        return mul;
    }

    // Not compiled.
    public double referenceReductionMulDouble(double[] a, double[] b, double[] c, double[] r, double mul) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = a[i] * 0.05 + b[i] * 0.07 + c[i] * 0.08 + 0.9; // [0.9..1.1]
            mul *= r[i]; // TODO: Store is required for double reduction.
        }
        return mul;
    }

    @Run(test = "testReductionMulDouble")
    @Warmup(0)
    public void runTestReductionMulDouble() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(dArrA, dArrB, dArrC);
            double init = RunInfo.getRandom().nextDouble() + 1.0; // avoid zero
            double s0 = testReductionMulDouble(dArrA, dArrB, dArrC, dArrR0, init);
            // // Comment: reduction order for doubles matters. Swapping order leads to wrong results.
            // // To verify: uncomment code below.
            // double tmpA = dArrA[50];
            // double tmpB = dArrB[50];
            // double tmpC = dArrC[50];
            // dArrA[50] = dArrA[51];
            // dArrB[50] = dArrB[51];
            // dArrC[50] = dArrC[51];
            // dArrA[51] = tmpA;
            // dArrB[51] = tmpB;
            // dArrC[51] = tmpC;
            double s1 = referenceReductionMulDouble(dArrA, dArrB, dArrC, dArrR1, init);
            verify("testReductionMulDouble mul", s0, s1);
            verify("testReductionMulDouble r", dArrR0, dArrR1);
            if (s0 == 0.0f || Double.isNaN(s0) || Double.isInfinite(s0)) {
                throw new RuntimeException("Test should not collapse. " + s0);
            }
        }
    }

    // ------------------------------------ ReductionMinDouble --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VD, "= 0", IRNode.MUL_VD, "= 0", IRNode.STORE_VECTOR, "= 0",
                  IRNode.MIN_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VD, "> 0", IRNode.MUL_VD, "> 0", IRNode.STORE_VECTOR, "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx", "true", "sve", "true"})
    public double testReductionMinDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: AVX required for MinReductionV for double.
            r[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            sum = Math.min(sum, r[i]); // TODO: Store is required for double reduction.
        }
        return sum;
    }

    // Not compiled.
    public double referenceReductionMinDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            sum = Math.min(sum, r[i]); // TODO: Store is required for double reduction.
        }
        return sum;
    }

    @Run(test = "testReductionMinDouble")
    @Warmup(0)
    public void runTestReductionMinDouble() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(dArrA, dArrB, dArrC);
            double init = RunInfo.getRandom().nextDouble();
            double s0 = testReductionMinDouble(dArrA, dArrB, dArrC, dArrR0, init);
            double s1 = referenceReductionMinDouble(dArrA, dArrB, dArrC, dArrR1, init);
            verify("testReductionMinDouble sum", s0, s1);
            verify("testReductionMinDouble r", dArrR0, dArrR1);
	}
    }

    // ------------------------------------ ReductionMaxDouble --------------------------------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VD, "= 0", IRNode.MUL_VD, "= 0", IRNode.STORE_VECTOR, "= 0",
                  IRNode.MAX_REDUCTION_V, "= 0"},
        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VD, "> 0", IRNode.MUL_VD, "> 0", IRNode.STORE_VECTOR, "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
        applyIfCPUFeatureOr = {"avx", "true", "sve", "true"})
    public double testReductionMaxDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            // Note: AVX required for MaxReductionV for double.
            r[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            sum = Math.max(sum, r[i]); // TODO: Store is required for double reduction.
        }
        return sum;
    }

    // Not compiled.
    public double referenceReductionMaxDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
        for (int i = 0; i < RANGE; i++) {
            r[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            sum = Math.max(sum, r[i]); // TODO: Store is required for double reduction.
        }
        return sum;
    }

    @Run(test = "testReductionMaxDouble")
    @Warmup(0)
    public void runTestReductionMaxDouble() {
        for (int j = 0; j < REPETITIONS; j++) {
            fillRandom(dArrA, dArrB, dArrC);
            double init = RunInfo.getRandom().nextDouble();
            double s0 = testReductionMaxDouble(dArrA, dArrB, dArrC, dArrR0, init);
            double s1 = referenceReductionMaxDouble(dArrA, dArrB, dArrC, dArrR1, init);
            verify("testReductionMaxDouble sum", s0, s1);
            verify("testReductionMaxDouble r", dArrR0, dArrR1);
	}
    }

// TODO fix it
//    // ------------------------------------ ReductionAddAbsNegDouble --------------------------------------------------
//
//    @Test
//    @IR(counts = {IRNode.LOAD_VECTOR, "= 0", IRNode.ADD_VF, "= 0", IRNode.MUL_VF, "= 0", IRNode.STORE_VECTOR, "= 0",
//                  IRNode.ADD_REDUCTION_VF, "= 0", IRNode.ABS_V, "= 0", IRNode.NEG_V, "= 0"},
//        applyIfOr = {"SuperWordReductions", "false", "LoopMaxUnroll", "<= 4"})
//    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.ADD_VF, "> 0", IRNode.MUL_VF, "> 0", IRNode.STORE_VECTOR, "> 0",
//                  IRNode.ADD_REDUCTION_VF, "> 0", IRNode.ABS_V, "> 0", IRNode.NEG_V, "> 0"},
//        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", "> 4"},
//        applyIfCPUFeatureOr = {"sse4.1", "true", "sve", "true"})
//    public double testReductionAddAbsNegDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
//        for (int i = 0; i < RANGE; i++) {
//            r[i] = Math.abs(-a[i] * -b[i]) + Math.abs(-a[i] * -c[i]) + Math.abs(-b[i] * -c[i]);
//            sum += r[i]; // TODO: Store is required for double reduction.
//        }
//        return sum;
//    }
//
//    // Not compiled.
//    public double referenceReductionAddAbsNegDouble(double[] a, double[] b, double[] c, double[] r, double sum) {
//        for (int i = 0; i < RANGE; i++) {
//            r[i] = Math.abs(-a[i] * -b[i]) + Math.abs(-a[i] * -c[i]) + Math.abs(-b[i] * -c[i]);
//            sum += r[i]; // TODO: Store is required for double reduction.
//        }
//        return sum;
//    }
//
//    @Run(test = "testReductionAddAbsNegDouble")
//    @Warmup(0)
//    public void runTestReductionAddAbsNegDouble() {
//        for (int j = 0; j < REPETITIONS; j++) {
//            fillRandom(dArrA, dArrB, dArrC);
//            double init = RunInfo.getRandom().nextDouble();
//            double s0 = testReductionAddAbsNegDouble(dArrA, dArrB, dArrC, dArrR0, init);
//            double s1 = referenceReductionAddAbsNegDouble(dArrA, dArrB, dArrC, dArrR1, init);
//            verify("testReductionAddAbsNegDouble sum", s0, s1);
//            verify("testReductionAddAbsNegDouble r", dArrR0, dArrR1);
//        }
//    }

    // ------------------------------------ VERIFICATION --------------------------------------------------

    static void verify(String name, int v0, int v1) {
        if (v0 != v1) {
            throw new RuntimeException(" Invalid " + name + " result: " + v0 + " != " + v1);
        }
    }

    static void verify(String name, int[] a0, int[] a1) {
        for (int i = 0; i < RANGE; i++) {
            if (a0[i] != a1[i]) {
                throw new RuntimeException(" Invalid " + name + " result: array[" + i + "]: " + a0[i] + " != " + a1[i]);
            }
        }
    }

    static void verify(String name, long v0, long v1) {
        if (v0 != v1) {
            throw new RuntimeException(" Invalid " + name + " result: " + v0 + " != " + v1);
        }
    }

    static void verify(String name, long[] a0, long[] a1) {
        for (int i = 0; i < RANGE; i++) {
            if (a0[i] != a1[i]) {
                throw new RuntimeException(" Invalid " + name + " result: array[" + i + "]: " + a0[i] + " != " + a1[i]);
            }
        }
    }

    static void verify(String name, float v0, float v1) {
        if (v0 != v1) {
            throw new RuntimeException(" Invalid " + name + " result: " + v0 + " != " + v1);
        }
    }

    static void verify(String name, float[] a0, float[] a1) {
        for (int i = 0; i < RANGE; i++) {
            if (a0[i] != a1[i]) {
                throw new RuntimeException(" Invalid " + name + " result: array[" + i + "]: " + a0[i] + " != " + a1[i]);
            }
        }
    }

    static void verify(String name, double v0, double v1) {
        if (v0 != v1) {
            throw new RuntimeException(" Invalid " + name + " result: " + v0 + " != " + v1);
        }
    }

    static void verify(String name, double[] a0, double[] a1) {
        for (int i = 0; i < RANGE; i++) {
            if (a0[i] != a1[i]) {
                throw new RuntimeException(" Invalid " + name + " result: array[" + i + "]: " + a0[i] + " != " + a1[i]);
            }
        }
    }

    // ------------------------------------ INITIALIZATION --------------------------------------------------

    static void fillRandom(int[] a, int[] b, int[] c) {
        fillRandom(a);
        fillRandom(b);
        fillRandom(c);
    }

    static void fillRandom(int[] arr) {
        for (int i = 0; i < RANGE; i++) {
            arr[i] = RunInfo.getRandom().nextInt();
        }
    }

    static int fillSmallPrime() {
        int[] primes = {3, 5, 7, 11, 13, 17, 23, 29};
        return primes[RunInfo.getRandom().nextInt(8)];
    }

    // Fill such that subtraction reveals small prime numbers
    static void fillSmallPrimeDiff(int[] a, int[] b) {
        for (int i = 0; i < RANGE; i++) {
            int r = RunInfo.getRandom().nextInt();
            a[i] = r;
            b[i] = r + fillSmallPrime();
        }
    }

    // Fill such that subtraction reveals base, except for a few bits flipped
    static void fillSpecialBytes(int[] a, int[] b, int base) {
        for (int i = 0; i < RANGE; i++) {
            a[i] = base;
        }
        // set at least 1 bit, but at most 31
        for (int i = 0; i < 31; i++) {
            int pos = RunInfo.getRandom().nextInt(32);
            int bit = 1 << pos;
            int j = RunInfo.getRandom().nextInt(RANGE);
            a[j] ^= bit; // set (xor / flip) the bit
	}
        for (int i = 0; i < RANGE; i++) {
            int r = RunInfo.getRandom().nextInt();
            a[i] += r;
            b[i] = r;
        }
    }

    static void fillRandom(long[] a, long[] b, long[] c) {
        fillRandom(a);
        fillRandom(b);
        fillRandom(c);
    }

    static void fillRandom(long[] arr) {
        for (int i = 0; i < RANGE; i++) {
            arr[i] = RunInfo.getRandom().nextLong();
        }
    }

    static void fillSmallPrimeDiff(long[] a, long[] b) {
        for (int i = 0; i < RANGE; i++) {
            long r = RunInfo.getRandom().nextLong();
            a[i] = r;
            b[i] = r + fillSmallPrime();
        }
    }

    // Fill such that subtraction reveals base, except for a few bits flipped
    static void fillSpecialBytes(long[] a, long[] b, long base) {
        for (int i = 0; i < RANGE; i++) {
            a[i] = base;
        }
        // set at least 1 bit, but at most 63
        for (int i = 0; i < 63; i++) {
            long pos = RunInfo.getRandom().nextInt(64);
            long bit = 1L << pos;
            int j = RunInfo.getRandom().nextInt(RANGE);
            a[j] ^= bit; // set (xor / flip) the bit
	}
        for (int i = 0; i < RANGE; i++) {
            long r = RunInfo.getRandom().nextLong();
            a[i] += r;
            b[i] = r;
        }
    }

    static void fillRandom(float[] a, float[] b, float[] c) {
        fillRandom(a);
        fillRandom(b);
        fillRandom(c);
    }

    static void fillRandom(float[] arr) {
        for (int i = 0; i < RANGE; i++) {
            arr[i] = RunInfo.getRandom().nextFloat();
        }
    }

    static void fillRandom(double[] a, double[] b, double[] c) {
        fillRandom(a);
        fillRandom(b);
        fillRandom(c);
    }

    static void fillRandom(double[] arr) {
        for (int i = 0; i < RANGE; i++) {
            arr[i] = RunInfo.getRandom().nextDouble();
        }
    }
}
