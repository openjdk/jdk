/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 * @bug 8274145
 * @summary C2: Incorrect computation after JDK-8269752
 *
 * @run main/othervm -Xcomp -XX:CompileOnly=TestIfReplacedByMainLoopExit::iMeth -XX:CompileOnly=TestIfReplacedByMainLoopExit::mainTest -XX:-TieredCompilation TestIfReplacedByMainLoopExit
 *
 */

public class TestIfReplacedByMainLoopExit {

    public static final int N = 400;

    public static long instanceCount=3024694135L;
    public static boolean bFld=true;
    public int iFld=-11;

    public static long iMeth_check_sum = 0;

    public static void vMeth(int i3, int i4, int i5) {

        int i6=-71, i7=88, i8=217, i9=14, i10=9677, i18=-244, i19=107, iArr[]=new int[N];
    }

    public static void init(int[] a, int seed) {
        for (int j = 0; j < a.length; j++) {
            a[j] = (j % 2 == 0) ? seed + j : seed - j;
        }
    }

    public static long checkSum(int[] a) {
        long sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        }
        return sum;
    }

    public static int iMeth(boolean b, int i2) {

        byte by=81;
        int i21=-24074, i22=7, i23=-7, i24=-70, iArr2[]=new int[N];
        boolean b2=false;
        init(iArr2, -27);

        vMeth(189, i2, i2);
        for (int i20 : iArr2) {
            by *= (byte) TestIfReplacedByMainLoopExit.instanceCount;
            for (i23 = 1; i23 < 4; ++i23) {
                i24 -= i23;
                TestIfReplacedByMainLoopExit.bFld = b2;
            }
        }
        long meth_res = (b ? 1 : 0) + i2 + by + i21 + i22 + i23 + i24 + (b2 ? 1 : 0) + checkSum(iArr2);
        iMeth_check_sum += meth_res;
        return (int)meth_res;
    }

    public void mainTest(String[] strArr1) {
        int i, i1, i25, i26=9, i27, i28;
        byte by1=35;
        float f2;

        for (i = 17; 310 > i; ++i) {
            i1 = ((iMeth(TestIfReplacedByMainLoopExit.bFld, iFld) - iFld) + by1);
        }
        i1 = 231;
        iFld += -13496;
        for (i25 = 2; i25 < 271; i25++) {
            i26 -= i;
            if (TestIfReplacedByMainLoopExit.bFld) break;
        }
        i26 = i;
        iFld += (int)1.338F;
        iFld += 30984;
        i27 = 1;
        do {
            iFld *= i25;
            for (i28 = 4; i28 < 75; ++i28) {
                i1 += i25;
            }
        } while (++i27 < 335);
        f2 = 210;
        do {
            iFld -= i25;
        } while (--f2 > 0);

        System.out.println("iFld = " + iFld);
    }

    public static void main(String[] strArr) {
        TestIfReplacedByMainLoopExit _instance = new TestIfReplacedByMainLoopExit();
        _instance.mainTest(strArr);
        int iFld_sav = _instance.iFld;
        for (int i = 0; i < 10; i++ ) {
            _instance.iFld=-11;
            _instance.mainTest(strArr);
            if (_instance.iFld != iFld_sav) {
                throw new RuntimeException("incorrect execution " + _instance.iFld + " != " + iFld_sav);
            }
        }
    }
}
