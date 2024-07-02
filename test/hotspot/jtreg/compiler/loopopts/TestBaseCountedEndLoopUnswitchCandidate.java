/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8325746
 * @summary Test Loop Unswitching with BaseCountedLoopEnd nodes as unswitch candidate.
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.loopopts.TestBaseCountedEndLoopUnswitchCandidate::test*
 *                   -Xcomp -XX:LoopMaxUnroll=0 -XX:-UseLoopPredicate -XX:-RangeCheckElimination
 *                   compiler.loopopts.TestBaseCountedEndLoopUnswitchCandidate
 * @run main compiler.loopopts.TestBaseCountedEndLoopUnswitchCandidate
 */

package compiler.loopopts;

public class TestBaseCountedEndLoopUnswitchCandidate {
    static int iFld;
    static long lFld;
    static A a = new A();
    static boolean flag;

    public static void main(String[] k) {
        for (int i = 0; i < 10000; i++) {
            testLongCountedLoopEnd();
            testCountedLoopEnd();
        }
    }

    public static void testLongCountedLoopEnd() {
        long limit = lFld;
        for (int i = 0; i < 100; i++) {

            // After peeling & IGNV:
            //      LongCountedEndLoop
            //           /      \
            //         True    False
            //        / \       /
            //  Store    Region
            //
            // LongCountedEndLoop has both paths inside loop and is therefore selected as unswitch candidate If in
            // Loop Unswitching.

            // Use stride > Integer.MAX_VALUE such that LongCountedLoopNode is not split further into loop nests.
            for (long j = 0; j < limit; j+=2147483648L) {
                a.i += 34; // NullCheck with trap on false path -> reason to peel
                if (j > 0) { // After peeling: j > 0 always true -> loop folded away
                    break;
                }
            }
        }
    }

    public static void testCountedLoopEnd() {
        int limit = iFld;
        for (int i = 0; i < 100; i++) {

            // After peeling & IGNV:
            //        CountedLoopEnd
            //           /      \
            //         True    False
            //        / \       /
            //  Store    Region
            //
            // CountedEndLoop has both paths inside loop and is therefore selected as unswitch candidate If in
            // Loop Unswitching.

            for (int j = 0; j < limit; j++) {
                a.i += 34; // NullCheck with trap on false path -> reason to peel
                if (j > 0) { // After peeling: j > 0 always true -> loop folded away
                    break;
                }
            }
        }
    }
}

class A {
    int i;
}
