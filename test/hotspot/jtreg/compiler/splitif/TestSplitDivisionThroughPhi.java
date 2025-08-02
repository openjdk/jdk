/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
* @test
* @key stress randomness
* @bug 8299259 8336729
* @requires vm.compiler2.enabled
* @summary Test various cases of divisions/modulo which should not be split through iv phis.
* @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:LoopUnrollLimit=0 -XX:+StressGCM -XX:StressSeed=884154126
*                   -XX:CompileCommand=compileonly,compiler.splitif.TestSplitDivisionThroughPhi::*
*                   compiler.splitif.TestSplitDivisionThroughPhi
*/

/**
* @test
* @key stress randomness
* @bug 8299259 8336729
* @requires vm.compiler2.enabled
* @summary Test various cases of divisions/modulo which should not be split through iv phis.
* @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:LoopUnrollLimit=0 -XX:+StressGCM
*                   -XX:CompileCommand=compileonly,compiler.splitif.TestSplitDivisionThroughPhi::*
*                   compiler.splitif.TestSplitDivisionThroughPhi
*/

/**
 * @test
 * @key stress randomness
 * @bug 8336729
 * @requires vm.compiler2.enabled
 * @summary Test various cases of divisions/modulo which should not be split through iv phis.
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:LoopUnrollLimit=0 -XX:+StressGCM -XX:StressSeed=3434
 *                   -XX:CompileCommand=compileonly,compiler.splitif.TestSplitDivisionThroughPhi::*
 *                   compiler.splitif.TestSplitDivisionThroughPhi
 */

/**
 * @test
 * @key stress randomness
 * @bug 8336729
 * @requires vm.compiler2.enabled
 * @summary Test various cases of divisions/modulo which should not be split through iv phis.
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:LoopUnrollLimit=0 -XX:+StressGCM
 *                   -XX:CompileCommand=compileonly,compiler.splitif.TestSplitDivisionThroughPhi::*
 *                   compiler.splitif.TestSplitDivisionThroughPhi
 */

package compiler.splitif;

public class TestSplitDivisionThroughPhi {
    static int iFld;
    static long lFld;
    static boolean flag;


    public static void main(String[] strArr) {
        // Make sure classes are loaded when compiling with -Xcomp
        Integer.divideUnsigned(2, 3);
        Long.divideUnsigned(2, 3);
        for (int i = 0; i < 5000; i++) {
            testPushDivIThruPhi();
            testPushDivIThruPhiInChain();
            testPushModIThruPhi();
            testPushModIThruPhiInChain();
            testPushDivLThruPhi();
            testPushDivLThruPhiInChain();
            testPushModLThruPhi();
            testPushModLThruPhiInChain();
            testPushDivLThruPhiForOuterLongLoop();
            testPushModLThruPhiForOuterLongLoop();
            testPushUDivLThruPhiForOuterLongLoop();
            testPushUModLThruPhiForOuterLongLoop();
            testPushUDivIThruPhi();
            testPushUDivIThruPhiInChain();
            testPushUModIThruPhi();
            testPushUModIThruPhiInChain();
            testPushUDivLThruPhi();
            testPushUDivLThruPhiInChain();
            testPushUModLThruPhi();
            testPushUModLThruPhiInChain();
        }
    }

    // Already fixed by JDK-8248552.
    static void testPushDivIThruPhi() {
        for (int i = 10; i > 1; i -= 2) {
            // The Div node is only split in later loop opts phase because the zero divisor check is only removed
            // in IGVN after the first loop opts phase.
            //
            // iv phi i type: [2..10]
            // When splitting the DivI through the iv phi, it ends up on the back edge with the trip count decrement
            // as input which has type [0..8]. We end up executing a division by zero on the last iteration because
            // the DivI it is not pinned to the loop exit test and can freely float above the loop exit check.
            iFld = 10 / i;
        }
    }

    // Fixed with JDK-8336729.
    static void testPushDivLThruPhiForOuterLongLoop() {
        // This loop is first transformed into a LongCountedLoop in the first loop opts phase.
        // In the second loop opts phase, the LongCountedLoop is split into an inner and an outer loop. Both get the
        // same iv phi type which is [2..10]. Only the inner loop is transformed into a CountedLoopNode while the outer
        // loop is still a LoopNode. We run into the same problem as described in testPushDivIThruPhi() when splitting
        // the DivL node through the long iv phi of the outer LoopNode.
        // The fix for JDK-8299259 only prevents this splitting for CountedLoopNodes. We now extend it to LoopNodes
        // in general.
        for (long i = 10; i > 1; i -= 2) {
            lFld = 10 / i;
        }
    }

