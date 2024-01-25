/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress randomness
 * @bug 8274074
 * @requires vm.compiler2.enabled
 * @summary Sinking a data node used as divisor of a DivI node into a zero check UCT loses its pin outside the loop due to
 *          optimizing the CastII node away, resulting in a div by zero crash (SIGFPE) due to letting the DivI node floating
 *          back inside the loop.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.loopopts.TestSinkingDivisorLostPin::* -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=4177789702 compiler.loopopts.TestSinkingDivisorLostPin
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.loopopts.TestSinkingDivisorLostPin::* -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM compiler.loopopts.TestSinkingDivisorLostPin
 */

package compiler.loopopts;

public class TestSinkingDivisorLostPin {
    static int iFld = 1;
    static int x = 1;
    static int q = 0;
    static int iArrFld[] = new int[100];

    public static void main(String[] strArr) {
        test();
    }

    static void test() {
        int y = 1;
        int i = 1;
        do {
            int j;
            for (j = 1; j < 88; j++) {
                iArrFld[1] = x;
            }
            try {
                y = iFld - q; // y = 1 - 0
                y = (iArrFld[2] / y); // y = iArrFld[2] / 1
                // Zero check Z1 with UCT
                // DivI node D on IfTrue path of zero check
                y = (5 / iFld); // y = 5 / 1
                // Zero check Z2 with UCT
                // DivI node D is only used on IfFalse path of zero check Z2 into UCT (on IfTrue path, the result is not used anywhere
                // because we directly overwrite it again with "y = (5 / iFld)). The IfFalse path of the zero check, however, is never
                // taken because iFld = 1. But before applying the sinking algorithm, the DivI node D could be executed during the
                // loop, as the zero check Z1 succeeds. Only after sinking the SubI node for "iFld - q" into the IfFalse path of Z2
                // and optimizing it accordingly (iFld is found to be zero because the zero check Z2 failed, i.e. iFld is zero which is
                // propagated into the CastII node whose type is improved to [0,0] and the node is replaced by constant zero), the
                // DivI node must NOT be executed inside the loop anymore. But the DivI node is executed in the loop because of losing
                // the CastII pin. The fix is to update the control input of the DivI node to the get_ctrl() input outside the loop
                // (IfFalse of zero check Z2).
            } catch (ArithmeticException a_e) {
            }

            iFld -= 8;
            if (y == 3) {
            }
            i++;
        } while (i < 10);
    }

}

