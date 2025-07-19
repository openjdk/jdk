/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:-TieredCompilation TestEliminatedRCCausesDeadCast
 */

public class TestEliminatedRCCausesDeadCast {
    private static int intField = 10;
    static volatile int barrier;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(8, true);
            testhelper(11, false, 11);
            test2(8, true);
            testhelper2(11, false, 11);
        }
    }


    private static int test2(int j, boolean flag) {
        return testhelper2(j, flag, 8);
    }

    private static int testhelper2(int j, boolean flag, int arraySize) {
        if (flag) {
            barrier = 0x42;
            return intField;
        } else {
            int i = 5;
            for (; i < 6; i *= 2);
            int[] array = new int[arraySize];
            if (j > arraySize) {
            }
            barrier = 0x42;
            // j = [min, 8]
            if (i < 0) {
            }
            barrier = 0x42;
            if (i >= j) {
            }
            barrier = 0x42;
            // i = [0, 7]
            int v = 0;
            for (int k = 1; k < arraySize; k *= 2) {
                array[k] = k;
                v += array[i];
            }
            return v;
        }
    }

    private static int test1(int j, boolean flag) {
        return testhelper(j, flag, 8);
    }

    private static int testhelper(int j, boolean flag, int arraySize) {
        if (flag) {
            barrier = 0x42;
            return intField;
        } else {
            int i = 5;
            for (; i < 6; i *= 2);
            int[] array = new int[arraySize];
            if (j > arraySize) {
            }
            barrier = 0x42;
            // j = [min, 8]
            if (i < 0) {
            }
            barrier = 0x42;
            if (i >= j) {
            }
            barrier = 0x42;
            // i = [0, 7]
            int v = array[i];
            return v;
        }
    }

}
