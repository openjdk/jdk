/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8364757
 * @summary Moving Store nodes from the main CountedLoop to the OuterStripMinedLoop causes
 *          subsequent Store nodes to be eventually removed because of missing Phi nodes,
 *          leading to wrong results.
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation
 *      -Xcomp -XX:-UseLoopPredicate
 *      -XX:+UnlockDiagnosticVMOptions -XX:-UseAutoVectorizationPredicate
 *      -XX:CompileCommand=compileonly,compiler.loopstripmining.MissingStoreAfterOuterStripMinedLoop::test*
 *      compiler.loopstripmining.MissingStoreAfterOuterStripMinedLoop
 * @run main compiler.loopstripmining.MissingStoreAfterOuterStripMinedLoop
 *
 */

package compiler.loopstripmining;

public class MissingStoreAfterOuterStripMinedLoop {
    public static int x = 0;
    public static int y = 0;

    static class A {
        int field;
    }

    // The store node in the loop body is moved to the OuterStripLoop.
    // When making the post loop the new store node
    // should have the moved store node as memory input, and not the
    // initial x = 0 store.
    //
    // store (x = 0)
    //  |
    // store (x += 1, exit of CountedLoop main)
    //  | <-- additional rewiring due to absence of phi node
    // store (x += 1, exit of CountedLoop post)
    //  |
    // store (x = 0)
    static public void test1() {
        x = 0;
        for (int i = 0; i < 20000; i++) {
            x += i;
        }
        x = 0;
    }

    // Two independent stores
    // They should be wired independently in the post loop, no aliasing
    static public void test2() {
        x = 0;
        y = 0;
        for (int i = 0; i < 20000; i++) {
            x += i;
            y += i;
        }
        x = 0;
        y = 0;
    }

    // Chain of stores with potential aliasing.
    // The entire chain is moved to the OuterStripLoop, between the
    // inner loop exit and the safepoint.
    // The chain should be preserved when cloning the main loop body
    // to create the post loop. Only the first store of the post loop
    // should be rewired to have the last store of the main loop
    // as memory input.
    //
    // ...
    //  |
    // store (a1.field = v, exit of CountedLoop main)
    //  |
    // store (a2.field = v, exit of CountedLoop main)
    //  |
    // store (a3.field = v, exit of CountedLoop main)
    //  | <-- only additional rewiring needed
    // store (a1.field = v, exit of CountedLoop post)
    //  |
    // store (a2.field = v, exit of CountedLoop post)
    //  |
    // store (a3.field = v, exit of CountedLoop post)
    static public void test3(A a1, A a2, A a3) {
        a1.field = 0;
        a2.field = 0;
        a3.field = 0;
        int v = 0;
        for (int i = 0; i < 20000; i++) {
            v++;
            a1.field = v;
            a2.field = v;
            a3.field = v;
        }
    }

    public static void main(String[] strArr) {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();

        test1();
        if (x != 0) {
            throw new RuntimeException("unexpected value: " + x);
        }

        test2();
        if (x != 0 || y != 0) {
            throw new RuntimeException("unexpected value: " + x + " " + y);
        }

        test3(a1, a2, a3);
        if (a1.field != 20000 || a2.field != 20000 || a3.field != 20000) {
            throw new RuntimeException("unexpected value: " + a1.field + " " + a2.field + " " + a3.field);
        }
    }
}
