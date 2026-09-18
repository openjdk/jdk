/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8346177
 * @summary Test parallel IV replacement in multi-exit loops, both where the result is sinkable out of
 *          the loop and where it is not.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.loopopts.parallel_iv.TestParallelIvMultiExit
 */
public class TestParallelIvMultiExit {

    static volatile int condA;
    static volatile int condB;
    static int warmup;
    static int[] ra = new int[1];
    static int[] rb = new int[1];

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.setDefaultWarmup(0).addFlags("-XX:LoopUnrollLimit=0").start();
    }

    private static void toggleBreaks() {
        warmup++;
        condA = (warmup % 3 == 0) ? 1 : 0;
        condB = (warmup % 5 == 0) ? 1 : 0;
    }

    @Test
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=3" })
    static int addStride1(int limit, int inc, int[] r) {
        int a = 0;
        for (int i = 0; i < limit; i++) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a += inc;
        }
        return a;
    }

    @Run(test = "addStride1")
    static void runAddStride1() {
        toggleBreaks();
        int[] r = new int[2];
        int result = addStride1(100, 3, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(100 * 3, result);
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_I })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.LSHIFT_I, "=3" })
    static int addStride3ExactRatioNotSinkable(int limit) {
        int a = 0;
        for (int i = 0; i < limit; i += 3) {
            if (condA != 0) { ra[0] = a; return 1; }
            if (condB != 0) { rb[0] = a; return 2; }
            a += 9;
        }
        return a;
    }

    @Run(test = "addStride3ExactRatioNotSinkable")
    static void runAddStride3ExactRatioNotSinkable() {
        toggleBreaks();
        int result = addStride3ExactRatioNotSinkable(99);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(33 * 9, result);
        }
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=3" })
    static int addStride3Sinkable(int limit, int inc, int[] r) {
        int a = 0;
        for (int i = 0; i < limit; i += 3) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a += inc;
        }
        return a;
    }

    @Run(test = "addStride3Sinkable")
    static void runAddStride3Sinkable() {
        toggleBreaks();
        int[] r = new int[2];
        int result = addStride3Sinkable(99, 7, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(33 * 7, result);
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_I, IRNode.LSHIFT_I })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    static int addStride3NoExactRatioNotSinkable(int limit) {
        int a = 0;
        for (int i = 0; i < limit; i += 3) {
            if (condA != 0) { ra[0] = a; return 1; }
            if (condB != 0) { rb[0] = a; return 2; }
            a += 7;
        }
        return a;
    }

    @Run(test = "addStride3NoExactRatioNotSinkable")
    static void runAddStride3NoExactRatioNotSinkable() {
        toggleBreaks();
        int result = addStride3NoExactRatioNotSinkable(99);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(33 * 7, result);
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_I })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    static int addStride3NonConstNotSinkable(int limit, int inc) {
        int a = 0;
        for (int i = 0; i < limit; i += 3) {
            if (condA != 0) { ra[0] = a; return 1; }
            if (condB != 0) { rb[0] = a; return 2; }
            a += inc;
        }
        return a;
    }

    @Run(test = "addStride3NonConstNotSinkable")
    static void runAddStride3NonConstNotSinkable() {
        toggleBreaks();
        int result = addStride3NonConstNotSinkable(99, 7);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(33 * 7, result);
        }
    }
}
