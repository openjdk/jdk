/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8392811
 * @summary Check that Reduce Allocation Merges doesn't cause an unexpected NPE
 *          when a reduced Phi folds to an input that only becomes scalar
 *          replaceable in a later iteration of escape analysis.
 * @run main/othervm -XX:CompileCommand=compileonly,*TestReduceAllocationAndFoldedMerge*::test*
 *                   -XX:-TieredCompilation -Xcomp
 *                   compiler.escapeAnalysis.TestReduceAllocationAndFoldedMerge
 * @run main compiler.escapeAnalysis.TestReduceAllocationAndFoldedMerge
 */

package compiler.escapeAnalysis;

public class TestReduceAllocationAndFoldedMerge {
    public static void main(String[] args) {
        // Load these classes before the test methods get compiled
        new Pair();
        new Picture();
        new Point();

        test1();
        test2();
        test3();
    }

    static void test1() {
        Pair p1 = new Pair();
        Pair p2 = new Pair();
        Pair p3 = new Pair();

        // Makes p1 NSR until the lock is eliminated
        synchronized (p1) { }

        Pair merge;
        if (p3.i < -1) {
            merge = p2;
        } else {
            merge = p1;
        }

        merge.b = Unloaded.get(); // uncommon trap
    }

    static void test2() {
        Pair p = new Pair();
        synchronized (p) { }
        (0.9993396081444631 > p.i ? p : new Pair()).i -= Double.valueOf(0.0); // uncommon trap
    }

    static void test3() {
        Point p = !new Picture().flag ? new Point() : (new Picture().p = new Point());
        new Point().o = new Object(); // uncommon trap
        synchronized (p) { }
    }

    static class Pair {
        boolean b;
        int i;

        Pair() {
            synchronized (new Object()) { }
        }
    }

    static class Unloaded {
        static boolean get() {
            return false;
        }
    }

    static class Picture {
        boolean flag = true;
        Point p = new Point();
    }

    static class Point {
        Object o;

        Point() {
            Integer.toString(0);
        }
    }
}
