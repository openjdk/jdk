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
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=dontinline,TestArrayAccessAboveRCAfterRCCastIIEliminated::notInlined
 *                   compiler.rangechecks.TestArrayAccessAboveRCAfterRCCastIIEliminated
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=dontinline,TestArrayAccessAboveRCAfterRCCastIIEliminated::notInlined
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM compiler.rangechecks.TestArrayAccessAboveRCAfterRCCastIIEliminated
 * @run main compiler.rangechecks.TestArrayAccessAboveRCAfterRCCastIIEliminated
 * @run main/othervm -XX:CompileCommand=dontinline,TestArrayAccessAboveRCAfterRCCastIIEliminated::notInlined
 *                   compiler.rangechecks.TestArrayAccessAboveRCAfterRCCastIIEliminated
 *
 */

package compiler.rangechecks;

public class TestArrayAccessAboveRCAfterRCCastIIEliminated {
    private static int intField;
    private static long longField;
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(9, 10, 1, true);
            test1(9, 10, 1, false);
            test2(9, 10, 1, true);
            test2(9, 10, 1, false);
            test3(9, 10, 1, true);
            test3(9, 10, 1, false);
            test4(9, 10, 1, true);
            test4(9, 10, 1, false);
            test5(9, 10, 1, true);
            test5(9, 10, 1, false);
            test6(9, 10, 1, true);
            test6(9, 10, 1, false);
            test7(9, 10, 1, true);
            test7(9, 10, 1, false);
            test8(9, 10, 1, true);
            test8(9, 10, 1, false);
            test9(9, 10, 1, true);
            test9(9, 10, 1, false);
            test10(9, 10, 1, true);
            test10(9, 10, 1, false);
            test11(9, 10, 1, true);
            test11(9, 10, 1, false);
            test12(9, 10, 1, true);
            test12(9, 10, 1, false);
            test13(9, 10, 1, true);
            test13(9, 10, 1, false);
            test14(8, 0, 1, true);
            test14(8, 0, 1, false);
            inlined14(0, 0);
            test15(8, 0, 1, true);
            test15(8, 0, 1, false);
            inlined15(0, 0);
            test16(0, 9, 1, true, false);
            test16(0, 9, 1, false, false);
            inlined16_2(9, 1, 0, arrayField16, true, 0);
            inlined16_3(0, 0);
            test17(0, 9, 1, true, false);
            test17(0, 9, 1, false, false);
            inlined17_2(9, 1, 1, true, 0);
            inlined17_3(0, 0);
            test18(0, 9, 1, true, false);
            test18(0, 9, 1, false, false);
            inlined18_2(9, 1, 0, arrayField18, true, 0);
            inlined18_3(0, 0);
            test19(0, 9, 1, true, false);
            test19(0, 9, 1, false, false);
            inlined19_2(9, 1, 1, true, 0);
            inlined19_3(0, 0);
            test20(0, 9, 1, true, false);
            test20(0, 9, 1, false, false);
            inlined20_2(9, 1, 1, true, 0);
            inlined20_3(0, 0);
            test21(0, 9, 1, true, false);
            test21(0, 9, 1, false, false);
            inlined21_2(9, 1, 1, true, 0);
            inlined21_3(0, 0);
            test22(0, 9, 1, true, false);
            test22(0, 9, 1, false, false);
            inlined22_2(9, 1, 1, true, 0);
            inlined22_3(0, 0);
            test23(0, 9, 1, true, false);
            test23(0, 9, 1, false, false);
            inlined23_2(9, 1, 1, true, 0);
            inlined23_3(0, 0);
            test24(0, 9, 1, true, false);
            test24(0, 9, 1, false, false);
            inlined24_2(9, 1, 1, true, 0);
            inlined24_3(0, 0);
            test25(0, 9, 1, true, false);
            test25(0, 9, 1, false, false);
            inlined25_2(9, 1, 1, true, 0);
            inlined25_3(0, 0);
            test26(0, 9, 1, true, false);
            test26(0, 9, 1, false, false);
            inlined26_2(9, 1, 1, true, 0);
            inlined26_3(0, 0);
            test27(0, 9, 1, true, false, 10);
            test27(0, 9, 1, false, false, 10);
            inlined27_2(9, 1, 0, arrayField27, true, 0);
            inlined27_3(0, 0);
            // test28(0, 9, 1, true, false, 10, false);
            // test28(0, 9, 1, false, false, 10, false);
            // inlined28_2(9, 1, 0, arrayField28, true, 0, false);
            // inlined28_3(0, 0);
            test29(0, 9, 1, true, false, 10, false);
            test29(0, 9, 1, false, false, 10, false);
            inlined29_2(9, 1, 0, 0, longArrayField29, 0, true, true, false);
            inlined29_2(9, 1, 0, 0, longArrayField29, 0, false, true, false);
            inlined29_3(0, 0);
            test30(0, 9, 1, false);
            inlined30_2(9, 1, 1, true, 0);
            inlined30_3(0, 0);
            test31(0, 9, 1, false);
            inlined31_2(9, 1, 1, true, 0);
            inlined31_3(0, 0);
            test32(0, 9, 1, false);
            inlined32_2(9, 1, 1, true, 0);
            inlined32_3(0, 0);
            test33(0, 9, 1, false);
            inlined33_2(9, 1, 1, true, 0);
            inlined33_3(0, 0);
            test34(0, 9, 1, false);
            inlined34_2(9, 1, 1, true, 0);
            inlined34_3(0, 0);
            test35(0, 9, 1, false);
            inlined35_2(9, 1, 1, true, 0);
            inlined35_3(0, 0);
            test36(0, 9, 1, false);
            inlined36_2(9, 1, 1, true, 0);
            inlined36_3(0, 0);
        }
        try {
            test1(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test2(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test3(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test4(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test5(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test6(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test7(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test8(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test9(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test10(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test11(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test12(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test13(-1, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test14(Integer.MAX_VALUE, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
        try {
            test15(Integer.MAX_VALUE, 10, 1, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
        }
    }

    private static void test1(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = array[otherArray.length];
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = array[otherArray.length];
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test2(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = 1 / (otherArray.length + 1);
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = 1 / (otherArray.length + 1);
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test3(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = 1L / (otherArray.length + 1);
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = 1L / (otherArray.length + 1);
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test4(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = 1 % (otherArray.length + 1);
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = 1 % (otherArray.length + 1);
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test5(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = 1L % (otherArray.length + 1);
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = 1L % (otherArray.length + 1);
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test6(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = 1 % (otherArray.length + 1) + 1 / (otherArray.length + 1);
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = 1 % (otherArray.length + 1) + 1 / (otherArray.length + 1);
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test7(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = 1L % (otherArray.length + 1) + 1L / (otherArray.length + 1);
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = 1L % (otherArray.length + 1) + 1L / (otherArray.length + 1);
        }
        for (int k = 0; k < 10; k++) {

        }
    }
    private static void test8(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = Integer.divideUnsigned(1, (otherArray.length + 1));
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = Integer.divideUnsigned(1, (otherArray.length + 1));
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test9(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = Long.divideUnsigned(1L, (otherArray.length + 1));
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = Long.divideUnsigned(1L, (otherArray.length + 1));
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test10(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = Integer.remainderUnsigned(1, (otherArray.length + 1));
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = Integer.remainderUnsigned(1, (otherArray.length + 1));
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test11(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = Long.remainderUnsigned(1L, (otherArray.length + 1));
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = Long.remainderUnsigned(1L, (otherArray.length + 1));
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test12(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = Integer.divideUnsigned(1, (otherArray.length + 1)) +
                    Integer.remainderUnsigned(1, (otherArray.length + 1));
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            intField = Integer.divideUnsigned(1, (otherArray.length + 1)) +
                    Integer.remainderUnsigned(1, (otherArray.length + 1));
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    private static void test13(int i, int j, int flag, boolean flag2) {
        i = Math.min(i, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
        }
        if (flag2) {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = Long.remainderUnsigned(1L, (otherArray.length + 1)) +
                    Long.divideUnsigned(1L, (otherArray.length + 1));
        } else {
            float[] newArray = new float[j];
            newArray[i] = 42;
            float[] otherArray = new float[i];
            if (flag == 0) {
            }
            longField = Long.remainderUnsigned(1L, (otherArray.length + 1)) +
                    Long.divideUnsigned(1L, (otherArray.length + 1));
        }
        for (int k = 0; k < 10; k++) {

        }
    }

    // Widened range check cast type after loop opts causes control dependency to be lost
    private static void test14(int i, int j, int flag, boolean flag2) {
        int l = 0;
        for (; l < 10; l++);
        j = inlined14(j, l);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            float[] newArray = new float[10];
            newArray[i+j] = 42; // i+j in [0, 9]
            float[] otherArray = new float[i+j]; // i+j in [0, max]
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length];
        } else {
            float[] newArray = new float[10];
            newArray[i+j] = 42; // i+j in [0, 9]
            float[] otherArray = new float[i+j]; // i+j in [0, max]
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length];
        }
    }

    private static int inlined14(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void test15(int i, int j, int flag, boolean flag2) {
        i = Integer.max(i, Integer.MIN_VALUE + 1);
        int l = 0;
        for (; l < 10; l++);
        j = inlined15(j, l);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            float[] newArray = new float[10];
            newArray[i+j] = 42; // i+j in [0, 9]
            float[] otherArray = new float[i+j]; // i+j in [0, max]
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length];
        } else {
            float[] newArray = new float[10];
            newArray[i+j] = 42; // i+j in [0, 9]
            float[] otherArray = new float[i+j]; // i+j in [0, max]
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length];
        }
    }

    private static int inlined15(int j, int l) {
        if (l == 10) {
            j = Integer.max(j, Integer.MIN_VALUE + 10);
        }
        return j;
    }

    private static void test16(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined16_3(j, l);

        int i = inlined16(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined16_2(j, flag, i, array, flag3, m);
        } else {
            inlined16_2(j, flag, i, array, flag3, m);
        }
    }

    private static int inlined16_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined16_2(int j, int flag, int i, int[] array, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [min+1..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length];
        }
    }

    static int[] arrayField16 = new int[10];

    // produces Integer.MIN_VALUE after macro expansion
    private static int inlined16(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField16[0] = Integer.MIN_VALUE;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField16[0] + array2[k] * (j - 10);
    }

    private static void test17(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined17_3(j, l);

        int i = inlined17(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined17_2(j, flag, i, flag3, m);
        } else {
            inlined17_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined17_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined17_2(int j, int flag, int i, boolean flag3, int m) {
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

    static int[] arrayField17 = new int[10];

    // produces -3 after macro expansion
    private static int inlined17(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField17[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField17[0] + array2[k] * (j - 10);
    }

    static final int[] test18Array = new int[10];

    private static void test18(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined18_3(j, l);

        int i = inlined18(k);
        j = Integer.min(j, 9);
        int[] array = test18Array;
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined18_2(j, flag, i, array, flag3, m);
        } else {
            inlined18_2(j, flag, i, array, flag3, m);
        }
    }

    private static int inlined18_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined18_2(int j, int flag, int i, int[] array, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [min+1..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length];
        }
    }

    static int[] arrayField18 = new int[10];

    // produces Integer.MIN_VALUE after macro expansion
    private static int inlined18(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField18[0] = Integer.MIN_VALUE;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField18[0] + array2[k] * (j - 10);
    }

    private static void test19(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined19_3(j, l);

        int i = inlined19(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined19_2(j, flag, i, flag3, m);
        } else {
            inlined19_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined19_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test19D = 1;

    private static void inlined19_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = test19D / (otherArray.length + 2);
        }
    }

    static int[] arrayField19 = new int[10];

    // produces 0 after macro expansion
    private static int inlined19(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField19[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField19[0] + array2[k] * (j - 10);
    }

    private static void test20(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined20_3(j, l);

        int i = inlined20(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined20_2(j, flag, i, flag3, m);
        } else {
            inlined20_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined20_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test20D = 1;

    private static void inlined20_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test20D / (otherArray.length + 2);
        }
    }

    static int[] arrayField20 = new int[10];

    // produces 0 after macro expansion
    private static int inlined20(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField20[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField20[0] + array2[k] * (j - 10);
    }

    private static void test21(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined21_3(j, l);

        int i = inlined21(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined21_2(j, flag, i, flag3, m);
        } else {
            inlined21_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined21_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test21D = 1;

    private static void inlined21_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = test21D % (otherArray.length + 2);
        }
    }

    static int[] arrayField21 = new int[10];

    // produces 0 after macro expansion
    private static int inlined21(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField21[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField21[0] + array2[k] * (j - 10);
    }

    private static void test22(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined22_3(j, l);

        int i = inlined22(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined22_2(j, flag, i, flag3, m);
        } else {
            inlined22_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined22_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test22D = 1;

    private static void inlined22_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test22D % (otherArray.length + 2);
        }
    }

    static int[] arrayField22 = new int[10];

    // produces 0 after macro expansion
    private static int inlined22(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField22[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField22[0] + array2[k] * (j - 10);
    }

    private static void test23(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined23_3(j, l);

        int i = inlined23(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined23_2(j, flag, i, flag3, m);
        } else {
            inlined23_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined23_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test23D = 1;

    private static void inlined23_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.divideUnsigned(test23D, (otherArray.length + 2));
        }
    }

    static int[] arrayField23 = new int[10];

    // produces 0 after macro expansion
    private static int inlined23(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField23[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField23[0] + array2[k] * (j - 10);
    }

    private static void test24(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined24_3(j, l);

        int i = inlined24(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined24_2(j, flag, i, flag3, m);
        } else {
            inlined24_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined24_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test24D = 1;

    private static void inlined24_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.divideUnsigned(test24D, (otherArray.length + 2));
        }
    }

    static int[] arrayField24 = new int[10];

    // produces 0 after macro expansion
    private static int inlined24(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField24[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField24[0] + array2[k] * (j - 10);
    }

    private static void test25(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined25_3(j, l);

        int i = inlined25(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined25_2(j, flag, i, flag3, m);
        } else {
            inlined25_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined25_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test25D = 1;

    private static void inlined25_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.remainderUnsigned(test25D, (otherArray.length + 2));
        }
    }

    static int[] arrayField25 = new int[10];

    // produces 0 after macro expansion
    private static int inlined25(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField25[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField25[0] + array2[k] * (j - 10);
    }

    private static void test26(int k, int j, int flag, boolean flag2, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined26_3(j, l);

        int i = inlined26(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined26_2(j, flag, i, flag3, m);
        } else {
            inlined26_2(j, flag, i, flag3, m);
        }
    }

    private static int inlined26_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test26D = 1;

    private static void inlined26_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.remainderUnsigned(test26D, (otherArray.length + 2));
        }
    }

    static int[] arrayField26 = new int[10];

    // produces 0 after macro expansion
    private static int inlined26(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField26[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField26[0] + array2[k] * (j - 10);
    }

    private static void test27(int k, int j, int flag, boolean flag2, boolean flag3, int arraySize) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined27_3(j, l);

        int i = inlined27(k);
        j = Integer.min(j, 9);
        arraySize = Integer.max(arraySize, 10);
        int[] array = new int[arraySize];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined27_2(j, flag, i, array, flag3, m);
        } else {
            inlined27_2(j, flag, i, array, flag3, m);
        }
    }

    private static int inlined27_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined27_2(int j, int flag, int i, int[] array, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [min+1..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length];
        }
    }

    static int[] arrayField27 = new int[10];

    // produces Integer.MIN_VALUE after macro expansion
    private static int inlined27(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField27[0] = Integer.MIN_VALUE;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField27[0] + array2[k] * (j - 10);
    }

    private static void test28(int k, int j, int flag, boolean flag2, boolean flag3, int arraySize, boolean flag4) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined28_3(j, l); // 1

        int i = inlined28(k);
        j = Integer.min(j, 9);
        arraySize = Integer.max(arraySize, m * 10);
        int[] array = new int[arraySize];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        if (flag2) {
            inlined28_2(j, flag, i, array, flag3, m, flag4);
        } else {
            inlined28_2(j, flag, i, array, flag3, m, flag4);
        }
    }

    private static int inlined28_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined28_2(int j, int flag, int i, int[] array, boolean flag3, int m, boolean flag4) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [min+1..10]
            // RC i <u (CastII j [min..max]) + 1
            float v = newArray[i + m]; // i + m <u j + 1
            if (flag4) {
                throw new RuntimeException("never taken " + v);
            }
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = array[otherArray.length]; // array.length >= 10, otherArray.length < 10
        }
    }

    static int[] arrayField28 = new int[10];

    // produces Integer.MAX_VALUE - 5 after macro expansion
    private static int inlined28(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField28[0] = Integer.MAX_VALUE - 5;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField28[0] + array2[k] * (j - 10);
    }

    private static void test29(int k, int j, int flag, boolean flag2, boolean flag3, int arraySize, boolean flag4) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined29_3(j, l);
        int i1 = inlined29(k, Integer.MAX_VALUE - 5);
        int i2 = inlined29(k, Integer.MAX_VALUE - 5);

        j = Integer.min(j, 9);
        arraySize = Integer.max(arraySize, m * 10);
        long[] array = new long[arraySize];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined29_2(j, flag, i1, i2, array, m, flag2, flag3, flag4);
    }

    private static void inlined29_2(int j, int flag, int i1, int i2, long[] array, int m, boolean flag2, boolean flag3, boolean flag4) {
        if (flag3) {
            int idx;
            if (flag2) {
                float[] newArray = new float[j + 1]; // j + 1 in [min+1..10]
                // RC i <u (CastII j [min..max]) + 1
                float v = newArray[i1 + m];
                if (flag4) {
                    throw new RuntimeException("never taken " + v);
                }
                float[] otherArray = new float[i1 + m];
                idx = otherArray.length;
            } else {
                float[] newArray = new float[j + 1]; // j + 1 in [min+1..10]
                // RC i <u (CastII j [min..max]) + 1
                float v = newArray[i2 + m];
                if (flag4) {
                    throw new RuntimeException("never taken " + v);
                }
                float[] otherArray = new float[i2 + m];
                idx = otherArray.length;
            }
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = array[idx];
        } else {
            volatileField = 42;
        }
    }

    private static int inlined29_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int[] arrayField29 = new int[10];
    static long[] longArrayField29 = new long[10];

    // produces Integer.MIN_VALUE after macro expansion
    private static int inlined29(int k, int v) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField29[0] = v;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField29[0] + array2[k] * (j - 10);
    }

    private static void test30(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined30_3(j, l);

        int i = inlined30(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined30_2(j, flag, i, flag3, m);
    }

    private static int inlined30_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    private static void inlined30_2(int j, int flag, int i, boolean flag3, int m) {
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

    static int[] arrayField30 = new int[10];

    // produces -3 after macro expansion
    private static int inlined30(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField30[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField30[0] + array2[k] * (j - 10);
    }

    private static void test31(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined31_3(j, l);

        int i = inlined31(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined31_2(j, flag, i, flag3, m);
    }

    private static int inlined31_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test31D = 1;

    private static void inlined31_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test31D / (otherArray.length + 2);
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField31 = new int[10];

    // produces 0 after macro expansion
    private static int inlined31(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField31[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField31[0] + array2[k] * (j - 10);
    }

    private static void test32(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined32_3(j, l);

        int i = inlined32(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined32_2(j, flag, i, flag3, m);
    }

    private static int inlined32_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test32D = 1;

    private static void inlined32_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = test32D % (otherArray.length + 2);
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField32 = new int[10];

    // produces 0 after macro expansion
    private static int inlined32(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField32[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField32[0] + array2[k] * (j - 10);
    }

    private static void test33(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined33_3(j, l);

        int i = inlined33(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined33_2(j, flag, i, flag3, m);
    }

    private static int inlined33_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test33D = 1;

    private static void inlined33_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = test33D % (otherArray.length + 2);
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField33 = new int[10];

    // produces 0 after macro expansion
    private static int inlined33(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField33[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField33[0] + array2[k] * (j - 10);
    }

    private static void test34(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined34_3(j, l);

        int i = inlined34(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined34_2(j, flag, i, flag3, m);
    }

    private static int inlined34_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test34D = 1;

    private static void inlined34_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.divideUnsigned(test34D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField34 = new int[10];

    // produces 0 after macro expansion
    private static int inlined34(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField34[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField34[0] + array2[k] * (j - 10);
    }

    private static void test35(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined35_3(j, l);

        int i = inlined35(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined35_2(j, flag, i, flag3, m);
    }

    private static int inlined35_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test35D = 1;

    private static void inlined35_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.divideUnsigned(test35D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField35 = new int[10];

    private static int inlined35(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField35[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField35[0] + array2[k] * (j - 10);
    }

    private static void test36(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined36_3(j, l);

        int i = inlined36(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined36_2(j, flag, i, flag3, m);
    }

    private static int inlined36_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static int test36D = 1;

    private static void inlined36_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            intField = Integer.remainderUnsigned(test36D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField36 = new int[10];

    // produces 0 after macro expansion
    private static int inlined36(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField36[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField36[0] + array2[k] * (j - 10);
    }

    private static void test37(int k, int j, int flag, boolean flag3) {
        int l = 0;
        for (; l < 10; l++);
        int m = inlined37_3(j, l);

        int i = inlined37(k);
        j = Integer.min(j, 9);
        int[] array = new int[10];
        notInlined(array);
        if (flag == 0) {
            throw new RuntimeException("never taken");
        }
        inlined37_2(j, flag, i, flag3, m);
    }

    private static int inlined37_3(int j, int l) {
        if (l == 10) {
            j = 1;
        }
        return j;
    }

    static long test37D = 1;

    private static void inlined37_2(int j, int flag, int i, boolean flag3, int m) {
        if (flag3) {
            float[] newArray = new float[j + 1]; // j + 1 in [0..10]
            // RC i <u (CastII j [min..max]) + 1
            newArray[i + m] = 42; // i + m in [0..9]
            float[] otherArray = new float[i + m];
            if (flag == 0) {
                throw new RuntimeException("never taken");
            }
            longField = Long.remainderUnsigned(test37D, (otherArray.length + 2));
        } else {
            volatileField = 42;
        }
    }

    static int[] arrayField37 = new int[10];

    // produces 0 after macro expansion
    private static int inlined37(int k) {
        k = Integer.max(0, Integer.min(k, 9));
        arrayField37[0] = -3;
        int[] array2 = new int[10];
        int j;
        for (j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        return arrayField37[0] + array2[k] * (j - 10);
    }

    private static void notInlined(Object array) {

    }
}
