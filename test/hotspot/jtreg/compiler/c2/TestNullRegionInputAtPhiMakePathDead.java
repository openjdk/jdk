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
 * @bug 8355230
 * @summary Crash in fuzzer tests: assert(n != nullptr) failed: must not be null
 * @run main/othervm -XX:CompileCommand=compileonly,TestNullRegionInputAtPhiMakePathDead::* -Xcomp TestNullRegionInputAtPhiMakePathDead
 */

public class TestNullRegionInputAtPhiMakePathDead {

    public static long instanceCount=-37082278491330812L;
    public static float fFld=1.509F;
    public static int iFld=89;

    public static long vMeth_check_sum = 0;

    public void mainTest() {

        int i19, i20=13736, i21, i24=5;
        boolean b2=true;

        double d;
        int i3;

        for (d = 5; d < 131; ++d) {
            i3 = 12;
            while (--i3 > 0) {
                TestNullRegionInputAtPhiMakePathDead.fFld *= -31237;
            }
        }
        TestNullRegionInputAtPhiMakePathDead.fFld %= 16334;
        TestNullRegionInputAtPhiMakePathDead.instanceCount = 0;
        for (i19 = 2; i19 < 281; i19++) {
            try {
                TestNullRegionInputAtPhiMakePathDead.iFld = (57 % i20);
            } catch (ArithmeticException a_e) {}
            i20 += (((i19 * TestNullRegionInputAtPhiMakePathDead.fFld) + TestNullRegionInputAtPhiMakePathDead.instanceCount) - i19);
            for (i21 = i19; i21 < 90; i21++) {
                if (b2) {
                } else {
                    // CastII of b2 to false added here becomes top during igvn. It's used by a Phi
                    // at a Region that merges paths from the switch and if. Some of those paths are
                    // found unreachable at parse time but added to the Region anyway.
                    switch ((((i21 >>> 1) % 4) * 5) + 115) {
                    case 129:
                        break;
                    case 135:
                        i24 |= i24;
                    }
                }
            }
        }

        System.out.println("vMeth_check_sum: " + vMeth_check_sum);
    }
    public static void main(String[] strArr) {
        try {
            TestNullRegionInputAtPhiMakePathDead _instance = new TestNullRegionInputAtPhiMakePathDead();
            for (int i = 0; i < 10; i++ ) {
                _instance.mainTest();
            }
         } catch (Exception ex) {
         }
    }
}
