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
//            test1(9, 10, 1, true);
//            test1(9, 10, 1, false);
//            test2(9, 10, 1, true);
//            test2(9, 10, 1, false);
//            test3(9, 10, 1, true);
//            test3(9, 10, 1, false);
//            test4(9, 10, 1, true);
//            test4(9, 10, 1, false);
//            test5(9, 10, 1, true);
//            test5(9, 10, 1, false);
//            test6(9, 10, 1, true);
//            test6(9, 10, 1, false);
//            test7(9, 10, 1, true);
//            test7(9, 10, 1, false);
//            test8(9, 10, 1, true);
//            test8(9, 10, 1, false);
//            test9(9, 10, 1, true);
//            test9(9, 10, 1, false);
//            test10(9, 10, 1, true);
//            test10(9, 10, 1, false);
//            test11(9, 10, 1, true);
//            test11(9, 10, 1, false);
//            test12(9, 10, 1, true);
//            test12(9, 10, 1, false);
//            test13(9, 10, 1, true);
//            test13(9, 10, 1, false);
//            test14(8, 0, 1, true);
//            test14(8, 0, 1, false);
//            inlined14(0, 0);
//            test15(8, 0, 1, true);
//            test15(8, 0, 1, false);
//            inlined15(0, 0);
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
        }
//        try {
//            test1(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test2(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test3(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test4(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test5(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test6(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test7(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test8(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test9(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test10(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test11(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test12(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test13(-1, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test14(Integer.MAX_VALUE, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
//        try {
//            test15(Integer.MAX_VALUE, 10, 1, true);
//        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
//        }
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

    // produces 0 after macro expansion
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

    private static void notInlined(int[] array) {

    }
}
