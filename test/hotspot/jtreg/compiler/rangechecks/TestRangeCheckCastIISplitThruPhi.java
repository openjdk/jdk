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

/**
 * @test
 * @bug 8332827
 * @summary [REDO] C2: crash in compiled code because of dependency on removed range check CastIIs
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation TestRangeCheckCastIISplitThruPhi
 * @run main TestRangeCheckCastIISplitThruPhi
 *
 */

import java.util.Arrays;

public class TestRangeCheckCastIISplitThruPhi {
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] array = new int[100];
        int[] baseline = null;
        for (int i = 0; i < 20_000; i++) {
            Arrays.fill(array, 0);
            test1(array);
            if (baseline == null) {
                baseline = array.clone();
            } else {
                boolean failures = false;
                for (int j = 0; j < array.length; j++) {
                    if (array[j] != baseline[j]) {
                        System.out.println("XXX @" + j + " " + array[j] + " != " + baseline[j]);
                       failures = true;
                    }
                }
                if (failures) {
                    throw new RuntimeException();
                }
            }
            test2(array, true);
            test2(array, false);
        }
    }

    private static void test1(int[] array) {
        int[] array2 = new int[100];
        int j = 4;
        int i = 3;
        int k;
        for (k = 1; k < 2; k *= 2) {

        }
        int stride = k / 2;
        do {
            synchronized (new Object()) {
            }
            array2[j-1] = 42;
            array[j+1] = 42;
            j = i;
            i -= stride;
        } while (i >= 0);
    }

    private static void test2(int[] array, boolean flag) {
        int[] array2 = new int[100];
        int j = 4;
        int i = 3;
        int k;
        for (k = 1; k < 2; k *= 2) {

        }
        int stride = k / 2;
        if (flag) {
            volatileField = 42;
            array[0] = 42;
        } else {
            do {
                synchronized (new Object()) {
                }
                array2[j - 1] = 42;
                array[j + 1] = 42;
                j = i;
                i -= stride;
            } while (i >= 0);
        }
    }
}
