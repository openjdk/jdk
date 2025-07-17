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
 * @bug 8303511
 * @summary C2: assert(get_ctrl(n) == cle_out) during unrolling
 * @requires vm.gc.Parallel
 * @run main/othervm -XX:-BackgroundCompilation -XX:+UseParallelGC TestAddPAtOuterLoopHead
 */


import java.util.Arrays;

public class TestAddPAtOuterLoopHead {
    public static void main(String[] args) {
        boolean[] flags1 = new boolean[1000];
        boolean[] flags2 = new boolean[1000];
        Arrays.fill(flags2, true);
        for (int i = 0; i < 20_000; i++) {
            testHelper(42, 42, 43);
            test(flags1);
            test(flags2);
        }
    }

    private static int test(boolean[] flags) {
        int[] array = new int[1000];

        int k;
        for (k = 0; k < 10; k++) {
            for (int i = 0; i < 2; i++) {
            }
        }
        k = k / 10;
        int m;
        for (m = 0; m < 2; m++) {
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                }
            }
        }


        int v = 0;
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 998; i += k) {
                int l = testHelper(m, j, i);
                v = array[i + l];
                if (flags[i]) {
                    return v;
                }
            }
        }

        return v;
    }

    private static int testHelper(int m, int j, int i) {
        return m == 2 ? j : i;
    }
}
