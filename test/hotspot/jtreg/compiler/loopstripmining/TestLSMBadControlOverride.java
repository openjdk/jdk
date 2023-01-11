/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8290781
 * @summary Segfault at PhaseIdealLoop::clone_loop_handle_data_uses
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:-TieredCompilation TestLSMBadControlOverride
 */

public class TestLSMBadControlOverride {
    private static volatile int barrier;

    public static void main(String[] args) {
        int[] array = new int[100];
        int[] small = new int[10];
        for (int i = 0; i < 20_000; i++) {
            test(array, array, true, true);
            test(array, array, true, false);
            test(array, array, false, false);
            try {
                test(small, array,true, true);
            } catch (ArrayIndexOutOfBoundsException aieoobe) {

            }
        }
    }

    private static int test(int[] array, int[] array2, boolean flag1, boolean flag2) {
        int i;
        int v = 0;
        int v1 = 0;
        for (i = 0; i < 100; i++) {
            v1 = array[i];
        }
        v += v1;
        if (flag1) {
            if (flag2) {
                barrier = 42;
            }
        }
        for (int j = 0; j < 100; j++) {
            array[j] = j;
            v += array[i-1];
        }
        return v;
    }
}
