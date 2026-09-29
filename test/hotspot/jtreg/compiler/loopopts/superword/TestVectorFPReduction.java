/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8320725
 * @summary Ensure strictly ordered AddReductionVF/VD and MulReductionVF/VD nodes
            are generated when these operations are auto-vectorized
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestVectorFPReduction
 */

public class TestVectorFPReduction {

    final private static int SIZE = 1024;

    private static double[] da = new double[SIZE];
    private static double[] db = new double[SIZE];
    private static float[] fa = new float[SIZE];
    private static float[] fb = new float[SIZE];
    private static float fresult;
    private static double dresult;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.ADD_REDUCTION_VF},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    @IR(counts = {"requires_strict_order", ">=1", IRNode.ADD_REDUCTION_VF, ">=1"},
        failOn = {"no_strict_order"},
        applyIfCPUFeatureOr = {"sve", "true", "sse2", "true", "rvv", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    private static void testAddReductionVF() {
        float result = 1;
        for (int i = 0; i < SIZE; i++) {
            result += (fa[i] + fb[i]);
        }
        fresult += result;
    }

    @Test
    @IR(failOn = {IRNode.ADD_REDUCTION_VD},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    @IR(counts = {"requires_strict_order", ">=1", IRNode.ADD_REDUCTION_VD, ">=1"},
        failOn = {"no_strict_order"},
        applyIfCPUFeatureOr = {"sve", "true", "sse2", "true", "rvv", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    private static void testAddReductionVD() {
        double result = 1;
        for (int i = 0; i < SIZE; i++) {
            result += (da[i] + db[i]);
        }
        dresult += result;
    }

    @Test
    @IR(failOn = {IRNode.MUL_REDUCTION_VF},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    @IR(counts = {"requires_strict_order", ">=1", IRNode.MUL_REDUCTION_VF, ">=1"},
        failOn = {"no_strict_order"},
        applyIfCPUFeatureOr = {"sve", "true", "sse2", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    private static void testMulReductionVF() {
        float result = 1;
        for (int i = 0; i < SIZE; i++) {
            result *= (fa[i] + fb[i]);
        }
        fresult += result;
    }

    @Test
    @IR(failOn = {IRNode.MUL_REDUCTION_VD},
        applyIfCPUFeatureAnd = {"asimd", "true", "sve", "false"})
    @IR(counts = {"requires_strict_order", ">=1", IRNode.MUL_REDUCTION_VD, ">=1"},
        failOn = {"no_strict_order"},
        applyIfCPUFeatureOr = {"sve", "true", "sse2", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    private static void testMulReductionVD() {
        double result = 1;
        for (int i = 0; i < SIZE; i++) {
            result *= (da[i] + db[i]);
        }
        dresult += result;
    }
}
