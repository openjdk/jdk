/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @run driver ${test.main.class}
 */

package compiler.loopopts;

import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;

public class TestReductionReassociation {
    private static long[] input = new long[10000];

    static {
        Generators.G.fill(Generators.G.longs(), input);
    }

    private Object[] expectedNonPowerOfTwoBatch = testNonPowerOfTwoBatch();

    public static void main(String[] args) {
        TestFramework.runWithFlags(
            "-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0",
            "-XX:+UseNewCode",
            "-XX:CompileCommand=dontinline,*::*dontinline*", "-XX:VerifyIterativeGVN=1000"
        );
    }

    @Test
    @IR(counts = {IRNode.MAX_L, "= 5"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] testNonPowerOfTwoBatch() {
        long result = Long.MIN_VALUE;
        long result2 = Long.MIN_VALUE;
        for (int i = 0; i < input.length; i += 5) {
            long v0 = input[i + 0];
            long v1 = input[i + 1];
            long v2 = input[i + 2];
            long v3 = input[i + 3];
            long v4 = input[i + 4];
            long u0 = Long.max(v0, result);
            long u1 = Long.max(v1, u0);
            long u2 = Long.max(v2, u1);
            long u3 = Long.max(v3, u2);
            long u4 = Long.max(v4, u3);
            long t0 = Long.max(v0, v1);
            long t1 = Long.max(v2, t0);
            long t2 = Long.max(v3, t1);
            long t3 = Long.max(v4, t2);
            long t4 = Long.max(result, t3);
            result = u4;
            result2 = t4;
        }
        return new Object[]{result, result2};
    }

    @Check(test = "testNonPowerOfTwoBatch")
    public void checkNonPowerOfTwoBatch(Object[] results) {
        Verify.checkEQ(expectedNonPowerOfTwoBatch[0], results[0]);
        Verify.checkEQ(expectedNonPowerOfTwoBatch[1], results[1]);
        Verify.checkEQ(results[0], results[1]);
    }

    private Object[] expectedUseIntermediate = testUseIntermediate();

    @Test
    @IR(counts = {IRNode.MAX_L, "= 8"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] testUseIntermediate() {
        long result = Long.MIN_VALUE;
        long result2 = Long.MIN_VALUE;
        for (int i = 0; i < input.length; i += 8) {
            long v0 = input[i + 0];
            long v1 = input[i + 1];
            long v2 = input[i + 2];
            long v3 = input[i + 3];
            long v4 = input[i + 4];
            long v5 = input[i + 5];
            long v6 = input[i + 6];
            long v7 = input[i + 7];

            var u0 = Math.max(v0, result);
            var u1 = Math.max(v1, u0);
            var u2 = Math.max(v2, u1);
            var u3 = Math.max(v3, u2);
            if (u3 == input.hashCode()) {
                System.out.print("");
            }
            var u4 = Math.max(v4, u3);
            var u5 = Math.max(v5, u4);
            var u6 = Math.max(v6, u5);
            var u7 = Math.max(v7, u6);

            long t0 = Long.max(v0, v1);
            long t1 = Long.max(v2, t0);
            long t2 = Long.max(v3, t1);
            long t3 = Long.max(result, t2);
            if (t3 == input.hashCode()) {
                System.out.print("");
            }
            long t4 = Long.max(v4, t3);
            long t5 = Long.max(v5, t4);
            long t6 = Long.max(v6, t5);
            long t7 = Long.max(v7, t6);

            result = u7;
            result2 = t7;
        }
        return new Object[]{result, result2};
    }

    @Check(test = "testUseIntermediate")
    public void checkUseIntermediate(Object[] results) {
        Verify.checkEQ(expectedUseIntermediate[0], results[0]);
        Verify.checkEQ(expectedUseIntermediate[1], results[1]);
        Verify.checkEQ(results[0], results[1]);
    }

}