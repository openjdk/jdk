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
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestArrayAccessAboveRCAfterLoopConditionalPropagation
 */

public class TestArrayAccessAboveRCAfterLoopConditionalPropagation {
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] array = new int[10];
        for (int i = 0; i < 20_000; i++) {
            test1(0, true, 0);
            test1(0, false, 0);
            test2(0, true, 0);
            test2(0, false, 0);
        }
        try {
            test1(-1, true, 0);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test2(-1, true, 0);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
    }

    private static int test1(int i, boolean flag, int j) {
        int k;
        for (k = 0; k < 10; k++) {
        }
        int l;
        for (l = 1; l < 2; l *= 2) {
        }
        if (j == 44 - l ) {
        }
        int[] array = new int[100];
        int v = 0;
        if (flag) {
            if (i < 0) {
            }
            if (i >= 10) {
            }
            if (j == 42) {
            }
            v = array[i];
            volatileField = 42;
        } else {
            if (i < 0) {
            }
            if (i >= 10) {
            }
            if (j == 42) {
            }
            v = array[i];
            volatileField = 42;
        }
        return v;
    }

    private static int test2(int i, boolean flag, int j) {
        int k;
        for (k = 0; k < 10; k++) {
        }
        int l;
        for (l = 1; l < 2; l *= 2) {
        }
        if (j == 44 - l ) {
        }
        int[] array = new int[1];
        int v = 0;
        if (flag) {
            if (i - 1 != -1) {
            }
            if (j == 42) {
            }
            // i - 1 = -1 (i == 0)
            // i <u 1 => i == 0
            v = array[i];
            volatileField = 42;
        } else {
            if (i - 1  != -1) {
            }
            if (j == 42) {
            }
            v = array[i];
            volatileField = 42;
        }
        return v;
    }

}
