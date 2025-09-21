/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @compile/module=java.base java/util/SortingHelper.java
 * @bug 6880672 6896573 6899694 6976036 7013585 7018258 8003981 8226297 8266431
 * @build Sorting
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_arraySort,_arrayPartition Sorting -shortrun
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=CompileThresholdScaling,java.util.DualPivotQuicksort::sort,0.0001 Sorting -shortrun
 * @summary Exercise Arrays.sort, Arrays.parallelSort
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 */

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;
import java.util.SortingHelper;

public class Sorting {

    private static final PrintStream out = System.out;
    private static final PrintStream err = System.err;

    // Lengths of arrays for [mixed] insertion sort
    private static final int[] RUN_LENGTHS =
        { 1, 2, 14, 100, 500, 1_000 };

    // Lengths of arrays for short run
    private static final int[] SHORT_LENGTHS =
        { 1, 2, 14, 100, 500, 1_000, 11_000 };

    // Lengths of arrays for long run (default)
    private static final int[] LONG_LENGTHS =
        { 1, 2, 14, 100, 500, 1_000, 11_000, 50_000};

    // Initial random values for short run
    private static final TestRandom[] SHORT_RANDOMS =
        {TestRandom.C0FFEE};

    // Initial random values for long run (default)
    private static final TestRandom[] LONG_RANDOMS =
        {TestRandom.DEDA, TestRandom.BABA, TestRandom.C0FFEE};

    // Constant to fill the left part of array
    private static final int A380 = 0xA380;

    // Constant to fill the right part of array
    private static final int B747 = 0xB747;

    private final SortingHelper sortingHelper;
    private final TestRandom[] randoms;
    private final int[] lengths;
    private final boolean withMin;
    private Object[] gold;
    private Object[] test;

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        boolean shortRun = args.length > 0 && args[0].equals("-shortrun");

        int[] lengths = shortRun ? SHORT_LENGTHS : LONG_LENGTHS;
        TestRandom[] randoms = shortRun ? SHORT_RANDOMS : LONG_RANDOMS;

        new Sorting(SortingHelper.INSERTION_SORT, randoms, false).testBase();
        new Sorting(SortingHelper.MIXED_INSERTION_SORT, randoms, true).testBase();
        new Sorting(SortingHelper.MERGING_SORT, randoms, lengths).testStructured();

        new Sorting(SortingHelper.RADIX_SORT, randoms, lengths).testBase();
        new Sorting(SortingHelper.HEAP_SORT, randoms, lengths).testBase();
        new Sorting(SortingHelper.COUNTING_SORT, randoms, lengths).testBase();

        new Sorting(SortingHelper.DUAL_PIVOT_QUICKSORT, randoms, lengths).testCore();
        new Sorting(SortingHelper.PARALLEL_QUICKSORT, randoms, lengths).testCore();

        new Sorting(SortingHelper.ARRAYS_SORT, randoms, lengths).testAll();
        new Sorting(SortingHelper.ARRAYS_PARALLEL_SORT, randoms, lengths).testAll();

