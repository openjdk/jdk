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
 * @bug 8332827
 * @summary [REDO] C2: crash in compiled code because of dependency on removed range check CastIIs
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=dontinline,TestNullDivModWidenedCastII::notInlined
 *                   compiler.integerArithmetic.TestNullDivModWidenedCastII
 * @run main compiler.integerArithmetic.TestNullDivModWidenedCastII
 * @run main/othervm -XX:CompileCommand=dontinline,TestNullDivModWidenedCastII::notInlined
 *                   compiler.integerArithmetic.TestNullDivModWidenedCastII
 *
 */

package compiler.integerArithmetic;

public class TestNullDivModWidenedCastII {
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
        }
    }

    private static void test1(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined1_3(j, l);

        int i = inlined1(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined1(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField1[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined2(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined2(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField2[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined3(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined3(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField3[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined4(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined4(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField4[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined5(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined5(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField5[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined6(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined6(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField6[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined7(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined7(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField7[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined8(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined8(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField8[0] = -3;
        int[] array2 = new int[10];
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

        int i = inlined9(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
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
    private static int inlined9(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField9[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField9[0] + array2[k] * (j - 10);
    }

    private static void notInlined(Object array) {

    }
}
