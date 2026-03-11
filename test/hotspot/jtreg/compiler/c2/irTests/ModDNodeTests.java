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
 * @key randomness
 * @summary Test that Ideal transformations of ModDNode are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.ModDNodeTests
 */
public class ModDNodeTests {
    public static final double q = Utils.getRandomInstance().nextDouble() * 100.0d;

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
        Asserts.assertEQ(constant(), q % 72.0d % 30.0d);
        Asserts.assertEQ(alsoConstant(), q % 31.432d);
        Asserts.assertTrue(Double.isNaN(nanLeftConstant()));
        Asserts.assertTrue(Double.isNaN(nanRightConstant()));
        Asserts.assertEQ(notConstant(37.5d), 37.5d % 32.0d);
        Asserts.assertEQ(veryNotConstant(531.25d, 14.5d), 531.25d % 32.0d % 14.5d);
        unusedResult(1.1d, 2.2d);
        repeatedlyUnused(1.1d, 2.2d);
        Asserts.assertEQ(unusedResultAfterLoopOpt1(1.1d, 2.2d), 0.d);
        Asserts.assertEQ(unusedResultAfterLoopOpt2(1.1d, 2.2d), 0.d);
        Asserts.assertEQ(unusedResultAfterLoopOpt3(1.1d, 2.2d), 0.d);
    }

    // Note: we used to check for ConD nodes in the IR. But that is a bit brittle:
    // Constant nodes can appear during IR transformations, and then lose their outputs.
    // During IGNV, the constants stay in the graph even if they lose the inputs. But
    // CCP cleans them out because they are not in the useful set. So for now, we do not
    // rely on any constant counting, just on counting the operation nodes.

    @Test
    @IR(counts = {IRNode.MOD_D, "2"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public double constant() {
        // All constants available during parsing
        return q % 72.0d % 30.0d;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.PHASEIDEALLOOP1) // Only constant fold after some loop opts
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public double alsoConstant() {
        // Make sure value is only available after second loop opts round
        double val = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = q;
            }
        }
        return val % 31.432d;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "2"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "2"},
        phase = CompilePhase.PHASEIDEALLOOP1) // Only constant fold after some loop opts
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public double nanLeftConstant() {
        // Make sure value is only available after second loop opts round
        double val = 134.18d;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = Double.NaN;
            }
        }
        return 56.234d % (val % 31.432d);
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "2"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "2"},
        phase = CompilePhase.PHASEIDEALLOOP1) // Only constant fold after some loop opts
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public double nanRightConstant() {
        // Make sure value is only available after second loop opts round
        double val = 134.18d;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = Double.NaN;
            }
        }
        return 56.234d % (31.432d % val);
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {".*CallLeaf.*drem.*", "1"},
        phase = CompilePhase.BEFORE_MATCHING) // no constant folding
    public double notConstant(double x) {
        return x % 32.0d;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "2"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {".*CallLeaf.*drem.*", "2"},
        phase = CompilePhase.BEFORE_MATCHING) // no constant folding
    public double veryNotConstant(double x, double y) {
        return x % 32.0d % y;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "0"},
        phase = CompilePhase.ITER_GVN1) // IGVN removes unused nodes
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public void unusedResult(double x, double y) {
        double unused = x % y;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "0"},
        phase = CompilePhase.ITER_GVN1) // IGVN removes unused nodes
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public void repeatedlyUnused(double x, double y) {
        double unused = 1.d;
        for (int i = 0; i < 100_000; i++) {
            unused = x % y;
        }
    }

    // The difference between unusedResultAfterLoopOpt1 and unusedResultAfterLoopOpt2
    // is that they exercise a slightly different reason why the node is being removed,
    // and thus a different execution path. In unusedResultAfterLoopOpt1 the modulo is
    // used in the traps of the parse predicates. In unusedResultAfterLoopOpt2, it is not.
    @Test
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.ITER_GVN2)
    @IR(counts = {IRNode.MOD_D, "0"},
        phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public double unusedResultAfterLoopOpt1(double x, double y) {
        double unused = x % y;

        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);

        if (b == 78) { // dead
            return unused;
        }
        return 0.d;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "1"},
        phase = CompilePhase.AFTER_CLOOPS)
    @IR(counts = {IRNode.MOD_D, "0"},
        phase = CompilePhase.PHASEIDEALLOOP1)
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public double unusedResultAfterLoopOpt2(double x, double y) {
        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);

        double unused = x % y;

        if (b == 78) { // dead
            return unused;
        }
        return 0.d;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "3"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "2"},
        phase = CompilePhase.AFTER_CLOOPS) // drop the useless one
    @IR(counts = {IRNode.MOD_D, "0"},
        phase = CompilePhase.PHASEIDEALLOOP1) // drop the rest
    @IR(counts = {".*CallLeaf.*drem.*", "0"},
        phase = CompilePhase.BEFORE_MATCHING)
    public double unusedResultAfterLoopOpt3(double x, double y) {
        double unused = x % y;

        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);

        int other = (b - 77) * (int)(x % y % 1.d);
        return (double)other;
    }
}
