/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8345766
 * @summary Test that Ideal transformations of ModFNode are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.ModFNodeTests
 */
public class ModFNodeTests {
    public static final float q = Utils.getRandomInstance().nextFloat() * 100.0f;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"constant", "notConstant", "veryNotConstant",
            "unusedResult",
            "repeatedlyUnused",
            "unusedResultAfterLoopOpt1",
            "unusedResultAfterLoopOpt2",
            "unusedResultAfterLoopOpt3",
    })
    public void runMethod() {
        Asserts.assertEQ(constant(), q % 72.0f % 30.0f);
        Asserts.assertEQ(alsoConstant(), q % 31.432f);
        Asserts.assertTrue(Float.isNaN(nanLeftConstant()));
        Asserts.assertTrue(Float.isNaN(nanRightConstant()));
        Asserts.assertEQ(notConstant(37.5f), 37.5f % 32.0f);
        Asserts.assertEQ(veryNotConstant(531.25f, 14.5f), 531.25f % 32.0f % 14.5f);
        unusedResult(1.1f, 2.2f);
        repeatedlyUnused(1.1f, 2.2f);
        Asserts.assertEQ(unusedResultAfterLoopOpt1(1.1f, 2.2f), 0.f);
        Asserts.assertEQ(unusedResultAfterLoopOpt2(1.1f, 2.2f), 0.f);
        Asserts.assertEQ(unusedResultAfterLoopOpt3(1.1f, 2.2f), 0.f);
    }

    @Test
    @IR(failOn = {"frem"}, phase = CompilePhase.BEFORE_MATCHING)
    @IR(counts = {IRNode.CON_F, "1"})
    public float constant() {
        // All constants available during parsing
        return q % 72.0f % 30.0f;
    }

    @Test
    @IR(failOn = {"frem"}, phase = CompilePhase.BEFORE_MATCHING)
    @IR(counts = {IRNode.CON_F, "1"})
    public float alsoConstant() {
        // Make sure value is only available after second loop opts round
        float val = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = q;
            }
        }
        return val % 31.432f;
    }

    @Test
    @IR(failOn = {"frem"}, phase = CompilePhase.BEFORE_MATCHING)
    @IR(counts = {IRNode.CON_F, "1"})
    public float nanLeftConstant() {
        // Make sure value is only available after second loop opts round
        float val = 134.18f;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = Float.NaN;
            }
        }
        return 56.234f % (val % 31.432f);
    }

    @Test
    @IR(failOn = {"frem"}, phase = CompilePhase.BEFORE_MATCHING)
    @IR(counts = {IRNode.CON_F, "1"})
    public float nanRightConstant() {
        // Make sure value is only available after second loop opts round
        float val = 134.18f;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = Float.NaN;
            }
        }
        return 56.234f % (31.432f % val);
    }

    @Test
    @IR(counts = {"frem", "1"}, phase = CompilePhase.BEFORE_MATCHING)
    @IR(counts = {IRNode.CON_F, "1"})
    public float notConstant(float x) {
        return x % 32.0f;
    }

    @Test
    @IR(counts = {"frem", "2"}, phase = CompilePhase.BEFORE_MATCHING)
    @IR(counts = {IRNode.CON_F, "1"})
    public float veryNotConstant(float x, float y) {
        return x % 32.0f % y;
    }

    @Test
    @IR(failOn = IRNode.MOD_F, phase = CompilePhase.ITER_GVN1)
    @IR(counts = {IRNode.MOD_F, "1"}, phase = CompilePhase.AFTER_PARSING)
    public void unusedResult(float x, float y) {
        float unused = x % y;
    }

    @Test
    @IR(failOn = IRNode.MOD_F, phase = CompilePhase.ITER_GVN1)
    @IR(counts = {IRNode.MOD_F, "1"}, phase = CompilePhase.AFTER_PARSING)
    public void repeatedlyUnused(float x, float y) {
        float unused = 1.f;
        for (int i = 0; i < 100_000; i++) {
            unused = x % y;
        }
    }

    // The difference between unusedResultAfterLoopOpt1 and unusedResultAfterLoopOpt2
    // is that they exercise a slightly different reason why the node is being removed,
    // and thus a different execution path. In unusedResultAfterLoopOpt1 the modulo is
    // used in the traps of the parse predicates. In unusedResultAfterLoopOpt2, it is not.
    @Test
    @IR(counts = {IRNode.MOD_F, "1"}, phase = CompilePhase.ITER_GVN2)
    @IR(failOn = IRNode.MOD_F, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    public float unusedResultAfterLoopOpt1(float x, float y) {
        float unused = x % y;

        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);

        if (b == 78) { // dead
            return unused;
        }
        return 0.f;
    }

    @Test
    @IR(counts = {IRNode.MOD_F, "1"}, phase = CompilePhase.AFTER_CLOOPS)
    @IR(failOn = IRNode.MOD_F, phase = CompilePhase.PHASEIDEALLOOP1)
    public float unusedResultAfterLoopOpt2(float x, float y) {
        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);

        float unused = x % y;

        if (b == 78) { // dead
            return unused;
        }
        return 0.f;
    }

    @Test
    @IR(counts = {IRNode.MOD_F, "2"}, phase = CompilePhase.AFTER_CLOOPS)
    @IR(failOn = IRNode.MOD_F, phase = CompilePhase.PHASEIDEALLOOP1)
    public float unusedResultAfterLoopOpt3(float x, float y) {
        float unused = x % y;

        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);

        int other = (b - 77) * (int)(x % y % 1.f);
        return (float)other;
    }
}
