/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8391724
 * @summary Expensive nodes are now hoisted by an explicit step instead of by
 *          get_early_ctrl(). Check that they still end up in the right place.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

/*
 * Math.sqrt() is the only expensive node left in C2. Each method below is one
 * of the shapes hoist_expensive_node() has to get right: a Sqrt that can move
 * above the loop, one that cannot move at all, and two identical ones that are
 * commoned by process_expensive_nodes(). A wrong placement either fails loop
 * verification on a debug VM or changes the result.
 *
 * Compile::cleanup_expensive_nodes() clears the control input of a Sqrt that
 * is the only one in its compilation, which leaves nothing to hoist. The
 * methods with two Sqrts on different inputs keep their control inputs, so
 * they go through hoist_expensive_node().
 *
 * A Sqrt directly on the loop head is already moved above the loop while
 * parsing, and loop opts such as partial peeling or unswitching move others
 * out, so most shapes look the same with or without hoist_expensive_node().
 * loopThenAfter() is one where only the hoisting gives the expected IR.
 */

package compiler.loopopts;

import compiler.lib.ir_framework.*;

public class TestExpensiveNodeHoisting {

    static final double[] VALUES = new double[100];

    static {
        for (int i = 0; i < VALUES.length; i++) {
            VALUES[i] = i;
        }
    }

    // Computed by the interpreter, before any of the methods is compiled.
    static final double INVARIANT = invariant(2.0);
    static final double VARIANT = variant(VALUES);
    static final double BOTH_BRANCHES = bothBranches(2.0);
    static final double INVARIANT_TWO = invariantTwo(2.0, 3.0);
    static final double VARIANT_TWO = variantTwo(VALUES, 3.0);
    static final double MIXED = mixed(VALUES, 2.0);
    static final double BOTH_BRANCHES_TWO = bothBranchesTwo(2.0, 3.0);
    static final double LOOP_THEN_AFTER = loopThenAfter(VALUES, 2.0);

    public static void main(String[] args) {
        TestFramework.run();
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+VerifyLoopOptimizations");
    }

    static void check(double result, double expected) {
        if (result != expected) {
            throw new RuntimeException("expected " + expected + " but got " + result);
        }
    }

    // Loop invariant, the Sqrt is hoisted above the loop.
    @Test
    static double invariant(double x) {
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += Math.sqrt(x);
        }
        return sum;
    }

    @Run(test = "invariant")
    static void runInvariant() {
        check(invariant(2.0), INVARIANT);
    }

    // Depends on the loop body, the Sqrt stays where it is.
    @Test
    static double variant(double[] a) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.sqrt(a[i]);
        }
        return sum;
    }

    @Run(test = "variant")
    static void runVariant() {
        check(variant(VALUES), VARIANT);
    }

    // Same Sqrt in both branches, moved above the If.
    @Test
    static double bothBranches(double x) {
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            if ((i & 1) == 0) {
                sum += Math.sqrt(x);
            } else {
                sum -= Math.sqrt(x);
            }
        }
        return sum;
    }

    @Run(test = "bothBranches")
    static void runBothBranches() {
        check(bothBranches(2.0), BOTH_BRANCHES);
    }

    // Two loop invariant Sqrts, both hoisted above the loop.
    @Test
    static double invariantTwo(double x, double y) {
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += Math.sqrt(x) + Math.sqrt(y);
        }
        return sum;
    }

    @Run(test = "invariantTwo")
    static void runInvariantTwo() {
        check(invariantTwo(2.0, 3.0), INVARIANT_TWO);
    }

    // Two Sqrts depending on the loop body, both stay where they are.
    @Test
    static double variantTwo(double[] a, double y) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.sqrt(a[i]) + Math.sqrt(a[i] + y);
        }
        return sum;
    }

    @Run(test = "variantTwo")
    static void runVariantTwo() {
        check(variantTwo(VALUES, 3.0), VARIANT_TWO);
    }

    // One Sqrt is hoisted above the loop, the other one stays.
    @Test
    static double mixed(double[] a, double x) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.sqrt(x) + Math.sqrt(a[i]);
        }
        return sum;
    }

    @Run(test = "mixed")
    static void runMixed() {
        check(mixed(VALUES, 2.0), MIXED);
    }

    // A different Sqrt in each branch, neither can move above the If.
    @Test
    static double bothBranchesTwo(double x, double y) {
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            if ((i & 1) == 0) {
                sum += Math.sqrt(x);
            } else {
                sum -= Math.sqrt(y);
            }
        }
        return sum;
    }

    @Run(test = "bothBranchesTwo")
    static void runBothBranchesTwo() {
        check(bothBranchesTwo(2.0, 3.0), BOTH_BRANCHES_TWO);
    }

    // The Sqrt in the loop is hoisted above the loop. Its control then
    // dominates the one of the Sqrt after the loop, and
    // process_expensive_nodes() commons them. Without the hoisting, the
    // zero trip guard of the loop keeps the two controls unrelated and both
    // Sqrts stay.
    @Test
    @IR(counts = {IRNode.SQRT_D, "1"})
    static double loopThenAfter(double[] a, double x) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * Math.sqrt(x);
        }
        return sum + Math.sqrt(x);
    }

    @Run(test = "loopThenAfter")
    static void runLoopThenAfter() {
        check(loopThenAfter(VALUES, 2.0), LOOP_THEN_AFTER);
    }
}
