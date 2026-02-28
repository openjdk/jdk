/*
 * Copyright (c) 2026, IBM and/or its affiliates. All rights reserved.
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
 *
 */
package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8378713
 * @key randomness
 * @summary Math.pow(base, exp) should constant propagate
 * @library /test/lib /
 * @run driver compiler.c2.irTests.PowDNodeTests
 */
public class PowDNodeTests {
    public static final double b = Utils.getRandomInstance().nextDouble() * 100.0d;

    public static void main(String[] args) {
        TestFramework.run();
    }

    // Test 1: pow(constant, constant) -> constant (no "pow" call in IR)
    @Test
    @IR(failOn = {"pow"}, phase = CompilePhase.BEFORE_MATCHING)
//    @IR(counts = {IRNode.CON_I, "1"}) // TODO: no CON_D node?
    public double constantFolding() {
        return Math.pow(2.0, 10.0);  // should fold to 1024.0
    }

    // Test 2: pow(x, 0.0) -> 1.0
    @Test
    @IR(failOn = {"pow"}, phase = CompilePhase.BEFORE_MATCHING)
    public double expZero() {
        return Math.pow(b, 0.0);
    }

    // Test 3: pow(x, 1.0) -> x (identity)
    @Test
    @IR(failOn = {"pow"}, phase = CompilePhase.BEFORE_MATCHING)
    public double expOne() {
        return Math.pow(b, 1.0);
    }

    // Test 4: pow(x, 2.0) -> x * x
    @Test
    @IR(failOn = {"pow"}, phase = CompilePhase.BEFORE_MATCHING)
    @IR(counts = {IRNode.MUL_D, "1"})
    @Arguments(values = {Argument.RANDOM_EACH})
    public double expTwo(double x) {
        return Math.pow(x, 2.0);
    }

    // Test 5: non-constant exponent stays as call
    @Test
    @IR(counts = {"pow", "1"}, phase = CompilePhase.BEFORE_MATCHING)
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public double nonConstant(double x, double y) {
        return Math.pow(x, y);
    }

    // Test 6: late constant discovery (value becomes constant after loop opts)
    @Test
    @IR(failOn = {"pow"}, phase = CompilePhase.BEFORE_MATCHING)
//    @IR(counts = {IRNode.CON_I, "1"}) // TODO: no CON_D node?
    public double lateConstant() {
        double val = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = b;
            }
        }
        return Math.pow(val, 3.0);
        // After loop opts, val == q (constant), so pow(q, 3.0) folds
    }
}