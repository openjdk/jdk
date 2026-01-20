/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8351409
 * @summary Test the IR effects of reduction reassociation
 * @library /test/lib /
 * @run driver compiler.loopopts.TestReductionReassociation4
 */

package compiler.loopopts;

import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.verify.Verify;

public class TestReductionReassociation4 {
    private static int[] input_2 = new int[10000];

    static {
        Generators.G.fill(Generators.G.ints(), input_2);
    }

    private Object[] expected_2 = test_2();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0", "-XX:CompileCommand=dontinline,*::*dontinline*", "-XX:VerifyIterativeGVN=1000");
    }

    @Test
    @IR(
        counts = {IRNode.ADD_I, "= 4"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] test_2() {
        int result = Integer.MIN_VALUE;
        int result2 = Integer.MIN_VALUE;
        int i = 0;
        while (i < input_2.length) {
            int v0 = getArrayInt_dontinline(0, i);
            int v1 = getArrayInt_dontinline(1, i);
            int v2 = getArrayInt_dontinline(2, i);
            int v3 = getArrayInt_dontinline(3, i);
            int u0 = v0 + result;
            int u1 = v1 + u0;
            int u2 = v2 + u1;
            int u3 = v3 + u2;
            int t0 = v0 + v1;
            int t1 = v2 + t0;
            int t2 = v3 + t1;
            int t3 = result + t2;
            result = u3;
            result2 = t3;
            i = sum_dontinline(i, 4);
        }
        return asArray_dontinline(result, result2);
    }

    private static Object[] asArray_dontinline(int result, int result2) {
        return new Object[]{result, result2};
    }

    private static int sum_dontinline(int a, int b) {
        return a + b;
    }

    private static int getArrayInt_dontinline(int pos, int base) {
        return input_2[pos + base];
    }

    @Check(test = "test_2")
    public void check_2(Object[] results) {
        Verify.checkEQ(expected_2[0], results[0]);
        Verify.checkEQ(expected_2[1], results[1]);
        Verify.checkEQ(results[0], results[1]);
    }
}