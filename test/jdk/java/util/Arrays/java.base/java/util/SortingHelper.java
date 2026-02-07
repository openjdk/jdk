/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

/**
 * This class provides access to package-private methods of DualPivotQuicksort class.
 *
 * @author Vladimir Yaroslavskiy
 *
 * @version 2024.06.14
 *
 * @since 14 * 20 ^ 26
 */
public enum SortingHelper {

    INSERTION_SORT("Insertion sort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> DualPivotQuicksort.insertionSort(ai, low, high);
                case long[] al -> DualPivotQuicksort.insertionSort(al, low, high);
                case byte[] ab -> DualPivotQuicksort.insertionSort(ab, low, high);
                case char[] ac -> DualPivotQuicksort.insertionSort(ac, low, high);
                case short[] as -> DualPivotQuicksort.insertionSort(as, low, high);
                case float[] af -> DualPivotQuicksort.insertionSort(af, low, high);
                case double[] ad -> DualPivotQuicksort.insertionSort(ad, low, high);
                default -> fail(a);
            }
        }
    },

    MIXED_INSERTION_SORT("Mixed insertion sort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> DualPivotQuicksort.mixedInsertionSort(ai, low, high);
                case long[] al -> DualPivotQuicksort.mixedInsertionSort(al, low, high);
                case float[] af -> DualPivotQuicksort.mixedInsertionSort(af, low, high);
                case double[] ad -> DualPivotQuicksort.mixedInsertionSort(ad, low, high);
                default -> fail(a);
            }
        }
    },

    MERGING_SORT("Merging sort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> checkMerging(DualPivotQuicksort.tryMergingSort(null, ai, low, high - low));
                case long[] al -> checkMerging(DualPivotQuicksort.tryMergingSort(null, al, low, high - low));
                case float[] af -> checkMerging(DualPivotQuicksort.tryMergingSort(null, af, low, high - low));
                case double[] ad -> checkMerging(DualPivotQuicksort.tryMergingSort(null, ad, low, high - low));
                default -> fail(a);
            }
        }
    },

    COUNTING_SORT("Counting sort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case byte[] ab -> DualPivotQuicksort.countingSort(ab, low, high);
                case char[] ac -> DualPivotQuicksort.countingSort(ac, low, high);
                case short[] as -> DualPivotQuicksort.countingSort(as, low, high);
                default -> fail(a);
            }
        }
    },

    HEAP_SORT("Heap sort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> DualPivotQuicksort.heapSort(ai, low, high);
                case long[] al -> DualPivotQuicksort.heapSort(al, low, high);
                case float[] af -> DualPivotQuicksort.heapSort(af, low, high);
                case double[] ad -> DualPivotQuicksort.heapSort(ad, low, high);
                default -> fail(a);
            }
        }
    },

    DUAL_PIVOT_QUICKSORT("Dual-Pivot Quicksort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> DualPivotQuicksort.sort(ai, 0, low, high);
                case long[] al -> DualPivotQuicksort.sort(al, 0, low, high);
                case byte[] ab -> DualPivotQuicksort.sort(ab, low, high);
                case char[] ac -> DualPivotQuicksort.sort(ac, low, high);
                case short[] as -> DualPivotQuicksort.sort(as, low, high);
                case float[] af -> DualPivotQuicksort.sort(af, 0, low, high);
                case double[] ad -> DualPivotQuicksort.sort(ad, 0, low, high);
                default -> fail(a);
            }
        }
    },

    PARALLEL_QUICKSORT("Parallel Quicksort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> DualPivotQuicksort.sort(ai, 4, low, high);
                case long[] al -> DualPivotQuicksort.sort(al, 4, low, high);
                case float[] af -> DualPivotQuicksort.sort(af, 4, low, high);
                case double[] ad -> DualPivotQuicksort.sort(ad, 4, low, high);
                default -> fail(a);
            }
        }
    },

    ARRAYS_SORT("Arrays.sort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> Arrays.sort(ai, low, high);
                case long[] al -> Arrays.sort(al, low, high);
                case byte[] ab -> Arrays.sort(ab, low, high);
                case char[] ac -> Arrays.sort(ac, low, high);
                case short[] as -> Arrays.sort(as, low, high);
                case float[] af -> Arrays.sort(af, low, high);
                case double[] ad -> Arrays.sort(ad, low, high);
                default -> fail(a);
            }
        }
    },

    ARRAYS_PARALLEL_SORT("Arrays.parallelSort") {
        @Override
        public void sort(Object a, int low, int high) {
            switch(a) {
                case int[] ai -> Arrays.parallelSort(ai, low, high);
                case long[] al -> Arrays.parallelSort(al, low, high);
                case byte[] ab -> Arrays.parallelSort(ab, low, high);
                case char[] ac -> Arrays.parallelSort(ac, low, high);
                case short[] as -> Arrays.parallelSort(as, low, high);
                case float[] af -> Arrays.parallelSort(af, low, high);
                case double[] ad -> Arrays.parallelSort(ad, low, high);
                default -> fail(a);
            }
        }
    };

    SortingHelper(String name) {
        this.name = name;
    }

    public abstract void sort(Object a, int low, int high);

    @Override
    public String toString() {
        return name;
    }

    private static void checkMerging(boolean result) {
        if (!result) {
            fail("Merging sort must return true");
        }
    }

    private static void fail(Object a) {
        fail("Unknown array: " + a.getClass().getName());
    }

    private static void fail(String message) {
        throw new RuntimeException(message);
    }

    private final String name;
}
