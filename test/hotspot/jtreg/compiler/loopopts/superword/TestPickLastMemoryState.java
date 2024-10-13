/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @bug 8290910 8293216
 * @summary Test which needs to select the memory state of the last load in a load pack in SuperWord::co_locate_pack.
 *
 * @run main/othervm -Xcomp -XX:CompileOnly=compiler.loopopts.superword.TestPickLastMemoryState::*
 *                   -Xbatch -XX:MaxVectorSize=16 compiler.loopopts.superword.TestPickLastMemoryState
 * @run main/othervm -Xcomp -XX:CompileOnly=compiler.loopopts.superword.TestPickLastMemoryState::*
 *                   -Xbatch -XX:MaxVectorSize=32 compiler.loopopts.superword.TestPickLastMemoryState
 * @run main/othervm -Xcomp -XX:CompileOnly=compiler.loopopts.superword.TestPickLastMemoryState::*
 *                   -Xbatch compiler.loopopts.superword.TestPickLastMemoryState
 */

package compiler.loopopts.superword;

public class TestPickLastMemoryState {
    static final int N = 400;
    static final int M = 32;
    static long lArrFld[] = new long[N];
    static long iMeth_check_sum;
    static float a[] = new float[M];

    static void f() {
        int b[] = new int[M];
        for (int h = 1; h < 32; h++) {
            a[h] = b[h - 1]--;
            b[h]--;
        }
        boolean c[] = new boolean[M];
    }

    static void test0() throws Exception {
        f();
        double s = checkSum(a);
        System.out.println(s);
        if (s < -31 || s > -29) {
            throw new Exception("expected s: -30, actual s: " + s);
        }
    }

    static void test1() {
        int i1 , i2 = -222, iArr[] = new int[N];
        init(iArr, 212);
        // For the following loop, statement 1 can be vectorized but statement 2 can't. When
        // finding the memory state for the LoadI pack, we cannot pick the memory state from
        // the first load as the LoadI vector operation must load the memory after iArr writes
        // 'iArr[i1 + 1] - (i2++)' to 'iArr[i1 + 1]'. We must take the memory state of the last
        // load where we have assigned new values ('iArr[i1 + 1] - (i2++)') to the iArr array.
        for (i1 = 6; i1 < 227; i1++) {
            iArr[i1] += lArrFld[i1]++; // statement 1
            iArr[i1 + 1] -= (i2++); // statement 2
        }
        iMeth_check_sum += checkSum(iArr);
    }

    static void test2() {
        int i1 , i2 = -222, iArr[] = new int[N];
        init(iArr, 212);
        for (i1 = 6; i1 < 227; i1++) {
            iArr[i1] += lArrFld[i1]++;
            iArr[i1 + 2] -= (i2++);
        }
        iMeth_check_sum += checkSum(iArr);
    }

    static void test3() {
        int i1 , i2 = -222, iArr[] = new int[N];
        init(iArr, 212);
        for (i1 = 6; i1 < 227; i1++) {
            iArr[i1-2] += lArrFld[i1]++;
            iArr[i1] -= (i2++);
        }
        iMeth_check_sum += checkSum(iArr);
    }

    static void test4() {
        int i1 , i2 = -222, iArr[] = new int[N];
        init(iArr, 212);
        for (i1 = 6; i1 < 227; i1++) {
            iArr[i1-3] += lArrFld[i1]++;
            iArr[i1] -= (i2++);
        }
        iMeth_check_sum += checkSum(iArr);
    }

    static void test5() {
        int i1 , i2 = -222, i3 = -100, iArr[] = new int[N];
        init(iArr, 212);
        for (i1 = 6; i1 < 227; i1++) {
            iArr[i1] += lArrFld[i1]++;
            iArr[i1+2] -= (i3++);
            iArr[i1+16] -= (i2++);
        }
        iMeth_check_sum += checkSum(iArr);
    }

    static void test6() {
        int i1 , i2 = -222, i3 = -100, iArr[] = new int[N];
        init(iArr, 212);
        for (i1 = 6; i1 < 227; i1++) {
            iArr[i1] += lArrFld[i1]++;
            iArr[i1+1] -= (i3++);
            iArr[i1+32] -= (i2++);
        }
        iMeth_check_sum += checkSum(iArr);
    }

    static void init(int[] a, int seed) {
        for (int j = 0; j < a.length; j++) {
            a[j] = (j % 2 == 0) ? seed + j : seed - j;
        }
    }

    static long checkSum(int[] a) {
        long sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        }
        return sum;
    }

    static double checkSum(float[] a) {
        double sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += a[j];
        }
        return sum;
    }

    static void reset() {
        for (int i = 0; i < N; i++) {
            lArrFld[i] = 0;
        }
        iMeth_check_sum = 0;
    }

    public static void main(String[] strArr)  throws Exception {
        test0();
        test0();
        for (int i = 0; i < 5_000; i++) {
            reset();
            test1();
            if (iMeth_check_sum != 22154) {
                throw new RuntimeException("iMeth wrong result at test1: " + iMeth_check_sum);
            }
            test2();
            if (iMeth_check_sum != 44246) {
                throw new RuntimeException("iMeth wrong result at test2: " + iMeth_check_sum);
            }
            test3();
            if (iMeth_check_sum != 66171) {
                throw new RuntimeException("iMeth wrong result at test3: " + iMeth_check_sum);
            }
            test4();
            if (iMeth_check_sum != 88309) {
                throw new RuntimeException("iMeth wrong result at test4: " + iMeth_check_sum);
            }
            test5();
            if (iMeth_check_sum != 109251) {
                throw new RuntimeException("iMeth wrong result at test5: " + iMeth_check_sum);
            }
            test6();
            if (iMeth_check_sum != 130073) {
                throw new RuntimeException("iMeth wrong result at test6: " + iMeth_check_sum);
            }
        }
    }
}
