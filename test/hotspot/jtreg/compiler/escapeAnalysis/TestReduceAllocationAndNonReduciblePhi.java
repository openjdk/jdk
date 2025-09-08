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
 * @bug 8340454
 * @summary Check that Reduce Allocation Merges doesn't crash when
 *          a reducible Phi becomes irreducible after the last of
 *          its SR inputs is flagged as NSR.
 * @run main/othervm -XX:CompileCommand=dontinline,*TestReduceAllocationAndNonReduciblePhi*::test
 *                   -XX:CompileCommand=compileonly,*TestReduceAllocationAndNonReduciblePhi*::test
 *                   -XX:CompileCommand=compileonly,*Picture*::*
 *                   -XX:CompileCommand=compileonly,*Point*::*
 *                   -XX:CompileCommand=inline,*Picture*::*
 *                   -XX:CompileCommand=inline,*Point*::*
 *                   -XX:CompileCommand=exclude,*::dummy*
 *                   -Xbatch
 *                   compiler.escapeAnalysis.TestReduceAllocationAndNonReduciblePhi
 *
 * @run main compiler.escapeAnalysis.TestReduceAllocationAndNonReduciblePhi
 */

package compiler.escapeAnalysis;

public class TestReduceAllocationAndNonReduciblePhi {
    public static void main(String args[]) {
        int result = 0;

        for (int i = 0; i < 20000; i++) {
            result += test(i % 2 == 0, i % 3);
        }

        System.out.println("Result is = " + result);
    }

    public static int test(boolean flag1, int pos) {
        Point p0 = new Point();
        Point p1 = flag1 ? null : p0;

        Picture pic = new Picture();
        pic.p = p0;

        Picture[] ps = new Picture[5];
        ps[pos] = pic;

        return p1 != null ? dummy1() : dummy2();
    }

    public static int dummy1() { return 1; }

    public static int dummy2() { return 2; }

    private static class Picture {
        public Point p;
    }

    private static class Point { }
}
