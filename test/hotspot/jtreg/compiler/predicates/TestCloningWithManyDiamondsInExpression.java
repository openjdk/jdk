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
 * @bug 8327110 8327111
 * @requires vm.compiler2.enabled
 * @summary Test that DFS algorithm for cloning Template Assertion Predicate Expression does not endlessly process paths.
 * @run main/othervm/timeout=30 -Xcomp -XX:LoopMaxUnroll=0
 *                              -XX:CompileCommand=compileonly,*TestCloningWithManyDiamondsInExpression::test*
 *                              -XX:CompileCommand=inline,*TestCloningWithManyDiamondsInExpression::create*
 *                              compiler.predicates.TestCloningWithManyDiamondsInExpression
 * @run main/othervm/timeout=30 -Xbatch -XX:LoopMaxUnroll=0
 *                              -XX:CompileCommand=compileonly,*TestCloningWithManyDiamondsInExpression::test*
 *                              -XX:CompileCommand=inline,*TestCloningWithManyDiamondsInExpression::create*
 *                              compiler.predicates.TestCloningWithManyDiamondsInExpression
 * @run main/timeout=30 compiler.predicates.TestCloningWithManyDiamondsInExpression
 */

 /*
  * @test
  * @bug 8327111
  * @summary Test that DFS algorithm for cloning Template Assertion Predicate Expression does not endlessly process paths.
  * @run main/othervm/timeout=30 -Xcomp
  *                              -XX:CompileCommand=compileonly,*TestCloningWithManyDiamondsInExpression::test*
  *                              -XX:CompileCommand=inline,*TestCloningWithManyDiamondsInExpression::create*
  *                              compiler.predicates.TestCloningWithManyDiamondsInExpression
  * @run main/othervm/timeout=30 -Xbatch
  *                              -XX:CompileCommand=compileonly,*TestCloningWithManyDiamondsInExpression::test*
  *                              -XX:CompileCommand=inline,*TestCloningWithManyDiamondsInExpression::create*
  *                              compiler.predicates.TestCloningWithManyDiamondsInExpression
  */

package compiler.predicates;

public class TestCloningWithManyDiamondsInExpression {
    static int limit = 100;
    static int iFld;
    static boolean flag;
    static int[] iArr;

    public static void main(String[] strArr) {
        Math.min(10, 13); // Load class for Xcomp mode.
        for (int i = 0; i < 10_000; i++) {
            testSplitIf(i % 2);
            testLoopUnswitching(i % 2);
            testLoopUnrolling(i % 2);
            testLoopPeeling(i % 2);
        }
    }

    static void testLoopUnswitching(int x) {
        // We create an array with a positive size whose type range is known by the C2 compiler to be positive.
        // Loop Predication will then be able to hoist the array check out of the loop by creating a Hoisted
        // Check Predicate accompanied by a Template Assertion Predicate. The Template Assertion Predicate
        // Expression gets the size as an input. When splitting the loop further (i.e. when doing Loop Unswitching),
        // the predicate needs to be updated. We need to clone all nodes of the Tempalte Assertion Predicate
        // Expression. We first need to find them by doing a DFS walk.
        //
        // createExpressionWithManyDiamonds() creates an expression with many diamonds. The current implementation
        // (found in create_bool_from_template_assertion_predicate()) to clone the Template Assertion Predicate
        // does not use a visited set. Therefore, the DFS implementation visits nodes twice to discover more paths.
        // The more diamonds we add, the more possible paths we get to visit. This leads to an exponential explosion
        // of paths and time required to visit them all. This example here will get "stuck" during DFS while trying
        // to walk all the possible paths.
        //
        int[] a = new int[createExpressionWithManyDiamonds(x) + 1000];
        for (int i = 0; i < limit; i++) {
            a[i] = i; // Loop Predication hoists this check and creates a Template Assertion Predicate.
            // Triggers Loop Unswitching -> we need to clone the Template Assertion Predicates
            // to both the true- and false-path loop. Will take forever (see explanation above).
            if (x == 0) {
                iFld = 34;
            }
        }
    }

    // Same as for Loop Unswitching but triggered in Split If when the Tempalte Assertion Predicate Expression
    // needs to be cloned. This time it's not the size of the array that contains many diamonds but the array
    // index for the first and last value Template Assertion Predicate Expression.
    static void testSplitIf(int x) {
        int e = createExpressionWithManyDiamonds(x);
        iArr = new int[1000];
        int a;
        if (flag) {
            a = 4;
        } else {
            a = 3;
        }

        for (int i = a; i < 100; i++) {
            iArr[i+e] = 34;
        }
    }

    static void testLoopUnrolling(int x) {
        int[] a = new int[createExpressionWithManyDiamonds(x) + 1000];
        for (int i = 0; i < limit; i++) {
            a[i] = i; // Loop Predication hoists this check and creates a Template Assertion Predicate.
        }
    }

    static void testLoopPeeling(int x) {
        int[] a = new int[createExpressionWithManyDiamonds(x) + 1000];
        for (int i = 0; i < limit; i++) {
            a[i] = i; // Loop Predication hoists this check and creates a Template Assertion Predicate.
            if (x == 0) { // Reason to peel with LoopMaxUnroll=0
                return;
            }
        }
    }

    // Creates in int expression with many diamonds. This method is forced-inlined.
    static int createExpressionWithManyDiamonds(int x) {
        int e = Math.min(10, Math.max(1, x));
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2) - 823542;
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2) - 823542;
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2);
        e = e + (e << 1) + (e << 2) - 823542;
        return e;
    }
}