    // Same as testPushDivLThruPhiForOuterLongLoop() but for ModL.
    static void testPushModLThruPhiForOuterLongLoop() {
        for (int i = 10; i > 1; i -= 2) {
            iFld = 10 % i;
        }
    }

   // Same as above but with an additional Mul node between the iv phi and the Div node. Both nodes are split through
   // the iv phi in one pass of Split If.
    static void testPushDivIThruPhiInChain() {
        for (int i = 10; i > 1; i -= 2) {
            // Empty one-iteration loop which is only removed after split if in first loop opts phase. This prevents
            // that the Mul node is already split through the iv phi while the Div node cannot be split yet due to
            // the zero divisor check which can only be removed in the IGVN after the first loop opts pass.
            for (int j = 0; j < 1; j++) {
            }
            iFld = 10 / (i * 100);
        }
    }

    // Already fixed by JDK-8248552.
    static void testPushModIThruPhi() {
        for (int i = 10; i > 1; i -= 2) {
            iFld = 10 / i;
        }
    }

    // Same as above but with ModI.
    static void testPushModIThruPhiInChain() {
        for (int i = 10; i > 1; i -= 2) {
            for (int j = 0; j < 1; j++) {
            }
            iFld = 10 / (i * 100);
        }
    }

    // Long cases only trigger since JDK-8256655.

    // Same as above but with DivL.
    static void testPushDivLThruPhi() {
        for (long i = 10; i > 1; i -= 2) {
            lFld = 10L / i;

            // Loop that is not removed such that we do not transform the outer LongCountedLoop (only done if innermost)
            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with DivL.
    static void testPushDivLThruPhiInChain() {
        for (long i = 10; i > 1; i -= 2) {
            for (int j = 0; j < 1; j++) {
            }
            lFld = 10L / (i * 100L);

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with ModL
    static void testPushModLThruPhi() {
        for (long i = 10; i > 1; i -= 2) {
            lFld = 10L % i;

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with ModL
    static void testPushModLThruPhiInChain() {
        for (long i = 10; i > 1; i -= 2) {
            for (int j = 0; j < 1; j++) {
            }
            lFld = 10L % (i * 100L);

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UDivI.
    static void testPushUDivIThruPhi() {
        for (int i = 10; i > 1; i -= 2) {
            lFld = Integer.divideUnsigned(10, i);

            // Loop that is not removed such that we do not transform the outer LongCountedLoop (only done if innermost)
            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UDivI.
    static void testPushUDivIThruPhiInChain() {
        for (int i = 10; i > 1; i -= 2) {
            for (int j = 0; j < 1; j++) {
            }
            lFld = Integer.divideUnsigned(10, (i * 100));

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UModI
    static void testPushUModIThruPhi() {
        for (int i = 10; i > 1; i -= 2) {
            lFld = Integer.remainderUnsigned(10, i);

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UModI
    static void testPushUModIThruPhiInChain() {
        for (int i = 10; i > 1; i -= 2) {
            for (int j = 0; j < 1; j++) {
            }
            lFld = Integer.remainderUnsigned(10, (i * 100));

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UDivL.
    static void testPushUDivLThruPhi() {
        for (long i = 10; i > 1; i -= 2) {
            lFld = Long.divideUnsigned(10L, i);

            // Loop that is not removed such that we do not transform the outer LongCountedLoop (only done if innermost)
            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UDivL.
    static void testPushUDivLThruPhiInChain() {
        for (long i = 10; i > 1; i -= 2) {
            for (int j = 0; j < 1; j++) {
            }
            lFld = Long.divideUnsigned(10L, (i * 100L));

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UModL
    static void testPushUModLThruPhi() {
        for (long i = 10; i > 1; i -= 2) {
            lFld = Long.remainderUnsigned(10L, i);

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    // Same as above but with UModL
    static void testPushUModLThruPhiInChain() {
        for (long i = 10; i > 1; i -= 2) {
            for (int j = 0; j < 1; j++) {
            }
            lFld = Long.remainderUnsigned(10L, (i * 100L));

            for (int j = 0; j < 10; j++) {
                flag = !flag;
            }
        }
    }

    static void testPushUDivLThruPhiForOuterLongLoop() {
        for (long i = 10; i > 1; i -= 2) {
            lFld = Long.divideUnsigned(10, i);
        }
    }

    static void testPushUModLThruPhiForOuterLongLoop() {
        for (int i = 10; i > 1; i -= 2) {
            iFld = Integer.remainderUnsigned(10, i);
        }
    }
}
