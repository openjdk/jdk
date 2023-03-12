/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run main Sorting -shortrun
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

    // Lengths of arrays for short run
    private static final int[] SHORT_RUN_LENGTHS =
        { 1, 2, 14, 100, 500, 1_000, 10_000 };

    // Lengths of arrays for long run (default)
    private static final int[] LONG_RUN_LENGTHS =
        { 1, 2, 14, 100, 500, 1_000, 10_000, 50_000 };

    // Initial random values for short run
    private static final TestRandom[] SHORT_RUN_RANDOMS =
        { TestRandom.C0FFEE };

    // Initial random values for long run (default)
    private static final TestRandom[] LONG_RUN_RANDOMS =
        { TestRandom.DEDA, TestRandom.BABA, TestRandom.C0FFEE };

    // Constant to fill the left part of array
    private static final int A380 = 0xA380;

    // Constant to fill the right part of array
    private static final int B747 = 0xB747;

    private final SortingHelper sortingHelper;
    private final TestRandom[] randoms;
    private final int[] lengths;
    private final boolean fix;
    private Object[] gold;
    private Object[] test;

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        boolean shortRun = args.length > 0 && args[0].equals("-shortrun");

        int[] lengths = shortRun ? SHORT_RUN_LENGTHS : LONG_RUN_LENGTHS;
        TestRandom[] randoms = shortRun ? SHORT_RUN_RANDOMS : LONG_RUN_RANDOMS;

        new Sorting(SortingHelper.MIXED_INSERTION_SORT, randoms).testBase();
        new Sorting(SortingHelper.MERGING_SORT, randoms, lengths).testStructured(512);
        new Sorting(SortingHelper.HEAP_SORT, randoms, lengths).testBase();
        new Sorting(SortingHelper.RADIX_SORT, randoms, lengths).testCore();
        new Sorting(SortingHelper.DUAL_PIVOT_QUICKSORT, randoms, lengths).testCore();
        new Sorting(SortingHelper.PARALLEL_SORT, randoms, lengths).testCore();
        new Sorting(SortingHelper.ARRAYS_SORT, randoms, lengths).testAll();
        new Sorting(SortingHelper.ARRAYS_PARALLEL_SORT, randoms, lengths).testAll();

        long end = System.currentTimeMillis();
        out.format("PASSED in %d sec.\n", (end - start) / 1_000);
    }

    private Sorting(SortingHelper sortingHelper, TestRandom[] randoms) {
        this(sortingHelper, randoms, SHORT_RUN_LENGTHS, true);
    }

    private Sorting(SortingHelper sortingHelper, TestRandom[] randoms, int[] lengths) {
        this(sortingHelper, randoms, lengths, false);
    }

    private Sorting(SortingHelper sortingHelper, TestRandom[] randoms, int[] lengths, boolean fix) {
        this.sortingHelper = sortingHelper;
        this.randoms = randoms;
        this.lengths = lengths;
        this.fix = fix;
    }

    private void testBase() {
        testStructured(0);
        testEmptyArray();

        for (int length : lengths) {
            createData(length);
            testSubArray(length);

            for (TestRandom random : randoms) {
                testWithCheckSum(length, random);
                testWithScrambling(length, random);
                testWithInsertionSort(length, random);
            }
        }
    }

    private void testCore() {
        testBase();

        for (int length : lengths) {
            createData(length);

            for (TestRandom random : randoms) {
                testNegativeZero(length, random);
                testFloatingPointSorting(length, random);
            }
        }
    }

    private void testAll() {
        testCore();

        for (int length : lengths) {
            createData(length);
            testRange(length);
        }
    }

    private void testStructured(int min) {
        for (int length : lengths) {
            createData(length);
            testStructured(length, min);
        }
    }

    private void testEmptyArray() {
        sortingHelper.sort(new int[] {});
        sortingHelper.sort(new int[] {}, 0, 0);

        sortingHelper.sort(new long[] {});
        sortingHelper.sort(new long[] {}, 0, 0);

        sortingHelper.sort(new byte[] {});
        sortingHelper.sort(new byte[] {}, 0, 0);

        sortingHelper.sort(new char[] {});
        sortingHelper.sort(new char[] {}, 0, 0);

        sortingHelper.sort(new short[] {});
        sortingHelper.sort(new short[] {}, 0, 0);

        sortingHelper.sort(new float[] {});
        sortingHelper.sort(new float[] {}, 0, 0);

        sortingHelper.sort(new double[] {});
        sortingHelper.sort(new double[] {}, 0, 0);
    }

    private void testSubArray(int length) {
        if (fix || length < 4) {
            return;
        }
        for (int m = 1; m < length / 2; m <<= 1) {
            int toIndex = length - m;

            prepareSubArray((int[]) gold[0], m, toIndex);
            convertData(length);

            for (int i = 0; i < test.length; ++i) {
                printTestName("Test subarray", length,
                    ", m = " + m + ", " + getType(i));
                sortingHelper.sort(test[i], m, toIndex);
                checkSubArray(test[i], m, toIndex);
            }
        }
        out.println();
    }

    private void testRange(int length) {
        for (int m = 1; m < length; m <<= 1) {
            for (int i = 1; i <= length; ++i) {
                ((int[]) gold[0])[i - 1] = i % m + m % i;
            }
            convertData(length);

            for (int i = 0; i < test.length; ++i) {
                printTestName("Test range check", length,
                    ", m = " + m + ", " + getType(i));
                checkRange(test[i], m);
            }
        }
        out.println();
    }

    private void testWithInsertionSort(int length, TestRandom random) {
        if (length > 1_000) {
            return;
        }
        for (int m = 1; m <= length; m <<= 1) {
            for (UnsortedBuilder builder : UnsortedBuilder.values()) {
                builder.build((int[]) gold[0], m, random);
                convertData(length);

                for (int i = 0; i < test.length; ++i) {
                    printTestName("Test with insertion sort", random, length,
                        ", m = " + m + ", " + getType(i) + " " + builder);
                    sortingHelper.sort(test[i]);
                    sortByInsertionSort(gold[i]);
                    checkSorted(gold[i]);
                    compare(test[i], gold[i]);
                }
            }
        }
        out.println();
    }

    private void testStructured(int length, int min) {
        if (length < min) {
            return;
        }
        for (int m = 1; m < 8; ++m) {
            for (StructuredBuilder builder : StructuredBuilder.values()) {
                builder.build((int[]) gold[0], m);
                convertData(length);

                for (int i = 0; i < test.length; ++i) {
                    printTestName("Test structured", length,
                        ", m = " + m + ", " +  getType(i) + " " + builder);
                    sortingHelper.sort(test[i]);
                    checkSorted(test[i]);
                }
            }
        }
        out.println();
    }

    private void testWithCheckSum(int length, TestRandom random) {
        if (length > 1_000) {
            return;
        }
        for (int m = 1; m <= length; m <<= 1) {
            for (UnsortedBuilder builder : UnsortedBuilder.values()) {
                builder.build((int[]) gold[0], m, random);
                convertData(length);

                for (int i = 0; i < test.length; ++i) {
                    printTestName("Test with check sum", random, length,
                        ", m = " + m + ", " + getType(i) + " " + builder);
                    sortingHelper.sort(test[i]);
                    checkWithCheckSum(test[i], gold[i]);
                }
            }
        }
        out.println();
    }

    private void testWithScrambling(int length, TestRandom random) {
        if (fix) {
            return;
        }
        for (int m = 1; m <= length; m <<= 1) {
            for (SortedBuilder builder : SortedBuilder.values()) {
                builder.build((int[]) gold[0], m);
                convertData(length);

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

        for (int a = 0; a < MAX; ++a) {
            for (int g = 0; g < MAX; ++g) {
                for (int z = 0; z < MAX; ++z) {
                    for (int n = 0; n < MAX; ++n) {
                        for (int p = 0; p < MAX; ++p) {
                            if (a + g + z + n + p + s != length) {
                                continue;
                            }
                            for (int i = 5; i < test.length; ++i) {
                                printTestName("Test float-pointing sorting", random, length,
                                    ", a = " + a + ", g = " + g + ", z = " + z +
                                    ", n = " + n + ", p = " + p + ", " + getType(i));
                                FloatingPointBuilder builder = FloatingPointBuilder.values()[i - 5];
                                builder.build(gold[i], a, g, z, n, p, random);
                                copy(test[i], gold[i]);
                                scramble(test[i], random);
                                sortingHelper.sort(test[i]);
                                compare(test[i], gold[i], a, n + 2, g);
                            }
                        }
                    }
                }
            }
        }
        for (int m = MAX; m > 4; --m) {
            int g = length / m;
            int a = length - g - g - g - g - s;

            for (int i = 5; i < test.length; ++i) {
                printTestName("Test float-pointing sorting", random, length,
                    ", a = " + a + ", g = " + g + ", z = " + g +
                    ", n = " + g + ", p = " + g + ", " + getType(i));
                FloatingPointBuilder builder = FloatingPointBuilder.values()[i - 5];
                builder.build(gold[i], a, g, g, g, g, random);
                copy(test[i], gold[i]);
                scramble(test[i], random);
                sortingHelper.sort(test[i]);
                compare(test[i], gold[i], a, g + 2, g);
            }
        }
        out.println();
    }

    private void prepareSubArray(int[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            a[i] = A380;
        }
        int middle = (fromIndex + toIndex) >>> 1;
        int k = 0;

        for (int i = fromIndex; i < middle; ++i) {
            a[i] = k++;
        }

        for (int i = middle; i < toIndex; ++i) {
            a[i] = k--;
        }

        for (int i = toIndex; i < a.length; ++i) {
            a[i] = B747;
        }
    }

    private void scramble(Object a, Random random) {
        if (a instanceof int[]) {
            scramble((int[]) a, random);
        } else if (a instanceof long[]) {
            scramble((long[]) a, random);
        } else if (a instanceof byte[]) {
            scramble((byte[]) a, random);
        } else if (a instanceof char[]) {
            scramble((char[]) a, random);
        } else if (a instanceof short[]) {
            scramble((short[]) a, random);
        } else if (a instanceof float[]) {
            scramble((float[]) a, random);
        } else if (a instanceof double[]) {
            scramble((double[]) a, random);
        } else {
            fail(a);
        }
    }

    private void scramble(int[] a, Random random) {
        for (int i = 0; i < a.length * 7; ++i) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private void scramble(long[] a, Random random) {
        for (int i = 0; i < a.length * 7; ++i) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private void scramble(byte[] a, Random random) {
        for (int i = 0; i < a.length * 7; ++i) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private void scramble(char[] a, Random random) {
        for (int i = 0; i < a.length * 7; ++i) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private void scramble(short[] a, Random random) {
        for (int i = 0; i < a.length * 7; ++i) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private void scramble(float[] a, Random random) {
        for (int i = 0; i < a.length * 7; ++i) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private void scramble(double[] a, Random random) {
        for (int i = 0; i < a.length * 7; ++i) {
            swap(a, random.nextInt(a.length), random.nextInt(a.length));
        }
    }

    private void swap(int[] a, int i, int j) {
        int t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private void swap(long[] a, int i, int j) {
        long t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private void swap(byte[] a, int i, int j) {
        byte t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private void swap(char[] a, int i, int j) {
        char t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private void swap(short[] a, int i, int j) {
        short t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private void swap(float[] a, int i, int j) {
        float t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private void swap(double[] a, int i, int j) {
        double t = a[i]; a[i] = a[j]; a[j] = t;
    }

    private void checkWithCheckSum(Object test, Object gold) {
        checkSorted(test);
        checkCheckSum(test, gold);
    }

    private void fail(Object object) {
        fail("Unknown type of array: " + object.getClass().getName());
    }

    private void fail(String message) {
        err.format("\n*** TEST FAILED ***\n\n%s\n\n", message);
        throw new RuntimeException("Test failed");
    }

    private void checkNegativeZero(Object a) {
        if (a instanceof float[]) {
            checkNegativeZero((float[]) a);
        } else if (a instanceof double[]) {
            checkNegativeZero((double[]) a);
        } else {
            fail(a);
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
        if (a instanceof float[]) {
            compare((float[]) a, (float[]) b, numNaN, numNeg, numNegZero);
        } else if (a instanceof double[]) {
            compare((double[]) a, (double[]) b, numNaN, numNeg, numNegZero);
        } else {
            fail(a);
        }
    }

    private void compare(float[] a, float[] b, int numNaN, int numNeg, int numNegZero) {
        for (int i = a.length - numNaN; i < a.length; ++i) {
            if (a[i] == a[i]) {
                fail("There must be NaN instead of " + a[i] + " at position " + i);
            }
        }
        final int NEGATIVE_ZERO = Float.floatToIntBits(-0.0f);

        for (int i = numNeg; i < numNeg + numNegZero; ++i) {
            if (NEGATIVE_ZERO != Float.floatToIntBits(a[i])) {
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
            if (a[i] == a[i]) {
                fail("There must be NaN instead of " + a[i] + " at position " + i);
            }
        }
        final long NEGATIVE_ZERO = Double.doubleToLongBits(-0.0d);

        for (int i = numNeg; i < numNeg + numNegZero; ++i) {
            if (NEGATIVE_ZERO != Double.doubleToLongBits(a[i])) {
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
        if (a instanceof int[]) {
            compare((int[]) a, (int[]) b);
        } else if (a instanceof long[]) {
            compare((long[]) a, (long[]) b);
        } else if (a instanceof byte[]) {
            compare((byte[]) a, (byte[]) b);
        } else if (a instanceof char[]) {
            compare((char[]) a, (char[]) b);
        } else if (a instanceof short[]) {
            compare((short[]) a, (short[]) b);
        } else if (a instanceof float[]) {
            compare((float[]) a, (float[]) b);
        } else if (a instanceof double[]) {
            compare((double[]) a, (double[]) b);
        } else {
            fail(a);
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

        if (a instanceof int[]) {
            return "INT   ";
        }
        if (a instanceof long[]) {
            return "LONG  ";
        }
        if (a instanceof byte[]) {
            return "BYTE  ";
        }
        if (a instanceof char[]) {
            return "CHAR  ";
        }
        if (a instanceof short[]) {
            return "SHORT ";
        }
        if (a instanceof float[]) {
            return "FLOAT ";
        }
        if (a instanceof double[]) {
            return "DOUBLE";
        }
        fail(a);
        return null;
    }

    private void checkSorted(Object a) {
        if (a instanceof int[]) {
            checkSorted((int[]) a);
        } else if (a instanceof long[]) {
            checkSorted((long[]) a);
        } else if (a instanceof byte[]) {
            checkSorted((byte[]) a);
        } else if (a instanceof char[]) {
            checkSorted((char[]) a);
        } else if (a instanceof short[]) {
            checkSorted((short[]) a);
        } else if (a instanceof float[]) {
            checkSorted((float[]) a);
        } else if (a instanceof double[]) {
            checkSorted((double[]) a);
        } else {
            fail(a);
        }
    }

    private void checkSorted(int[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
    }

    private void checkSorted(long[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
    }

    private void checkSorted(byte[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
    }

    private void checkSorted(char[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
    }

    private void checkSorted(short[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
    }

    private void checkSorted(float[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }
    }

    private void checkSorted(double[] a) {
        for (int i = 0; i < a.length - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
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
        if (a instanceof int[]) {
            return checkSumXor((int[]) a);
        }
        if (a instanceof long[]) {
            return checkSumXor((long[]) a);
        }
        if (a instanceof byte[]) {
            return checkSumXor((byte[]) a);
        }
        if (a instanceof char[]) {
            return checkSumXor((char[]) a);
        }
        if (a instanceof short[]) {
            return checkSumXor((short[]) a);
        }
        if (a instanceof float[]) {
            return checkSumXor((float[]) a);
        }
        if (a instanceof double[]) {
            return checkSumXor((double[]) a);
        }
        fail(a);
        return -1;
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
        if (a instanceof int[]) {
            return checkSumPlus((int[]) a);
        }
        if (a instanceof long[]) {
            return checkSumPlus((long[]) a);
        }
        if (a instanceof byte[]) {
            return checkSumPlus((byte[]) a);
        }
        if (a instanceof char[]) {
            return checkSumPlus((char[]) a);
        }
        if (a instanceof short[]) {
            return checkSumPlus((short[]) a);
        }
        if (a instanceof float[]) {
            return checkSumPlus((float[]) a);
        }
        if (a instanceof double[]) {
            return checkSumPlus((double[]) a);
        }
        fail(a);
        return -1;
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

    private void sortByInsertionSort(Object a) {
        SortingHelper.INSERTION_SORT.sort(a);
    }

    private void checkSubArray(Object a, int fromIndex, int toIndex) {
        if (a instanceof int[]) {
            checkSubArray((int[]) a, fromIndex, toIndex);
        } else if (a instanceof long[]) {
            checkSubArray((long[]) a, fromIndex, toIndex);
        } else if (a instanceof byte[]) {
            checkSubArray((byte[]) a, fromIndex, toIndex);
        } else if (a instanceof char[]) {
            checkSubArray((char[]) a, fromIndex, toIndex);
        } else if (a instanceof short[]) {
            checkSubArray((short[]) a, fromIndex, toIndex);
        } else if (a instanceof float[]) {
            checkSubArray((float[]) a, fromIndex, toIndex);
        } else if (a instanceof double[]) {
            checkSubArray((double[]) a, fromIndex, toIndex);
        } else {
            fail(a);
        }
    }

    private void checkSubArray(int[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            if (a[i] != A380) {
                fail("Range sort changes left element at position " + i + hex(a[i], A380));
            }
        }

        for (int i = fromIndex; i < toIndex - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; ++i) {
            if (a[i] != B747) {
                fail("Range sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSubArray(long[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            if (a[i] != (long) A380) {
                fail("Range sort changes left element at position " + i + hex(a[i], A380));
            }
        }

        for (int i = fromIndex; i < toIndex - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; ++i) {
            if (a[i] != (long) B747) {
                fail("Range sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSubArray(byte[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            if (a[i] != (byte) A380) {
                fail("Range sort changes left element at position " + i + hex(a[i], A380));
            }
        }

        for (int i = fromIndex; i < toIndex - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; ++i) {
            if (a[i] != (byte) B747) {
                fail("Range sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSubArray(char[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            if (a[i] != (char) A380) {
                fail("Range sort changes left element at position " + i + hex(a[i], A380));
            }
        }

        for (int i = fromIndex; i < toIndex - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; ++i) {
            if (a[i] != (char) B747) {
                fail("Range sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSubArray(short[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            if (a[i] != (short) A380) {
                fail("Range sort changes left element at position " + i + hex(a[i], A380));
            }
        }

        for (int i = fromIndex; i < toIndex - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; ++i) {
            if (a[i] != (short) B747) {
                fail("Range sort changes right element at position " + i + hex(a[i], B747));
            }
        }
    }

    private void checkSubArray(float[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            if (a[i] != (float) A380) {
                fail("Range sort changes left element at position " + i + hex((long) a[i], A380));
            }
        }

        for (int i = fromIndex; i < toIndex - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; ++i) {
            if (a[i] != (float) B747) {
                fail("Range sort changes right element at position " + i + hex((long) a[i], B747));
            }
        }
    }

    private void checkSubArray(double[] a, int fromIndex, int toIndex) {
        for (int i = 0; i < fromIndex; ++i) {
            if (a[i] != (double) A380) {
                fail("Range sort changes left element at position " + i + hex((long) a[i], A380));
            }
        }

        for (int i = fromIndex; i < toIndex - 1; ++i) {
            if (a[i] > a[i + 1]) {
                fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; ++i) {
            if (a[i] != (double) B747) {
                fail("Range sort changes right element at position " + i + hex((long) a[i], B747));
            }
        }
    }

    private void checkRange(Object a, int m) {
        if (a instanceof int[]) {
            checkRange((int[]) a, m);
        } else if (a instanceof long[]) {
            checkRange((long[]) a, m);
        } else if (a instanceof byte[]) {
            checkRange((byte[]) a, m);
        } else if (a instanceof char[]) {
            checkRange((char[]) a, m);
        } else if (a instanceof short[]) {
            checkRange((short[]) a, m);
        } else if (a instanceof float[]) {
            checkRange((float[]) a, m);
        } else if (a instanceof double[]) {
            checkRange((double[]) a, m);
        } else {
            fail(a);
        }
    }

    private void checkRange(int[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " does not throw IllegalArgumentException " +
                "as expected: fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                    "as expected: fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                        "as expected: toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {}
            }
        }
    }

    private void checkRange(long[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " does not throw IllegalArgumentException " +
                "as expected: fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                    "as expected: fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                        "as expected: toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {}
            }
        }
    }

    private void checkRange(byte[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " does not throw IllegalArgumentException " +
                "as expected: fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                    "as expected: fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                        "as expected: toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {}
            }
        }
    }

    private void checkRange(char[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " does not throw IllegalArgumentException " +
                "as expected: fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                    "as expected: fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                        "as expected: toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {}
            }
        }
    }

    private void checkRange(short[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " does not throw IllegalArgumentException " +
                "as expected: fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                    "as expected: fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                        "as expected: toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {}
            }
        }
    }

    private void checkRange(float[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " does not throw IllegalArgumentException " +
                "as expected: fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                    "as expected: fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                        "as expected: toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {}
            }
        }
    }

    private void checkRange(double[] a, int m) {
        try {
            sortingHelper.sort(a, m + 1, m);
            fail(sortingHelper + " does not throw IllegalArgumentException " +
                "as expected: fromIndex = " + (m + 1) + ", toIndex = " + m);
        } catch (IllegalArgumentException iae) {
            try {
                sortingHelper.sort(a, -m, a.length);
                fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                    "as expected: fromIndex = " + (-m));
            } catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    sortingHelper.sort(a, 0, a.length + m);
                    fail(sortingHelper + " does not throw ArrayIndexOutOfBoundsException " +
                        "as expected: toIndex = " + (a.length + m));
                } catch (ArrayIndexOutOfBoundsException expected) {}
            }
        }
    }

    private void copy(Object dst, Object src) {
        if (src instanceof float[]) {
            copy((float[]) dst, (float[]) src);
        } else if (src instanceof double[]) {
            copy((double[]) dst, (double[]) src);
        } else {
            fail(src);
        }
    }

    private void copy(float[] dst, float[] src) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    private void copy(double[] dst, double[] src) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    private void createData(int length) {
        gold = new Object[] {
            new int[length], new long[length],
            new byte[length], new char[length], new short[length],
            new float[length], new double[length]
        };

        test = new Object[] {
            new int[length], new long[length],
            new byte[length], new char[length], new short[length],
            new float[length], new double[length]
        };
    }

    private void convertData(int length) {
        for (int i = 0; i < gold.length; ++i) {
            TypeConverter converter = TypeConverter.values()[i];
            converter.convert((int[]) gold[0], gold[i], fix);
        }

        for (int i = 0; i < gold.length; ++i) {
            System.arraycopy(gold[i], 0, test[i], 0, length);
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

    private enum TypeConverter {

        INT {
            @Override
            void convert(int[] src, Object dst, boolean fix) {
                if (fix) {
                    src[0] = Integer.MIN_VALUE;
                }
            }
        },

        LONG {
            @Override
            void convert(int[] src, Object dst, boolean fix) {
                long[] b = (long[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = src[i];
                }
                if (fix) {
                    b[0] = Long.MIN_VALUE;
                }
            }
        },

        BYTE {
            @Override
            void convert(int[] src, Object dst, boolean fix) {
                byte[] b = (byte[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = (byte) src[i];
                }
                if (fix) {
                    b[0] = Byte.MIN_VALUE;
                }
            }
        },

        CHAR {
            @Override
            void convert(int[] src, Object dst, boolean fix) {
                char[] b = (char[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = (char) src[i];
                }
                if (fix) {
                    b[0] = Character.MIN_VALUE;
                }
            }
        },

        SHORT {
            @Override
            void convert(int[] src, Object dst, boolean fix) {
                short[] b = (short[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = (short) src[i];
                }
                if (fix) {
                    b[0] = Short.MIN_VALUE;
                }
            }
        },

        FLOAT {
            @Override
            void convert(int[] src, Object dst, boolean fix) {
                float[] b = (float[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = (float) src[i];
                }
                if (fix) {
                    b[0] = Float.NEGATIVE_INFINITY;
                }
            }
        },

        DOUBLE {
            @Override
            void convert(int[] src, Object dst, boolean fix) {
                double[] b = (double[]) dst;

                for (int i = 0; i < src.length; ++i) {
                    b[i] = src[i];
                }
                if (fix) {
                    b[0] = Double.NEGATIVE_INFINITY;
                }
            }
        };

        abstract void convert(int[] src, Object dst, boolean fix);
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
                    int t = a[i - 1]; a[i - 1] = a[k]; a[k] = t;
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

        MASKED {
            @Override
            void build(int[] a, int m) {
                int mask = (m << 15) - 1;

                for (int i = 0; i < a.length; ++i) {
                    a[i] = (i ^ 0xFF) & mask;
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

        STAGGER {
            @Override
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; ++i) {
                    a[i] = (i * m + i) % a.length;
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
                int max = a.length / m;
                max = Math.max(max, 2);

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
                reverse(a, m, a.length - 1);
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
            void build(Object o, int a, int g, int z, int n, int p, Random random) {
                float negativeValue = -random.nextFloat();
                float positiveValue =  random.nextFloat();
                float[] data = (float[]) o;
                int fromIndex = 0;

                fillWithValue(data, Float.NEGATIVE_INFINITY, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(data, -Float.MAX_VALUE, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(data, negativeValue, fromIndex, n);
                fromIndex += n;

                fillWithValue(data, -0.0f, fromIndex, g);
                fromIndex += g;

                fillWithValue(data, 0.0f, fromIndex, z);
                fromIndex += z;

                fillWithValue(data, positiveValue, fromIndex, p);
                fromIndex += p;

                fillWithValue(data, Float.MAX_VALUE, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(data, Float.POSITIVE_INFINITY, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(data, Float.NaN, fromIndex, a);
            }
        },

        DOUBLE {
            @Override
            void build(Object o, int a, int g, int z, int n, int p, Random random) {
                double negativeValue = -random.nextFloat();
                double positiveValue =  random.nextFloat();
                double[] data = (double[]) o;
                int fromIndex = 0;

                fillWithValue(data, Double.NEGATIVE_INFINITY, fromIndex, 1);
                fromIndex++;

                fillWithValue(data, -Double.MAX_VALUE, fromIndex, 1);
                fromIndex++;

                fillWithValue(data, negativeValue, fromIndex, n);
                fromIndex += n;

                fillWithValue(data, -0.0d, fromIndex, g);
                fromIndex += g;

                fillWithValue(data, 0.0d, fromIndex, z);
                fromIndex += z;

                fillWithValue(data, positiveValue, fromIndex, p);
                fromIndex += p;

                fillWithValue(data, Double.MAX_VALUE, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(data, Double.POSITIVE_INFINITY, fromIndex, 1);
                fromIndex += 1;

                fillWithValue(data, Double.NaN, fromIndex, a);
            }
        };

        abstract void build(Object o, int a, int g, int z, int n, int p, Random random);

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