        long end = System.currentTimeMillis();
        out.format("PASSED in %d sec.\n", (end - start) / 1_000);
    }

    private Sorting(SortingHelper sortingHelper, TestRandom[] randoms, boolean withMin) {
        this(sortingHelper, randoms, RUN_LENGTHS, withMin);
    }

    private Sorting(SortingHelper sortingHelper, TestRandom[] randoms, int[] lengths) {
        this(sortingHelper, randoms, lengths, false);
    }

    private Sorting(SortingHelper sortingHelper, TestRandom[] randoms, int[] lengths, boolean withMin) {
        this.sortingHelper = sortingHelper;
        this.randoms = randoms;
        this.lengths = lengths;
        this.withMin = withMin;
    }

    private void testBase() {
        testEmptyArray();

        for (int length : lengths) {
            createArray(length);
            testStructured(length);

            for (TestRandom random : randoms) {
                testWithCheckSum(length, random);
                testWithInsertionSort(length, random);
                testWithScrambling(length, random);
            }
        }
    }

    private void testCore() {
        testBase();

        for (int length : lengths) {
            createArray(length);

            for (TestRandom random : randoms) {
                testNegativeZero(length, random);
                testFloatingPointSorting(length, random);
            }
        }
    }

    private void testAll() {
        testCore();

        for (int length : lengths) {
            createArray(length);
            sortRange(length);
        }
    }

    private void testEmptyArray() {
        sortingHelper.sort(new int[]{});
        sortingHelper.sort(new int[]{}, 0, 0);

        sortingHelper.sort(new long[]{});
        sortingHelper.sort(new long[]{}, 0, 0);

        sortingHelper.sort(new byte[]{});
        sortingHelper.sort(new byte[]{}, 0, 0);

        sortingHelper.sort(new char[]{});
        sortingHelper.sort(new char[]{}, 0, 0);

        sortingHelper.sort(new short[]{});
        sortingHelper.sort(new short[]{}, 0, 0);

        sortingHelper.sort(new float[]{});
        sortingHelper.sort(new float[]{}, 0, 0);

        sortingHelper.sort(new double[]{});
        sortingHelper.sort(new double[]{}, 0, 0);
    }

    private void sortRange(int length) {
        int[] a = (int[]) gold[0];

        for (int m = 1; m < length; m <<= 1) {
            for (int i = 1; i <= length; ++i) {
                a[i - 1] = i % m + m % i;
            }
            convertArray(m / 4);

            for (int i = 0; i < test.length; ++i) {
                printTestName("Test range check", length,
                        ", m = " + m + ", " + getType(i));
                sortRange(test[i], m);
            }
        }
        out.println();
    }

    private void testWithInsertionSort(int length, TestRandom random) {
        if (length > 1_000) {
            return;
        }
        int[] a = (int[]) gold[0];

        for (int m = 1; m <= length; m <<= 1) {
            for (UnsortedBuilder builder : UnsortedBuilder.values()) {
                builder.build(a, m, random);
                int shift = m / 4;
                convertArray(shift);

                for (int i = 0; i < test.length; ++i) {
                    printTestName("Test with insertion sort", random, length,
                            ", m = " + m + ", " + getType(i) + " " + builder);
                    sortingHelper.sort(test[i], shift, length - shift);
                    sortByInsertionSort(gold[i], shift, length - shift);
                    checkSorted(gold[i], shift);
                    compare(test[i], gold[i]);
                }
            }
        }
        out.println();
    }

    private void testStructured() {
        for (int length : lengths) {
            createArray(length);
            testStructured(length);
        }
    }

    private void testStructured(int length) {
        if (length < 512) {
            return;
        }
        int[] a = (int[]) gold[0];

        for (int m = 1; m < 8; ++m) {
            for (StructuredBuilder builder : StructuredBuilder.values()) {
                builder.build(a, m);
                convertArray(0);

                for (int i = 0; i < test.length; ++i) {
                    printTestName("Test structured", length,
                            ", m = " + m + ", " + getType(i) + " " + builder);
                    sortingHelper.sort(test[i]/*, shift, length - shift*/);
                    checkSorted(test[i], 0);
                }
            }
        }
        out.println();
    }

    private void testWithCheckSum(int length, TestRandom random) {
        int[] a = (int[]) gold[0];

        for (int m = 1; m <= length; m <<= 1) {
            for (UnsortedBuilder builder : UnsortedBuilder.values()) {
                builder.build(a, m, random);
                int shift = m / 4;
                convertArray(shift);

                for (int i = 0; i < test.length; ++i) {
                    printTestName("Test with check sum", random, length,
                            ", m = " + m + ", " + getType(i) + " " + builder);
                    sortingHelper.sort(test[i], shift, length - shift);
                    checkWithCheckSum(test[i], gold[i], shift);
                }
            }
        }
        out.println();
    }

    private void testWithScrambling(int length, TestRandom random) {
        int[] a = (int[]) gold[0];

        for (int m = 1; m <= length; m <<= 1) {
            for (SortedBuilder builder : SortedBuilder.values()) {
                builder.build(a, m);
                convertArray(0);

                for (int i = 0; i < test.length; ++i) {
                    printTestName("Test with scrambling", random, length,
                            ", m = " + m + ", " + getType(i) + " " + builder);
                    scramble(test[i], random);
                    sortingHelper.sort(test[i]);
                    compare(test[i], gold[i]);
                }
            }
        }
        out.println();
    }

    private void testNegativeZero(int length, TestRandom random) {
        for (int i = 5; i < test.length; ++i) {
            printTestName("Test negative zero -0.0", random, length, " " + getType(i));

            NegativeZeroBuilder builder = NegativeZeroBuilder.values()[i - 5];
            builder.build(test[i], random);

            sortingHelper.sort(test[i]);
            checkNegativeZero(test[i]);
        }
        out.println();
    }

    private void testFloatingPointSorting(int length, TestRandom random) {
        if (length < 6) {
            return;
        }
        final int MAX = 14;
        int s = 4;

        for (int k = 0; k < MAX; ++k) {
            for (int g = 0; g < MAX; ++g) {
                for (int z = 0; z < MAX; ++z) {
                    for (int n = 0; n < MAX; ++n) {
                        for (int p = 0; p < MAX; ++p) {
                            if (k + g + z + n + p + s != length) {
                                continue;
                            }
                            for (int i = 5; i < test.length; ++i) {
                                printTestName("Test float-pointing sorting", random, length,
                                        ", k = " + k + ", g = " + g + ", z = " + z +
                                                ", n = " + n + ", p = " + p + ", " + getType(i));
                                FloatingPointBuilder builder = FloatingPointBuilder.values()[i - 5];
                                builder.build(gold[i], k, g, z, n, p, random);
                                copy(test[i], gold[i]);
                                scramble(test[i], random);
                                sortingHelper.sort(test[i]);
                                compare(test[i], gold[i], k, n + 2, g);
                            }
                        }
                    }
                }
            }
        }

        for (int m = MAX; m > 4; --m) {
            int g = length / m;
            int k = length - g - g - g - g - s;

            for (int i = 5; i < test.length; ++i) {
                printTestName("Test float-pointing sorting", random, length,
                        ", k = " + k + ", g = " + g + ", z = " + g +
                                ", n = " + g + ", p = " + g + ", " + getType(i));
                FloatingPointBuilder builder = FloatingPointBuilder.values()[i - 5];
                builder.build(gold[i], k, g, g, g, g, random);
                copy(test[i], gold[i]);
                scramble(test[i], random);
                sortingHelper.sort(test[i]);
                compare(test[i], gold[i], k, g + 2, g);
            }
        }
        out.println();
    }

    private void scramble(Object a, Random random) {
        switch (a) {
            case int[] ai -> scramble(ai, random);
            case long[] al -> scramble(al, random);
            case byte[] ab -> scramble(ab, random);
            case char[] ac -> scramble(ac, random);
            case short[] as -> scramble(as, random);
            case float[] af -> scramble(af, random);
            case double[] ad -> scramble(ad, random);
            default -> fail(a);
        }
    }

    private void scramble(int[] a, Random random) {
        if (withMin) {
            for (int i = 7; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length - 1) + 1, random.nextInt(a.length - 1) + 1);
            }
        } else {
            for (int i = 0; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length), random.nextInt(a.length));
            }
        }
    }

    private void scramble(long[] a, Random random) {
        if (withMin) {
            for (int i = 7; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length - 1) + 1, random.nextInt(a.length - 1) + 1);
            }
        } else {
            for (int i = 1; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length), random.nextInt(a.length));
            }
        }
    }

    private void scramble(byte[] a, Random random) {
        if (withMin) {
            for (int i = 7; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length - 1) + 1, random.nextInt(a.length - 1) + 1);
            }
        } else {
            for (int i = 1; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length), random.nextInt(a.length));
            }
        }
    }

    private void scramble(char[] a, Random random) {
        if (withMin) {
            for (int i = 7; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length - 1) + 1, random.nextInt(a.length - 1) + 1);
            }
        } else {
            for (int i = 1; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length), random.nextInt(a.length));
            }
        }
    }

    private void scramble(short[] a, Random random) {
        if (withMin) {
            for (int i = 7; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length - 1) + 1, random.nextInt(a.length - 1) + 1);
            }
        } else {
            for (int i = 1; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length), random.nextInt(a.length));
            }
        }
    }

    private void scramble(float[] a, Random random) {
        if (withMin) {
            for (int i = 7; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length - 1) + 1, random.nextInt(a.length - 1) + 1);
            }
        } else {
            for (int i = 1; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length), random.nextInt(a.length));
            }
        }
    }

    private void scramble(double[] a, Random random) {
        if (withMin) {
            for (int i = 7; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length - 1) + 1, random.nextInt(a.length - 1) + 1);
            }
        } else {
            for (int i = 1; i < a.length * 7; ++i) {
                swap(a, random.nextInt(a.length), random.nextInt(a.length));
            }
        }
    }

    private void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private void swap(long[] a, int i, int j) {
        long t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private void swap(byte[] a, int i, int j) {
        byte t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private void swap(char[] a, int i, int j) {
        char t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private void swap(short[] a, int i, int j) {
        short t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private void swap(float[] a, int i, int j) {
        float t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private void swap(double[] a, int i, int j) {
        double t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private void checkWithCheckSum(Object test, Object gold, int m) {
        checkSorted(test, m);
        checkCheckSum(test, gold);
    }

    private void checkNegativeZero(Object a) {
        switch (a) {
            case float[] af -> checkNegativeZero(af);
            case double[] ad -> checkNegativeZero(ad);
            default -> fail(a);
        }
    }

    private void checkNegativeZero(float[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (Float.floatToRawIntBits(a[i]) == 0 && Float.floatToRawIntBits(a[i + 1]) < 0) {
                fail(a[i] + " before " + a[i + 1] + " at position " + i);
            }
        }
    }

    private void checkNegativeZero(double[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (Double.doubleToRawLongBits(a[i]) == 0 && Double.doubleToRawLongBits(a[i + 1]) < 0) {
                fail(a[i] + " before " + a[i + 1] + " at position " + i);
            }
        }
    }

    private void compare(Object a, Object b, int numNaN, int numNeg, int numNegZero) {
        switch (a) {
            case float[] af -> compare(af, (float[]) b, numNaN, numNeg, numNegZero);
            case double[] ad -> compare(ad, (double[]) b, numNaN, numNeg, numNegZero);
            default -> fail(a);
        }
    }

    private void compare(float[] a, float[] b, int numNaN, int numNeg, int numNegZero) {
        for (int i = a.length - numNaN; i < a.length; ++i) {
            if (!Float.isNaN(a[i])) {
                fail("There must be NaN instead of " + a[i] + " at position " + i);
            }
        }
        final int NEGATIVE_ZERO = Float.floatToIntBits(-0.0f);

        for (int i = numNeg; i < numNeg + numNegZero; ++i) {
            if (Float.floatToIntBits(a[i]) != NEGATIVE_ZERO) {
                fail("There must be -0.0 instead of " + a[i] + " at position " + i);
            }
        }

        for (int i = 0; i < a.length - numNaN; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(double[] a, double[] b, int numNaN, int numNeg, int numNegZero) {
        for (int i = a.length - numNaN; i < a.length; ++i) {
            if (!Double.isNaN(a[i])) {
                fail("There must be NaN instead of " + a[i] + " at position " + i);
            }
        }
        final long NEGATIVE_ZERO = Double.doubleToLongBits(-0.0d);

        for (int i = numNeg; i < numNeg + numNegZero; ++i) {
            if (Double.doubleToLongBits(a[i]) != NEGATIVE_ZERO) {
                fail("There must be -0.0 instead of " + a[i] + " at position " + i);
            }
        }

        for (int i = 0; i < a.length - numNaN; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(Object a, Object b) {
        switch (a) {
            case int[] ai -> compare(ai, (int[]) b);
            case long[] al -> compare(al, (long[]) b);
            case byte[] ab -> compare(ab, (byte[]) b);
            case char[] ac -> compare(ac, (char[]) b);
            case short[] as -> compare(as, (short[]) b);
            case float[] af -> compare(af, (float[]) b);
            case double[] ad -> compare(ad, (double[]) b);
            default -> fail(a);
        }
    }

    private void compare(int[] a, int[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(long[] a, long[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(char[] a, char[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(short[] a, short[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(float[] a, float[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private void compare(double[] a, double[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
                fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
            }
        }
    }

    private String getType(int i) {
        Object a = test[i];

        return switch (a) {
            case int[] _ -> "INT   ";
            case long[] _ -> "LONG  ";
            case byte[] _ -> "BYTE  ";
            case char[] _ -> "CHAR  ";
            case short[] _ -> "SHORT ";
            case float[] _ -> "FLOAT ";
            case double[] _ -> "DOUBLE";
            default -> null;
        };
    }

    private void checkSorted(Object a, int m) {
        switch (a) {
            case int[] ai -> checkSorted(ai, m);
            case long[] al -> checkSorted(al, m);
            case byte[] ab -> checkSorted(ab, m);
            case char[] ac -> checkSorted(ac, m);
            case short[] as -> checkSorted(as, m);
            case float[] af -> checkSorted(af, m);
            case double[] ad -> checkSorted(ad, m);
            default -> fail(a);
        }
    }

    private void checkSorted(int[] a, int m) {
        for (int i = 0; i < m; ++i) {
            if (a[i] != A380) {
                fail("Sort changes left element at position " + i + hex(a[i], A380));
            }
        }
        for (int i = m; i < a.length - m - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
        for (int i = a.length - m; i < a.length; ++i) {
            if (a[i] != B747) {
                fail("Sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSorted(long[] a, int m) {
        for (int i = 0; i < m; ++i) {
            if (a[i] != toLong(A380)) {
                fail("Sort changes left element at position " + i + hex(a[i], A380));
            }
        }
        for (int i = m; i < a.length - m - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
        for (int i = a.length - m; i < a.length; ++i) {
            if (a[i] != toLong(B747)) {
                fail("Sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSorted(byte[] a, int m) {
        for (int i = 0; i < m; ++i) {
            if (a[i] != (byte) A380) {
                fail("Sort changes left element at position " + i + hex(a[i], A380));
            }
        }
        for (int i = m; i < a.length - m - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
        for (int i = a.length - m; i < a.length; ++i) {
            if (a[i] != (byte) B747) {
                fail("Sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSorted(char[] a, int m) {
        for (int i = 0; i < m; ++i) {
            if (a[i] != (char) A380) {
                fail("Sort changes left element at position " + i + hex(a[i], A380));
            }
        }
        for (int i = m; i < a.length - m - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
        for (int i = a.length - m; i < a.length; ++i) {
            if (a[i] != (char) B747) {
                fail("Sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSorted(short[] a, int m) {
        for (int i = 0; i < m; ++i) {
            if (a[i] != (short) A380) {
                fail("Sort changes left element at position " + i + hex(a[i], A380));
            }
        }
        for (int i = m; i < a.length - m - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
        for (int i = a.length - m; i < a.length; ++i) {
            if (a[i] != (short) B747) {
                fail("Sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSorted(float[] a, int m) {
        for (int i = 0; i < m; ++i) {
            if (a[i] != (float) A380) {
                fail("Sort changes left element at position " + i + hex((long) a[i], A380));
            }
        }
        for (int i = m; i < a.length - m - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
        for (int i = a.length - m; i < a.length; ++i) {
            if (a[i] != (float) B747) {
                fail("Sort changes right element at position " + i + hex((long) a[i], B747));
            }
        }
    }

    private void checkSorted(double[] a, int m) {
        for (int i = 0; i < m; ++i) {
            if (a[i] != toDouble(A380)) {
                fail("Sort changes left element at position " + i + hex((long) a[i], A380));
            }
        }
        for (int i = m; i < a.length - m - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
        for (int i = a.length - m; i < a.length; ++i) {
            if (a[i] != B747) {
                fail("Sort changes right element at position " + i + hex((long) a[i], B747));
            }
        }
    }

    private void checkCheckSum(Object test, Object gold) {
        if (checkSumXor(test) != checkSumXor(gold)) {
            fail("Original and sorted arrays are not identical [^]");
        }
        if (checkSumPlus(test) != checkSumPlus(gold)) {
            fail("Original and sorted arrays are not identical [+]");
        }
    }

    private int checkSumXor(Object a) {
        return switch (a) {
            case int[] ai -> checkSumXor(ai);
            case long[] al -> checkSumXor(al);
            case byte[] ab -> checkSumXor(ab);
            case char[] ac -> checkSumXor(ac);
            case short[] as -> checkSumXor(as);
            case float[] af -> checkSumXor(af);
            case double[] ad -> checkSumXor(ad);
            default -> -1;
        };
    }

    private int checkSumXor(int[] a) {
        int checkSum = 0;

        for (int e : a) {
            checkSum ^= e;
        }
        return checkSum;
    }

    private int checkSumXor(long[] a) {
        long checkSum = 0;

        for (long e : a) {
            checkSum ^= e;
        }
        return (int) checkSum;
    }

    private int checkSumXor(byte[] a) {
        byte checkSum = 0;

        for (byte e : a) {
            checkSum ^= e;
        }
        return checkSum;
    }

    private int checkSumXor(char[] a) {
        char checkSum = 0;

        for (char e : a) {
            checkSum ^= e;
        }
        return checkSum;
    }

    private int checkSumXor(short[] a) {
        short checkSum = 0;

        for (short e : a) {
            checkSum ^= e;
        }
        return checkSum;
    }

    private int checkSumXor(float[] a) {
        int checkSum = 0;

        for (float e : a) {
            checkSum ^= (int) e;
        }
        return checkSum;
    }

    private int checkSumXor(double[] a) {
        int checkSum = 0;

        for (double e : a) {
            checkSum ^= (int) e;
        }
        return checkSum;
    }

    private int checkSumPlus(Object a) {
        return switch (a) {
            case int[] ai -> checkSumPlus(ai);
            case long[] al -> checkSumPlus(al);
            case byte[] ab -> checkSumPlus(ab);
            case char[] ac -> checkSumPlus(ac);
            case short[] as -> checkSumPlus(as);
            case float[] af -> checkSumPlus(af);
            case double[] ad -> checkSumPlus(ad);
            default -> -1;
        };
    }

    private int checkSumPlus(int[] a) {
        int checkSum = 0;

        for (int e : a) {
            checkSum += e;
        }
        return checkSum;
    }

    private int checkSumPlus(long[] a) {
        long checkSum = 0;

        for (long e : a) {
            checkSum += e;
        }
        return (int) checkSum;
    }

    private int checkSumPlus(byte[] a) {
        byte checkSum = 0;

        for (byte e : a) {
            checkSum += e;
        }
        return checkSum;
    }

    private int checkSumPlus(char[] a) {
        char checkSum = 0;

        for (char e : a) {
            checkSum += e;
        }
        return checkSum;
    }

    private int checkSumPlus(short[] a) {
        short checkSum = 0;

        for (short e : a) {
            checkSum += e;
        }
        return checkSum;
    }

    private int checkSumPlus(float[] a) {
        int checkSum = 0;

        for (float e : a) {
            checkSum += (int) e;
        }
        return checkSum;
    }

    private int checkSumPlus(double[] a) {
        int checkSum = 0;

        for (double e : a) {
            checkSum += (int) e;
        }
        return checkSum;
    }

    private void sortByInsertionSort(Object a, int low, int high) {
        SortingHelper.INSERTION_SORT.sort(a, low, high);
    }

    private void sortRange(Object a, int m) {
        switch (a) {
            case int[] ai -> sortRange(ai, m);
            case long[] al -> sortRange(al, m);
            case byte[] ab -> sortRange(ab, m);
            case char[] ac -> sortRange(ac, m);
            case short[] as -> sortRange(as, m);
            case float[] af -> sortRange(af, m);
            case double[] ad -> sortRange(ad, m);
            default -> fail(a);
        }
    }

    private void sortRange(int[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " must throw IllegalArgumentException: " +
                    "fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                        "fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {
                }
            }
        }
    }

    private void sortRange(long[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " must throw IllegalArgumentException: " +
                    "fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                        "fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {
                }
            }
        }
    }

    private void sortRange(byte[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " must throw IllegalArgumentException: " +
                    "fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                        "fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {
                }
            }
        }
    }

    private void sortRange(char[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " must throw IllegalArgumentException: " +
                    "fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                        "fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {
                }
            }
        }
    }

    private void sortRange(short[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " must throw IllegalArgumentException: " +
                    "fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                        "fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {
                }
            }
        }
    }

    private void sortRange(float[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " must throw IllegalArgumentException: " +
                    "fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                        "fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {
                }
            }
        }
    }

    private void sortRange(double[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " must throw IllegalArgumentException: " +
                    "fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                        "fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {
                }
            }
        }
    }

    private void copy(Object dst, Object src) {
        switch (src) {
            case float[] sf -> System.arraycopy(sf, 0, dst, 0, sf.length);
            case double[] sd -> System.arraycopy(sd, 0, dst, 0, sd.length);
            default -> fail(src);
        }
    }

    private void createArray(int length) {
        gold = new Object[]{
                new int[length], new long[length],
                new byte[length], new char[length], new short[length],
                new float[length], new double[length]
        };

        test = new Object[]{
                new int[length], new long[length],
                new byte[length], new char[length], new short[length],
                new float[length], new double[length]
        };
    }

    private void convertArray(int m) {
        int[] a = (int[]) gold[0];

        for (int i = 0; i < m; ++i) {
            a[i] = A380;
        }
        for (int i = a.length - m; i < a.length; ++i) {
            a[i] = B747;
        }
        for (int i = 0; i < gold.length; ++i) {
            TypeConverter converter = TypeConverter.values()[i];
            converter.convert(a, gold[i], withMin, m);
        }
        for (int i = 0; i < gold.length; ++i) {
            System.arraycopy(gold[i], 0, test[i], 0, a.length);
        }
    }

    private String hex(long a, int b) {
        return ": " + Long.toHexString(a) + ", must be " + Integer.toHexString(b);
    }

    private void printTestName(String test, int length, String message) {
        out.println("[" + sortingHelper + "] '" + test + "' length = " + length + message);
    }

    private void printTestName(String test, TestRandom random, int length, String message) {
        out.println("[" + sortingHelper + "] '" + test +
                "' length = " + length + ", random = " + random + message);
    }

    private void fail(Object a) {
        fail("Unknown type: " + a.getClass().getName());
    }

    private void fail(String message) {
        err.format("*** TEST FAILED ***\n\n%s\n\n", message);
        throw new RuntimeException("Test failed");
    }

    private static long toLong(int i) {
        return (((long) i) << 32) | i;
    }

    private static double toDouble(int i) {
        long v = toLong(i);
        v = (v > 0) ? ~v : v & ~(1L << 63);
        double d = Double.longBitsToDouble(v);
        return Double.isNaN(d) ? 0.0d : d;
    }

    private enum TypeConverter {
        INT {
            @Override
            void convert(int[] src, Object dst, boolean withMin, int m) {
                if (withMin) {
                    src[m] = Integer.MIN_VALUE;
                }
            }
        },

        LONG {
            @Override
            void convert(int[] src, Object dst, boolean withMin, int m) {
                long[] b = (long[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = toLong(src[i]);
                }
                if (withMin) {
                    b[m] = Long.MIN_VALUE;
                }
            }
        },

        BYTE {
            @Override
            void convert(int[] src, Object dst, boolean withMin, int m) {
                byte[] b = (byte[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = (byte) src[i];
                }
                if (withMin) {
                    b[m] = Byte.MIN_VALUE;
                }
            }
        },

        CHAR {
            @Override
            void convert(int[] src, Object dst, boolean withMin, int m) {
                char[] b = (char[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = (char) src[i];
                }
                if (withMin) {
                    b[m] = Character.MIN_VALUE;
                }
            }
        },

        SHORT {
            @Override
            void convert(int[] src, Object dst, boolean withMin, int m) {
                short[] b = (short[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = (short) src[i];
                }
                if (withMin) {
                    b[m] = Short.MIN_VALUE;
                }
            }
        },

        FLOAT {
            @Override
            void convert(int[] src, Object dst, boolean withMin, int m) {
                float[] b = (float[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = src[i];
                }
                if (withMin) {
                    b[m] = Float.NEGATIVE_INFINITY;
                }
            }
        },

        DOUBLE {
            @Override
            void convert(int[] src, Object dst, boolean withMin, int m) {
                double[] b = (double[]) dst;

                for (int i = 0; i < src.length / 2; ++i) {
                    b[i] = toDouble(src[i]);
                }
                for (int i = src.length / 2; i < src.length; ++i) {
                    b[i] = src[i];
                }
                if (withMin) {
                    b[m] = Double.NEGATIVE_INFINITY;
                }
            }
        };

        abstract void convert(int[] src, Object dst, boolean withMin, int m);
    }

    private enum SortedBuilder {
        STEPS {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < m; ++i) {
                    a[i] = 0;
                }

                for (int i = m; i < a.length; ++i) {
                    a[i] = 1;
                }
            }
        };

        abstract void build(int[] a, int m);
    }

    private enum UnsortedBuilder {
        RANDOM {
            @Override
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = random.nextInt();
                }
            }
        },

        PERMUTATION {
            @Override
            void build(int[] a, int m, Random random) {
                int mask = ~(0x000000FF << (random.nextInt(4) * 2));

                for (int i = 0; i < a.length; ++i) {
                    a[i] = i & mask;
                }
                for (int i = a.length; i > 1; --i) {
                    int k = random.nextInt(i);
                    int t = a[i - 1];
                    a[i - 1] = a[k];
                    a[k] = t;
                }
            }
        },

        UNIFORM {
            @Override
            void build(int[] a, int m, Random random) {
                int mask = (m << 15) - 1;

                for (int i = 0; i < a.length; ++i) {
                    a[i] = random.nextInt() & mask;
                }
            }
        },

        STAGGER {
            @Override
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = (i * m + i) % a.length;
                }
            }
        },

        REPEATED {
            @Override
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = i % m;
                }
            }
        },

        DUPLICATED {
            @Override
            void build(int[] a, int m, Random random) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = random.nextInt(m);
                }
            }
        },

        SAWTOOTH {
            @Override
            void build(int[] a, int m, Random random) {
                int incCount = 1;
                int decCount = a.length;
                int i = 0;
                int period = m--;

                while (true) {
                    for (int k = 1; k <= period; ++k) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = incCount++;
                    }
                    period += m;

                    for (int k = 1; k <= period; ++k) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = decCount--;
                    }
                    period += m;
                }
            }
        },

        SHUFFLE {
            @Override
            void build(int[] a, int m, Random random) {
                for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                    a[i] = random.nextInt(m) > 0 ? (j += 2) : (k += 2);
                }
            }
        };

        abstract void build(int[] a, int m, Random random);
    }

    private enum StructuredBuilder {
        ASCENDING {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = m + i;
                }
            }
        },

        DESCENDING {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = a.length - m - i;
                }
            }
        },

        EQUAL {
            @Override
            void build(int[] a, int m) {
                Arrays.fill(a, m);
            }
        },

        SHIFTED {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = i << 10;
                }
            }
        },

        ORGAN_PIPES {
            @Override
            void build(int[] a, int m) {
                int middle = a.length / (m + 1);

                for (int i = 0; i < middle; ++i) {
                    a[i] = i;
                }
                for (int i = middle; i < a.length; ++i) {
                    a[i] = a.length - i - 1;
                }
            }
        },

        PLATEAU {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = Math.min(i, m);
                }
            }
        },

        LATCH {
            @Override
            void build(int[] a, int m) {
                int max = Math.max(a.length / m, 2);

                for (int i = 0; i < a.length; ++i) {
                    a[i] = i % max;
                }
            }
        },

        POINT {
            @Override
            void build(int[] a, int m) {
                Arrays.fill(a, 0);
                a[a.length / 2] = m;
            }
        },

        LINE {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = i;
                }
                reverse(a, Math.max(0, a.length - m), a.length);
            }
        },

        PEARL {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = i;
                }
                reverse(a, 0, Math.min(m, a.length));
            }
        },

        TRAPEZIUM {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = i;
                }
                reverse(a, m, a.length - m);
            }
        },

        RING {
            @Override
            void build(int[] a, int m) {
                int k1 = a.length / 3;
                int k2 = a.length / 3 * 2;
                int level = a.length / 3;

                for (int i = 0, k = level; i < k1; ++i) {
                    a[i] = k--;
                }
                for (int i = k1; i < k2; ++i) {
                    a[i] = 0;
                }
                for (int i = k2, k = level; i < a.length; ++i) {
                    a[i] = k--;
                }
            }
        };

        abstract void build(int[] a, int m);

        private static void reverse(int[] a, int lo, int hi) {
            for (--hi; lo < hi; ) {
                int tmp = a[lo];
                a[lo++] = a[hi];
                a[hi--] = tmp;
            }
        }
    }

    private enum NegativeZeroBuilder {
        FLOAT {
            @Override
            void build(Object o, Random random) {
                float[] a = (float[]) o;

                for (int i = 0; i < a.length; ++i) {
                    a[i] = random.nextBoolean() ? -0.0f : 0.0f;
                }
            }
        },

        DOUBLE {
            @Override
            void build(Object o, Random random) {
                double[] a = (double[]) o;

                for (int i = 0; i < a.length; ++i) {
                    a[i] = random.nextBoolean() ? -0.0d : 0.0d;
                }
            }
        };

        abstract void build(Object o, Random random);
    }

    private enum FloatingPointBuilder {
        FLOAT {
            @Override
            void build(Object o, int k, int g, int z, int n, int p, Random random) {
                float negativeValue = -random.nextFloat();
                float positiveValue = random.nextFloat();
                float[] a = (float[]) o;
                int fromIndex = 0;

                fillWithValue(a, Float.NEGATIVE_INFINITY, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(a, -Float.MAX_VALUE, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(a, negativeValue, fromIndex, n);
                fromIndex += n;

                fillWithValue(a, -0.0f, fromIndex, g);
                fromIndex += g;

                fillWithValue(a, 0.0f, fromIndex, z);
                fromIndex += z;

                fillWithValue(a, positiveValue, fromIndex, p);
                fromIndex += p;

                fillWithValue(a, Float.MAX_VALUE, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(a, Float.POSITIVE_INFINITY, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(a, Float.NaN, fromIndex, k);
            }
        },

        DOUBLE {
            @Override
            void build(Object o, int k, int g, int z, int n, int p, Random random) {
                double negativeValue = -random.nextFloat();
                double positiveValue = random.nextFloat();
                double[] a = (double[]) o;
                int fromIndex = 0;

                fillWithValue(a, Double.NEGATIVE_INFINITY, fromIndex, 1);
                fromIndex++;

                fillWithValue(a, -Double.MAX_VALUE, fromIndex, 1);
                fromIndex++;

                fillWithValue(a, negativeValue, fromIndex, n);
                fromIndex += n;

                fillWithValue(a, -0.0d, fromIndex, g);
                fromIndex += g;

                fillWithValue(a, 0.0d, fromIndex, z);
                fromIndex += z;

                fillWithValue(a, positiveValue, fromIndex, p);
                fromIndex += p;

                fillWithValue(a, Double.MAX_VALUE, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(a, Double.POSITIVE_INFINITY, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(a, Double.NaN, fromIndex, k);
            }
        };

        abstract void build(Object o, int k, int g, int z, int n, int p, Random random);

        private static void fillWithValue(float[] a, float value, int fromIndex, int count) {
            for (int i = fromIndex; i < fromIndex + count; ++i) {
                a[i] = value;
            }
        }

        private static void fillWithValue(double[] a, double value, int fromIndex, int count) {
            for (int i = fromIndex; i < fromIndex + count; ++i) {
                a[i] = value;
            }
        }
    }

    private static class TestRandom extends Random {

        private static final TestRandom DEDA = new TestRandom(0xDEDA);
        private static final TestRandom BABA = new TestRandom(0xBABA);
        private static final TestRandom C0FFEE = new TestRandom(0xC0FFEE);

        private TestRandom(long seed) {
            super(seed);
            this.seed = Long.toHexString(seed).toUpperCase();
        }

        @Override
        public String toString() {
            return seed;
        }

        private final String seed;
    }
}
