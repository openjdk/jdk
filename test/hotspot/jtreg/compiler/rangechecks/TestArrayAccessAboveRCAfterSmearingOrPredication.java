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
 * @bug 8319793
 * @summary Replacing a test with a dominating test can cause an array load to float above a range check that guards it
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:-TieredCompilation TestArrayAccessAboveRCAfterSmearingOrPredication
 */


public class TestArrayAccessAboveRCAfterSmearingOrPredication {
    private static int field;
    private static int flagField;
    private static volatile int volatileField;

    public static void main(String[] args) {
        float[] array = new float[100];
        for (int i = 0; i < 20_000; i++) {
            testRangeCheckSmearing(array, 0, 1, true, true, true);
            testRangeCheckSmearing(array, 0, 1, true, false, true);
            testRangeCheckSmearing(array, 0, 1, false, false, true);
            testRangeCheckSmearing(array, 0, 1, true, true, false);
            testRangeCheckSmearing(array, 0, 1, true, false, false);
            testRangeCheckSmearing(array, 0, 1, false, false, false);
            testHelper(0);

            testLoopPredication(array, 0, 1, true, true, true);
            testLoopPredication(array, 0, 1, true, false, true);
            testLoopPredication(array, 0, 1, false, false, true);
            testLoopPredication(array, 0, 1, true, true, false);
            testLoopPredication(array, 0, 1, true, false, false);
            testLoopPredication(array, 0, 1, false, false, false);
        }
        try {
            testRangeCheckSmearing(array, Integer.MAX_VALUE, 1, false, false, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            testLoopPredication(array, Integer.MAX_VALUE, 1, false, false, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
    }

    private static float testRangeCheckSmearing(float[] array, int i, int flag, boolean flag2, boolean flag3, boolean flag4) {
        if (array == null) {
        }
        flagField = flag;
        int j;
        for (j = 0; j < 10; j++) {
        }
        for (int k = 0; k < 10; k++) {
            for (int l = 0; l < 10; l++) {
            }
        }
        testHelper(j);
        float v = 0;
        if (flag == 1) {
            if (flag4) {
                v += array[i];
                if (flag2) {
                    if (flag3) {
                        field = 0x42;
                    }
                }
                if (flagField == 1) {
                    v += array[i];
                }
            } else {
                v += array[i];
                if (flag2) {
                    if (flag3) {
                        field = 0x42;
                    }
                }
                if (flagField == 1) {
                    v += array[i];
                }
            }
        }
        return v;
    }

    private static void testHelper(int j) {
        if (j == 10) {
            return;
        }
        flagField = 0;
    }

    private static float testLoopPredication(float[] array, int i, int flag, boolean flag2, boolean flag3, boolean flag4) {
        i = Math.min(i, Integer.MAX_VALUE - 2);
        if (array == null) {
        }
        flagField = flag;
        int j;
        for (j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {
            }
        }
        testHelper(j);

        float v = 0;
        if (flag == 1) {
            if (flag4) {
                float dummy = array[i];
                dummy = array[i + 2];
                if (flag2) {
                    if (flag3) {
                        field = 0x42;
                    }
                }
                if (flagField == 1) {
                    for (int m = 0; m < 3; m++) {
                        v += array[i + m];
                    }
                }
                volatileField = 42;
            } else {
                float dummy = array[i];
                dummy = array[i + 2];
                if (flag2) {
                    if (flag3) {
                        field = 0x42;
                    }
                }
                if (flagField == 1) {
                    for (int m = 0; m < 3; m++) {
                        v += array[i + m];
                    }
                }
                volatileField = 42;
            }
        }

        return v;
    }
}
