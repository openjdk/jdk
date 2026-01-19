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
 * @run driver compiler.loopopts.TestReductionReassociation3
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

public class TestReductionReassociation3 {
    private static long[] input_122 = new long[10000];

    static {
        Generators.G.fill(Generators.G.longs(), input_122);
    }

    private Object[] expected_122 = test_122();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0", "-XX:CompileCommand=dontinline,*::*dontinline*", "-XX:VerifyIterativeGVN=1000");
    }

    @Test
    @IR(
        counts = {IRNode.MAX_L, "= 4"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] test_122() {
        // i = max(a, max(b, max(c, max(d, i))))
        // i' = max(i, max(a, max(b, max(c, d))))

        // result = max(v3, max(v2, max(v1, max(v0, result))))
        // result2 = max(result, max(v3, max(v2, max(v1, v0))))

        long result = Long.MIN_VALUE;
        long result2 = Long.MIN_VALUE;
        for (int i = 0; i < input_122.length; i += 4) {
            long v0 = input_122[i + 0];
            long v1 = input_122[i + 1];
            long v2 = input_122[i + 2];
            long v3 = input_122[i + 3];
            long u0 = Long.max(v0, result);
            long u1 = Long.max(v1, u0);
            long u2 = Long.max(v2, u1);
            long u3 = Long.max(v3, u2);
            long t0 = Long.max(v0, v1);
            long t1 = Long.max(v2, t0);
            long t2 = Long.max(v3, t1);
            long t3 = Long.max(result, t2);
            result = u3;
            result2 = t3;
        }
        return new Object[] {result, result2};
    }

    @Check(test = "test_122")
    public void check_122(Object[] results) {
        Verify.checkEQ(expected_122[0], results[0]);
        Verify.checkEQ(expected_122[1], results[1]);
        Verify.checkEQ(results[0], results[1]);
    }
}