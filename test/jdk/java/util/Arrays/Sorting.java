/*
 * Copyright (c) 2009, 2026, Oracle and/or its affiliates. All rights reserved.
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

// -- This file was mechanically generated: Do not edit! -- //

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

    // Lengths of arrays for short run
    private static final int[] SHORT_LENGTHS =
        { 1, 2, 3, 14, 55, 100, 500, 1_000, 14_000 };

    // Lengths of arrays for long run (default)
    private static final int[] LONG_LENGTHS =
        { 1, 2, 3, 14, 55, 100, 500, 1_000, 14_000, 64_000};

    private static final Random random = new Random(0xC0FFEE);

    public static void main(String[] args) {
        boolean shortRun = args.length > 0 && args[0].equals("-shortrun");
        int[] lengths = shortRun ? SHORT_LENGTHS : LONG_LENGTHS;
        long start = System.currentTimeMillis();

        for (int length : lengths) {
            new IntegerHolder().test(length);
            new LongHolder().test(length);
            new ByteHolder().test(length);
            new CharacterHolder().test(length);
            new ShortHolder().test(length);
            new FloatHolder().test(length);
            new DoubleHolder().test(length);
        }
        long end = System.currentTimeMillis();
        out.format("PASSED in %d sec.\n", (end - start) / 1_000);
    }

    private static class IntegerHolder {
        // Constant to fill the left part of array
        private static final int A380 = (int) 0xA380;

        // Constant to fill the right part of array
        private static final int B747 = (int) 0xB747;

        private SortingHelper sortingHelper;
        private boolean withMin;
        private int[] gold;
        private int[] test;

        private IntegerHolder set(SortingHelper sortingHelper) {
            return set(sortingHelper, false);
        }

        private IntegerHolder set(SortingHelper sortingHelper, boolean withMin) {
            this.sortingHelper = sortingHelper;
            this.withMin = withMin;
            return this;
        }

        private void test(int length) {
            gold = new int[length];
            test = new int[length];

            set(SortingHelper.MERGING_SORT).testStructured();
            set(SortingHelper.MIXED_INSERTION_SORT, true).testBase();
            set(SortingHelper.INSERTION_SORT).testBase();
            set(SortingHelper.HEAP_SORT).testBase();
            set(SortingHelper.DUAL_PIVOT_QUICKSORT).testCore();
            set(SortingHelper.PARALLEL_QUICKSORT).testCore();
            set(SortingHelper.ARRAYS_SORT).testAll();
            set(SortingHelper.ARRAYS_PARALLEL_SORT).testAll();

            out.println();
        }

        private void testEmpty() {
            sortingHelper.sort(new int[test.length], 0, 0);
        }

        private void testStructured() {
            testEmpty();

            if (test.length < 512) {
                return;
            }
            for (int m = 1; m < 9; ++m) {
                for (Builder builder : StructuredBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("structured", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    checkWithCheckSum(0);
                }
            }
        }

        private void testBase() {
            if (test.length > 1_000) {
                return;
            }
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testCore() {
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testAll() {
            testCore();
            testRange();
        }

        private void testRange() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("range", m, builder);
                    testRange(m);
                }
            }
        }

        private void testRange(int m) {
            try {
                sortingHelper.sort(test, m + 1, m);
                fail(sortingHelper + " must throw IllegalArgumentException: " +
                        "fromIndex = " + (m + 1) + ", toIndex = " + m);
            } catch (IllegalArgumentException iae) {
                try {
                    sortingHelper.sort(test, -m, test.length);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "fromIndex = " + (-m));
                } catch (ArrayIndexOutOfBoundsException aoe) {
                    try {
                        sortingHelper.sort(test, 0, test.length + m);
                        fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                                "toIndex = " + (test.length + m));
                    } catch (ArrayIndexOutOfBoundsException expected) {}
                }
            }
        }

        private void testWithInsertionSort() {
            if (test.length > 1_000) {
                return;
            }
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with insertion sort", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    sortByInsertionSort(gold, offset, test.length - offset);
                    compare(test, gold);
                }
            }
        }

        private void testWithCheckSum() {
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with check sum", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    checkWithCheckSum(offset);
                }
            }
        }

        private void testWithScrambling() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : SortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("with scrambling", m, builder);
                    scramble();
                    sortingHelper.sort(test, 0, test.length);
                    compare(test, gold);
                }
            }
        }

        private void scramble() {
            if (withMin) {
                for (int i = 7; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length - 1) + 1, random.nextInt(test.length - 1) + 1);
                }
            } else {
                for (int i = 0; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length), random.nextInt(test.length));
                }
            }
        }

        private void checkWithCheckSum(int m) {
            checkSorted(test, m);
            checkCheckSum(test, gold);
        }

        private void checkSorted(int[] a, int m) {
            for (int i = 0; i < m; ++i) {
                if (a[i] != A380) {
                    fail("Sort changes left element at position " + i + ": " + a[i] + ", must be A380");
                }
            }
            for (int i = m; i < a.length - m - 1; ++i) {
                if (a[i] > a[i + 1]) {
                    fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
                }
            }
            for (int i = a.length - m; i < a.length; ++i) {
                if (a[i] != B747) {
                    fail("Sort changes right element at position " + i + ": " + a[i] + ", must be B747");
                }
            }
        }

        private void checkCheckSum(int[] a, int[] b) {
            if (checkSumXor(a) != checkSumXor(b)) {
                fail("Original and sorted arrays are not identical [^]");
            }
            if (checkSumPlus(a) != checkSumPlus(b)) {
                fail("Original and sorted arrays are not identical [+]");
            }
        }

        private long checkSumXor(int[] a) {
            long checkSum = 0;

            for (int e : a) {
                checkSum ^= (long) e;
            }
            return checkSum;
        }

        private long checkSumPlus(int[] a) {
            long checkSum = 0;

            for (int e : a) {
                checkSum += (long) e;
            }
            return checkSum;
        }

        private void compare(int[] a, int[] b) {
            for (int i = 0; i < a.length; ++i) {
                if (a[i] != b[i]) {
                    fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
                }
            }
        }

        private void sortByInsertionSort(int[] a, int low, int high) {
            SortingHelper.INSERTION_SORT.sort(a, low, high);
        }

        private void setup(int m) {
            for (int i = 0; i < m; ++i) {
                gold[i] = A380;
            }
            for (int i = gold.length - m; i < gold.length; ++i) {
                gold[i] = B747;
            }
            if (withMin) {
                gold[m] = Integer.MIN_VALUE;
            }
            test = gold.clone();
        }

        private void print(String name, int m, Builder builder) {
            out(name, "Integer", test.length, sortingHelper, m, builder);
        }

        private static void swap(int[] a, int i, int j) {
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }

        private static void reverse(int[] a, int lo, int hi) {
            for (--hi; lo < hi; swap(a, lo++, hi--));
        }

        private interface Builder {
            void build(int[] a, int m);
        }

        private enum SortedBuilder implements Builder {
            ANGLE {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (Math.min(i + m, 127));
                    }
                }
            },

            STEPS {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < m; ++i) {
                        a[i] = 0;
                    }
                    for (int i = m; i < a.length; ++i) {
                        a[i] = 1;
                    }
                }
            }
        }

        private enum UnsortedBuilder implements Builder {
            RANDOM {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) random.nextInt();
                    }
                }
            },

            PERMUTATION {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (m + i);
                    }
                    for (int i = a.length; i > 1; --i) {
                        swap(a, i - 1, random.nextInt(i));
                    }
                }
            },

            UNIFORM {
                @Override
                public void build(int[] a, int m) {
                    int mask = (m << 15) - 1;

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (random.nextInt() & mask);
                    }
                }
            },

            REPEATED {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (i % m);
                    }
                }
            },

            DUPLICATED {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) random.nextInt(m);
                    }
                }
            },

            SAWTOOTH {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0, minus = a.length, plus = 0; i < a.length; ) {
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (int) (++plus);
                        }
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (int) (--minus);
                        }
                    }
                }
            },

            SHUFFLE {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (int) (random.nextInt(m) > 0 ? (j += 2) : (k += 2));
                    }
                }
            }
        }

        private enum StructuredBuilder implements Builder {
            ASCENDING {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (m + i);
                    }
                }
            },

            DESCENDING {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (a.length - m - i);
                    }
                }
            },

            EQUAL {
                @Override
                public void build(int[] a, int m) {
                    Arrays.fill(a, (int) m);
                }
            },

            SHIFTED {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (i << 10);
                    }
                }
            },

            ORGAN_PIPES {
                @Override
                public void build(int[] a, int m) {
                    int middle = a.length / (m + 1);

                    for (int i = 0; i < middle; ++i) {
                        a[i] = (int) i;
                    }
                    for (int i = middle; i < a.length; ++i) {
                        a[i] = (int) (a.length - i - 1);
                    }
                }
            },

            PLATEAU {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) Math.min(i, m);
                    }
                }
            },

            LATCH {
                @Override
                public void build(int[] a, int m) {
                    int max = Math.max(a.length / m, 2);

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) (i % max);
                    }
                }
            },

            POINT {
                @Override
                public void build(int[] a, int m) {
                    Arrays.fill(a, (int) 0);
                    a[a.length / 2] = (int) m;
                }
            },

            LINE {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) i;
                    }
                    reverse(a, Math.max(0, a.length - m), a.length);
                }
            },

            PEARL {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) i;
                    }
                    reverse(a, 0, Math.min(m, a.length));
                }
            },

            TRAPEZIUM {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) i;
                    }
                    reverse(a, m, a.length - m);
                }
            },

            STAGGER {
                @Override
                public void build(int[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (int) ((i * m + i) % a.length);
                    }
                }
            }
        }
    }

    private static class LongHolder {
        // Constant to fill the left part of array
        private static final long A380 = (long) 0xA380;

        // Constant to fill the right part of array
        private static final long B747 = (long) 0xB747;

        private SortingHelper sortingHelper;
        private boolean withMin;
        private long[] gold;
        private long[] test;

        private LongHolder set(SortingHelper sortingHelper) {
            return set(sortingHelper, false);
        }

        private LongHolder set(SortingHelper sortingHelper, boolean withMin) {
            this.sortingHelper = sortingHelper;
            this.withMin = withMin;
            return this;
        }

        private void test(int length) {
            gold = new long[length];
            test = new long[length];

            set(SortingHelper.MERGING_SORT).testStructured();
            set(SortingHelper.MIXED_INSERTION_SORT, true).testBase();
            set(SortingHelper.INSERTION_SORT).testBase();
            set(SortingHelper.HEAP_SORT).testBase();
            set(SortingHelper.DUAL_PIVOT_QUICKSORT).testCore();
            set(SortingHelper.PARALLEL_QUICKSORT).testCore();
            set(SortingHelper.ARRAYS_SORT).testAll();
            set(SortingHelper.ARRAYS_PARALLEL_SORT).testAll();

            out.println();
        }

        private void testEmpty() {
            sortingHelper.sort(new long[test.length], 0, 0);
        }

        private void testStructured() {
            testEmpty();

            if (test.length < 512) {
                return;
            }
            for (int m = 1; m < 9; ++m) {
                for (Builder builder : StructuredBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("structured", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    checkWithCheckSum(0);
                }
            }
        }

        private void testBase() {
            if (test.length > 1_000) {
                return;
            }
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testCore() {
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testAll() {
            testCore();
            testRange();
        }

        private void testRange() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("range", m, builder);
                    testRange(m);
                }
            }
        }

        private void testRange(int m) {
            try {
                sortingHelper.sort(test, m + 1, m);
                fail(sortingHelper + " must throw IllegalArgumentException: " +
                        "fromIndex = " + (m + 1) + ", toIndex = " + m);
            } catch (IllegalArgumentException iae) {
                try {
                    sortingHelper.sort(test, -m, test.length);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "fromIndex = " + (-m));
                } catch (ArrayIndexOutOfBoundsException aoe) {
                    try {
                        sortingHelper.sort(test, 0, test.length + m);
                        fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                                "toIndex = " + (test.length + m));
                    } catch (ArrayIndexOutOfBoundsException expected) {}
                }
            }
        }

        private void testWithInsertionSort() {
            if (test.length > 1_000) {
                return;
            }
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with insertion sort", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    sortByInsertionSort(gold, offset, test.length - offset);
                    compare(test, gold);
                }
            }
        }

        private void testWithCheckSum() {
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with check sum", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    checkWithCheckSum(offset);
                }
            }
        }

        private void testWithScrambling() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : SortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("with scrambling", m, builder);
                    scramble();
                    sortingHelper.sort(test, 0, test.length);
                    compare(test, gold);
                }
            }
        }

        private void scramble() {
            if (withMin) {
                for (int i = 7; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length - 1) + 1, random.nextInt(test.length - 1) + 1);
                }
            } else {
                for (int i = 0; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length), random.nextInt(test.length));
                }
            }
        }

        private void checkWithCheckSum(int m) {
            checkSorted(test, m);
            checkCheckSum(test, gold);
        }

        private void checkSorted(long[] a, int m) {
            for (int i = 0; i < m; ++i) {
                if (a[i] != A380) {
                    fail("Sort changes left element at position " + i + ": " + a[i] + ", must be A380");
                }
            }
            for (int i = m; i < a.length - m - 1; ++i) {
                if (a[i] > a[i + 1]) {
                    fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
                }
            }
            for (int i = a.length - m; i < a.length; ++i) {
                if (a[i] != B747) {
                    fail("Sort changes right element at position " + i + ": " + a[i] + ", must be B747");
                }
            }
        }

        private void checkCheckSum(long[] a, long[] b) {
            if (checkSumXor(a) != checkSumXor(b)) {
                fail("Original and sorted arrays are not identical [^]");
            }
            if (checkSumPlus(a) != checkSumPlus(b)) {
                fail("Original and sorted arrays are not identical [+]");
            }
        }

        private long checkSumXor(long[] a) {
            long checkSum = 0;

            for (long e : a) {
                checkSum ^= (long) e;
            }
            return checkSum;
        }

        private long checkSumPlus(long[] a) {
            long checkSum = 0;

            for (long e : a) {
                checkSum += (long) e;
            }
            return checkSum;
        }

        private void compare(long[] a, long[] b) {
            for (int i = 0; i < a.length; ++i) {
                if (a[i] != b[i]) {
                    fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
                }
            }
        }

        private void sortByInsertionSort(long[] a, int low, int high) {
            SortingHelper.INSERTION_SORT.sort(a, low, high);
        }

        private void setup(int m) {
            for (int i = 0; i < m; ++i) {
                gold[i] = A380;
            }
            for (int i = gold.length - m; i < gold.length; ++i) {
                gold[i] = B747;
            }
            if (withMin) {
                gold[m] = Long.MIN_VALUE;
            }
            test = gold.clone();
        }

        private void print(String name, int m, Builder builder) {
            out(name, "Long", test.length, sortingHelper, m, builder);
        }

        private static void swap(long[] a, int i, int j) {
            long t = a[i]; a[i] = a[j]; a[j] = t;
        }

        private static void reverse(long[] a, int lo, int hi) {
            for (--hi; lo < hi; swap(a, lo++, hi--));
        }

        private interface Builder {
            void build(long[] a, int m);
        }

        private enum SortedBuilder implements Builder {
            ANGLE {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (Math.min(i + m, 127));
                    }
                }
            },

            STEPS {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < m; ++i) {
                        a[i] = 0;
                    }
                    for (int i = m; i < a.length; ++i) {
                        a[i] = 1;
                    }
                }
            }
        }

        private enum UnsortedBuilder implements Builder {
            RANDOM {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) random.nextInt();
                    }
                }
            },

            PERMUTATION {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (m + i);
                    }
                    for (int i = a.length; i > 1; --i) {
                        swap(a, i - 1, random.nextInt(i));
                    }
                }
            },

            UNIFORM {
                @Override
                public void build(long[] a, int m) {
                    int mask = (m << 15) - 1;

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (random.nextInt() & mask);
                    }
                }
            },

            REPEATED {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (i % m);
                    }
                }
            },

            DUPLICATED {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) random.nextInt(m);
                    }
                }
            },

            SAWTOOTH {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0, minus = a.length, plus = 0; i < a.length; ) {
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (long) (++plus);
                        }
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (long) (--minus);
                        }
                    }
                }
            },

            SHUFFLE {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (long) (random.nextInt(m) > 0 ? (j += 2) : (k += 2));
                    }
                }
            }
        }

        private enum StructuredBuilder implements Builder {
            ASCENDING {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (m + i);
                    }
                }
            },

            DESCENDING {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (a.length - m - i);
                    }
                }
            },

            EQUAL {
                @Override
                public void build(long[] a, int m) {
                    Arrays.fill(a, (long) m);
                }
            },

            SHIFTED {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (i << 10);
                    }
                }
            },

            ORGAN_PIPES {
                @Override
                public void build(long[] a, int m) {
                    int middle = a.length / (m + 1);

                    for (int i = 0; i < middle; ++i) {
                        a[i] = (long) i;
                    }
                    for (int i = middle; i < a.length; ++i) {
                        a[i] = (long) (a.length - i - 1);
                    }
                }
            },

            PLATEAU {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) Math.min(i, m);
                    }
                }
            },

            LATCH {
                @Override
                public void build(long[] a, int m) {
                    int max = Math.max(a.length / m, 2);

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) (i % max);
                    }
                }
            },

            POINT {
                @Override
                public void build(long[] a, int m) {
                    Arrays.fill(a, (long) 0);
                    a[a.length / 2] = (long) m;
                }
            },

            LINE {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) i;
                    }
                    reverse(a, Math.max(0, a.length - m), a.length);
                }
            },

            PEARL {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) i;
                    }
                    reverse(a, 0, Math.min(m, a.length));
                }
            },

            TRAPEZIUM {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) i;
                    }
                    reverse(a, m, a.length - m);
                }
            },

            STAGGER {
                @Override
                public void build(long[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (long) ((i * m + i) % a.length);
                    }
                }
            }
        }
    }

    private static class ByteHolder {
        // Constant to fill the left part of array
        private static final byte A380 = (byte) 0xA380;

        // Constant to fill the right part of array
        private static final byte B747 = (byte) 0xB747;

        private SortingHelper sortingHelper;
        private boolean withMin;
        private byte[] gold;
        private byte[] test;

        private ByteHolder set(SortingHelper sortingHelper) {
            return set(sortingHelper, false);
        }

        private ByteHolder set(SortingHelper sortingHelper, boolean withMin) {
            this.sortingHelper = sortingHelper;
            this.withMin = withMin;
            return this;
        }

        private void test(int length) {
            gold = new byte[length];
            test = new byte[length];

            set(SortingHelper.INSERTION_SORT).testBase();
            set(SortingHelper.COUNTING_SORT).testCore();
            set(SortingHelper.DUAL_PIVOT_QUICKSORT).testCore();
            set(SortingHelper.ARRAYS_SORT).testAll();
            set(SortingHelper.ARRAYS_PARALLEL_SORT).testAll();

            out.println();
        }

        private void testEmpty() {
            sortingHelper.sort(new byte[test.length], 0, 0);
        }

        private void testStructured() {
            testEmpty();

            if (test.length < 512) {
                return;
            }
            for (int m = 1; m < 9; ++m) {
                for (Builder builder : StructuredBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("structured", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    checkWithCheckSum(0);
                }
            }
        }

        private void testBase() {
            if (test.length > 1_000) {
                return;
            }
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testCore() {
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testAll() {
            testCore();
            testRange();
        }

        private void testRange() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("range", m, builder);
                    testRange(m);
                }
            }
        }

        private void testRange(int m) {
            try {
                sortingHelper.sort(test, m + 1, m);
                fail(sortingHelper + " must throw IllegalArgumentException: " +
                        "fromIndex = " + (m + 1) + ", toIndex = " + m);
            } catch (IllegalArgumentException iae) {
                try {
                    sortingHelper.sort(test, -m, test.length);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "fromIndex = " + (-m));
                } catch (ArrayIndexOutOfBoundsException aoe) {
                    try {
                        sortingHelper.sort(test, 0, test.length + m);
                        fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                                "toIndex = " + (test.length + m));
                    } catch (ArrayIndexOutOfBoundsException expected) {}
                }
            }
        }

        private void testWithInsertionSort() {
            if (test.length > 1_000) {
                return;
            }
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with insertion sort", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    sortByInsertionSort(gold, offset, test.length - offset);
                    compare(test, gold);
                }
            }
        }

        private void testWithCheckSum() {
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with check sum", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    checkWithCheckSum(offset);
                }
            }
        }

        private void testWithScrambling() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : SortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("with scrambling", m, builder);
                    scramble();
                    sortingHelper.sort(test, 0, test.length);
                    compare(test, gold);
                }
            }
        }

        private void scramble() {
            if (withMin) {
                for (int i = 7; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length - 1) + 1, random.nextInt(test.length - 1) + 1);
                }
            } else {
                for (int i = 0; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length), random.nextInt(test.length));
                }
            }
        }

        private void checkWithCheckSum(int m) {
            checkSorted(test, m);
            checkCheckSum(test, gold);
        }

        private void checkSorted(byte[] a, int m) {
            for (int i = 0; i < m; ++i) {
                if (a[i] != A380) {
                    fail("Sort changes left element at position " + i + ": " + a[i] + ", must be A380");
                }
            }
            for (int i = m; i < a.length - m - 1; ++i) {
                if (a[i] > a[i + 1]) {
                    fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
                }
            }
            for (int i = a.length - m; i < a.length; ++i) {
                if (a[i] != B747) {
                    fail("Sort changes right element at position " + i + ": " + a[i] + ", must be B747");
                }
            }
        }

        private void checkCheckSum(byte[] a, byte[] b) {
            if (checkSumXor(a) != checkSumXor(b)) {
                fail("Original and sorted arrays are not identical [^]");
            }
            if (checkSumPlus(a) != checkSumPlus(b)) {
                fail("Original and sorted arrays are not identical [+]");
            }
        }

        private long checkSumXor(byte[] a) {
            long checkSum = 0;

            for (byte e : a) {
                checkSum ^= (long) e;
            }
            return checkSum;
        }

        private long checkSumPlus(byte[] a) {
            long checkSum = 0;

            for (byte e : a) {
                checkSum += (long) e;
            }
            return checkSum;
        }

        private void compare(byte[] a, byte[] b) {
            for (int i = 0; i < a.length; ++i) {
                if (a[i] != b[i]) {
                    fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
                }
            }
        }

        private void sortByInsertionSort(byte[] a, int low, int high) {
            SortingHelper.INSERTION_SORT.sort(a, low, high);
        }

        private void setup(int m) {
            for (int i = 0; i < m; ++i) {
                gold[i] = A380;
            }
            for (int i = gold.length - m; i < gold.length; ++i) {
                gold[i] = B747;
            }
            if (withMin) {
                gold[m] = Byte.MIN_VALUE;
            }
            test = gold.clone();
        }

        private void print(String name, int m, Builder builder) {
            out(name, "Byte", test.length, sortingHelper, m, builder);
        }

        private static void swap(byte[] a, int i, int j) {
            byte t = a[i]; a[i] = a[j]; a[j] = t;
        }

        private static void reverse(byte[] a, int lo, int hi) {
            for (--hi; lo < hi; swap(a, lo++, hi--));
        }

        private interface Builder {
            void build(byte[] a, int m);
        }

        private enum SortedBuilder implements Builder {
            ANGLE {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (Math.min(i + m, 127));
                    }
                }
            },

            STEPS {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < m; ++i) {
                        a[i] = 0;
                    }
                    for (int i = m; i < a.length; ++i) {
                        a[i] = 1;
                    }
                }
            }
        }

        private enum UnsortedBuilder implements Builder {
            RANDOM {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) random.nextInt();
                    }
                }
            },

            PERMUTATION {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (m + i);
                    }
                    for (int i = a.length; i > 1; --i) {
                        swap(a, i - 1, random.nextInt(i));
                    }
                }
            },

            UNIFORM {
                @Override
                public void build(byte[] a, int m) {
                    int mask = (m << 15) - 1;

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (random.nextInt() & mask);
                    }
                }
            },

            REPEATED {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (i % m);
                    }
                }
            },

            DUPLICATED {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) random.nextInt(m);
                    }
                }
            },

            SAWTOOTH {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0, minus = a.length, plus = 0; i < a.length; ) {
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (byte) (++plus);
                        }
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (byte) (--minus);
                        }
                    }
                }
            },

            SHUFFLE {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (byte) (random.nextInt(m) > 0 ? (j += 2) : (k += 2));
                    }
                }
            }
        }

        private enum StructuredBuilder implements Builder {
            ASCENDING {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (m + i);
                    }
                }
            },

            DESCENDING {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (a.length - m - i);
                    }
                }
            },

            EQUAL {
                @Override
                public void build(byte[] a, int m) {
                    Arrays.fill(a, (byte) m);
                }
            },

            SHIFTED {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (i << 10);
                    }
                }
            },

            ORGAN_PIPES {
                @Override
                public void build(byte[] a, int m) {
                    int middle = a.length / (m + 1);

                    for (int i = 0; i < middle; ++i) {
                        a[i] = (byte) i;
                    }
                    for (int i = middle; i < a.length; ++i) {
                        a[i] = (byte) (a.length - i - 1);
                    }
                }
            },

            PLATEAU {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) Math.min(i, m);
                    }
                }
            },

            LATCH {
                @Override
                public void build(byte[] a, int m) {
                    int max = Math.max(a.length / m, 2);

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (i % max);
                    }
                }
            },

            POINT {
                @Override
                public void build(byte[] a, int m) {
                    Arrays.fill(a, (byte) 0);
                    a[a.length / 2] = (byte) m;
                }
            },

            LINE {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) i;
                    }
                    reverse(a, Math.max(0, a.length - m), a.length);
                }
            },

            PEARL {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) i;
                    }
                    reverse(a, 0, Math.min(m, a.length));
                }
            },

            TRAPEZIUM {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) i;
                    }
                    reverse(a, m, a.length - m);
                }
            },

            STAGGER {
                @Override
                public void build(byte[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) ((i * m + i) % a.length);
                    }
                }
            }
        }
    }

    private static class CharacterHolder {
        // Constant to fill the left part of array
        private static final char A380 = (char) 0xA380;

        // Constant to fill the right part of array
        private static final char B747 = (char) 0xB747;

        private SortingHelper sortingHelper;
        private boolean withMin;
        private char[] gold;
        private char[] test;

        private CharacterHolder set(SortingHelper sortingHelper) {
            return set(sortingHelper, false);
        }

        private CharacterHolder set(SortingHelper sortingHelper, boolean withMin) {
            this.sortingHelper = sortingHelper;
            this.withMin = withMin;
            return this;
        }

        private void test(int length) {
            gold = new char[length];
            test = new char[length];

            set(SortingHelper.INSERTION_SORT).testBase();
            set(SortingHelper.COUNTING_SORT).testCore();
            set(SortingHelper.DUAL_PIVOT_QUICKSORT).testCore();
            set(SortingHelper.ARRAYS_SORT).testAll();
            set(SortingHelper.ARRAYS_PARALLEL_SORT).testAll();

            out.println();
        }

        private void testEmpty() {
            sortingHelper.sort(new char[test.length], 0, 0);
        }

        private void testStructured() {
            testEmpty();

            if (test.length < 512) {
                return;
            }
            for (int m = 1; m < 9; ++m) {
                for (Builder builder : StructuredBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("structured", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    checkWithCheckSum(0);
                }
            }
        }

        private void testBase() {
            if (test.length > 1_000) {
                return;
            }
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testCore() {
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testAll() {
            testCore();
            testRange();
        }

        private void testRange() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("range", m, builder);
                    testRange(m);
                }
            }
        }

        private void testRange(int m) {
            try {
                sortingHelper.sort(test, m + 1, m);
                fail(sortingHelper + " must throw IllegalArgumentException: " +
                        "fromIndex = " + (m + 1) + ", toIndex = " + m);
            } catch (IllegalArgumentException iae) {
                try {
                    sortingHelper.sort(test, -m, test.length);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "fromIndex = " + (-m));
                } catch (ArrayIndexOutOfBoundsException aoe) {
                    try {
                        sortingHelper.sort(test, 0, test.length + m);
                        fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                                "toIndex = " + (test.length + m));
                    } catch (ArrayIndexOutOfBoundsException expected) {}
                }
            }
        }

        private void testWithInsertionSort() {
            if (test.length > 1_000) {
                return;
            }
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with insertion sort", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    sortByInsertionSort(gold, offset, test.length - offset);
                    compare(test, gold);
                }
            }
        }

        private void testWithCheckSum() {
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with check sum", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    checkWithCheckSum(offset);
                }
            }
        }

        private void testWithScrambling() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : SortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("with scrambling", m, builder);
                    scramble();
                    sortingHelper.sort(test, 0, test.length);
                    compare(test, gold);
                }
            }
        }

        private void scramble() {
            if (withMin) {
                for (int i = 7; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length - 1) + 1, random.nextInt(test.length - 1) + 1);
                }
            } else {
                for (int i = 0; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length), random.nextInt(test.length));
                }
            }
        }

        private void checkWithCheckSum(int m) {
            checkSorted(test, m);
            checkCheckSum(test, gold);
        }

        private void checkSorted(char[] a, int m) {
            for (int i = 0; i < m; ++i) {
                if (a[i] != A380) {
                    fail("Sort changes left element at position " + i + ": " + a[i] + ", must be A380");
                }
            }
            for (int i = m; i < a.length - m - 1; ++i) {
                if (a[i] > a[i + 1]) {
                    fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
                }
            }
            for (int i = a.length - m; i < a.length; ++i) {
                if (a[i] != B747) {
                    fail("Sort changes right element at position " + i + ": " + a[i] + ", must be B747");
                }
            }
        }

        private void checkCheckSum(char[] a, char[] b) {
            if (checkSumXor(a) != checkSumXor(b)) {
                fail("Original and sorted arrays are not identical [^]");
            }
            if (checkSumPlus(a) != checkSumPlus(b)) {
                fail("Original and sorted arrays are not identical [+]");
            }
        }

        private long checkSumXor(char[] a) {
            long checkSum = 0;

            for (char e : a) {
                checkSum ^= (long) e;
            }
            return checkSum;
        }

        private long checkSumPlus(char[] a) {
            long checkSum = 0;

            for (char e : a) {
                checkSum += (long) e;
            }
            return checkSum;
        }

        private void compare(char[] a, char[] b) {
            for (int i = 0; i < a.length; ++i) {
                if (a[i] != b[i]) {
                    fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
                }
            }
        }

        private void sortByInsertionSort(char[] a, int low, int high) {
            SortingHelper.INSERTION_SORT.sort(a, low, high);
        }

        private void setup(int m) {
            for (int i = 0; i < m; ++i) {
                gold[i] = A380;
            }
            for (int i = gold.length - m; i < gold.length; ++i) {
                gold[i] = B747;
            }
            if (withMin) {
                gold[m] = Character.MIN_VALUE;
            }
            test = gold.clone();
        }

        private void print(String name, int m, Builder builder) {
            out(name, "Character", test.length, sortingHelper, m, builder);
        }

        private static void swap(char[] a, int i, int j) {
            char t = a[i]; a[i] = a[j]; a[j] = t;
        }

        private static void reverse(char[] a, int lo, int hi) {
            for (--hi; lo < hi; swap(a, lo++, hi--));
        }

        private interface Builder {
            void build(char[] a, int m);
        }

        private enum SortedBuilder implements Builder {
            ANGLE {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (Math.min(i + m, 127));
                    }
                }
            },

            STEPS {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < m; ++i) {
                        a[i] = 0;
                    }
                    for (int i = m; i < a.length; ++i) {
                        a[i] = 1;
                    }
                }
            }
        }

        private enum UnsortedBuilder implements Builder {
            RANDOM {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) random.nextInt();
                    }
                }
            },

            PERMUTATION {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (m + i);
                    }
                    for (int i = a.length; i > 1; --i) {
                        swap(a, i - 1, random.nextInt(i));
                    }
                }
            },

            UNIFORM {
                @Override
                public void build(char[] a, int m) {
                    int mask = (m << 15) - 1;

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (random.nextInt() & mask);
                    }
                }
            },

            REPEATED {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (i % m);
                    }
                }
            },

            DUPLICATED {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) random.nextInt(m);
                    }
                }
            },

            SAWTOOTH {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0, minus = a.length, plus = 0; i < a.length; ) {
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (char) (++plus);
                        }
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (char) (--minus);
                        }
                    }
                }
            },

            SHUFFLE {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (char) (random.nextInt(m) > 0 ? (j += 2) : (k += 2));
                    }
                }
            }
        }

        private enum StructuredBuilder implements Builder {
            ASCENDING {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (m + i);
                    }
                }
            },

            DESCENDING {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (a.length - m - i);
                    }
                }
            },

            EQUAL {
                @Override
                public void build(char[] a, int m) {
                    Arrays.fill(a, (char) m);
                }
            },

            SHIFTED {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (i << 10);
                    }
                }
            },

            ORGAN_PIPES {
                @Override
                public void build(char[] a, int m) {
                    int middle = a.length / (m + 1);

                    for (int i = 0; i < middle; ++i) {
                        a[i] = (char) i;
                    }
                    for (int i = middle; i < a.length; ++i) {
                        a[i] = (char) (a.length - i - 1);
                    }
                }
            },

            PLATEAU {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) Math.min(i, m);
                    }
                }
            },

            LATCH {
                @Override
                public void build(char[] a, int m) {
                    int max = Math.max(a.length / m, 2);

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (i % max);
                    }
                }
            },

            POINT {
                @Override
                public void build(char[] a, int m) {
                    Arrays.fill(a, (char) 0);
                    a[a.length / 2] = (char) m;
                }
            },

            LINE {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) i;
                    }
                    reverse(a, Math.max(0, a.length - m), a.length);
                }
            },

            PEARL {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) i;
                    }
                    reverse(a, 0, Math.min(m, a.length));
                }
            },

            TRAPEZIUM {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) i;
                    }
                    reverse(a, m, a.length - m);
                }
            },

            STAGGER {
                @Override
                public void build(char[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) ((i * m + i) % a.length);
                    }
                }
            }
        }
    }

    private static class ShortHolder {
        // Constant to fill the left part of array
        private static final short A380 = (short) 0xA380;

        // Constant to fill the right part of array
        private static final short B747 = (short) 0xB747;

        private SortingHelper sortingHelper;
        private boolean withMin;
        private short[] gold;
        private short[] test;

        private ShortHolder set(SortingHelper sortingHelper) {
            return set(sortingHelper, false);
        }

        private ShortHolder set(SortingHelper sortingHelper, boolean withMin) {
            this.sortingHelper = sortingHelper;
            this.withMin = withMin;
            return this;
        }

        private void test(int length) {
            gold = new short[length];
            test = new short[length];

            set(SortingHelper.INSERTION_SORT).testBase();
            set(SortingHelper.COUNTING_SORT).testCore();
            set(SortingHelper.DUAL_PIVOT_QUICKSORT).testCore();
            set(SortingHelper.ARRAYS_SORT).testAll();
            set(SortingHelper.ARRAYS_PARALLEL_SORT).testAll();

            out.println();
        }

        private void testEmpty() {
            sortingHelper.sort(new short[test.length], 0, 0);
        }

        private void testStructured() {
            testEmpty();

            if (test.length < 512) {
                return;
            }
            for (int m = 1; m < 9; ++m) {
                for (Builder builder : StructuredBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("structured", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    checkWithCheckSum(0);
                }
            }
        }

        private void testBase() {
            if (test.length > 1_000) {
                return;
            }
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testCore() {
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testAll() {
            testCore();
            testRange();
        }

        private void testRange() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("range", m, builder);
                    testRange(m);
                }
            }
        }

        private void testRange(int m) {
            try {
                sortingHelper.sort(test, m + 1, m);
                fail(sortingHelper + " must throw IllegalArgumentException: " +
                        "fromIndex = " + (m + 1) + ", toIndex = " + m);
            } catch (IllegalArgumentException iae) {
                try {
                    sortingHelper.sort(test, -m, test.length);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "fromIndex = " + (-m));
                } catch (ArrayIndexOutOfBoundsException aoe) {
                    try {
                        sortingHelper.sort(test, 0, test.length + m);
                        fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                                "toIndex = " + (test.length + m));
                    } catch (ArrayIndexOutOfBoundsException expected) {}
                }
            }
        }

        private void testWithInsertionSort() {
            if (test.length > 1_000) {
                return;
            }
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with insertion sort", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    sortByInsertionSort(gold, offset, test.length - offset);
                    compare(test, gold);
                }
            }
        }

        private void testWithCheckSum() {
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with check sum", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    checkWithCheckSum(offset);
                }
            }
        }

        private void testWithScrambling() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : SortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("with scrambling", m, builder);
                    scramble();
                    sortingHelper.sort(test, 0, test.length);
                    compare(test, gold);
                }
            }
        }

        private void scramble() {
            if (withMin) {
                for (int i = 7; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length - 1) + 1, random.nextInt(test.length - 1) + 1);
                }
            } else {
                for (int i = 0; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length), random.nextInt(test.length));
                }
            }
        }

        private void checkWithCheckSum(int m) {
            checkSorted(test, m);
            checkCheckSum(test, gold);
        }

        private void checkSorted(short[] a, int m) {
            for (int i = 0; i < m; ++i) {
                if (a[i] != A380) {
                    fail("Sort changes left element at position " + i + ": " + a[i] + ", must be A380");
                }
            }
            for (int i = m; i < a.length - m - 1; ++i) {
                if (a[i] > a[i + 1]) {
                    fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
                }
            }
            for (int i = a.length - m; i < a.length; ++i) {
                if (a[i] != B747) {
                    fail("Sort changes right element at position " + i + ": " + a[i] + ", must be B747");
                }
            }
        }

        private void checkCheckSum(short[] a, short[] b) {
            if (checkSumXor(a) != checkSumXor(b)) {
                fail("Original and sorted arrays are not identical [^]");
            }
            if (checkSumPlus(a) != checkSumPlus(b)) {
                fail("Original and sorted arrays are not identical [+]");
            }
        }

        private long checkSumXor(short[] a) {
            long checkSum = 0;

            for (short e : a) {
                checkSum ^= (long) e;
            }
            return checkSum;
        }

        private long checkSumPlus(short[] a) {
            long checkSum = 0;

            for (short e : a) {
                checkSum += (long) e;
            }
            return checkSum;
        }

        private void compare(short[] a, short[] b) {
            for (int i = 0; i < a.length; ++i) {
                if (a[i] != b[i]) {
                    fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
                }
            }
        }

        private void sortByInsertionSort(short[] a, int low, int high) {
            SortingHelper.INSERTION_SORT.sort(a, low, high);
        }

        private void setup(int m) {
            for (int i = 0; i < m; ++i) {
                gold[i] = A380;
            }
            for (int i = gold.length - m; i < gold.length; ++i) {
                gold[i] = B747;
            }
            if (withMin) {
                gold[m] = Short.MIN_VALUE;
            }
            test = gold.clone();
        }

        private void print(String name, int m, Builder builder) {
            out(name, "Short", test.length, sortingHelper, m, builder);
        }

        private static void swap(short[] a, int i, int j) {
            short t = a[i]; a[i] = a[j]; a[j] = t;
        }

        private static void reverse(short[] a, int lo, int hi) {
            for (--hi; lo < hi; swap(a, lo++, hi--));
        }

        private interface Builder {
            void build(short[] a, int m);
        }

        private enum SortedBuilder implements Builder {
            ANGLE {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (Math.min(i + m, 127));
                    }
                }
            },

            STEPS {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < m; ++i) {
                        a[i] = 0;
                    }
                    for (int i = m; i < a.length; ++i) {
                        a[i] = 1;
                    }
                }
            }
        }

        private enum UnsortedBuilder implements Builder {
            RANDOM {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) random.nextInt();
                    }
                }
            },

            PERMUTATION {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (m + i);
                    }
                    for (int i = a.length; i > 1; --i) {
                        swap(a, i - 1, random.nextInt(i));
                    }
                }
            },

            UNIFORM {
                @Override
                public void build(short[] a, int m) {
                    int mask = (m << 15) - 1;

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (random.nextInt() & mask);
                    }
                }
            },

            REPEATED {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (i % m);
                    }
                }
            },

            DUPLICATED {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) random.nextInt(m);
                    }
                }
            },

            SAWTOOTH {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0, minus = a.length, plus = 0; i < a.length; ) {
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (short) (++plus);
                        }
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (short) (--minus);
                        }
                    }
                }
            },

            SHUFFLE {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (short) (random.nextInt(m) > 0 ? (j += 2) : (k += 2));
                    }
                }
            }
        }

        private enum StructuredBuilder implements Builder {
            ASCENDING {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (m + i);
                    }
                }
            },

            DESCENDING {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (a.length - m - i);
                    }
                }
            },

            EQUAL {
                @Override
                public void build(short[] a, int m) {
                    Arrays.fill(a, (short) m);
                }
            },

            SHIFTED {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (i << 10);
                    }
                }
            },

            ORGAN_PIPES {
                @Override
                public void build(short[] a, int m) {
                    int middle = a.length / (m + 1);

                    for (int i = 0; i < middle; ++i) {
                        a[i] = (short) i;
                    }
                    for (int i = middle; i < a.length; ++i) {
                        a[i] = (short) (a.length - i - 1);
                    }
                }
            },

            PLATEAU {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) Math.min(i, m);
                    }
                }
            },

            LATCH {
                @Override
                public void build(short[] a, int m) {
                    int max = Math.max(a.length / m, 2);

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (i % max);
                    }
                }
            },

            POINT {
                @Override
                public void build(short[] a, int m) {
                    Arrays.fill(a, (short) 0);
                    a[a.length / 2] = (short) m;
                }
            },

            LINE {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) i;
                    }
                    reverse(a, Math.max(0, a.length - m), a.length);
                }
            },

            PEARL {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) i;
                    }
                    reverse(a, 0, Math.min(m, a.length));
                }
            },

            TRAPEZIUM {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) i;
                    }
                    reverse(a, m, a.length - m);
                }
            },

            STAGGER {
                @Override
                public void build(short[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) ((i * m + i) % a.length);
                    }
                }
            }
        }
    }

    private static class FloatHolder {
        // Constant to fill the left part of array
        private static final float A380 = (float) 0xA380;

        // Constant to fill the right part of array
        private static final float B747 = (float) 0xB747;

        private SortingHelper sortingHelper;
        private boolean withMin;
        private float[] gold;
        private float[] test;

        private FloatHolder set(SortingHelper sortingHelper) {
            return set(sortingHelper, false);
        }

        private FloatHolder set(SortingHelper sortingHelper, boolean withMin) {
            this.sortingHelper = sortingHelper;
            this.withMin = withMin;
            return this;
        }

        private void test(int length) {
            gold = new float[length];
            test = new float[length];

            set(SortingHelper.MERGING_SORT).testStructured();
            set(SortingHelper.MIXED_INSERTION_SORT, true).testBase();
            set(SortingHelper.INSERTION_SORT).testBase();
            set(SortingHelper.HEAP_SORT).testBase();
            set(SortingHelper.DUAL_PIVOT_QUICKSORT).testCore();
            set(SortingHelper.PARALLEL_QUICKSORT).testCore();
            set(SortingHelper.ARRAYS_SORT).testAll();
            set(SortingHelper.ARRAYS_PARALLEL_SORT).testAll();

            out.println();
        }

        private void testEmpty() {
            sortingHelper.sort(new float[test.length], 0, 0);
        }

        private void testStructured() {
            testEmpty();

            if (test.length < 512) {
                return;
            }
            for (int m = 1; m < 9; ++m) {
                for (Builder builder : StructuredBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("structured", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    checkWithCheckSum(0);
                }
            }
        }

        private void testBase() {
            if (test.length > 1_000) {
                return;
            }
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testCore() {
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
            testNegativeZeroAndNaN();
        }

        private void testAll() {
            testCore();
            testRange();
        }

        private void testRange() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("range", m, builder);
                    testRange(m);
                }
            }
        }

        private void testRange(int m) {
            try {
                sortingHelper.sort(test, m + 1, m);
                fail(sortingHelper + " must throw IllegalArgumentException: " +
                        "fromIndex = " + (m + 1) + ", toIndex = " + m);
            } catch (IllegalArgumentException iae) {
                try {
                    sortingHelper.sort(test, -m, test.length);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "fromIndex = " + (-m));
                } catch (ArrayIndexOutOfBoundsException aoe) {
                    try {
                        sortingHelper.sort(test, 0, test.length + m);
                        fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                                "toIndex = " + (test.length + m));
                    } catch (ArrayIndexOutOfBoundsException expected) {}
                }
            }
        }

        private void testWithInsertionSort() {
            if (test.length > 1_000) {
                return;
            }
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with insertion sort", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    sortByInsertionSort(gold, offset, test.length - offset);
                    compare(test, gold);
                }
            }
        }

        private void testWithCheckSum() {
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with check sum", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    checkWithCheckSum(offset);
                }
            }
        }

        private void testWithScrambling() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : SortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("with scrambling", m, builder);
                    scramble();
                    sortingHelper.sort(test, 0, test.length);
                    compare(test, gold);
                }
            }
        }

        private void testNegativeZeroAndNaN() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : FloatingPointBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    scramble();
                    print("negative zero and NaN", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    check(test, m);
                }
            }
        }

        private void scramble() {
            if (withMin) {
                for (int i = 7; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length - 1) + 1, random.nextInt(test.length - 1) + 1);
                }
            } else {
                for (int i = 0; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length), random.nextInt(test.length));
                }
            }
        }

        private void checkWithCheckSum(int m) {
            checkSorted(test, m);
            checkCheckSum(test, gold);
        }

        private void checkSorted(float[] a, int m) {
            for (int i = 0; i < m; ++i) {
                if (a[i] != A380) {
                    fail("Sort changes left element at position " + i + ": " + a[i] + ", must be A380");
                }
            }
            for (int i = m; i < a.length - m - 1; ++i) {
                if (a[i] > a[i + 1]) {
                    fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
                }
            }
            for (int i = a.length - m; i < a.length; ++i) {
                if (a[i] != B747) {
                    fail("Sort changes right element at position " + i + ": " + a[i] + ", must be B747");
                }
            }
        }

        private void checkCheckSum(float[] a, float[] b) {
            if (checkSumXor(a) != checkSumXor(b)) {
                fail("Original and sorted arrays are not identical [^]");
            }
            if (checkSumPlus(a) != checkSumPlus(b)) {
                fail("Original and sorted arrays are not identical [+]");
            }
        }

        private long checkSumXor(float[] a) {
            long checkSum = 0;

            for (float e : a) {
                checkSum ^= (long) e;
            }
            return checkSum;
        }

        private long checkSumPlus(float[] a) {
            long checkSum = 0;

            for (float e : a) {
                checkSum += (long) e;
            }
            return checkSum;
        }

        private void compare(float[] a, float[] b) {
            for (int i = 0; i < a.length; ++i) {
                if (a[i] != b[i]) {
                    fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
                }
            }
        }

        private void sortByInsertionSort(float[] a, int low, int high) {
            SortingHelper.INSERTION_SORT.sort(a, low, high);
        }

        private void setup(int m) {
            for (int i = 0; i < m; ++i) {
                gold[i] = A380;
            }
            for (int i = gold.length - m; i < gold.length; ++i) {
                gold[i] = B747;
            }
            if (withMin) {
                gold[m] = Float.NEGATIVE_INFINITY;
            }
            test = gold.clone();
        }

        private void print(String name, int m, Builder builder) {
            out(name, "Float", test.length, sortingHelper, m, builder);
        }

        private static void swap(float[] a, int i, int j) {
            float t = a[i]; a[i] = a[j]; a[j] = t;
        }

        private static void reverse(float[] a, int lo, int hi) {
            for (--hi; lo < hi; swap(a, lo++, hi--));
        }

        private void check(float[] a, int m) {
            final int NEGATIVE_ZERO = Float.floatToIntBits(-0.0f);

            int k1 = a.length / (m + 1) * m     / 5;
            int k2 = a.length / (m + 1) * m * 2 / 5;
            int k3 = a.length / (m + 1) * m * 3 / 5;
            int k4 = a.length / (m + 1) * m * 4 / 5;

            for (int i = 0; i < k1; ++i) {
                float v = (float) (-(a.length + m) + i);

                if (a[i] != v) {
                    fail("There must be " + v + " instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k1; i < k2; ++i) {
                if (Float.floatToIntBits(a[i]) != NEGATIVE_ZERO) {
                    fail("There must be -0.0 instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k2; i < k3; ++i) {
                if (a[i] != 0.0f || Float.floatToIntBits(a[i]) == NEGATIVE_ZERO) {
                    fail("There must be 0.0 instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k3; i < k4; ++i) {
                float v = (float) (m + i);

                if (a[i] != v) {
                    fail("There must be " + v + " instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k4; i < a.length; ++i) {
                if (!Float.isNaN(a[i])) {
                    fail("There must be NaN instead of " + a[i] + " at position " + i);
                }
            }
        }

        private interface Builder {
            void build(float[] a, int m);
        }

        private enum SortedBuilder implements Builder {
            ANGLE {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (Math.min(i + m, 127));
                    }
                }
            },

            STEPS {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < m; ++i) {
                        a[i] = 0;
                    }
                    for (int i = m; i < a.length; ++i) {
                        a[i] = 1;
                    }
                }
            }
        }

        private enum UnsortedBuilder implements Builder {
            RANDOM {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) random.nextInt();
                    }
                }
            },

            PERMUTATION {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (m + i);
                    }
                    for (int i = a.length; i > 1; --i) {
                        swap(a, i - 1, random.nextInt(i));
                    }
                }
            },

            UNIFORM {
                @Override
                public void build(float[] a, int m) {
                    int mask = (m << 15) - 1;

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (random.nextInt() & mask);
                    }
                }
            },

            REPEATED {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (i % m);
                    }
                }
            },

            DUPLICATED {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) random.nextInt(m);
                    }
                }
            },

            SAWTOOTH {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0, minus = a.length, plus = 0; i < a.length; ) {
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (float) (++plus);
                        }
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (float) (--minus);
                        }
                    }
                }
            },

            SHUFFLE {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (float) (random.nextInt(m) > 0 ? (j += 2) : (k += 2));
                    }
                }
            }
        }

        private enum StructuredBuilder implements Builder {
            ASCENDING {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (m + i);
                    }
                }
            },

            DESCENDING {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (a.length - m - i);
                    }
                }
            },

            EQUAL {
                @Override
                public void build(float[] a, int m) {
                    Arrays.fill(a, (float) m);
                }
            },

            SHIFTED {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (i << 10);
                    }
                }
            },

            ORGAN_PIPES {
                @Override
                public void build(float[] a, int m) {
                    int middle = a.length / (m + 1);

                    for (int i = 0; i < middle; ++i) {
                        a[i] = (float) i;
                    }
                    for (int i = middle; i < a.length; ++i) {
                        a[i] = (float) (a.length - i - 1);
                    }
                }
            },

            PLATEAU {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) Math.min(i, m);
                    }
                }
            },

            LATCH {
                @Override
                public void build(float[] a, int m) {
                    int max = Math.max(a.length / m, 2);

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) (i % max);
                    }
                }
            },

            POINT {
                @Override
                public void build(float[] a, int m) {
                    Arrays.fill(a, (float) 0);
                    a[a.length / 2] = (float) m;
                }
            },

            LINE {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) i;
                    }
                    reverse(a, Math.max(0, a.length - m), a.length);
                }
            },

            PEARL {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) i;
                    }
                    reverse(a, 0, Math.min(m, a.length));
                }
            },

            TRAPEZIUM {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) i;
                    }
                    reverse(a, m, a.length - m);
                }
            },

            STAGGER {
                @Override
                public void build(float[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (float) ((i * m + i) % a.length);
                    }
                }
            }
        }

        private enum FloatingPointBuilder implements Builder {
            NEGATIVE_ZERO_AND_NAN {
                @Override
                public void build(float[] a, int m) {
                    int k1 = a.length / (m + 1) * m     / 5;
                    int k2 = a.length / (m + 1) * m * 2 / 5;
                    int k3 = a.length / (m + 1) * m * 3 / 5;
                    int k4 = a.length / (m + 1) * m * 4 / 5;

                    for (int i = 0; i < k1; ++i) {
                        a[i] = (float) (-(a.length + m) + i);
                    }
                    for (int i = k1; i < k2; ++i) {
                        a[i] = -0.0f;
                    }
                    for (int i = k2; i < k3; ++i) {
                        a[i] = 0.0f;
                    }
                    for (int i = k3; i < k4; ++i) {
                        a[i] = (float) (m + i);
                    }
                    for (int i = k4; i < a.length; ++i) {
                        a[i] = Float.NaN;
                    }
                }
            }
        }
    }

    private static class DoubleHolder {
        // Constant to fill the left part of array
        private static final double A380 = (double) 0xA380;

        // Constant to fill the right part of array
        private static final double B747 = (double) 0xB747;

        private SortingHelper sortingHelper;
        private boolean withMin;
        private double[] gold;
        private double[] test;

        private DoubleHolder set(SortingHelper sortingHelper) {
            return set(sortingHelper, false);
        }

        private DoubleHolder set(SortingHelper sortingHelper, boolean withMin) {
            this.sortingHelper = sortingHelper;
            this.withMin = withMin;
            return this;
        }

        private void test(int length) {
            gold = new double[length];
            test = new double[length];

            set(SortingHelper.MERGING_SORT).testStructured();
            set(SortingHelper.MIXED_INSERTION_SORT, true).testBase();
            set(SortingHelper.INSERTION_SORT).testBase();
            set(SortingHelper.HEAP_SORT).testBase();
            set(SortingHelper.DUAL_PIVOT_QUICKSORT).testCore();
            set(SortingHelper.PARALLEL_QUICKSORT).testCore();
            set(SortingHelper.ARRAYS_SORT).testAll();
            set(SortingHelper.ARRAYS_PARALLEL_SORT).testAll();

            out.println();
        }

        private void testEmpty() {
            sortingHelper.sort(new double[test.length], 0, 0);
        }

        private void testStructured() {
            testEmpty();

            if (test.length < 512) {
                return;
            }
            for (int m = 1; m < 9; ++m) {
                for (Builder builder : StructuredBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("structured", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    checkWithCheckSum(0);
                }
            }
        }

        private void testBase() {
            if (test.length > 1_000) {
                return;
            }
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
        }

        private void testCore() {
            testStructured();
            testWithCheckSum();
            testWithInsertionSort();
            testWithScrambling();
            testNegativeZeroAndNaN();
        }

        private void testAll() {
            testCore();
            testRange();
        }

        private void testRange() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("range", m, builder);
                    testRange(m);
                }
            }
        }

        private void testRange(int m) {
            try {
                sortingHelper.sort(test, m + 1, m);
                fail(sortingHelper + " must throw IllegalArgumentException: " +
                        "fromIndex = " + (m + 1) + ", toIndex = " + m);
            } catch (IllegalArgumentException iae) {
                try {
                    sortingHelper.sort(test, -m, test.length);
                    fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                            "fromIndex = " + (-m));
                } catch (ArrayIndexOutOfBoundsException aoe) {
                    try {
                        sortingHelper.sort(test, 0, test.length + m);
                        fail(sortingHelper + " must throw ArrayIndexOutOfBoundsException: " +
                                "toIndex = " + (test.length + m));
                    } catch (ArrayIndexOutOfBoundsException expected) {}
                }
            }
        }

        private void testWithInsertionSort() {
            if (test.length > 1_000) {
                return;
            }
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with insertion sort", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    sortByInsertionSort(gold, offset, test.length - offset);
                    compare(test, gold);
                }
            }
        }

        private void testWithCheckSum() {
            for (int m = 1; m <= test.length; m <<= 1) {
                int offset = m / 4;

                for (Builder builder : UnsortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(offset);
                    print("with check sum", m, builder);
                    sortingHelper.sort(test, offset, test.length - offset);
                    checkWithCheckSum(offset);
                }
            }
        }

        private void testWithScrambling() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : SortedBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    print("with scrambling", m, builder);
                    scramble();
                    sortingHelper.sort(test, 0, test.length);
                    compare(test, gold);
                }
            }
        }

        private void testNegativeZeroAndNaN() {
            for (int m = 1; m <= test.length; m <<= 1) {
                for (Builder builder : FloatingPointBuilder.values()) {
                    builder.build(gold, m);
                    setup(0);
                    scramble();
                    print("negative zero and NaN", m, builder);
                    sortingHelper.sort(test, 0, test.length);
                    check(test, m);
                }
            }
        }

        private void scramble() {
            if (withMin) {
                for (int i = 7; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length - 1) + 1, random.nextInt(test.length - 1) + 1);
                }
            } else {
                for (int i = 0; i < test.length * 7; ++i) {
                    swap(test, random.nextInt(test.length), random.nextInt(test.length));
                }
            }
        }

        private void checkWithCheckSum(int m) {
            checkSorted(test, m);
            checkCheckSum(test, gold);
        }

        private void checkSorted(double[] a, int m) {
            for (int i = 0; i < m; ++i) {
                if (a[i] != A380) {
                    fail("Sort changes left element at position " + i + ": " + a[i] + ", must be A380");
                }
            }
            for (int i = m; i < a.length - m - 1; ++i) {
                if (a[i] > a[i + 1]) {
                    fail("Array is not sorted at " + i + "-th position: " + a[i] + " and " + a[i + 1]);
                }
            }
            for (int i = a.length - m; i < a.length; ++i) {
                if (a[i] != B747) {
                    fail("Sort changes right element at position " + i + ": " + a[i] + ", must be B747");
                }
            }
        }

        private void checkCheckSum(double[] a, double[] b) {
            if (checkSumXor(a) != checkSumXor(b)) {
                fail("Original and sorted arrays are not identical [^]");
            }
            if (checkSumPlus(a) != checkSumPlus(b)) {
                fail("Original and sorted arrays are not identical [+]");
            }
        }

        private long checkSumXor(double[] a) {
            long checkSum = 0;

            for (double e : a) {
                checkSum ^= (long) e;
            }
            return checkSum;
        }

        private long checkSumPlus(double[] a) {
            long checkSum = 0;

            for (double e : a) {
                checkSum += (long) e;
            }
            return checkSum;
        }

        private void compare(double[] a, double[] b) {
            for (int i = 0; i < a.length; ++i) {
                if (a[i] != b[i]) {
                    fail("There must be " + b[i] + " instead of " + a[i] + " at position " + i);
                }
            }
        }

        private void sortByInsertionSort(double[] a, int low, int high) {
            SortingHelper.INSERTION_SORT.sort(a, low, high);
        }

        private void setup(int m) {
            for (int i = 0; i < m; ++i) {
                gold[i] = A380;
            }
            for (int i = gold.length - m; i < gold.length; ++i) {
                gold[i] = B747;
            }
            if (withMin) {
                gold[m] = Double.NEGATIVE_INFINITY;
            }
            test = gold.clone();
        }

        private void print(String name, int m, Builder builder) {
            out(name, "Double", test.length, sortingHelper, m, builder);
        }

        private static void swap(double[] a, int i, int j) {
            double t = a[i]; a[i] = a[j]; a[j] = t;
        }

        private static void reverse(double[] a, int lo, int hi) {
            for (--hi; lo < hi; swap(a, lo++, hi--));
        }

        private void check(double[] a, int m) {
            final long NEGATIVE_ZERO = Double.doubleToLongBits(-0.0d);

            int k1 = a.length / (m + 1) * m     / 5;
            int k2 = a.length / (m + 1) * m * 2 / 5;
            int k3 = a.length / (m + 1) * m * 3 / 5;
            int k4 = a.length / (m + 1) * m * 4 / 5;

            for (int i = 0; i < k1; ++i) {
                double v = (double) (-(a.length + m) + i);

                if (a[i] != v) {
                    fail("There must be " + v + " instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k1; i < k2; ++i) {
                if (Double.doubleToLongBits(a[i]) != NEGATIVE_ZERO) {
                    fail("There must be -0.0 instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k2; i < k3; ++i) {
                if (a[i] != 0.0d || Double.doubleToLongBits(a[i]) == NEGATIVE_ZERO) {
                    fail("There must be 0.0 instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k3; i < k4; ++i) {
                double v = (double) (m + i);

                if (a[i] != v) {
                    fail("There must be " + v + " instead of " + a[i] + " at position " + i);
                }
            }
            for (int i = k4; i < a.length; ++i) {
                if (!Double.isNaN(a[i])) {
                    fail("There must be NaN instead of " + a[i] + " at position " + i);
                }
            }
        }

        private interface Builder {
            void build(double[] a, int m);
        }

        private enum SortedBuilder implements Builder {
            ANGLE {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (Math.min(i + m, 127));
                    }
                }
            },

            STEPS {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < m; ++i) {
                        a[i] = 0;
                    }
                    for (int i = m; i < a.length; ++i) {
                        a[i] = 1;
                    }
                }
            }
        }

        private enum UnsortedBuilder implements Builder {
            RANDOM {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) random.nextInt();
                    }
                }
            },

            PERMUTATION {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (m + i);
                    }
                    for (int i = a.length; i > 1; --i) {
                        swap(a, i - 1, random.nextInt(i));
                    }
                }
            },

            UNIFORM {
                @Override
                public void build(double[] a, int m) {
                    int mask = (m << 15) - 1;

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (random.nextInt() & mask);
                    }
                }
            },

            REPEATED {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (i % m);
                    }
                }
            },

            DUPLICATED {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) random.nextInt(m);
                    }
                }
            },

            SAWTOOTH {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0, minus = a.length, plus = 0; i < a.length; ) {
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (double) (++plus);
                        }
                        for (int k = 0; ++k <= m && i < a.length; ++i) {
                            a[i] = (double) (--minus);
                        }
                    }
                }
            },

            SHUFFLE {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (double) (random.nextInt(m) > 0 ? (j += 2) : (k += 2));
                    }
                }
            }
        }

        private enum StructuredBuilder implements Builder {
            ASCENDING {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (m + i);
                    }
                }
            },

            DESCENDING {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (a.length - m - i);
                    }
                }
            },

            EQUAL {
                @Override
                public void build(double[] a, int m) {
                    Arrays.fill(a, (double) m);
                }
            },

            SHIFTED {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (i << 10);
                    }
                }
            },

            ORGAN_PIPES {
                @Override
                public void build(double[] a, int m) {
                    int middle = a.length / (m + 1);

                    for (int i = 0; i < middle; ++i) {
                        a[i] = (double) i;
                    }
                    for (int i = middle; i < a.length; ++i) {
                        a[i] = (double) (a.length - i - 1);
                    }
                }
            },

            PLATEAU {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) Math.min(i, m);
                    }
                }
            },

            LATCH {
                @Override
                public void build(double[] a, int m) {
                    int max = Math.max(a.length / m, 2);

                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) (i % max);
                    }
                }
            },

            POINT {
                @Override
                public void build(double[] a, int m) {
                    Arrays.fill(a, (double) 0);
                    a[a.length / 2] = (double) m;
                }
            },

            LINE {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) i;
                    }
                    reverse(a, Math.max(0, a.length - m), a.length);
                }
            },

            PEARL {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) i;
                    }
                    reverse(a, 0, Math.min(m, a.length));
                }
            },

            TRAPEZIUM {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) i;
                    }
                    reverse(a, m, a.length - m);
                }
            },

            STAGGER {
                @Override
                public void build(double[] a, int m) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (double) ((i * m + i) % a.length);
                    }
                }
            }
        }

        private enum FloatingPointBuilder implements Builder {
            NEGATIVE_ZERO_AND_NAN {
                @Override
                public void build(double[] a, int m) {
                    int k1 = a.length / (m + 1) * m     / 5;
                    int k2 = a.length / (m + 1) * m * 2 / 5;
                    int k3 = a.length / (m + 1) * m * 3 / 5;
                    int k4 = a.length / (m + 1) * m * 4 / 5;

                    for (int i = 0; i < k1; ++i) {
                        a[i] = (double) (-(a.length + m) + i);
                    }
                    for (int i = k1; i < k2; ++i) {
                        a[i] = -0.0d;
                    }
                    for (int i = k2; i < k3; ++i) {
                        a[i] = 0.0d;
                    }
                    for (int i = k3; i < k4; ++i) {
                        a[i] = (double) (m + i);
                    }
                    for (int i = k4; i < a.length; ++i) {
                        a[i] = Double.NaN;
                    }
                }
            }
        }
    }

    private static void out(String name, String type, int length, SortingHelper sortingHelper, int m, Object builder) {
        out.println("[ " + type + " | Length = " + length + " | " + sortingHelper + " ] 'Test " + name + "', m = " + m + ", " + builder);
    }

    private static void fail(String message) {
        err.format("*** TEST FAILED ***\n\n%s\n\n", message);
        throw new RuntimeException("Test failed");
    }
}
