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
 * @bug 8324517
 * @summary C2: out of bound array load because of dependency on removed range check CastIIs
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=dontinline,TestArrayAccessAboveRCAfterRCCastIIEliminated::notInlined
 *                   TestArrayAccessAboveRCAfterRCCastIIEliminated
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=dontinline,TestArrayAccessAboveRCAfterRCCastIIEliminated::notInlined
 *                   -XX:+StressIGVN TestArrayAccessAboveRCAfterRCCastIIEliminated
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=dontinline,TestArrayAccessAboveRCAfterRCCastIIEliminated::notInlined
 *                   -XX:+StressIGVN -XX:StressSeed=94546681 TestArrayAccessAboveRCAfterRCCastIIEliminated
 * @run main/othervm TestArrayAccessAboveRCAfterRCCastIIEliminated
 * @run main/othervm -XX:CompileCommand=dontinline,TestArrayAccessAboveRCAfterRCCastIIEliminated::notInlined
 *                   TestArrayAccessAboveRCAfterRCCastIIEliminated
 *
 */

public class TestArrayAccessAboveRCAfterRCCastIIEliminated {
    private static int intField;
    private static long longField;
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] array = new int[100];
        for (int i = 0; i < 20_000; i++) {
            test1(9, 10, 1, true);
            test1(9, 10, 1, false);
            test2(9, 10, 1, true);
            test2(9, 10, 1, false);
            test3(9, 10, 1, true);
            test3(9, 10, 1, false);
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

    private static void notInlined(int[] array) {

    }
}
