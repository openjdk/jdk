/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8357530
 * @summary Test the effect of AutoVectorizationOverrideProfitability.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAutoVectorizationOverrideProfitability
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestAutoVectorizationOverrideProfitability {
    public static final Generator<Integer> GEN_I = G.ints();
    public static final Generator<Float>   GEN_F = G.floats();

    public static int[] aI = new int[10_000];
    public static int[] rI = new int[10_000];
    public static float[] aF = new float[10_000];
    public static float[] rF = new float[10_000];

    static {
        G.fill(GEN_I, aI);
        G.fill(GEN_F, aF);
    }

    public static void main(String[] args) throws Exception {
        // Do not vectorize, even if profitable.
        TestFramework.runWithFlags("-XX:+UnlockDiagnosticVMOptions", "-XX:AutoVectorizationOverrideProfitability=0");

        // Normal run, i.e. with normal heuristic. In some cases this vectorizes, in some not.
        // By default, we have AutoVectorizationOverrideProfitability=1
        TestFramework.run();

        // Vectorize even if not profitable.
        TestFramework.runWithFlags("-XX:+UnlockDiagnosticVMOptions", "-XX:AutoVectorizationOverrideProfitability=2");
    }

    public static final float GOLD_SIMPLE_FLOAT_REDUCTION = simpleFloatReduction();

    @Test
    @Warmup(10)
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 2"},
        counts = {IRNode.ADD_REDUCTION_VF, "> 0"})
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "< 2"},
        counts = {IRNode.ADD_REDUCTION_VF, "= 0"})
    // The simple float reduction is not profitable. We need to sequentially
    // add up the values, and so we cannot move the reduction out of the loop.
    private static float simpleFloatReduction() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum += aF[i];
        }
        return sum;
    }

    @Check(test="simpleFloatReduction")
    public static void checkSimpleFloatReduction(float result) {
        Verify.checkEQ(GOLD_SIMPLE_FLOAT_REDUCTION, result);
    }

    static { simpleFloatCopy(); }
    public static final float[] GOLD_SIMPLE_FLOAT_COPY = rF.clone();

    @Test
    @Warmup(10)
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"},
        counts = {IRNode.LOAD_VECTOR_F, "> 0"})
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"},
        counts = {IRNode.LOAD_VECTOR_F, "= 0"})
    // The simple float copy is always profitable.
    private static void simpleFloatCopy() {
        for (int i = 0; i < aF.length; i++) {
            rF[i] = aF[i];
        }
    }

    @Check(test="simpleFloatCopy")
    public static void checkSimpleFloatCopy() {
        Verify.checkEQ(GOLD_SIMPLE_FLOAT_COPY, rF);
    }

    public static final int GOLD_SIMPLE_INT_REDUCTION = simpleIntReduction();

    @Test
    @Warmup(10)
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 2"},
        counts = {IRNode.ADD_REDUCTION_VI, "> 0", IRNode.ADD_VI, "> 0"})
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "< 2"},
        counts = {IRNode.ADD_REDUCTION_VI, "= 0", IRNode.ADD_VI, "= 0"})
    // Current heuristics say that this simple int reduction is not profitable.
    // But it would actually be profitable, since we are able to move the
    // reduction out of the loop (we can reorder the reduction). When moving
    // the reduction out of the loop, we instead accumulate with a simple
    // ADD_VI inside the loop.
    // See: JDK-8307516 JDK-8345044
    private static int simpleIntReduction() {
        int sum = 0;
        for (int i = 0; i < aI.length; i++) {
            sum += aI[i];
        }
        return sum;
    }

    @Check(test="simpleIntReduction")
    public static void checkSimpleIntReduction(int result) {
        Verify.checkEQ(GOLD_SIMPLE_INT_REDUCTION, result);
    }

    static { simpleIntCopy(); }
    public static final int[] GOLD_SIMPLE_INT_COPY = rI.clone();

    @Test
    @Warmup(10)
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"},
        counts = {IRNode.LOAD_VECTOR_I, "> 0"})
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"},
        counts = {IRNode.LOAD_VECTOR_I, "= 0"})
    // The simple int copy is always profitable.
    private static void simpleIntCopy() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = aI[i];
        }
    }

    @Check(test="simpleIntCopy")
    public static void checkSimpleIntCopy() {
        Verify.checkEQ(GOLD_SIMPLE_INT_COPY, rI);
    }
}
