/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @bug 8275854
 * @summary Crashes in PhaseIdealLoop::transform_long_counted_loop
 * @requires vm.compiler2.enabled
 *
 * @run main TestLoopEndNodeEliminate
 *
 */

public class TestLoopEndNodeEliminate {
    public volatile boolean bFld=true;
    public double dFld=1.20070;
    public volatile byte byFld=0;
    public volatile short sArrFld[]=new short[N];
    public int iArrFld[]=new int[N];
    public volatile byte byArrFld[]=new byte[N];
    public boolean bArrFld[]=new boolean[N];

    public static int iFld=10;
    public static final int N = 400;
    public static long instanceCount=0L;
    public static long vSmallMeth_check_sum = 0;
    public static long bMeth_check_sum = 0;
    public static long lMeth_check_sum = 0;
    public static long bMeth1_check_sum = 0;

    public static int[] int1array(int sz, int seed) {
        int[] ret = new int[sz];

        init(ret, seed);
        return ret;
    }

    public static byte[] byte1array(int sz, byte seed) {
        byte[] ret = new byte[sz];

        init(ret, seed);
        return ret;
    }

    public static void init(int[] a, int seed) {
        for (int j = 0; j < a.length; j++) {
            a[j] = (j % 2 == 0) ? seed + j : seed - j;
        }
    }

    public static void init(byte[] a, byte seed) {
        for (int j = 0; j < a.length; j++) {
            a[j] = (byte) ((j % 2 == 0) ? seed + j : seed - j);
        }
    }

    public static long checkSum(int[] a) {
        long sum = 0;

        for (int j = 0; j < a.length; j++) {
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        }
        return sum;
    }

    public static void vSmallMeth(boolean b, int i12) {
        int iArr[]=new int[N];

        init(iArr, -115);
        iArr = int1array(N, (int)10852);
        iArr = (iArr = iArr);
        vSmallMeth_check_sum += (b ? 1 : 0) + i12 + checkSum(iArr);
    }

    public long lMeth() {
        long l1=-33582180L;
        int i14=-5, i15=-14, i16=0, i17=25699, i18=97, i19=-3, i20=0, i21=0, i22=42, i23=0, i24=25699, i25=97;

        for (l1 = 286; l1 > 16; l1 -= 3) {
            for (i15 = 17; i15 > l1; --i15) {
                switch (((iArrFld[i15] >>> 1) % 7) + 101) {
                case 101:
                case 102:
                case 103:
                case 104:
                    for (i17 = (int)(l1); i17 < 1; i17++) {
                        bArrFld[i17] = bFld;
                    }
                    break;
                case 105:
                case 106:
                case 107:
                }
            }
            for (i19 = 1; i19 < 27; ++i19) {
                TestLoopEndNodeEliminate.iFld += byFld;
                i21 = 1;
                while (++i21 < 2) {
                    bFld = true;
                }
                for (i22 = 1; 2 > i22; ++i22) {
                    bFld = true;
                }
                for (i24 = 1; 2 > i24; ++i24) {
                    bFld = true;
                }
                bArrFld[(int)(l1)] = bFld;
                sArrFld[i19 - 1] ^= (short)(++TestLoopEndNodeEliminate.instanceCount);
            }
        }
        long meth_res = l1 + i14 + i15 + i16 + i17 + i18 + i19 + i20 + i21 + i22 + i23 + i24 + i25;
        lMeth_check_sum += meth_res;
        return (long)meth_res;
    }

    public static boolean bMeth1() {
        int i26=0, i27=42, i28=0, i29=25699;

        for (i26 = 1; 151 > i26; ++i26) {
            for (i28 = i26; i28 < 11; i28++) {
                TestLoopEndNodeEliminate.instanceCount = TestLoopEndNodeEliminate.iFld;
            }
        }
        long meth_res = i26 + i27 + i28 + i29;
        bMeth1_check_sum += meth_res;
        return meth_res % 2 > 0;
    }

    public boolean bMeth(int i13) {
        long l2=-12L;
        int i30=-108, i31=0, i32=42, i33=0, i34=25699, i35=97, i36=-3, i37=0;

        iArrFld = (iArrFld = (iArrFld = iArrFld));
        dFld = Math.min(lMeth(), TestLoopEndNodeEliminate.instanceCount);
        bArrFld[(i13 >>> 1) % N] = bMeth1();
        iArrFld = (iArrFld = (iArrFld = iArrFld));
        dFld = Math.min(lMeth(), -5L);
        for (l2 = 286; l2 > 16; l2 -= 3) {
            for (i31 = 17; i31 > l2; --i31) {
                switch (((iArrFld[i31] >>> 1) % 7) + 101) {
                case 101:
                case 102:
                case 103:
                case 104:
                    for (i33 = (int)(l2); i33 < 1; ++i33) {
                        bArrFld[i33] = bFld;
                    }
                    break;
                case 105:
                case 106:
                case 107:
                }
            }
            for (i35 = 1; i35 < 17; ++i35) {
                TestLoopEndNodeEliminate.iFld += byFld;
                i37 = 1;
                while (++i37 < 2) {
                    bFld = true;
                }
                bArrFld[(int)(l2)] = bFld;
            }
        }
        long meth_res = i13 + l2 + i30 + i31 + i32 + i33 + i34 + i35 + i36 + i37;
        bMeth_check_sum += meth_res;
        return meth_res % 2 > 0;
    }

    public void mainTest(String[] strArr1) {
        long l=12L, l3=-512L;
        int i11=0, i38=25699, i39=97, i40=-3, i41=0, i42=0, i43=42, i44=0, i45=25699, i46=97, i47=-3, i48=42, i49=0,
            i50=25699, i51=97, i52=-3;

        bFld = (-97 == TestLoopEndNodeEliminate.iFld);
        for (l = 286; l > 16; l -= 3) {
            for (int smallinvoc=0; smallinvoc<62; smallinvoc++) {
                vSmallMeth(bMeth(TestLoopEndNodeEliminate.iFld), TestLoopEndNodeEliminate.iFld);
            }
            for (i38 = 10; 278 > i38; ++i38) {
                bFld = true;
            }
            for (i40 = 1; 278 > i40; ++i40) {
                bFld = true;
            }
            for (i42 = 1; 278 > i42; ++i42) {
                bFld = true;
            }
            for (i44 = 1; 278 > i44; ++i44) {
                bFld = true;
            }
            for (i46 = 1; 278 > i46; i46++) {
                bFld = true;
            }
        }
        dFld = Math.min(lMeth(), -5L);
        for (l3 = 286; l3 > 16; l3 -= 3) {
            for (i49 = 10; 278 > i49; i49++) {
                bFld = true;
            }
            for (i51 = 1; 278 > i51; ++i51) {
                bFld = true;
            }
        }
    }

    public static void main(String[] strArr) {
        try {
            TestLoopEndNodeEliminate _instance = new TestLoopEndNodeEliminate();
            for (int i = 0; i < 10; i++ ) {
                _instance.mainTest(strArr);
            }
         } catch (Exception ex) {
            System.out.println(ex.getClass().getCanonicalName());
         }
    }
}
