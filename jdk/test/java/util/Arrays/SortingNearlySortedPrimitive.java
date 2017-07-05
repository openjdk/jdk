/*
 * Copyright 2015 Goldman Sachs.
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests the sorting of a large array of sorted primitive values,
 *          predominently for cases where the array is nearly sorted. This tests
 *          code that detects patterns in the array to determine if it is nearly
 *          sorted and if so employs and optimizes merge sort rather than a
 *          Dual-Pivot QuickSort.
 *
 * @run testng SortingNearlySortedPrimitive
 */

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.function.Supplier;

public class SortingNearlySortedPrimitive {
    private static final int ARRAY_SIZE = 1_000_000;

    @DataProvider(name = "arrays")
    public Object[][] createData() {
        return new Object[][]{
                {"hiZeroLowTest", (Supplier<int[]>) this::hiZeroLowData},
                {"endLessThanTest", (Supplier<int[]>) this::endLessThanData},
                {"highFlatLowTest", (Supplier<int[]>) this::highFlatLowData},
                {"identicalTest", (Supplier<int[]>) this::identicalData},
                {"sortedReversedSortedTest", (Supplier<int[]>) this::sortedReversedSortedData},
                {"pairFlipTest", (Supplier<int[]>) this::pairFlipData},
                {"zeroHiTest", (Supplier<int[]>) this::zeroHiData},
        };
    }

    @Test(dataProvider = "arrays")
    public void runTests(String testName, Supplier<int[]> dataMethod) throws Exception {
        int[] intSourceArray = dataMethod.get();

        // Clone source array to ensure it is not modified
        this.sortAndAssert(intSourceArray.clone());
        this.sortAndAssert(floatCopyFromInt(intSourceArray));
        this.sortAndAssert(doubleCopyFromInt(intSourceArray));
        this.sortAndAssert(longCopyFromInt(intSourceArray));
        this.sortAndAssert(shortCopyFromInt(intSourceArray));
        this.sortAndAssert(charCopyFromInt(intSourceArray));
    }

    private float[] floatCopyFromInt(int[] src) {
        float[] result = new float[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }

    private double[] doubleCopyFromInt(int[] src) {
        double[] result = new double[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }

    private long[] longCopyFromInt(int[] src) {
        long[] result = new long[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }

    private short[] shortCopyFromInt(int[] src) {
        short[] result = new short[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) src[i];
        }
        return result;
    }

    private char[] charCopyFromInt(int[] src) {
        char[] result = new char[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (char) src[i];
        }
        return result;
    }

    private void sortAndAssert(int[] array) {
        Arrays.sort(array);
        for (int i = 1; i < ARRAY_SIZE; i++) {
            if (array[i] < array[i - 1]) {
                throw new AssertionError("not sorted");
            }
        }
        Assert.assertEquals(ARRAY_SIZE, array.length);
    }

    private void sortAndAssert(char[] array) {
        Arrays.sort(array);
        for (int i = 1; i < ARRAY_SIZE; i++) {
            if (array[i] < array[i - 1]) {
                throw new AssertionError("not sorted");
            }
        }
        Assert.assertEquals(ARRAY_SIZE, array.length);
    }

    private void sortAndAssert(short[] array) {
        Arrays.sort(array);
        for (int i = 1; i < ARRAY_SIZE; i++) {
            if (array[i] < array[i - 1]) {
                throw new AssertionError("not sorted");
            }
        }
        Assert.assertEquals(ARRAY_SIZE, array.length);
    }

    private void sortAndAssert(double[] array) {
        Arrays.sort(array);
        for (int i = 1; i < ARRAY_SIZE; i++) {
            if (array[i] < array[i - 1]) {
                throw new AssertionError("not sorted");
            }
        }
        Assert.assertEquals(ARRAY_SIZE, array.length);
    }

    private void sortAndAssert(float[] array) {
        Arrays.sort(array);
        for (int i = 1; i < ARRAY_SIZE; i++) {
            if (array[i] < array[i - 1]) {
                throw new AssertionError("not sorted");
            }
        }
        Assert.assertEquals(ARRAY_SIZE, array.length);
    }

    private void sortAndAssert(long[] array) {
        Arrays.sort(array);
        for (int i = 1; i < ARRAY_SIZE; i++) {
            if (array[i] < array[i - 1]) {
                throw new AssertionError("not sorted");
            }
        }
        Assert.assertEquals(ARRAY_SIZE, array.length);
    }

    private int[] zeroHiData() {
        int[] array = new int[ARRAY_SIZE];

        int threeQuarters = (int) (ARRAY_SIZE * 0.75);
        for (int i = 0; i < threeQuarters; i++) {
            array[i] = 0;
        }
        int k = 1;
        for (int i = threeQuarters; i < ARRAY_SIZE; i++) {
            array[i] = k;
            k++;
        }

        return array;
    }

    private int[] hiZeroLowData() {
        int[] array = new int[ARRAY_SIZE];

        int oneThird = ARRAY_SIZE / 3;
        for (int i = 0; i < oneThird; i++) {
            array[i] = i;
        }
        int twoThirds = oneThird * 2;
        for (int i = oneThird; i < twoThirds; i++) {
            array[i] = 0;
        }
        for (int i = twoThirds; i < ARRAY_SIZE; i++) {
            array[i] = oneThird - i + twoThirds;
        }
        return array;
    }

    private int[] highFlatLowData() {
        int[] array = new int[ARRAY_SIZE];

        int oneThird = ARRAY_SIZE / 3;
        for (int i = 0; i < oneThird; i++) {
            array[i] = i;
        }
        int twoThirds = oneThird * 2;
        int constant = oneThird - 1;
        for (int i = oneThird; i < twoThirds; i++) {
            array[i] = constant;
        }
        for (int i = twoThirds; i < ARRAY_SIZE; i++) {
            array[i] = constant - i + twoThirds;
        }

        return array;
    }

    private int[] identicalData() {
        int[] array = new int[ARRAY_SIZE];
        int listNumber = 24;

        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = listNumber;
        }

        return array;
    }

    private int[] endLessThanData() {
        int[] array = new int[ARRAY_SIZE];

        for (int i = 0; i < ARRAY_SIZE - 1; i++) {
            array[i] = 3;
        }
        array[ARRAY_SIZE - 1] = 1;

        return array;
    }

    private int[] sortedReversedSortedData() {
        int[] array = new int[ARRAY_SIZE];

        for (int i = 0; i < ARRAY_SIZE / 2; i++) {
            array[i] = i;
        }
        int num = 0;
        for (int i = ARRAY_SIZE / 2; i < ARRAY_SIZE; i++) {
            array[i] = ARRAY_SIZE - num;
            num++;
        }

        return array;
    }

    private int[] pairFlipData() {
        int[] array = new int[ARRAY_SIZE];

        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = i;
        }
        for (int i = 0; i < ARRAY_SIZE; i += 2) {
            int temp = array[i];
            array[i] = array[i + 1];
            array[i + 1] = temp;
        }

        return array;
    }
}
