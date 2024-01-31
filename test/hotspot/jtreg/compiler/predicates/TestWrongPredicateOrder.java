/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308892
 * @summary Test that Parse Predicates immediately following other Parse Predicates
            are cleaned up properly.
 * @run main/othervm -Xbatch compiler.predicates.TestWrongPredicateOrder
 */

package compiler.predicates;

public class TestWrongPredicateOrder {
    static boolean flag;
    static int iFld = 0;
    static int iFld2 = 34;
    static int iArr[] = new int[1005];
    static int iArr2[] = new int[2];


    public static void main(String[] strArr) {
        // We will keep hitting the Profiled Loop Predicate for RC1 (Integer.MAX_VALUE - 1 - 3 > 1005) such that we will
        // not emit the Profile Loop Parse Predicate anymore. After that, we will also keep hitting the Loop Limit Check
        // Predicate (Interger.MAX_VALUE - 1 > Integer.MAX_VALUE - 2) such that we will also not emit the Loop Limit Check
        // Parse Predicate anymore. As a result, we'll only emit the Loop Parse Predicate in the next re-compilation.
        // In the next re-compilation, we'll hoist IC1 as Loop Predicate and IC2 as Profiled Loop Predicate.
        // They have a data dependency between them but this is normally okay because Profiled Loop Predicates are below
        // Loop Predicates in the graph. But due to the flipped order of Parse Predicates in this bug, we create the
        // Hoisted Predicates in the wrong order and we end up with a bad graph and assert.
        for (int i = 0; i < 10000; i++) {
            flag = !flag;
            test();
        }
    }

    public static void test() {
        // Ensure to emit Loop Limit Check Predicate which is hit too often
        // -> no Loop Limit Check Parse Predicate is added in re-compilation anymore
        int limit = flag ? Integer.MAX_VALUE - 1 : 1000;

        int i = 0;
        // Loop Limit Check Predicate: limit <= Integer.MAX_VALUE - stride + 1 = Integer.MAX_VALUE - 2
        while (i < limit) {
            i += 3;
            // Invariant check hoisted as Loop Predicate
            iArr2[iFld] = 1; // (IC1)

            if (flag) {
                // Early exit -> enables Profiled Loop Predicate creation below
                return;
            }

            // Invariant check hoisted as Profiled Loop Predicate
            // Data dependency on Loop Predicate for "iArr2[0] = 1"
            iArr2[1] = 5; // (IC2)

            // Profiled Loop Predicate for range check hit too much -> no Profiled Loop Parse Predicate is added in
            // re-compilation anymore
            iArr[i] = 34; // (RC1)

            if (iFld2 == 5555) {
                i++; // UCT -> ensures to emit Parse Predicates twice with an If in between that is folded after parsing
            }
        }
    }
}

