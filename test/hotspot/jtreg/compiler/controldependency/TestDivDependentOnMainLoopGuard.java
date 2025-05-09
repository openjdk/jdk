/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8349139
 * @summary C2: Div looses dependency on condition that guarantees divisor not null in counted loop
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileOnly=TestDivDependentOnMainLoopGuard::*
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=35878193 TestDivDependentOnMainLoopGuard
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileOnly=TestDivDependentOnMainLoopGuard::*
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM TestDivDependentOnMainLoopGuard
 * @run main/othervm -Xcomp -XX:CompileOnly=TestDivDependentOnMainLoopGuard::* TestDivDependentOnMainLoopGuard
 */

import jdk.test.lib.Utils;
import java.util.Random;

public class TestDivDependentOnMainLoopGuard {

    public static final int N = 400;
    private static final Random RANDOM = Utils.getRandomInstance();
    public static final int stop = RANDOM.nextInt(0, 68);

    public int iArrFld[]=new int[N];

    public void mainTest(String[] strArr1, int otherPhi) {

        int i=57657, i1=577, i2=6, i3=157, i4=12, i23=61271;
        boolean bArr[]=new boolean[N];

        for (i = 9; 379 > i; i++) {
            i2 = 1;
            do {
                i1 <<= i3;
            } while (++i2 < 68);
            for (i23 = 68; i23 > stop; otherPhi=i23-1, i23--) {
                bArr[i23 + 1] = true;
                try {
                    i1 = (-42360 / i23);
                    iArrFld[i + 1] = otherPhi;
                } catch (ArithmeticException a_e) {}
            }
        }
    }

    public static void main(String[] strArr) {
        TestDivDependentOnMainLoopGuard _instance = new TestDivDependentOnMainLoopGuard();
        for (int i = 0; i < 10; i++ ) {
            _instance.mainTest(strArr, 0);
        }
    }
}
