/*
 * Copyright 2026, Kerem Kat. All Rights Reserved.
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


package compiler.loopopts.parallel_iv;

import compiler.lib.ir_framework.*;
import compiler.lib.generators.RestrictableGenerator;
import jdk.test.lib.Asserts;

import java.util.Objects;

import static compiler.lib.generators.Generators.G;

/**
 * @test
 * @bug 8346177
 * @key randomness
 * @summary test parallel IV replacement with a CastII between the phi and its increment
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestParallelIvCast {

    private static final RestrictableGenerator<Integer> TRIP_COUNT = G.uniformInts(0, 10000);

    private static boolean flag;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.setDefaultWarmup(0).addFlags("-XX:LoopMaxUnroll=0").start();
    }

    @Test
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int rangeCheckCast(int stop, int inc, int length) {
        int a = 0;
        for (int i = 0; i < stop; i++) {
            a = Objects.checkIndex(a, length) + inc;
        }
        return a;
    }

    @Run(test = "rangeCheckCast")
    private static void runRangeCheckCast() {
        int s = TRIP_COUNT.next();
        int inc = G.uniformInts(0, 1000).next();
        Asserts.assertEQ(s * inc, rangeCheckCast(s, inc, Integer.MAX_VALUE));
    }

    // The loop is unswitched on flag. In the flag == true copy, the CastII is pinned on the loop head.
    @Test
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(counts = { IRNode.MUL_I, "=1" })
    private static int loopHeadCast(int a, int stop) {
        int b = 0;
        for (int i = 0; i < stop; i++) {
            for (int j = 1; j < 5; j++) {
                a -= 11;
                b -= a;
                if (flag) {
                    break;
                }
            }
        }
        return b;
    }

    @Run(test = "loopHeadCast")
    private static void runLoopHeadCast() {
        int s = TRIP_COUNT.next();
        int a = G.ints().next();
        flag = false;
        Asserts.assertEQ(loopHeadCastExpected(a, 4 * s), loopHeadCast(a, s));
        flag = true;
        Asserts.assertEQ(loopHeadCastExpected(a, s), loopHeadCast(a, s));
    }

    private static int loopHeadCastExpected(int a, int iters) {
        return 11 * (iters * (iters + 1) / 2) - iters * a;
    }
}
