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
 * @summary sinking an array load out of loop can cause it to become dependent on a test other than its range check
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-TieredCompilation -XX:-BackgroundCompilation TestArrayAccessAboveRCAfterSinking
 */


import java.util.Arrays;

public class TestArrayAccessAboveRCAfterSinking {
    public static void main(String[] args) {
        boolean[] allFalse = new boolean[100];
        boolean[] allTrue = new boolean[100];
        Arrays.fill(allTrue, true);
        int[] array = new int[100];
        for (int i = 0; i < 20_000; i++) {
            test1(allTrue, array, 0, true, 0);
            test1(allTrue, array, 0, false, 0);
            inlined1(allFalse, array, 2, 0);
            inlined1(allFalse, array, 42, 0);
            inlined1(allTrue, array, 2, 0);
            test2(allTrue, array, 0, true, 0);
            test2(allTrue, array, 0, false, 0);
            inlined2(allFalse, array, 2, 0);
            inlined2(allFalse, array, 42, 0);
            inlined2(allTrue, array, 2, 0);
        }
        try {
            test1(allTrue, array, -1, true, 0);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test2(allTrue, array, -1, true, 0);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
    }

    private static int test1(boolean[] flags, int[] array, int k, boolean flag, int v) {
        if (flags == null) {
        }
        if (array == null) {
        }
        int j = 1;
        for (; j < 2; j *= 2) {
        }
        int i;
        for (i = 0; i < 10; i += j) {

        }
        if (flags[i - 10]) {
            if (flag) {
                return inlined1(flags, array, j, k);
            } else {
                return inlined1(flags, array, j, k) + v;
            }
        }
        return 0;
    }

    private static int inlined1(boolean[] flags, int[] array, int j, int k) {
        for (int i = 0; i < 100; i++) {
            final boolean flag = flags[i & (j - 3)];
            int v = array[i + k];
            if (flag) {
                return v;
            }
            if (j + (i & (j - 2)) == 2) {
                break;
            }
        }
        return 0;
    }

    private static int test2(boolean[] flags, int[] array, int k, boolean flag, int v) {
        if (flags == null) {
        }
        if (array == null) {
        }
        int j = 1;
        for (; j < 2; j *= 2) {
        }
        int i;
        for (i = 0; i < 10; i += j) {

        }
        if (flags[i - 10]) {
            if (flag) {
                return inlined2(flags, array, j, k);
            } else {
                return inlined2(flags, array, j, k) + v;
            }
        }
        return 0;
    }

    private static int inlined2(boolean[] flags, int[] array, int j, int k) {
        for (int i = 0; i < 100; i++) {
            int v = array[i + k];
            if (flags[i & (j - 3)]) {
                return v;
            }
            if (j + (i & (j - 2)) == 2) {
                break;
            }
        }
        return 0;
    }
}
