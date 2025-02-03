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
 * @bug 8331194
 * @summary Check that Reduce Allocation Merges doesn't crash when an input
 *          of the Phi is not the _current_ output of the Phi but said input
 *          needs to be rematerialized because it's used regardless of the
 *          Phi output.
 * @run main/othervm -XX:CompileCommand=dontinline,*TestReduceAllocationAndNestedScalarized*::test
 *                   -XX:CompileCommand=compileonly,*TestReduceAllocationAndNestedScalarized*::test
 *                   -XX:CompileCommand=compileonly,*Picture*::*init*
 *                   -XX:CompileCommand=compileonly,*Point*::*init*
 *                   -XX:CompileCommand=exclude,*Unloaded*::*
 *                   -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:-UseCompressedOops
 *                   -Xcomp
 *                   -server
 *                   compiler.escapeAnalysis.TestReduceAllocationAndNestedScalarized
 * @run main compiler.escapeAnalysis.TestReduceAllocationAndNestedScalarized
 */

package compiler.escapeAnalysis;

public class TestReduceAllocationAndNestedScalarized {
    static class Picture {
        public Point first;
        public Point second;
    }

    static class Point {
        int x;
    }

    static class Unloaded {
    }

    static int test(boolean cond) {
        Picture p = new Picture();
        p.first = new Point();
        Point p2 = p.first;

        if (cond) p2 = new Point();

        p.second = p2;

        new Unloaded();

        return p.first.x;
    }

    public static void main(String[] args) {
        Picture pic = new Picture();
        Point pnt   = new Point();
        int res     = test(true);
        System.out.println("Result is: " + res);
    }
}
