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
 * @bug 8324969
 * @summary C2 incorrectly marks unbalanced (after coarsened locks were eliminated)
 *          nested locks for elimination.
 * @requires vm.compMode != "Xint"
 * @run main/othervm -XX:-BackgroundCompilation TestCoarsenedAndNestedLocksElimination
 */

public class TestCoarsenedAndNestedLocksElimination {

    public static void main(String[] strArr) {
        for (int i = 0; i < 12000; ++i) {
            test1(-1);
            test2(-1);
        }
    }

    static synchronized int methodA(int var) {
        return var;
    }

    static synchronized int methodB(int var) {
        return var;
    }

    static int varA = 0;
    static int varB = 0;

    static void test1(int var) {
        synchronized (TestNestedLocksElimination.class) {
            for (int i2 = 0; i2 < 3; i2++) { // Fully unrolled
                 varA = methodA(i2);         // Nested synchronized methods also use
                 varB = i2 + methodB(var);   // TestNestedLocksElimination.class for lock
            }
        }
        TestNestedLocksElimination t = new TestNestedLocksElimination(); // Triggers EA
    }

    static boolean test2(int var) {
        synchronized (TestNestedLocksElimination.class) {
            for (int i1 = 0; i1 < 100; i1++) {
                switch (42) {
                case 42:
                    short[] sArr = new short[256]; // Big enough to avoid scalarization checks
                case 50:
                    for (int i2 = 2; i2 < 8; i2 += 2) { // Fully unrolled
                        for (int i3 = 1;;) {
                            int var1 = methodA(i2);
                            int var2 = i2 + methodB(i3);
                            break;
                        }
                    }
                }
            }
        }
        return var > 0;
    }
}
