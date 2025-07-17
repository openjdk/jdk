/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8299546
 * @summary Tests that MulL::Value() does not return bottom type and then an optimized type again in CCP.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.ccp.TestMissingMulLOptimization::*
 *                   -XX:CompileCommand=dontinline,compiler.ccp.TestMissingMulLOptimization::*
 *                   compiler.ccp.TestMissingMulLOptimization
 */
package compiler.ccp;

public class TestMissingMulLOptimization {
    static int N;
    static long x;

    public static void main(String[] strArr) {
        try {
            test();
        } catch (RuntimeException e) {
            // Expected
        }
    }

    static int test() {
        int i6 = 2, i10 = 3, i11, iArr[] = new int[N];
        long l = 3151638515L;
        double dArr[] = new double[N];
        dontInline();
        int i;
        for (i = 7; i < 221; i++) {
            i6 *= i6;
        }
        for (int j = 9; 83 > j; ) {
            for (i11 = 1; i11 < 6; ++i11) {
                l *= i;
                l += 3;
            }
        }
        x += i6;
        return 34;
    }

    static int dontInline() {
        throw new RuntimeException();
    }
}
