/*
 * Copyright (c) 2024 Red Hat and/or its affiliates. All rights reserved.
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

package compiler.c2;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

/**
 * @test
 * @summary
 * @library /test/lib /
 * @run driver compiler.c2.TestBoolNodeGvn
 */
public class TestBoolNodeGvn {
    public static void main(String[] args) {
        TestFramework.run();
        testCorrectness();
    }

    /**
     * Test changing ((x & m) u<= m) or ((m & x) u<= m) to always true, same with ((x & m) u< m+1) and ((m & x) u< m+1)
     * The test is not applicable to x86 (32bit) for not having <code>Integer.compareUnsigned</code> intrinsified.
     */
    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = IRNode.CMP_U, phase = CompilePhase.AFTER_PARSING, applyIfPlatform = {"x86", "false"})
    public static boolean test(int x, int m) {
        return !(Integer.compareUnsigned((x & m), m) > 0) // assert in inversions to generates the pattern looking for
                & !(Integer.compareUnsigned((m & x), m) > 0)
                & Integer.compareUnsigned((x & m), m + 1) < 0
                & Integer.compareUnsigned((m & x), m + 1) < 0;
    }

    private static void testCorrectness() {
        int[] values = { 0, 1, 5, 8, 16, 42, 100, Integer.MAX_VALUE };

        for (int x : values) {
            for (int m : values) {
                if (!test(x, m)) {
                    throw new RuntimeException("Bad result for x = " + x + " and m = " + m + ", expected always true");
                }
            }
        }
    }
}
