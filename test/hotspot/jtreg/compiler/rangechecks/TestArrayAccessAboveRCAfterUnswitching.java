/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8323274
 * @summary loop unswitching can cause an array load to become dependent on a test other than its range check
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:CompileOnly=TestArrayAccessAboveRCAfterUnswitching::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=148059521 TestArrayAccessAboveRCAfterUnswitching
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:CompileOnly=TestArrayAccessAboveRCAfterUnswitching::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM TestArrayAccessAboveRCAfterUnswitching
 */

import java.util.Arrays;

public class TestArrayAccessAboveRCAfterUnswitching {
    private static int field;

    public static void main(String[] args) {
        int[] array = new int[1000];
        boolean[] allFalse = new boolean[1000];
        boolean[] allTrue = new boolean[1000];
        Arrays.fill(allTrue, true);
        for (int i = 0; i < 20_000; i++) {
            inlined(array, allFalse, 42, 2, 2, 0);
            inlined(array, allFalse, 2, 42, 2, 0);
            inlined(array, allFalse, 2, 2, 2, 0);
            inlined(array, allFalse, 2, 2, 42, 0);
            inlined(array, allTrue, 2, 2, 2, 0);
            test(array, allTrue, 0);
        }
        try {
            test(array, allTrue, -1);
        } catch (ArrayIndexOutOfBoundsException aioobe) {
        }
    }

    private static int test(int[] array, boolean[] flags, int start) {
        if (flags == null) {
        }
        if (array == null) {
        }
        int j = 1;
        for (; j < 2; j *= 2) {
        }
        int k = 1;
        for (; k < 2; k *= 2) {
        }
        int l = 1;
        for (; l < 2; l *= 2) {
        }
        int i;
        for (i = 0; i < 10; i += l) {

        }
        if (flags[i - 10]) {
            return inlined(array, flags, j, k, l, start);
        }
        return 0;
    }

    private static int inlined(int[] array, boolean[] flags, int j, int k, int l, int start) {
        for (int i = 0; i < 100; i++) {
            final boolean flag = flags[i & (j - 3)];
            int v = array[(i + start) & (j - 3)];
            if (flag) {
                return v;
            }
            if (j != 2) {
                field = v;
            } else {
                if (k != 2) {
                    field = 42;
                } else {
                    if (l == 2) {
                        break;
                    }
                }
            }
        }
        return 0;
    }
}
