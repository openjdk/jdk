/*
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
 * @requires vm.compiler2.enabled
 * @bug 8290910
 * @summary Test which needs to select the memory state of the last load in a load pack in SuperWord::co_locate_pack.
 *
 * @run main/othervm -Xcomp -Xbatch -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestPickLastMemoryState::*
 *                   compiler.loopopts.superword.TestPickLastMemoryState
 */

package compiler.loopopts.superword;

public class TestPickLastMemoryState {
    static final int N = 400;
    static long lArrFld[] = new long[N];
    static long iMeth_check_sum;
    static long[] golden_sum = {22154, 44050, 66167, 88359, 110684, 132686, 154755, 176703, 198872, 220874};

    static void iMeth() {
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

    static void reset() {
        for (int i = 0; i < N; i++) {
            lArrFld[i] = 0;
        }
        iMeth_check_sum = 0;
    }

    static void test() {
        for (int i = 0; i < 10; i++) {
            iMeth();
            if (iMeth_check_sum != golden_sum[i]) {
                throw new RuntimeException("iMeth wrong result at " + i + ": " + iMeth_check_sum);
            }
        }
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 5_000; i++) {
            reset();
            test();
        }
    }
}

