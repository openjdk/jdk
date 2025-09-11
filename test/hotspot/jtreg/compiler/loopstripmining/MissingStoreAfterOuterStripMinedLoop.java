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
 *      -Xcomp -XX:-UseLoopPredicate -XX:-UseAutoVectorizationPredicate
 *      -XX:CompileCommand=compileonly,compiler.loopstripmining.MoveStoreAfterLoopSebsequentStores::test*
 *      compiler.loopstripmining.MoveStoreAfterLoopSebsequentStores
 * @run main compiler.loopstripmining.MoveStoreAfterLoopSebsequentStores
 *
 */

package compiler.loopstripmining;

public class MissingStoreAfterOuterStripMinedLoop {
    public static int x = 0;
    public static int y = 0;

    static class A {
        int field;
    }

    static public void test1() {
        x = 0;
        for (int i = 0; i < 20000; i++) {
            x += i;
        }
        x = 0;
    }

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

    static public void test3(A a1, A a2) {
        a1.field = 0;
        a2.field = 0;
        for (int i = 0; i < 20000; i++) {
            a1.field += i;
            a2.field += i;
        }
        a1.field = 0;
        a2.field = 0;
    }

    public static void main(String[] strArr) {
        A a1 = new A();
        A a2 = new A();

        test1();
        if (x != 0) {
            throw new RuntimeException("unexpected value: " + x);
        }

        test2();
        if (x != 0 || y != 0) {
            throw new RuntimeException("unexpected value: " + x + " " + y);
        }

        test3(a1, a1);
        if (a1.field != 0 || a2.field != 0) {
            throw new RuntimeException("unexpected value: " + a1.field + " " + a2.field);
        }
    }
}