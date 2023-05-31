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
    static int iArr2[] = new int[100000];


    public static void main(String[] strArr) {
        // Warmup without hitting Profiled Loop Predicate for range check
        for (int i = 0; i < 10000; i++) {
            flag = !flag;
            iFld = 0;
            test();
        }

        // Constantly hitting Profiled Loop Predicate for range check -> re-compilation without
        for (int i = 0; i < 10000; i++) {
            try {
                iFld = 1000; // Ensures out of bounds access to hit Profiled Loop Predicate
                flag = !flag;
                test();
            } catch (Exception e) {
                // Expected
            }
        }
    }

    public static void test() {
        // Ensure to emit Loop Limit Check Predicate which is hit too often -> no Loop Limit Check Parse Predicate is added in re-compilation anymore
        int limit = flag ? Integer.MAX_VALUE - 1 : 1000;

        int i = 0;
        while (i < limit) {
            i += 3;
            iArr2[0] = 1; // Invariant check hoisted as Loop Predicate


            if (flag) {
                // Early exit -> enables Profiled Loop Predicate creation below
                return;
            }

            // Data dependency on Loop Predicate for "iArr2[0] = 1", we need to hoist this (invariant) check with a Profiled Loop Predicate
            iArr2[1] = 5;

            // Profiled Range Check predicate -> hit too much -> no Profiled Parse Predicate is added in re-compilation anymore
            iArr[i + iFld] = 34;


            if (iFld2 == 5555) {
                i++; // UCT -> ensures to emit parse predicates twice with an If in between that is folded after parsing
            }
        }
    }
}

