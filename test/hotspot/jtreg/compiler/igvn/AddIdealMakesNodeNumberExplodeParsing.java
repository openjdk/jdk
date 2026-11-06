/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.igvn;

/**
 * @test
 * @bug 8374495
 * @summary Transformation (a-b)+(c-d) -> (a+c)-(b+d) in AddNode::IdealIL can apply iteratively and
 *          result in an explosion in node creation. Reusing nodes mitigates that.
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+AbortVMOnCompilationFailure
 *                   -Xbatch
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class AddIdealMakesNodeNumberExplodeParsing {
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test(i, 2*i, 3*i);
        }
    }

    static int test(int i1, int i2, int i3) {
        int s1 = i1 - i2;
        int s2 = i3 - s1;  // i3 - (i1 - i2) = i3 - i1 + i2

        int s3 = s2 - i2;  // (i3 - i1 + i2) - i2 = i3 - i1
        int s4 = s2 - s3;  // (i3 - i1 + i2) - (i3 - i1) = i2

        int s5 = s4 - i2;  // i2 - i2 = 0
        int s6 = s4 - s5;  // i2 - 0 = i2

        int s7 = s6 - i2;  // i2 - i2 = 0
        int s8 = s6 - s7;  // i2 - 0 = i2

        int s9 = s8 - i2;   // i2 - i2 = 0
        int s10 = s8 - s9;  // i2 - 0 = i2

        int s11 = s10 - i2;  // ...
        int s12 = s10 - s11;  // ...

        int s13 = s12 - i2;
        int s14 = s12 - s13;

        int s15 = s14 - i2;
        int s16 = s14 - s15;

        int s17 = s16 - i2;
        int s18 = s16 - s17;

        int s19 = s18 - i2;
        int s20 = s18 - s19;

        int s21 = s20 - i2;
        int s22 = s20 - s21;

        int s23 = s22 - i2;
        int s24 = s22 - s23;

        int s25 = s24 - i2;
        int s26 = s24 - s25;

        int s27 = s26 - i2;
        int s28 = s26 - s27;

        int s29 = s28 - i2;
        int s30 = s28 - s29;

        int s31 = s30 - i2;
        int s32 = s30 - s31;

        int s33 = s32 - i2;  // ...
        int s34 = s32 - s33;  // ...

        int s35 = s34 - i2;  // = 0
        int s36 = s34 - s35;  // = i2

        int s37 = s36 - i2;  // = 0

        int a1 = s36 + s36;  // i2 + i2
        int a2 = s37 + s37;  // 0 + 0 = 0

        return a1 * a2; // (i2 + i2) * 0 = 0
    }
}
