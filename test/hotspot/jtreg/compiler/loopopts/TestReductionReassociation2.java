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
 * @run driver compiler.loopopts.TestReductionReassociation2
 */

package compiler.loopopts;

import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;

public class TestReductionReassociation2 {
    private static long[] input_2 = new long[10000];

    static {
        Generators.G.fill(Generators.G.longs(), input_2);
    }

    private Object[] expected_2 = test_2();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0", "-XX:CompileCommand=dontinline,*::*dontinline*", "-XX:VerifyIterativeGVN=1000");
    }

    @Test
    @IR(
        counts = {IRNode.ADD_L, "= 4"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] test_2() {
        long result = Long.MIN_VALUE;
        long result2 = Long.MIN_VALUE;
        for (int i = 0; i < input_2.length; i += 4) {
            long v0 = getArrayLong_dontinline(i + 0);
            long v1 = getArrayLong_dontinline(i + 1);
            long v2 = getArrayLong_dontinline(i + 2);
            long v3 = getArrayLong_dontinline(i + 3);
            long u0 = v0 + result;
            long u1 = v1 + u0;
            long u2 = v2 + u1;
            long u3 = v3 + u2;
            long t0 = v0 + v1;
            long t1 = v2 + t0;
            long t2 = v3 + t1;
            long t3 = result + t2;
            result = u3;
            result2 = t3;
        }
        return new Object[] {result, result2};
    }

    private static long getArrayLong_dontinline(int i) {
        return input_2[i];
    }

    @Check(test = "test_2")
    public void check_2(Object[] results) {
        Verify.checkEQ(expected_2[0], results[0]);
        Verify.checkEQ(expected_2[1], results[1]);
        Verify.checkEQ(results[0], results[1]);
    }
}