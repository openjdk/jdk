/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8283466
 * @summary Skeleton predicates were not copied to peeled loop. ConvI2L for array access collapsed,
 *          but the control flow did not because we did not have the more specific skeleton predicates
 *          instantiated for that peeled loop.
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+StressLCM -XX:+StressGCM -XX:+StressCCP -XX:+StressIGVN
 *                   -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestPeelingSkeletonPredicateInstantiation::*
 *                   -XX:RepeatCompilation=100
 *                   -XX:-LoopUnswitching
 *                   compiler.loopopts.TestPeelingSkeletonPredicateInstantiation
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+StressLCM -XX:+StressGCM -XX:+StressCCP -XX:+StressIGVN
 *                   -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestPeelingSkeletonPredicateInstantiation::*
 *                   -XX:RepeatCompilation=100
 *                   compiler.loopopts.TestPeelingSkeletonPredicateInstantiation
*/

package compiler.loopopts;

public class TestPeelingSkeletonPredicateInstantiation {
    int N = 400;
    byte byArrFld[] = new byte[N];
    void mainTest(String[] strArr1) {
        int i = 9, i17 = 32040, i18 = 14, i19 = 159, i20 = 13, i21 = 7, i22 = 58,
            iArr[] = new int[N], iArr4[] = new int[N];
        boolean b3 = false;
        for (i17 = 5; 228 > i17; ++i17) {
            try {
                i = (46318 / iArr4[i17 - 1]);
            } catch (ArithmeticException a_e) {
            }
            i19 = 1;
            do
                for (i20 = 1; i20 > i17; i20 -= 3) {
                    iArr4[i20 - 1] %= (i18 | 1);
                    iArr4[i19 - 1] -= i19;
                    if (b3)
                        switch ((i17 % 5) + 3) {
                        case 3:
                            switch (0 + 20) {
                            default:
                                iArr[i17 + 1] *= 0f;
                            }
                        case 5:
                            i += 22490;
                        case 6:
                            byArrFld[i20 + 1] = (byte)223;
                        case 7:
                            i18 = (int)5442422125903453825L;
                        }
                }
            while (++i19 < 113);
        }
    }
    public static void main(String[] strArr) {
        try {
            TestPeelingSkeletonPredicateInstantiation _instance = new TestPeelingSkeletonPredicateInstantiation();
            for (int i = 0; i < 10; i++)
                _instance.mainTest(strArr);
        } catch (Exception ex) {
        }
    }
}


