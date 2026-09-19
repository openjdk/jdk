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
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyLoopOptimizations
 *                   -XX:CompileCommand=compileonly,${test.main.class}::*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

/*
 * Math.sqrt() is the only expensive node left in C2. Each method below is one
 * of the shapes hoist_expensive_node() has to get right: a Sqrt that can move
 * above the loop, one that cannot move at all, and two identical ones that are
 * commoned by process_expensive_nodes(). A wrong placement either fails loop
 * verification on a debug VM or changes the result.
 */

package compiler.loopopts;

public class TestExpensiveNodeHoisting {

    static final double[] VALUES = new double[100];

    static {
        for (int i = 0; i < VALUES.length; i++) {
            VALUES[i] = i;
        }
    }

    // Loop invariant, the Sqrt is hoisted above the loop.
    static double invariant(double x) {
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += Math.sqrt(x);
        }
        return sum;
    }

    // Depends on the loop body, the Sqrt stays where it is.
    static double variant(double[] a) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.sqrt(a[i]);
        }
        return sum;
    }

    // Same Sqrt in both branches, moved above the If.
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

    static void check(double result, double expected) {
        if (result != expected) {
            throw new RuntimeException("expected " + expected + " but got " + result);
        }
    }

    public static void main(String[] args) {
        double invariantResult = invariant(2.0);
        double variantResult = variant(VALUES);
        double bothBranchesResult = bothBranches(2.0);

        for (int i = 0; i < 20_000; i++) {
            check(invariant(2.0), invariantResult);
            check(variant(VALUES), variantResult);
            check(bothBranches(2.0), bothBranchesResult);
        }
    }
}
