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
 * @bug 8371558
 * @summary An expression of the form "abs(0-x)" should be transformed to "abs(x)".
 *          This test ensures that updates to the Sub node’s inputs propagate as
 *          expected and that the optimization is not missed.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -Xcomp
 *      -XX:CompileCommand=compileonly,compiler.c2.TestMissingOptAbsZeroMinusX::test*
 *      -XX:VerifyIterativeGVN=1110 compiler.c2.TestMissingOptAbsZeroMinusX
 * @run main compiler.c2.TestMissingOptAbsZeroMinusX
 *
 */

package compiler.c2;

public class TestMissingOptAbsZeroMinusX {
    static int a;
    static boolean b;

    public static void main(String[] strArr) {
        // no known reproducer for AbsL
        testAbsI();
        testAbsF();
        testAbsD();
    }

    static void testAbsI() {
        int d = 4;
        for (int i = 8; i < 133; i += 3) {
            d -= a;
            b = (d != Math.abs(d));
            for (long f = 3; f < 127; f++) {
                for (int e = 1; e < 3; e++) {}
            }
            d = 0;
        }
    }

    static void testAbsF() {
        float d = 12.3f;
        for (int i = 8; i < 133; i += 3) {
            d -= a;
            b = (d != Math.abs(d));
            for (long f = 3; f < 127; f++) {
                for (int e = 1; e < 3; e++) {}
            }
            d = 0.0f;
        }
    }

    static void testAbsD() {
        double d = 12.3;
        for (int i = 8; i < 133; i += 3) {
            d -= a;
            b = (d != Math.abs(d));
            for (long f = 3; f < 127; f++) {
                for (int e = 1; e < 3; e++) {}
            }
            d = 0.0;
        }
    }
}