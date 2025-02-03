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
 * @bug 8343137
 * @requires vm.debug == true & vm.compiler2.enabled
 * @summary Test that set_ctrl() is properly set for true constant when folding useless Template Assertion Predicate.
 * @run main/othervm -Xcomp -XX:+VerifyLoopOptimizations
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestMissingSetCtrlForTrueConstant::test
 *                   compiler.predicates.assertion.TestMissingSetCtrlForTrueConstant
 */

package compiler.predicates.assertion;

public class TestMissingSetCtrlForTrueConstant {
    static long iFld;
    static int[] iArrFld = new int[100];
    static double[] dArrFld = new double[100];

    public static void main(String[] strArr) {
        test();
    }

    static void test() {
        long l = 34;
        for (int i = 78; i > 8; --i) {
            switch (i) {
                case 24:
                    l += iFld - 34;
                case 25:
                    iFld = iArrFld[i] += i;
            }
            dArrFld[i + 1] += i;
        }
    }
}
