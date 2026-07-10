/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug 8380166
 * @summary C2: crash in compiled code due to zero division because of widened CastII
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   ${test.main.class}
 * @run main ${test.main.class}
 *
 */

package compiler.integerArithmetic;

public class TestZeroDivModWidenedCastII {
    private static int intField;
    private static long longField;
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(0, 9, 1, true, false);
            test1(0, 9, 1, false, false);
            inlined1_2(9, 1, 1, true, 0);
            inlined1_3(0, 0);
            test2(0, 9, 1, true, false);
            test2(0, 9, 1, false, false);
            inlined2_2(9, 1, 1, true, 0);
            inlined2_3(0, 0);
            test3(0, 9, 1, true, false);
            test3(0, 9, 1, false, false);
            inlined3_2(9, 1, 1, true, 0);
            inlined3_3(0, 0);
            test4(0, 9, 1, true, false);
            test4(0, 9, 1, false, false);
            inlined4_2(9, 1, 1, true, 0);
            inlined4_3(0, 0);
            test5(0, 9, 1, true, false);
            test5(0, 9, 1, false, false);
            inlined5_2(9, 1, 1, true, 0);
            inlined5_3(0, 0);
            test6(0, 9, 1, true, false);
            test6(0, 9, 1, false, false);
            inlined6_2(9, 1, 1, true, 0);
            inlined6_3(0, 0);
            test7(0, 9, 1, true, false);
            test7(0, 9, 1, false, false);
            inlined7_2(9, 1, 1, true, 0);
            inlined7_3(0, 0);
            test8(0, 9, 1, true, false);
            test8(0, 9, 1, false, false);
            inlined8_2(9, 1, 1, true, 0);
            inlined8_3(0, 0);
            test9(0, 9, 1, true, false);
            test9(0, 9, 1, false, false);
            inlined9_2(9, 1, 1, true, 0);
            inlined9_3(0, 0);
            test10(0, 9, 1, false);
            inlined10_2(9, 1, 1, true, 0);
            inlined10_3(0, 0);
            test11(0, 9, 1, false);
            inlined11_2(9, 1, 1, true, 0);
            inlined11_3(0, 0);
            test12(0, 9, 1, false);
            inlined12_2(9, 1, 1, true, 0);
            inlined12_3(0, 0);
            test13(0, 9, 1, false);
            inlined13_2(9, 1, 1, true, 0);
            inlined13_3(0, 0);
            test14(0, 9, 1, false);
            inlined14_2(9, 1, 1, true, 0);
            inlined14_3(0, 0);
            test15(0, 9, 1, false);
            inlined15_2(9, 1, 1, true, 0);
            inlined15_3(0, 0);
            test16(0, 9, 1, false);
            inlined16_2(9, 1, 1, true, 0);
            inlined16_3(0, 0);
            test17(0, 9, 1, false);
            inlined17_2(9, 1, 1, true, 0);
            inlined17_3(0, 0);
        }
    }

    private static void test1(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined1_3(j, l);

        int i = inlined1(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined1_2(j, flag, i, flag3, m);
        } else {
            inlined1_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined1_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined1_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = 1 / (otherArray.length + 2);
        }
    }

    static int[] arrayField1 = new int[10];

    // produces -3 after macro expansion
    private static int inlined1(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField1[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField1[0] + array2[k] * (j - 10);
    }

    private static void test2(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined2_3(j, l);

        int i = inlined2(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined2_2(j, flag, i, flag3, m);
        } else {
            inlined2_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined2_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test2D = 1;

    private static void inlined2_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = test2D / (otherArray.length + 2);
        }
    }

    static int[] arrayField2 = new int[10];

    // produces 0 after macro expansion
    private static int inlined2(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField2[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField2[0] + array2[k] * (j - 10);
    }

    private static void test3(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined3_3(j, l);

        int i = inlined3(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined3_2(j, flag, i, flag3, m);
        } else {
            inlined3_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined3_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test3D = 1;

    private static void inlined3_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test3D / (otherArray.length + 2);
        }
    }

    static int[] arrayField3 = new int[10];

    // produces 0 after macro expansion
    private static int inlined3(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField3[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField3[0] + array2[k] * (j - 10);
    }

    private static void test4(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined4_3(j, l);

        int i = inlined4(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined4_2(j, flag, i, flag3, m);
        } else {
            inlined4_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined4_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test4D = 1;

    private static void inlined4_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = test4D % (otherArray.length + 2);
        }
    }

    static int[] arrayField4 = new int[10];

    // produces 0 after macro expansion
    private static int inlined4(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField4[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField4[0] + array2[k] * (j - 10);
    }

    private static void test5(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined5_3(j, l);

        int i = inlined5(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined5_2(j, flag, i, flag3, m);
        } else {
            inlined5_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined5_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test5D = 1;

    private static void inlined5_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test5D % (otherArray.length + 2);
        }
    }

    static int[] arrayField5 = new int[10];

    // produces 0 after macro expansion
    private static int inlined5(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField5[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField5[0] + array2[k] * (j - 10);
    }

    private static void test6(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined6_3(j, l);

        int i = inlined6(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined6_2(j, flag, i, flag3, m);
        } else {
            inlined6_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined6_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test6D = 1;

    private static void inlined6_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.divideUnsigned(test6D, (otherArray.length + 2));
        }
    }

    static int[] arrayField6 = new int[10];

    // produces 0 after macro expansion
    private static int inlined6(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField6[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField6[0] + array2[k] * (j - 10);
    }

    private static void test7(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined7_3(j, l);

        int i = inlined7(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined7_2(j, flag, i, flag3, m);
        } else {
            inlined7_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined7_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test7D = 1;

    private static void inlined7_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.divideUnsigned(test7D, (otherArray.length + 2));
        }
    }

    static int[] arrayField7 = new int[10];

    // produces 0 after macro expansion
    private static int inlined7(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField7[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField7[0] + array2[k] * (j - 10);
    }

    private static void test8(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined8_3(j, l);

        int i = inlined8(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined8_2(j, flag, i, flag3, m);
        } else {
            inlined8_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined8_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test8D = 1;

    private static void inlined8_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.remainderUnsigned(test8D, (otherArray.length + 2));
        }
    }

    static int[] arrayField8 = new int[10];

    // produces 0 after macro expansion
    private static int inlined8(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField8[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField8[0] + array2[k] * (j - 10);
    }

    private static void test9(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined9_3(j, l);

        int i = inlined9(k, flag2);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined9_2(j, flag, i, flag3, m);
        } else {
            inlined9_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined9_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test9D = 1;

    private static void inlined9_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.remainderUnsigned(test9D, (otherArray.length + 2));
        }
    }

    static int[] arrayField9 = new int[10];

    // produces 0 after macro expansion
    private static int inlined9(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField9[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField9[0] + array2[k] * (j - 10);
    }

    private static void test10(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined10_3(j, l);

        int i = inlined10(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined10_2(j, flag, i, flag3, m);
    }

    private static int inlined10_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined10_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = 1 / (otherArray.length + 2);
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField10 = new int[10];

    // produces -3 after macro expansion
    private static int inlined10(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField10[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField10[0] + array2[k] * (j - 10);
    }

    private static void test11(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined11_3(j, l);

        int i = inlined11(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined11_2(j, flag, i, flag3, m);
    }

    private static int inlined11_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test11D = 1;

    private static void inlined11_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test11D / (otherArray.length + 2);
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField11 = new int[10];

    // produces 0 after macro expansion
    private static int inlined11(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField11[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField11[0] + array2[k] * (j - 10);
    }

    private static void test12(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined12_3(j, l);

        int i = inlined12(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined12_2(j, flag, i, flag3, m);
    }

    private static int inlined12_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test12D = 1;

    private static void inlined12_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = test12D % (otherArray.length + 2);
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField12 = new int[10];

    // produces 0 after macro expansion
    private static int inlined12(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField12[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField12[0] + array2[k] * (j - 10);
    }

    private static void test13(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined13_3(j, l);

        int i = inlined13(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined13_2(j, flag, i, flag3, m);
    }

    private static int inlined13_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test13D = 1;

    private static void inlined13_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test13D % (otherArray.length + 2);
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField13 = new int[10];

    // produces 0 after macro expansion
    private static int inlined13(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField13[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField13[0] + array2[k] * (j - 10);
    }

    private static void test14(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined14_3(j, l);

        int i = inlined14(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined14_2(j, flag, i, flag3, m);
    }

    private static int inlined14_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test14D = 1;

    private static void inlined14_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.divideUnsigned(test14D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField14 = new int[10];

    // produces 0 after macro expansion
    private static int inlined14(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField14[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField14[0] + array2[k] * (j - 10);
    }

    private static void test15(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined15_3(j, l);

        int i = inlined15(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined15_2(j, flag, i, flag3, m);
    }

    private static int inlined15_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test15D = 1;

    private static void inlined15_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.divideUnsigned(test15D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField15 = new int[10];

    private static int inlined15(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField15[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField15[0] + array2[k] * (j - 10);
    }

    private static void test16(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined16_3(j, l);

        int i = inlined16(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined16_2(j, flag, i, flag3, m);
    }

    private static int inlined16_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test16D = 1;

    private static void inlined16_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.remainderUnsigned(test16D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField16 = new int[10];

    // produces 0 after macro expansion
    private static int inlined16(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField16[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField16[0] + array2[k] * (j - 10);
    }

    private static void test17(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined17_3(j, l);

        int i = inlined17(k, flag3);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined17_2(j, flag, i, flag3, m);
    }

    private static int inlined17_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test17D = 1;

    private static void inlined17_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.remainderUnsigned(test17D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField17 = new int[10];

    // produces 0 after macro expansion
    private static int inlined17(int k, boolean flag2) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField17[0] = -3;
        int[] array2;
        if (flag2) {
            array2 = new int[10];
        } else {
            array2 = new int[10];
        }
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField17[0] + array2[k] * (j - 10);
    }
}
