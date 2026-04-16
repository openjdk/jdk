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
 * @summary Test parallel IV replacement in multi-exit loops where the
 *          dom_lca of outside-loop uses is inside the loop (not sinkable).
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.loopopts.parallel_iv.TestParallelIvMultiExit
 */
public class TestParallelIvMultiExit {

    static volatile int condA;
    static volatile int condB;
    static int warmup;

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
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=3" })
    static int subStride1(int limit, int inc, int[] r) {
        int a = 0;
        for (int i = 0; i < limit; i++) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a -= inc;
        }
        return a;
    }

    @Run(test = "subStride1")
    static void runSubStride1() {
        toggleBreaks();
        int[] r = new int[2];
        int result = subStride1(100, 3, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(-(100 * 3), result);
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=3" })
    @IR(counts = { IRNode.URSHIFT_I, "=3" })
    static int addStride2(int limit, int inc, int[] r) {
        int a = 0;
        for (int i = 0; i < limit; i += 2) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a += inc;
        }
        return a;
    }

    @Run(test = "addStride2")
    static void runAddStride2() {
        toggleBreaks();
        int[] r = new int[2];
        int result = addStride2(100, 7, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(50 * 7, result);
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_I }, phase = CompilePhase.BEFORE_CLOOPS)
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=3" })
    static int addStrideNeg1(int start, int inc, int[] r) {
        int a = 0;
        for (int i = start; i > 0; i--) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a += inc;
        }
        return a;
    }

    @Run(test = "addStrideNeg1")
    static void runAddStrideNeg1() {
        toggleBreaks();
        int[] r = new int[2];
        int result = addStrideNeg1(100, 5, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(100 * 5, result);
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_I })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    static int addStride3ConstExactRatio(int limit, int[] r) {
        int a = 0;
        for (int i = 0; i < limit; i += 3) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a += 9;
        }
        return a;
    }

    @Run(test = "addStride3ConstExactRatio")
    static void runAddStride3ConstExactRatio() {
        toggleBreaks();
        int[] r = new int[2];
        int result = addStride3ConstExactRatio(99, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(33 * 9, result);
        }
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    @IR(counts = { IRNode.MUL_I, "=3" })
    static int addStride3NonConstBailout(int limit, int inc, int[] r) {
        int a = 0;
        for (int i = 0; i < limit; i += 3) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a += inc;
        }
        return a;
    }

    @Run(test = "addStride3NonConstBailout")
    static void runAddStride3NonConstBailout() {
        toggleBreaks();
        int[] r = new int[2];
        int result = addStride3NonConstBailout(99, 7, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(33 * 7, result);
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_I })
    @IR(counts = { IRNode.COUNTED_LOOP, "=1" })
    static int addStride3ConstNoExactRatio(int limit, int[] r) {
        int a = 0;
        for (int i = 0; i < limit; i += 3) {
            if (condA != 0) { r[0] = a; break; }
            if (condB != 0) { r[1] = a; break; }
            a += 7;
        }
        return a;
    }

    @Run(test = "addStride3ConstNoExactRatio")
    static void runAddStride3ConstNoExactRatio() {
        toggleBreaks();
        int[] r = new int[2];
        int result = addStride3ConstNoExactRatio(99, r);
        if (condA == 0 && condB == 0) {
            Asserts.assertEQ(33 * 7, result);
        }
    }
}
