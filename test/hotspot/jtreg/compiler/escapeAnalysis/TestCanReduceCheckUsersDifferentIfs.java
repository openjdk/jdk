/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8343380
 * @summary Test that can_reduce_check_users() can handle different If nodes and that we bail out properly if it's not
 *          an actual IfNode.
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.escapeAnalysis.TestCanReduceCheckUsersDifferentIfs::test*
 *                   -Xcomp compiler.escapeAnalysis.TestCanReduceCheckUsersDifferentIfs
 */

package compiler.escapeAnalysis;

public class TestCanReduceCheckUsersDifferentIfs {
    static int iFld, iFld2;
    static boolean flag;

    public static void main(String[] args) {
        // Make sure classes are loaded.
        new B();
        new C();
        testParsePredicate();
        testOuterStripMinedLoopEnd();
    }

    static void testOuterStripMinedLoopEnd() {
        // (1) phi1 for a: phi(CheckCastPP(B), CheckCastPP(c)) with type A:NotNull
        A a = flag ? new B() : new C();

        // (4) Loop removed in PhaseIdealLoop before EA and we know that x == 77.
        int x = 77;
        int y = 0;
        do {
            x--;
            y++;
        } while (x > 0);

        // (L)
        for (int i = 0; i < 100; i++) {
            iFld += 34;
        }
        // (6) CastPP(phi1) ends up at IfFalse of OuterStripMinedLoopEnd of loop (L).
        // (7) EA tries to reduce phi1(CheckCastPP(B), CheckCastPP(c)) and looks at
        //     OuterStripMinedLoopEnd and asserts that if it's not an IfNode that it has
        //     an OpaqueConstantBool which obviously is not the case and the assert fails.

        // (5) Found to be false after PhaseIdealLoop before EA and is folded away.
        if (y == 76) {
            a = (B) a; // (2) a = CheckCastPP(phi1)
        }
        // (3) phi2 for a: phi(if, else) = phi(CheckCastPP(phi1), phi1)
        //     phi(CheckCastPP(phi1), phi1) is replaced in PhiNode::Ideal with a CastPP:
        //     a = CastPP(phi1) with type A:NotNull
        iFld2 = a.iFld;
    }

    // Same as testOuterStripMinedLoopEnd() but we find in (7) a ParsePredicate from the
    // removed loop (L) which also does not have an OpaqueConstantBool and the assert fails.
    static void testParsePredicate() {
        A a = flag ? new B() : new C();

        int x = 77;
        int y = 0;
        // (L)
        do {
            x--;
            y++;
        } while (x > 0);

        if (y == 76) {
            a = (B) a;
        }
        iFld2 = a.iFld;
    }
}

class A {
    int iFld;
}

class B extends A {
}

class C extends A {
}
