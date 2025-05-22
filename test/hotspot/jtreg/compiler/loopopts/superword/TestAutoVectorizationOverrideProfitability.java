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

public class TestAutoVectorizationOverrideProfitability {

    public static int[] aI = new int[10_000];
    public static int[] rI = new int[10_000];
    public static float[] aF = new float[10_000];
    public static float[] rF = new float[10_000];

    static {
        for (int i = 0; i < aF.length; i++) {
            aI[i] = i;
            aF[i] = i;
        }
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
    @IR(applyIfCPUFeatureOr = {"avx", "true"},
        applyIf = {"SuperWordReductions", "true"},
        counts = {IRNode.ADD_REDUCTION_VF, ">= 1"})
    private static float simpleFloatReduction() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum += aF[i];
        }
        return sum;
    }

    @Check(test="simpleFloatReduction")
    public static void checkSimpleFloatReduction(float result) {
    }
}
