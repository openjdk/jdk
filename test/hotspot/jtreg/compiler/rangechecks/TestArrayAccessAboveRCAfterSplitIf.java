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
 * @summary split if can cause an array load to become dependent on a test other than its range check
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation TestArrayAccessAboveRCAfterSplitIf
 */

public class TestArrayAccessAboveRCAfterSplitIf {
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] array = new int[1000];
        for (int i = 0; i < 20_000; i++) {
            test1(array, array, 0, 2, true);
            inlined1(42, array, array, 0, 2, 10, true);
            inlined1(2, array, array, 0, 2, 10, true);
            inlined1(42, array, array, 0, 2, 10, false);
            inlined1(2, array, array, 0, 2, 10, false);
            test2(array, array, 0, 2, true);
            inlined2(42, array, array, 0, 2, 10, true);
            inlined2(2, array, array, 0, 2, 10, true);
            inlined2(42, array, array, 0, 2, 10, false);
            inlined2(2, array, array, 0, 2, 10, false);
        }
        try {
            test1(array, array, -1, 2, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test2(array, array, -1, 2, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
    }

    private static int test1(int[] array1, int[] array2, int i, int l, boolean flag) {
        for (int j = 0; j < 10; j++) {
        }
        int k;
        for (k = 1; k < 2; k *= 2) {

        }
        int m;
        for (m = 0; m < 10; m+=k) {

        }
        return inlined1(k, array1, array2, i, l, m, flag);
    }

    private static int inlined1(int k, int[] array1, int[] array2, int i, int l, int m, boolean flag) {
        int v;
        int[] array;
        if (array1 == null) {
        }
        if (l == 10) {

        }
        if (flag) {
            if (k == 2) {
                v = array1[i];
                array = array1;
                if (l == m) {
                }
            } else {
                v = array2[i];
                array = array2;
            }
            v += array[i];
            v += array2[i];
        } else {
            if (k == 2) {
                v = array1[i];
                array = array1;
                if (l == m) {
                }
            } else {
                v = array2[i];
                array = array2;
            }
            v += array[i];
            v += array2[i];
        }
        return v;
    }

    private static int test2(int[] array1, int[] array2, int i, int l, boolean flag) {
        for (int j = 0; j < 10; j++) {
        }
        int k;
        for (k = 1; k < 2; k *= 2) {

        }
        int m;
        for (m = 0; m < 10; m+=k) {

        }
        return inlined2(k, array1, array2, i, l, m, flag);
    }

    private static int inlined2(int k, int[] array1, int[] array2, int i, int l, int m, boolean flag) {
        int v;
        int[] array;
        if (array1 == null) {
        }
        if (l == 10) {

        }
        if (flag) {
            if (k == 2) {
                v = array1[i];
                array = array1;
                if (l == m) {
                }
            } else {
                v = array2[i];
                array = array2;
            }
            if (Integer.compareUnsigned(i, array.length) >= 0) {
            }
            v += array[i];
            v += array2[i];
        } else {
            if (k == 2) {
                v = array1[i];
                array = array1;
                if (l == m) {
                }
            } else {
                v = array2[i];
                array = array2;
            }
            if (Integer.compareUnsigned(i, array.length) >= 0) {
            }
            v += array[i];
            v += array2[i];
        }
        return v;
    }
}
