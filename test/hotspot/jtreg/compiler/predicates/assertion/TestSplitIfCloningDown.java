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
 *
 */

/*
 * @test
 * @bug 8330004
 * @summary Sanity test to exercise code to clone a Template Assertion Predicate down in Split If.
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestSplitIfCloningDown::*
 *                   compiler.predicates.assertion.TestSplitIfCloningDown
 */

/*
 * @test id=no-flags
 * @bug 8330004
 * @summary Sanity test to exercise code to clone a Template Assertion Predicate down in Split If.
 * @run main compiler.predicates.assertion.TestSplitIfCloningDown
 */

package compiler.predicates.assertion;

public class TestSplitIfCloningDown {
    static int[] iArr = new int[100];
    static boolean flag;
    static int iFld;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            testPhiIntoNonOpaqueLoopExpressionNode();
            testPhiIntoOpaqueLoopExpressionNode();
        }
    }


    static void testPhiIntoNonOpaqueLoopExpressionNode() {
        int zero = 34;
        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }


        for (int t = 0; t < 100; t++) { // Use outer loop such that OpaqueLoop* will get an earlier ctrl.
            iArr = new int[1000];

            // Replaced by CMove which is an input into Template Assertion Predicate Expression which
            // is not an OpaqueLoop* node. Split If will create a phi and tries to split a Template
            // Assertion Predicate Expression node -> Need to clone template down.
            int a;
            if (flag) {
                a = 4;
            } else {
                a = 3;
            }

            for (int i = 0; i < 100; i++) {
                iArr[i+a] = 34; // Hoisted with Hoisted Check Predicate and Template Assertion Predicate
                if (i * zero < iFld) { // Unswitched after Split If to check further template cloning.
                    return;
                }
            }
        }
    }

    // Same as test above but this time the phi inputs into an OpaqueLoop* node and not a node in between.
    static void testPhiIntoOpaqueLoopExpressionNode() {
        int zero = 34;
        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        iArr = new int[1000];

        // Replaced by CMove which is an input into Template Assertion Predicate Expression which
        // is not an OpaqueLoop* node. Split If will create a phi and tries to split a Template
        // Assertion Predicate Expression node -> Need to clone template down.
        int a;
        if (flag) {
            a = 4;
        } else {
            a = 3;
        }

        for (int i = 0; i < 100; i++) {
            iArr[i+a] = 34; // Hoisted with Hoisted Check Predicate and Template Assertion Predicate
            if (i * zero < iFld) { // Unswitched after Split If to check further template cloning.
                return;
            }
        }
    }
}

