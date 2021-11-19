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
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,TestLoopEndNodeEliminate::lMeth TestLoopEndNodeEliminate
 *
 */

public class TestLoopEndNodeEliminate {
     public volatile boolean bFld=true;
     public volatile byte byFld=0;
     public volatile short sArrFld[]=new short[N];
     public int iArrFld[]=new int[N];
     public boolean bArrFld[]=new boolean[N];

     public static int iFld=10;
     public static final int N = 400;
     public static long instanceCount=0L;
     public static long lMeth_check_sum = 0;

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
             for (i19 = 1; i19 < 270; ++i19) {
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
                 bArrFld[(int)(l1) % N] = bFld;
                 sArrFld[i19 - 1] ^= (short)(++TestLoopEndNodeEliminate.instanceCount);
             }
         }
         long meth_res = l1 + i14 + i15 + i16 + i17 + i18 + i19 + i20 + i21 + i22 + i23 + i24 + i25;
         lMeth_check_sum += meth_res;
         return (long)meth_res;
     }

     public static void main(String[] strArr) {
        TestLoopEndNodeEliminate _instance = new TestLoopEndNodeEliminate();
        for (int i = 0; i < 10000; i++ ) {
            _instance.lMeth();
        }
     }
}
