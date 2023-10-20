/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8303737
 * @summary C2: cast nodes from PhiNode::Ideal() cause "Base pointers must match" assert failure
 * @run main/othervm -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:+StressCCP -Xcomp
 *                   -XX:CompileOnly=TestAddPChainMismatchedBase2::* -XX:StressSeed=1581936900 TestAddPChainMismatchedBase2
 * @run main/othervm -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:+StressCCP -Xcomp
 *                   -XX:CompileOnly=TestAddPChainMismatchedBase2::* TestAddPChainMismatchedBase2
 */

public class TestAddPChainMismatchedBase2 {
    static final int N = 400;
    static int iFld;

    public static void main(String[] strArr) {
        test(8);
    }

    static void test(int i2) {
        int i12 = 4, iArr1[] = new int[N];
        double d1, dArr2[] = new double[N];
        do {
            iArr1[i12] = 400907;
            try {
                iArr1[1] = 47 % i2;
            } catch (ArithmeticException a_e) {
            }
            iArr1[i12 + 1] -= d1 = 1;
            while ((d1 += 2) < 5) {
                iArr1 = iArr1;
                iArr1[6] = 3;
            }
        } while (++i12 < 14);
    }
}
