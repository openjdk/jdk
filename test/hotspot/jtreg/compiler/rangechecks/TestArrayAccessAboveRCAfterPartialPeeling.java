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
 * @summary partial peeling loop can cause an array load to become dependent on a test other than its range check
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-TieredCompilation -XX:-BackgroundCompilation TestArrayAccessAboveRCAfterPartialPeeling
 */

public class TestArrayAccessAboveRCAfterPartialPeeling {
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] array = new int[100];
        for (int i = 0; i < 20_000; i++) {
            test(array, 2, true, 1);
            test(array, 2, false, 1);
            inlined(array, 2, 42, true, 42, 1, 1);
            inlined(array, 2, 42, false, 42, 1, 1);
        }
        try {
            test(array, 2, true, -1);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
    }

    private static int test(int[] array, int k, boolean flag, int j) {
        int l;
        for (l = 1; l < 2; l *= 2) {

        }
        int m;
        for (m = 0; m < 42; m += l) {

        }
        int n;
        for (n = 0; n < 10; n += m/42) {

        }
        return inlined(array, k, l, flag, m, n/10, j);
    }

    private static int inlined(int[] array, int k, int l, boolean flag, int m, int n, int j) {
        if (array == null) {
        }
        int[] otherArray = new int[100];
        int i = 0;
        int v = 0;
        if (k == m) {
        }

        if (flag) {
            v += array[j];
            v += otherArray[i];

            for (; ; ) {
                synchronized (new Object()) {
                }
                if (j >= 100) {
                    break;
                }
                if (k == 42) {
                }
                v += array[j];
                v += otherArray[i];
                if (i >= n) {
                    otherArray[i] = v;
                }
                v += array[j];
                if (l == 2) {
                    break;
                }
                i++;
                j *= 2;
                volatileField = 42;
                k = 2;
                l = 42;
            }
        } else {
            v += array[j];
            v += otherArray[i];

            for (; ; ) {
                synchronized (new Object()) {
                }
                if (j >= 100) {
                    break;
                }
                if (k == 42) {
                }
                v += array[j];
                v += otherArray[i];
                if (i >= n) {
                    otherArray[i] = v;
                }
                v += array[j];
                if (l == 2) {
                    break;
                }
                i++;
                j *= 2;
                volatileField = 42;
                k = 2;
                l = 42;
            }
        }
        return v;
    }
}
