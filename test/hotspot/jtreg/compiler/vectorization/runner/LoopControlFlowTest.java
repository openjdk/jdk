/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

/*
 * @test
 * @summary Vectorization test on simple control flow in loop
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopControlFlowTest
 *
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class LoopControlFlowTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private int[] a;
    private int[] b;
    private boolean invCond;

    public LoopControlFlowTest() {
        a = new int[SIZE];
        b = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = i + 80000;;
            b[i] = 80 * i;
        }
        Random ran = new Random(505050);
        invCond = (ran.nextInt() % 2 == 0);
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] loopInvariantCondition() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            if (invCond) {
                res[i] = a[i] + b[i];
            } else {
                res[i] = a[i] - b[i];
            }
        }
        return res;
    }

    @Test
    public int[] arrayElementCondition() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            if (b[i] > 10000) {
                res[i] = a[i] + b[i];
            } else {
                res[i] = a[i] - b[i];
            }
        }
        return res;
    }

    @Test
    // Note that this loop cannot be vectorized due to early break.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public int conditionalBreakReduction() {
        int sum = 0, i = 0;
        for (i = 0; i < SIZE; i++) {
            sum += i;
            if (invCond) break;
        }
        return i;
    }
}
