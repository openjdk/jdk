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

/*
 * @test
 * @bug 8371674
 * @summary An expression of the form "MoveX2Y (LoadX mem)" should be
 *          transformed into "LoadY mem". This test ensures that changes
 *          to the number of users of the Load node propagate as expected
 *          and that the optimization is not missed.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -Xcomp
 *      -XX:CompileCommand=compileonly,compiler.c2.TestMissingOptMoveX2YLoadX::test*
 *      -XX:VerifyIterativeGVN=1110 compiler.c2.TestMissingOptMoveX2YLoadX
 * @run main compiler.c2.TestMissingOptMoveX2YLoadX
 *
 */

package compiler.c2;

public class TestMissingOptMoveX2YLoadX {
    static final int N = 400;
    static volatile long b;

    public static void main(String[] strArr) {
        // could theoretically happen with other variants of MoveNode
        // but there is no known reproducer for the other cases
        Double.longBitsToDouble(0l);
        testMoveL2D();
    }

    static void testMoveL2D() {
        int e = 8, f, g = 9, h = 2, i[] = new int[N];
        long j[] = new long[N];
        while (++e < 37) {
            for (f = 1; f < 7; f++) {
                h >>>= (int)(--g - Double.longBitsToDouble(j[e]));
                b -= b;
            }
        }
    }
}