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
 * @bug 8320909
 * @summary AndNode has a special handling when one of the operands is a LShiftNode:
 *          (LHS << s) & RHS
 *          if RHS fits in less than s bits, the value of this expression is 0.
 *          The case where there is a conversion node between the Shift and the And as in:
 *          AndLNode(ConvI2L(LShiftI(LHS, s)), RHS)
 *          is also handled, but the AndL must be pushed directly in IGVN's worklist because
 *          ConvI2L might not have an update when its input change. In this example, the
 *          input was a Phi with a dead branch and becomes a LShiftI with the same type.
 *
 * @run main/othervm
 *          -XX:CompileOnly=MissedOptWithShiftConvAnd::test
 *          -XX:-TieredCompilation -Xbatch
 *          -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=10
 *          MissedOptWithShiftConvAnd
 */

/*
 * @test
 * @bug 8320909
 *
 * @run main/othervm MissedOptWithShiftConvAnd
 */

public class MissedOptWithShiftConvAnd {
    static long lFld;

    public static void main(String[] strArr) {
        for (int i = 0; i < 100; i++) {
            test();
        }
    }

    static void test() {
        long l3 = 0;
        int i13 = 1;
        for (l3 = 8; l3 < 200; ++l3) {
            for (int i12 = 1; i12 < 2; i12++) {
                i13 <<= 73;
            }
        }
        for (int i14 = 1; 2 > i14; ++i14) {
            i13 &= l3;
            lFld = i13;
        }
    }
}
