/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.util;

import java.util.concurrent.CountedCompleter;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * This class implements powerful and fully optimized versions, both
 * sequential and parallel, of the Dual-Pivot Quicksort algorithm by
 * Vladimir Yaroslavskiy, Jon Bentley and Josh Bloch. This algorithm
 * offers O(n log(n)) performance on all data sets, and is typically
 * faster than traditional (one-pivot) Quicksort implementations.<p>
 *
 * There are also additional algorithms, invoked from the Dual-Pivot
 * Quicksort such as merging sort, sorting network, heap sort, mixed
 * insertion sort, counting sort and parallel merge sort. The actual
 * sorting algorithm depends on the data type and array size.<p>
 *
 * <b>Type: int/long/float/double</b><p>
 *
 * If the array size is small, invoke mixed insertion sort on non-leftmost
 * parts or insertion sort on leftmost part.<p>
 *
 * Then try merging sort which is the best on almost sorted arrays.<p>
 *
 * On the next step check the recursion depth to avoid quadratic time
 * with heap sort.<p>
 *
 * Then apply Quicksort with two pivots on random data, otherwise
 * run one-pivot Quicksort.<p>
 *
 * <b>Type: float/double</b><p>
 *
 * Floating-point values require additional steps to process
 * negative zeros -0.0 and NaNs (Not-a-Number) before sorting and
 * re-arrange negative zeros at the end.<p>
 *
 * <b>Type: byte</b><p>
 *
 * Invoke insertion sort, if the array size is small, otherwise switch
 * to counting sort.<p>
 *
 * <b>Type: char/short</b><p>
 *
 * Invoke counting sort on large array, otherwise run insertion sort
 * on small array.<p>
 *
 * On the next step check the recursion depth to avoid quadratic time
 * with counting sort.<p>
 *
 * Then apply Quicksort with two pivots on random data, otherwise
 * run one-pivot Quicksort.<p>
 *
 * <b>Parallel sorting (int/long/float/double)</b><p>
 *
 * If the array size is small, sequential sort is run. Otherwise
 * invoke parallel merge sort (the recursion depth depends on
 * parallelism level), then run parallel Quicksort.
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 * @author Doug Lea
 *
 * @version 2024.06.14
 *
 * @since 1.7 * 14 ^ 26
 */
final class DualPivotQuicksort {

    /**
     * Prevents instantiation.
     */
    private DualPivotQuicksort() {}

    /* --------------------- Insertion sort --------------------- */

    /**
     * Max size of array to use insertion sort (the best on shuffle data).
     */
    private static final int MAX_INSERTION_SORT_SIZE = 37;

    /* ---------------------- Merging sort ---------------------- */

    /**
     * Min size of array to use merging sort (the best on stagger data).
     */
    private static final int MIN_MERGING_SORT_SIZE = 512;

    /**
     * Min size of run to continue scanning (the best on stagger data).
     */
    private static final int MIN_RUN_SIZE = 64;

    /**
     * Max capacity of the index array to track the runs.
     */
    private static final int MAX_RUN_CAPACITY = 10 << 10;

    /* ---------------------- Digital sort ---------------------- */

    /**
     * Min size of array to use counting sort (the best on random data).
     */
    private static final int MIN_COUNTING_SORT_SIZE = 640;

    /**
     * Min size of array to use numerical sort (the best on repeated data).
     */
    private static final int MIN_NUMERICAL_SORT_SIZE = 9 << 10;

    /* --------------------- Parallel sort ---------------------- */

    /**
     * Min size of array to perform sorting in parallel (the best on stagger data).
     */
    private static final int MIN_PARALLEL_SORT_SIZE = 3 << 10;

    /* --------------------- Infrastructure --------------------- */

    /**
     * Max recursive depth before switching to heap sort.
     */
    private static final int MAX_RECURSION_DEPTH = 64 << 1;

    /**
     * Max size of additional buffer in bytes,
     *      limited by max_heap / 16 or 2 GB max.
     */
    private static final int MAX_BUFFER_SIZE =
        Math.clamp(Runtime.getRuntime().maxMemory() >>> 4, 0, Integer.MAX_VALUE);

    /**
     * Represents a function that accepts the array and sorts
     * the specified range of the array into ascending order.
     *
     * @param <T> the class of array
     */
    @FunctionalInterface
    private interface SortOperation<T> {
        /**
         * Sorts the specified range of the array.
         *
         * @param a the array to be sorted
         * @param low the index of the first element, inclusive, to be sorted
         * @param high the index of the last element, exclusive, to be sorted
         */
        void sort(T a, int low, int high);
    }

    /**
     * Sorts the specified range of the array into ascending numerical order.
     * The signature of this method is in sync with native implementation
     * based on AVX512 instructions from linux/native/libsimdsort package,
     * don't change the signature.
     *
     * @param <T> the class of array
     * @param elemType the class of the elements of the array to be sorted
     * @param a the array to be sorted
     * @param offset the relative offset, in bytes, from the base
     *        address of the array to partition, otherwise if the
     *        array is {@code null}, an absolute address pointing
     *        to the first element to partition from
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @param so the method reference for the fallback implementation
     */
    @ForceInline
    @IntrinsicCandidate
    private static <T> void sort(Class<?> elemType, T a, long offset,
            int low, int high, SortOperation<T> so) {
        so.sort(a, low, high);
    }

    /**
     * Represents a function that accepts the array and partitions
     * the specified range of the array using the given pivots.
     *
     * @param <T> the class of array
     */
    @FunctionalInterface
    private interface PartitionOperation<T> {
        /**
         * Partitions the specified range of the array using the given pivots.
         *
         * @param a the array for partitioning
         * @param low the index of the first element, inclusive, for partitioning
         * @param high the index of the last element, exclusive, for partitioning
         * @param pivotIndex1 the index of pivot1, the first pivot
         * @param pivotIndex2 the index of pivot2, the second pivot
         * @return indices of parts after partitioning
         */
        int[] partition(T a, int low, int high, int pivotIndex1, int pivotIndex2);
    }

    /**
     * Partitions the specified range of the array using the given pivots.
     * The signature of this method is in sync with native implementation
     * based on AVX512 instructions from linux/native/libsimdsort package,
     * don't change the signature.
     *
     * @param <T> the class of array
     * @param elemType the class of the array for partitioning
     * @param a the array for partitioning
     * @param offset the relative offset, in bytes, from the base
     *        address of the array to partition, otherwise if the
     *        array is {@code null}, an absolute address pointing
     *        to the first element to partition from
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of pivot1, the first pivot
     * @param pivotIndex2 the index of pivot2, the second pivot
     * @param po the method reference for the fallback implementation
     * @return indices of parts after partitioning
     */
    @ForceInline
    @IntrinsicCandidate
    private static <T> int[] partition(Class<?> elemType, T a, long offset,
            int low, int high, int pivotIndex1, int pivotIndex2, PartitionOperation<T> po) {
        return po.partition(a, low, high, pivotIndex1, pivotIndex2);
    }

// #[int]

    /**
     * Sorts the specified range of the array using parallel merge
     * sort and/or Dual-Pivot Quicksort.<p>
     *
     * To balance the faster splitting and parallelism of merge sort
     * with the faster element partitioning of Quicksort, ranges are
     * subdivided in tiers such that, if there is enough parallelism,
     * the four-way parallel merge is started, still ensuring enough
     * parallelism to process the partitions.
     *
     * @param a the array to be sorted
     * @param parallelism the parallelism level
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(int[] a, int parallelism, int low, int high) {
        if (parallelism > 1 && high - low > MIN_PARALLEL_SORT_SIZE) {
            new Sorter<>(a, parallelism, low, high - low).invoke();
        } else {
            sort(null, a, 0, low, high);
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that range is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(Sorter<int[]> sorter, int[] a, int bits, int low, int high) {
        while (true) {
            int size = high - low;

            /*
             * Run adaptive mixed insertion sort on small non-leftmost parts.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                sort(int.class, a, Unsafe.ARRAY_INT_BASE_OFFSET,
                    low, high, DualPivotQuicksort::mixedInsertionSort);
                return;
            }

            /*
             * Invoke adaptive insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits * 5) {
                sort(int.class, a, Unsafe.ARRAY_INT_BASE_OFFSET,
                    low, high, DualPivotQuicksort::insertionSort);
                return;
            }

            /*
             * Try merging sort on large part.
             */
            if (size > MIN_MERGING_SORT_SIZE * bits
                    && tryMergingSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Divide the given array into the golden ratio using
             * an inexpensive approximation to select five sample
             * elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 7);

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = high - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;

            /*
             * Sort these elements in-place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *   1 ---------o---------------o-----------------
             *              |               |
             *   2 ---------|-------o-------o-------o---------
             *              |       |               |
             *   3 ---------|-------|---------------|---------
             *              |       |               |
             *   4 ---------o-------|-------o-------o---------
             *                      |       |
             *   5 -----------------o-------o-----------------
             */
            if (a[e1] > a[e4]) { int t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e2] > a[e5]) { int t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e4] > a[e5]) { int t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e2]) { int t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e2] > a[e4]) { int t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a[e3] < a[e2]) {
                if (a[e3] < a[e1]) {
                    int t = a[e3]; a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = t;
                } else {
                    int t = a[e3]; a[e3] = a[e2]; a[e2] = t;
                }
            } else if (a[e3] > a[e4]) {
                if (a[e3] > a[e5]) {
                    int t = a[e3]; a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = t;
                } else {
                    int t = a[e3]; a[e3] = a[e4]; a[e4] = t;
                }
            }

            /*
             * Switch to heap sort to avoid quadratic time.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            /*
             * indices[0] - the index of the last element of the left part
             * indices[1] - the index of the first element of the right part
             */
            int[] indices;

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                indices = partition(int.class, a, Unsafe.ARRAY_INT_BASE_OFFSET,
                    low, high, e1, e5, DualPivotQuicksort::partitionWithTwoPivots);

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[0] + 1, indices[1]);
                    sorter.fork(bits | 1, indices[1] + 1, high);
                } else {
                    sort(sorter, a, bits | 1, indices[0] + 1, indices[1]);
                    sort(sorter, a, bits | 1, indices[1] + 1, high);
                }

            } else { // Partitioning with one pivot

                indices = partition(int.class, a, Unsafe.ARRAY_INT_BASE_OFFSET,
                    low, high, e3, e3, DualPivotQuicksort::partitionWithOnePivot);

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[1], high);
                } else {
                    sort(sorter, a, bits | 1, indices[1], high);
                }
            }
            high = indices[0]; // Iterate along the left part
        }
    }

    /**
     * Partitions the specified range of the array using two given pivots.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of pivot1, the first pivot
     * @param pivotIndex2 the index of pivot2, the second pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithTwoPivots(
            int[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = --high;
        int lower = low;

        /*
         * Use the first and fifth of the five sorted elements as
         * the pivots. These values are inexpensive approximation
         * of tertiles. Note, that pivot1 < pivot2.
         */
        int pivot1 = a[pivotIndex1];
        int pivot2 = a[pivotIndex2];

        /*
         * The first and the last elements to be sorted are moved
         * to the locations formerly occupied by the pivots. When
         * partitioning is completed, the pivots are swapped back
         * into their final positions, and excluded from the next
         * subsequent sorting.
         */
        a[pivotIndex1] = a[lower];
        a[pivotIndex2] = a[upper];

        /*
         * Skip elements, which are less or greater than the pivots.
         */
        while (a[++lower] < pivot1);
        while (a[--upper] > pivot2);

        /*
         * Backward 3-interval partitioning
         *
         *     left part                     central part          right part
         * +--------------+----------+--------------------------+--------------+
         * |   < pivot1   |    ?     |  pivot1 <= .. <= pivot2  |   > pivot2   |
         * +--------------+----------+--------------------------+--------------+
         *               ^          ^                            ^
         *               |          |                            |
         *             lower        k                          upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int unused = --lower, k = ++upper; --k > lower; ) {
            int ak = a[k];

            if (ak < pivot1) { // Move a[k] to the left part
                while (a[++lower] < pivot1);

                if (lower > k) {
                    lower = k;
                    break;
                }
                if (a[lower] > pivot2) {
                    a[k] = a[--upper];
                    a[upper] = a[lower];
                } else {
                    a[k] = a[lower];
                }
                a[lower] = ak;
            } else if (ak > pivot2) { // Move a[k] to the right part
                a[k] = a[--upper];
                a[upper] = ak;
            }
        }

        /*
         * Swap the pivots into their final positions.
         */
        a[low]  = a[lower]; a[lower] = pivot1;
        a[high] = a[upper]; a[upper] = pivot2;

        return new int[] { lower, upper };
    }

    /**
     * Partitions the specified range of the array using one given pivot.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of single pivot
     * @param pivotIndex2 the index of single pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithOnePivot(
            int[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = high;
        int lower = low;

        /*
         * Use the third of the five sorted elements as the pivot.
         * This value is inexpensive approximation of the median.
         */
        int pivot = a[pivotIndex1];

        /*
         * The first element to be sorted is moved to the
         * location formerly occupied by the pivot. After
         * completion of partitioning the pivot is swapped
         * back into its final position, and excluded from
         * the next subsequent sorting.
         */
        a[pivotIndex1] = a[lower];

        /*
         * Dutch National Flag partitioning
         *
         *     left part               central part    right part
         * +--------------+----------+--------------+-------------+
         * |   < pivot    |    ?     |   == pivot   |   > pivot   |
         * +--------------+----------+--------------+-------------+
         *               ^          ^                ^
         *               |          |                |
         *             lower        k              upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int k = upper; --k > lower; ) {
            int ak = a[k];

            if (ak == pivot) {
                continue;
            }
            a[k] = pivot;

            if (ak < pivot) { // Move a[k] to the left part
                while (a[++lower] < pivot);

                if (a[lower] > pivot) {
                    a[--upper] = a[lower];
                }
                a[lower] = ak;
            } else { // ak > pivot - Move a[k] to the right part
                a[--upper] = ak;
            }
        }

        /*
         * Swap the pivot into its final position.
         */
        a[low] = a[lower]; a[lower] = pivot;

        return new int[] { lower, upper };
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.<p>
     *
     * Mixed insertion sort is combination of pin insertion sort
     * and pair insertion sort.<p>
     *
     * In the context of Dual-Pivot Quicksort, the pivot element
     * from the left part plays the role of sentinel, because it
     * is less than any elements from the given part. Therefore,
     * expensive check of the left range can be skipped on each
     * iteration unless it is the leftmost call.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void mixedInsertionSort(int[] a, int low, int high) {
        /*
         * Split the array for pin and pair insertion sorts.
         */
        int end = high - ((3 * ((high - low) >> 2)) & ~1);

        /*
         * Start with pin insertion sort.
         */
        for (int i, p = high; ++low < end; ) {
            int ai = a[i = low], pin = a[--p];

            /*
             * Swap larger element with pin.
             */
            if (ai > pin) {
                ai = pin;
                a[p] = a[i];
            }

            /*
             * Insert element into sorted part.
             */
            while (ai < a[i - 1]) {
                a[i] = a[--i];
            }
            a[i] = ai;
        }

        /*
         * Finish with pair insertion sort.
         */
        for (int i; low < high; ++low) {
            int a1 = a[i = low], a2 = a[++low];

            /*
             * Insert two elements per iteration: at first, insert the
             * larger element and then insert the smaller element, but
             * from the position where the larger element was inserted.
             */
            if (a1 > a2) {

                while (a1 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a1;

                while (a2 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a2;

            } else if (a1 < a[i - 1]) {

                while (a2 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a2;

                while (a1 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a1;
            }
        }
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(int[] a, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            int ai = a[i = k];

            if (ai < a[i - 1]) {
                do {
                    a[i] = a[--i];
                } while (i > low && ai < a[i - 1]);

                a[i] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryMergingSort(Sorter<int[]> sorter, int[] a, int low, int high) {
        /*
         * The element run[i] holds the start index
         * of i-th sequence in non-descending order.
         */
        int count = 1;
        int[] run = null;

        /*
         * Identify all possible runs.
         */
        for (int k = low + 1, last = low; k < high; ) {
            /*
             * Find the next run.
             */
            if (a[k - 1] < a[k]) {

                // Identify ascending sequence
                while (++k < high && a[k - 1] <= a[k]);

            } else if (a[k - 1] > a[k]) {

                // Identify descending sequence
                while (++k < high && a[k - 1] >= a[k]);

                // Reverse into ascending order
                for (int i = last - 1, j = k; ++i < --j && a[i] > a[j]; ) {
                    int ai = a[i]; a[i] = a[j]; a[j] = ai;
                }

                // Check the next sequence
                if (k < high && a[k - 1] < a[k]) {
                    continue;
                }

            } else { // Identify constant sequence
                for (int ak = a[k]; ++k < high && ak == a[k]; );

                // Check the next sequence
                if (k < high) {
                    continue;
                }
            }

            /*
             * Process the current run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }
                run = new int[Math.min((high - low) >> 6, MAX_RUN_CAPACITY) | 8];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

                if (k - low < count * MIN_RUN_SIZE) {
                    /*
                     * Terminate the scanning,
                     * if the runs are too small.
                     */
                    return false;
                }

                if (++count == run.length) {
                    /*
                     * Array is not highly structured.
                     */
                    return false;
                }
            }

            /*
             * Save the current run.
             */
            run[count] = (last = k);

            /*
             * Check single-element run at the end.
             */
            if (++k == high) {
                --k;
            }
        }

        /*
         * Merge all runs.
         */
        if (count > 1) {
            int[] b; int offset = low;

            if (sorter != null && (b = sorter.b) != null) {
                offset = sorter.offset;
            } else if ((b = tryAllocate(int[].class, high - low)) == null) {
                return false;
            }
            mergeRuns(sorter, a, b, offset, true, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param sorter the parallel context
     * @param a the source array
     * @param b the buffer for merging
     * @param offset the start index in the source, inclusive
     * @param aim whether the original array is used for merging
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     */
    private static void mergeRuns(Sorter<int[]> sorter, int[] a, int[] b, int offset,
            boolean aim, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (!aim) {
                System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            }
            return;
        }

        /*
         * Split the array into two approximately equal parts.
         */
        int mi = lo, key = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= key);

        /*
         * Merge the runs of all parts.
         */
        mergeRuns(sorter, a, b, offset, !aim, run, lo, mi);
        mergeRuns(sorter, a, b, offset, !aim, run, mi, hi);

        int[] dst = aim ? a : b;
        int[] src = aim ? b : a;

        int k  = !aim ? run[lo] - offset : run[lo];
        int lo1 = aim ? run[lo] - offset : run[lo];
        int hi1 = aim ? run[mi] - offset : run[mi];
        int lo2 = aim ? run[mi] - offset : run[mi];
        int hi2 = aim ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && sorter != null) {
            new Merger<>(null, dst, k, src, lo1, hi1, lo2, hi2).invoke();
        } else {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        }
    }

    /**
     * Merges the sorted parts in parallel.
     *
     * @param merger the parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<int[]> merger, int[] dst, int k,
            int[] src, int lo1, int hi1, int lo2, int hi2) {

        while (true) {
            /*
             * The first part must be larger.
             */
            if (hi1 - lo1 < hi2 - lo2) {
                int lo = lo1; lo1 = lo2; lo2 = lo;
                int hi = hi1; hi1 = hi2; hi2 = hi;
            }

            /*
             * Merge the small parts sequentially.
             */
            if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                break;
            }

            /*
             * Find the median of the larger part.
             */
            int mi1 = (lo1 + hi1) >>> 1;
            int mi2 = hi2;
            int key = src[mi1];

            /*
             * Split the smaller part.
             */
            for (int mi0 = lo2; mi0 < mi2; ) {
                int mid = (mi0 + mi2) >>> 1;

                if (key > src[mid]) {
                    mi0 = mid + 1;
                } else {
                    mi2 = mid;
                }
            }

            /*
             * Merge the first parts in parallel.
             */
            merger.fork(k, lo1, mi1, lo2, mi2);

            /*
             * Reserve space for the second parts.
             */
            k += mi2 - lo2 + mi1 - lo1;

            /*
             * Iterate along the second parts.
             */
            lo1 = mi1;
            lo2 = mi2;
        }

        /*
         * Check if the array is already ordered and then merge the parts.
         */
        if (lo1 < hi1 && lo2 < hi2 && src[hi1 - 1] > src[lo2]) {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        } else {
            System.arraycopy(src, lo1, dst, k, hi1 - lo1);
            System.arraycopy(src, lo2, dst, k + hi1 - lo1, hi2 - lo2);
        }
    }

    /**
     * Merges the sorted parts sequentially.
     *
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(int[] dst, int k,
            int[] src, int lo1, int hi1, int lo2, int hi2) {

        if (src[hi1 - 1] < src[hi2 - 1]) {
            while (lo1 < hi1) {
                int next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else if (src[hi1 - 1] > src[hi2 - 1]) {
            while (lo2 < hi2) {
                int next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else {
            while (lo1 < hi1 && lo2 < hi2) {
                int next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        }

        /*
         * Copy the tail of the left and right parts.
         */
        System.arraycopy(src, lo1, dst, k, hi1 - lo1);
        System.arraycopy(src, lo2, dst, k, hi2 - lo2);
    }

    /**
     * Sorts the specified range of the array using heap sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void heapSort(int[] a, int low, int high) {
        for (int k = (low + high) >>> 1; k > low; ) {
            pushDown(a, --k, a[k], low, high);
        }
        while (--high > low) {
            int max = a[low];
            pushDown(a, low, a[high], low, high);
            a[high] = max;
        }
    }

    /**
     * Pushes specified element down during heap sort.
     *
     * @param a the given array
     * @param p the start index
     * @param value the given element
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void pushDown(int[] a, int p, int value, int low, int high) {
        for (int k ;; a[p] = a[p = k]) {
            k = (p << 1) - low + 2; // Index of the right child

            if (k > high) {
                break;
            }
            if (k == high || a[k] < a[k - 1]) {
                --k;
            }
            if (a[k] <= value) {
                break;
            }
        }
        a[p] = value;
    }

// #[long]

    /**
     * Sorts the specified range of the array using parallel merge
     * sort and/or Dual-Pivot Quicksort.<p>
     *
     * To balance the faster splitting and parallelism of merge sort
     * with the faster element partitioning of Quicksort, ranges are
     * subdivided in tiers such that, if there is enough parallelism,
     * the four-way parallel merge is started, still ensuring enough
     * parallelism to process the partitions.
     *
     * @param a the array to be sorted
     * @param parallelism the parallelism level
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(long[] a, int parallelism, int low, int high) {
        if (parallelism > 1 && high - low > MIN_PARALLEL_SORT_SIZE) {
            new Sorter<>(a, parallelism, low, high - low).invoke();
        } else {
            sort(null, a, 0, low, high);
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that range is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(Sorter<long[]> sorter, long[] a, int bits, int low, int high) {
        while (true) {
            int size = high - low;

            /*
             * Run adaptive mixed insertion sort on small non-leftmost parts.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                sort(long.class, a, Unsafe.ARRAY_LONG_BASE_OFFSET,
                    low, high, DualPivotQuicksort::mixedInsertionSort);
                return;
            }

            /*
             * Invoke adaptive insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits * 5) {
                sort(long.class, a, Unsafe.ARRAY_LONG_BASE_OFFSET,
                    low, high, DualPivotQuicksort::insertionSort);
                return;
            }

            /*
             * Try merging sort on large part.
             */
            if (size > MIN_MERGING_SORT_SIZE * bits
                    && tryMergingSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Divide the given array into the golden ratio using
             * an inexpensive approximation to select five sample
             * elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 7);

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = high - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;

            /*
             * Sort these elements in-place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *   1 ---------o---------------o-----------------
             *              |               |
             *   2 ---------|-------o-------o-------o---------
             *              |       |               |
             *   3 ---------|-------|---------------|---------
             *              |       |               |
             *   4 ---------o-------|-------o-------o---------
             *                      |       |
             *   5 -----------------o-------o-----------------
             */
            if (a[e1] > a[e4]) { long t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e2] > a[e5]) { long t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e4] > a[e5]) { long t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e2]) { long t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e2] > a[e4]) { long t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a[e3] < a[e2]) {
                if (a[e3] < a[e1]) {
                    long t = a[e3]; a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = t;
                } else {
                    long t = a[e3]; a[e3] = a[e2]; a[e2] = t;
                }
            } else if (a[e3] > a[e4]) {
                if (a[e3] > a[e5]) {
                    long t = a[e3]; a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = t;
                } else {
                    long t = a[e3]; a[e3] = a[e4]; a[e4] = t;
                }
            }

            /*
             * Switch to heap sort to avoid quadratic time.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            /*
             * indices[0] - the index of the last element of the left part
             * indices[1] - the index of the first element of the right part
             */
            int[] indices;

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                indices = partition(long.class, a, Unsafe.ARRAY_LONG_BASE_OFFSET,
                    low, high, e1, e5, DualPivotQuicksort::partitionWithTwoPivots);

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[0] + 1, indices[1]);
                    sorter.fork(bits | 1, indices[1] + 1, high);
                } else {
                    sort(sorter, a, bits | 1, indices[0] + 1, indices[1]);
                    sort(sorter, a, bits | 1, indices[1] + 1, high);
                }

            } else { // Partitioning with one pivot

                indices = partition(long.class, a, Unsafe.ARRAY_LONG_BASE_OFFSET,
                    low, high, e3, e3, DualPivotQuicksort::partitionWithOnePivot);

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[1], high);
                } else {
                    sort(sorter, a, bits | 1, indices[1], high);
                }
            }
            high = indices[0]; // Iterate along the left part
        }
    }

    /**
     * Partitions the specified range of the array using two given pivots.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of pivot1, the first pivot
     * @param pivotIndex2 the index of pivot2, the second pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithTwoPivots(
            long[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = --high;
        int lower = low;

        /*
         * Use the first and fifth of the five sorted elements as
         * the pivots. These values are inexpensive approximation
         * of tertiles. Note, that pivot1 < pivot2.
         */
        long pivot1 = a[pivotIndex1];
        long pivot2 = a[pivotIndex2];

        /*
         * The first and the last elements to be sorted are moved
         * to the locations formerly occupied by the pivots. When
         * partitioning is completed, the pivots are swapped back
         * into their final positions, and excluded from the next
         * subsequent sorting.
         */
        a[pivotIndex1] = a[lower];
        a[pivotIndex2] = a[upper];

        /*
         * Skip elements, which are less or greater than the pivots.
         */
        while (a[++lower] < pivot1);
        while (a[--upper] > pivot2);

        /*
         * Backward 3-interval partitioning
         *
         *     left part                     central part          right part
         * +--------------+----------+--------------------------+--------------+
         * |   < pivot1   |    ?     |  pivot1 <= .. <= pivot2  |   > pivot2   |
         * +--------------+----------+--------------------------+--------------+
         *               ^          ^                            ^
         *               |          |                            |
         *             lower        k                          upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int unused = --lower, k = ++upper; --k > lower; ) {
            long ak = a[k];

            if (ak < pivot1) { // Move a[k] to the left part
                while (a[++lower] < pivot1);

                if (lower > k) {
                    lower = k;
                    break;
                }
                if (a[lower] > pivot2) {
                    a[k] = a[--upper];
                    a[upper] = a[lower];
                } else {
                    a[k] = a[lower];
                }
                a[lower] = ak;
            } else if (ak > pivot2) { // Move a[k] to the right part
                a[k] = a[--upper];
                a[upper] = ak;
            }
        }

        /*
         * Swap the pivots into their final positions.
         */
        a[low]  = a[lower]; a[lower] = pivot1;
        a[high] = a[upper]; a[upper] = pivot2;

        return new int[] { lower, upper };
    }

    /**
     * Partitions the specified range of the array using one given pivot.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of single pivot
     * @param pivotIndex2 the index of single pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithOnePivot(
            long[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = high;
        int lower = low;

        /*
         * Use the third of the five sorted elements as the pivot.
         * This value is inexpensive approximation of the median.
         */
        long pivot = a[pivotIndex1];

        /*
         * The first element to be sorted is moved to the
         * location formerly occupied by the pivot. After
         * completion of partitioning the pivot is swapped
         * back into its final position, and excluded from
         * the next subsequent sorting.
         */
        a[pivotIndex1] = a[lower];

        /*
         * Dutch National Flag partitioning
         *
         *     left part               central part    right part
         * +--------------+----------+--------------+-------------+
         * |   < pivot    |    ?     |   == pivot   |   > pivot   |
         * +--------------+----------+--------------+-------------+
         *               ^          ^                ^
         *               |          |                |
         *             lower        k              upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int k = upper; --k > lower; ) {
            long ak = a[k];

            if (ak == pivot) {
                continue;
            }
            a[k] = pivot;

            if (ak < pivot) { // Move a[k] to the left part
                while (a[++lower] < pivot);

                if (a[lower] > pivot) {
                    a[--upper] = a[lower];
                }
                a[lower] = ak;
            } else { // ak > pivot - Move a[k] to the right part
                a[--upper] = ak;
            }
        }

        /*
         * Swap the pivot into its final position.
         */
        a[low] = a[lower]; a[lower] = pivot;

        return new int[] { lower, upper };
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.<p>
     *
     * Mixed insertion sort is combination of pin insertion sort
     * and pair insertion sort.<p>
     *
     * In the context of Dual-Pivot Quicksort, the pivot element
     * from the left part plays the role of sentinel, because it
     * is less than any elements from the given part. Therefore,
     * expensive check of the left range can be skipped on each
     * iteration unless it is the leftmost call.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void mixedInsertionSort(long[] a, int low, int high) {
        /*
         * Split the array for pin and pair insertion sorts.
         */
        int end = high - ((3 * ((high - low) >> 2)) & ~1);

        /*
         * Start with pin insertion sort.
         */
        for (int i, p = high; ++low < end; ) {
            long ai = a[i = low], pin = a[--p];

            /*
             * Swap larger element with pin.
             */
            if (ai > pin) {
                ai = pin;
                a[p] = a[i];
            }

            /*
             * Insert element into sorted part.
             */
            while (ai < a[i - 1]) {
                a[i] = a[--i];
            }
            a[i] = ai;
        }

        /*
         * Finish with pair insertion sort.
         */
        for (int i; low < high; ++low) {
            long a1 = a[i = low], a2 = a[++low];

            /*
             * Insert two elements per iteration: at first, insert the
             * larger element and then insert the smaller element, but
             * from the position where the larger element was inserted.
             */
            if (a1 > a2) {

                while (a1 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a1;

                while (a2 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a2;

            } else if (a1 < a[i - 1]) {

                while (a2 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a2;

                while (a1 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a1;
            }
        }
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(long[] a, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            long ai = a[i = k];

            if (ai < a[i - 1]) {
                do {
                    a[i] = a[--i];
                } while (i > low && ai < a[i - 1]);

                a[i] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryMergingSort(Sorter<long[]> sorter, long[] a, int low, int high) {
        /*
         * The element run[i] holds the start index
         * of i-th sequence in non-descending order.
         */
        int count = 1;
        int[] run = null;

        /*
         * Identify all possible runs.
         */
        for (int k = low + 1, last = low; k < high; ) {
            /*
             * Find the next run.
             */
            if (a[k - 1] < a[k]) {

                // Identify ascending sequence
                while (++k < high && a[k - 1] <= a[k]);

            } else if (a[k - 1] > a[k]) {

                // Identify descending sequence
                while (++k < high && a[k - 1] >= a[k]);

                // Reverse into ascending order
                for (int i = last - 1, j = k; ++i < --j && a[i] > a[j]; ) {
                    long ai = a[i]; a[i] = a[j]; a[j] = ai;
                }

                // Check the next sequence
                if (k < high && a[k - 1] < a[k]) {
                    continue;
                }

            } else { // Identify constant sequence
                for (long ak = a[k]; ++k < high && ak == a[k]; );

                // Check the next sequence
                if (k < high) {
                    continue;
                }
            }

            /*
             * Process the current run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }
                run = new int[Math.min((high - low) >> 6, MAX_RUN_CAPACITY) | 8];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

                if (k - low < count * MIN_RUN_SIZE) {
                    /*
                     * Terminate the scanning,
                     * if the runs are too small.
                     */
                    return false;
                }

                if (++count == run.length) {
                    /*
                     * Array is not highly structured.
                     */
                    return false;
                }
            }

            /*
             * Save the current run.
             */
            run[count] = (last = k);

            /*
             * Check single-element run at the end.
             */
            if (++k == high) {
                --k;
            }
        }

        /*
         * Merge all runs.
         */
        if (count > 1) {
            long[] b; int offset = low;

            if (sorter != null && (b = sorter.b) != null) {
                offset = sorter.offset;
            } else if ((b = tryAllocate(long[].class, high - low)) == null) {
                return false;
            }
            mergeRuns(sorter, a, b, offset, true, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param sorter the parallel context
     * @param a the source array
     * @param b the buffer for merging
     * @param offset the start index in the source, inclusive
     * @param aim whether the original array is used for merging
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     */
    private static void mergeRuns(Sorter<long[]> sorter, long[] a, long[] b, int offset,
            boolean aim, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (!aim) {
                System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            }
            return;
        }

        /*
         * Split the array into two approximately equal parts.
         */
        int mi = lo, key = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= key);

        /*
         * Merge the runs of all parts.
         */
        mergeRuns(sorter, a, b, offset, !aim, run, lo, mi);
        mergeRuns(sorter, a, b, offset, !aim, run, mi, hi);

        long[] dst = aim ? a : b;
        long[] src = aim ? b : a;

        int k  = !aim ? run[lo] - offset : run[lo];
        int lo1 = aim ? run[lo] - offset : run[lo];
        int hi1 = aim ? run[mi] - offset : run[mi];
        int lo2 = aim ? run[mi] - offset : run[mi];
        int hi2 = aim ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && sorter != null) {
            new Merger<>(null, dst, k, src, lo1, hi1, lo2, hi2).invoke();
        } else {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        }
    }

    /**
     * Merges the sorted parts in parallel.
     *
     * @param merger the parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<long[]> merger, long[] dst, int k,
            long[] src, int lo1, int hi1, int lo2, int hi2) {

        while (true) {
            /*
             * The first part must be larger.
             */
            if (hi1 - lo1 < hi2 - lo2) {
                int lo = lo1; lo1 = lo2; lo2 = lo;
                int hi = hi1; hi1 = hi2; hi2 = hi;
            }

            /*
             * Merge the small parts sequentially.
             */
            if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                break;
            }

            /*
             * Find the median of the larger part.
             */
            int mi1 = (lo1 + hi1) >>> 1;
            int mi2 = hi2;
            long key = src[mi1];

            /*
             * Split the smaller part.
             */
            for (int mi0 = lo2; mi0 < mi2; ) {
                int mid = (mi0 + mi2) >>> 1;

                if (key > src[mid]) {
                    mi0 = mid + 1;
                } else {
                    mi2 = mid;
                }
            }

            /*
             * Merge the first parts in parallel.
             */
            merger.fork(k, lo1, mi1, lo2, mi2);

            /*
             * Reserve space for the second parts.
             */
            k += mi2 - lo2 + mi1 - lo1;

            /*
             * Iterate along the second parts.
             */
            lo1 = mi1;
            lo2 = mi2;
        }

        /*
         * Check if the array is already ordered and then merge the parts.
         */
        if (lo1 < hi1 && lo2 < hi2 && src[hi1 - 1] > src[lo2]) {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        } else {
            System.arraycopy(src, lo1, dst, k, hi1 - lo1);
            System.arraycopy(src, lo2, dst, k + hi1 - lo1, hi2 - lo2);
        }
    }

    /**
     * Merges the sorted parts sequentially.
     *
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(long[] dst, int k,
            long[] src, int lo1, int hi1, int lo2, int hi2) {

        if (src[hi1 - 1] < src[hi2 - 1]) {
            while (lo1 < hi1) {
                long next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else if (src[hi1 - 1] > src[hi2 - 1]) {
            while (lo2 < hi2) {
                long next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else {
            while (lo1 < hi1 && lo2 < hi2) {
                long next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        }

        /*
         * Copy the tail of the left and right parts.
         */
        System.arraycopy(src, lo1, dst, k, hi1 - lo1);
        System.arraycopy(src, lo2, dst, k, hi2 - lo2);
    }

    /**
     * Sorts the specified range of the array using heap sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void heapSort(long[] a, int low, int high) {
        for (int k = (low + high) >>> 1; k > low; ) {
            pushDown(a, --k, a[k], low, high);
        }
        while (--high > low) {
            long max = a[low];
            pushDown(a, low, a[high], low, high);
            a[high] = max;
        }
    }

    /**
     * Pushes specified element down during heap sort.
     *
     * @param a the given array
     * @param p the start index
     * @param value the given element
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void pushDown(long[] a, int p, long value, int low, int high) {
        for (int k ;; a[p] = a[p = k]) {
            k = (p << 1) - low + 2; // Index of the right child

            if (k > high) {
                break;
            }
            if (k == high || a[k] < a[k - 1]) {
                --k;
            }
            if (a[k] <= value) {
                break;
            }
        }
        a[p] = value;
    }

// #[byte]

    /**
     * Sorts the specified range of the array using insertion sort or counting sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(byte[] a, int low, int high) {
        if (high - low < MAX_INSERTION_SORT_SIZE) {
            insertionSort(a, low, high);
        } else {
            countingSort(a, low, high);
        }
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(byte[] a, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            byte ai = a[i = k];

            if (ai < a[i - 1]) {
                do {
                    a[i] = a[--i];
                } while (i > low && ai < a[i - 1]);

                a[i] = ai;
            }
        }
    }

    /**
     * Sorts the specified range of the array using counting sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void countingSort(byte[] a, int low, int high) {
        /*
         * Count the number of all values.
         */
        int[] count = new int[1 << 8];

        /*
         * Compute the histogram.
         */
        for (int i = high; i > low; ++count[a[--i] & 0xFF]);

        /*
         * Place values on their final positions.
         */
        for (int value = Byte.MIN_VALUE; high > low; ) {
            while (count[--value & 0xFF] == 0);
            int num = count[value & 0xFF];

            do {
                a[--high] = (byte) value;
            } while (--num > 0);
        }
    }

// #[char]

    /**
     * Sorts the specified range of the array using counting sort
     * Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(char[] a, int low, int high) {
        if (high - low > MIN_COUNTING_SORT_SIZE) {
            countingSort(a, low, high);
        } else {
            sort(a, 0, low, high);
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that range is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void sort(char[] a, int bits, int low, int high) {
        while (true) {
            int size = high - low;

            /*
             * Invoke insertion sort on small part.
             */
            if (size < MAX_INSERTION_SORT_SIZE) {
                insertionSort(a, low, high);
                return;
            }

            /*
             * Divide the given array into the golden ratio using
             * an inexpensive approximation to select five sample
             * elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 7);

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = high - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;

            /*
             * Sort these elements in-place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *   1 ---------o---------------o-----------------
             *              |               |
             *   2 ---------|-------o-------o-------o---------
             *              |       |               |
             *   3 ---------|-------|---------------|---------
             *              |       |               |
             *   4 ---------o-------|-------o-------o---------
             *                      |       |
             *   5 -----------------o-------o-----------------
             */
            if (a[e1] > a[e4]) { char t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e2] > a[e5]) { char t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e4] > a[e5]) { char t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e2]) { char t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e2] > a[e4]) { char t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a[e3] < a[e2]) {
                if (a[e3] < a[e1]) {
                    char t = a[e3]; a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = t;
                } else {
                    char t = a[e3]; a[e3] = a[e2]; a[e2] = t;
                }
            } else if (a[e3] > a[e4]) {
                if (a[e3] > a[e5]) {
                    char t = a[e3]; a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = t;
                } else {
                    char t = a[e3]; a[e3] = a[e4]; a[e4] = t;
                }
            }

            /*
             * Switch to counting sort to avoid quadratic time.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                countingSort(a, low, high);
                return;
            }

            /*
             * indices[0] - the index of the last element of the left part
             * indices[1] - the index of the first element of the right part
             */
            int[] indices;

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                indices = partitionWithTwoPivots(a, low, high, e1, e5);

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                sort(a, bits | 1, indices[0] + 1, indices[1]);
                sort(a, bits | 1, indices[1] + 1, high);

            } else { // Partitioning with one pivot

                indices = partitionWithOnePivot(a, low, high, e3, e3);

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                sort(a, bits | 1, indices[1], high);
            }
            high = indices[0]; // Iterate along the left part
        }
    }

    /**
     * Partitions the specified range of the array using two given pivots.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of pivot1, the first pivot
     * @param pivotIndex2 the index of pivot2, the second pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithTwoPivots(
            char[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = --high;
        int lower = low;

        /*
         * Use the first and fifth of the five sorted elements as
         * the pivots. These values are inexpensive approximation
         * of tertiles. Note, that pivot1 < pivot2.
         */
        char pivot1 = a[pivotIndex1];
        char pivot2 = a[pivotIndex2];

        /*
         * The first and the last elements to be sorted are moved
         * to the locations formerly occupied by the pivots. When
         * partitioning is completed, the pivots are swapped back
         * into their final positions, and excluded from the next
         * subsequent sorting.
         */
        a[pivotIndex1] = a[lower];
        a[pivotIndex2] = a[upper];

        /*
         * Skip elements, which are less or greater than the pivots.
         */
        while (a[++lower] < pivot1);
        while (a[--upper] > pivot2);

        /*
         * Backward 3-interval partitioning
         *
         *     left part                     central part          right part
         * +--------------+----------+--------------------------+--------------+
         * |   < pivot1   |    ?     |  pivot1 <= .. <= pivot2  |   > pivot2   |
         * +--------------+----------+--------------------------+--------------+
         *               ^          ^                            ^
         *               |          |                            |
         *             lower        k                          upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int unused = --lower, k = ++upper; --k > lower; ) {
            char ak = a[k];

            if (ak < pivot1) { // Move a[k] to the left part
                while (a[++lower] < pivot1);

                if (lower > k) {
                    lower = k;
                    break;
                }
                if (a[lower] > pivot2) {
                    a[k] = a[--upper];
                    a[upper] = a[lower];
                } else {
                    a[k] = a[lower];
                }
                a[lower] = ak;
            } else if (ak > pivot2) { // Move a[k] to the right part
                a[k] = a[--upper];
                a[upper] = ak;
            }
        }

        /*
         * Swap the pivots into their final positions.
         */
        a[low]  = a[lower]; a[lower] = pivot1;
        a[high] = a[upper]; a[upper] = pivot2;

        return new int[] { lower, upper };
    }

    /**
     * Partitions the specified range of the array using one given pivot.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of single pivot
     * @param pivotIndex2 the index of single pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithOnePivot(
            char[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = high;
        int lower = low;

        /*
         * Use the third of the five sorted elements as the pivot.
         * This value is inexpensive approximation of the median.
         */
        char pivot = a[pivotIndex1];

        /*
         * The first element to be sorted is moved to the
         * location formerly occupied by the pivot. After
         * completion of partitioning the pivot is swapped
         * back into its final position, and excluded from
         * the next subsequent sorting.
         */
        a[pivotIndex1] = a[lower];

        /*
         * Dutch National Flag partitioning
         *
         *     left part               central part    right part
         * +--------------+----------+--------------+-------------+
         * |   < pivot    |    ?     |   == pivot   |   > pivot   |
         * +--------------+----------+--------------+-------------+
         *               ^          ^                ^
         *               |          |                |
         *             lower        k              upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int k = upper; --k > lower; ) {
            char ak = a[k];

            if (ak == pivot) {
                continue;
            }
            a[k] = pivot;

            if (ak < pivot) { // Move a[k] to the left part
                while (a[++lower] < pivot);

                if (a[lower] > pivot) {
                    a[--upper] = a[lower];
                }
                a[lower] = ak;
            } else { // ak > pivot - Move a[k] to the right part
                a[--upper] = ak;
            }
        }

        /*
         * Swap the pivot into its final position.
         */
        a[low] = a[lower]; a[lower] = pivot;

        return new int[] { lower, upper };
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(char[] a, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            char ai = a[i = k];

            if (ai < a[i - 1]) {
                do {
                    a[i] = a[--i];
                } while (i > low && ai < a[i - 1]);

                a[i] = ai;
            }
        }
    }

    /**
     * Sorts the specified range of the array using counting sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void countingSort(char[] a, int low, int high) {
        int size = high - low;

        if (size > MIN_NUMERICAL_SORT_SIZE) {
            /*
             * Count the number of all values.
             */
            int[] count = new int[1 << 16];

            /*
             * Compute the histogram.
             */
            for (int i = high; i > low; ++count[a[--i]]);

            /*
             * Place values on their final positions.
             */
            for (int value = count.length; high > low; ) {
                while (count[--value] == 0);
                int num = count[value];

                do {
                    a[--high] = (char) value;
                } while (--num > 0);
            }

        } else {

            /*
             * Allocate additional buffer.
             */
            char[] b = new char[size];

            /*
             * Count the number of all digits.
             */
            int[] count1 = new int[1 << 8];
            int[] count2 = new int[1 << 8];

            for (int i = low; i < high; ++i) {
                ++count1[  a[i]        & 0xFF];
                ++count2[((a[i] >>> 8) & 0xFF)];
            }

            /*
             * Check digits to be processed.
             */
            boolean processDigit1 = processDigit(count1, size, low);
            boolean processDigit2 = processDigit(count2, size, low);

            /*
             * Process the 1-st digit.
             */
            if (processDigit1) {
                for (int i = high; i > low; ) {
                    b[--count1[a[--i] & 0xFF] - low] = a[i];
                }
            }

            /*
             * Process the 2-nd digit.
             */
            if (processDigit2) {
                if (processDigit1) {
                    for (int i = size; i > 0; ) {
                        a[--count2[((b[--i] >>> 8) & 0xFF)]] = b[i];
                    }
                } else {
                    for (int i = high; i > low; ) {
                        b[--count2[((a[--i] >>> 8) & 0xFF)] - low] = a[i];
                    }
                }
            }

            /*
             * Copy the buffer to original array, if we process ood number of digits.
             */
            if (processDigit1 ^ processDigit2) {
                System.arraycopy(b, 0, a, low, size);
            }
        }
    }

// #[short]

    /**
     * Sorts the specified range of the array using counting sort
     * Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(short[] a, int low, int high) {
        if (high - low > MIN_COUNTING_SORT_SIZE) {
            countingSort(a, low, high);
        } else {
            sort(a, 0, low, high);
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that range is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void sort(short[] a, int bits, int low, int high) {
        while (true) {
            int size = high - low;

            /*
             * Invoke insertion sort on small part.
             */
            if (size < MAX_INSERTION_SORT_SIZE) {
                insertionSort(a, low, high);
                return;
            }

            /*
             * Divide the given array into the golden ratio using
             * an inexpensive approximation to select five sample
             * elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 7);

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = high - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;

            /*
             * Sort these elements in-place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *   1 ---------o---------------o-----------------
             *              |               |
             *   2 ---------|-------o-------o-------o---------
             *              |       |               |
             *   3 ---------|-------|---------------|---------
             *              |       |               |
             *   4 ---------o-------|-------o-------o---------
             *                      |       |
             *   5 -----------------o-------o-----------------
             */
            if (a[e1] > a[e4]) { short t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e2] > a[e5]) { short t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e4] > a[e5]) { short t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e2]) { short t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e2] > a[e4]) { short t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a[e3] < a[e2]) {
                if (a[e3] < a[e1]) {
                    short t = a[e3]; a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = t;
                } else {
                    short t = a[e3]; a[e3] = a[e2]; a[e2] = t;
                }
            } else if (a[e3] > a[e4]) {
                if (a[e3] > a[e5]) {
                    short t = a[e3]; a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = t;
                } else {
                    short t = a[e3]; a[e3] = a[e4]; a[e4] = t;
                }
            }

            /*
             * Switch to counting sort to avoid quadratic time.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                countingSort(a, low, high);
                return;
            }

            /*
             * indices[0] - the index of the last element of the left part
             * indices[1] - the index of the first element of the right part
             */
            int[] indices;

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                indices = partitionWithTwoPivots(a, low, high, e1, e5);

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                sort(a, bits | 1, indices[0] + 1, indices[1]);
                sort(a, bits | 1, indices[1] + 1, high);

            } else { // Partitioning with one pivot

                indices = partitionWithOnePivot(a, low, high, e3, e3);

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                sort(a, bits | 1, indices[1], high);
            }
            high = indices[0]; // Iterate along the left part
        }
    }

    /**
     * Partitions the specified range of the array using two given pivots.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of pivot1, the first pivot
     * @param pivotIndex2 the index of pivot2, the second pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithTwoPivots(
            short[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = --high;
        int lower = low;

        /*
         * Use the first and fifth of the five sorted elements as
         * the pivots. These values are inexpensive approximation
         * of tertiles. Note, that pivot1 < pivot2.
         */
        short pivot1 = a[pivotIndex1];
        short pivot2 = a[pivotIndex2];

        /*
         * The first and the last elements to be sorted are moved
         * to the locations formerly occupied by the pivots. When
         * partitioning is completed, the pivots are swapped back
         * into their final positions, and excluded from the next
         * subsequent sorting.
         */
        a[pivotIndex1] = a[lower];
        a[pivotIndex2] = a[upper];

        /*
         * Skip elements, which are less or greater than the pivots.
         */
        while (a[++lower] < pivot1);
        while (a[--upper] > pivot2);

        /*
         * Backward 3-interval partitioning
         *
         *     left part                     central part          right part
         * +--------------+----------+--------------------------+--------------+
         * |   < pivot1   |    ?     |  pivot1 <= .. <= pivot2  |   > pivot2   |
         * +--------------+----------+--------------------------+--------------+
         *               ^          ^                            ^
         *               |          |                            |
         *             lower        k                          upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int unused = --lower, k = ++upper; --k > lower; ) {
            short ak = a[k];

            if (ak < pivot1) { // Move a[k] to the left part
                while (a[++lower] < pivot1);

                if (lower > k) {
                    lower = k;
                    break;
                }
                if (a[lower] > pivot2) {
                    a[k] = a[--upper];
                    a[upper] = a[lower];
                } else {
                    a[k] = a[lower];
                }
                a[lower] = ak;
            } else if (ak > pivot2) { // Move a[k] to the right part
                a[k] = a[--upper];
                a[upper] = ak;
            }
        }

        /*
         * Swap the pivots into their final positions.
         */
        a[low]  = a[lower]; a[lower] = pivot1;
        a[high] = a[upper]; a[upper] = pivot2;

        return new int[] { lower, upper };
    }

    /**
     * Partitions the specified range of the array using one given pivot.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of single pivot
     * @param pivotIndex2 the index of single pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithOnePivot(
            short[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = high;
        int lower = low;

        /*
         * Use the third of the five sorted elements as the pivot.
         * This value is inexpensive approximation of the median.
         */
        short pivot = a[pivotIndex1];

        /*
         * The first element to be sorted is moved to the
         * location formerly occupied by the pivot. After
         * completion of partitioning the pivot is swapped
         * back into its final position, and excluded from
         * the next subsequent sorting.
         */
        a[pivotIndex1] = a[lower];

        /*
         * Dutch National Flag partitioning
         *
         *     left part               central part    right part
         * +--------------+----------+--------------+-------------+
         * |   < pivot    |    ?     |   == pivot   |   > pivot   |
         * +--------------+----------+--------------+-------------+
         *               ^          ^                ^
         *               |          |                |
         *             lower        k              upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int k = upper; --k > lower; ) {
            short ak = a[k];

            if (ak == pivot) {
                continue;
            }
            a[k] = pivot;

            if (ak < pivot) { // Move a[k] to the left part
                while (a[++lower] < pivot);

                if (a[lower] > pivot) {
                    a[--upper] = a[lower];
                }
                a[lower] = ak;
            } else { // ak > pivot - Move a[k] to the right part
                a[--upper] = ak;
            }
        }

        /*
         * Swap the pivot into its final position.
         */
        a[low] = a[lower]; a[lower] = pivot;

        return new int[] { lower, upper };
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(short[] a, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            short ai = a[i = k];

            if (ai < a[i - 1]) {
                do {
                    a[i] = a[--i];
                } while (i > low && ai < a[i - 1]);

                a[i] = ai;
            }
        }
    }

    /**
     * Sorts the specified range of the array using counting sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void countingSort(short[] a, int low, int high) {
        int size = high - low;

        if (size > MIN_NUMERICAL_SORT_SIZE) {
            /*
             * Count the number of all values.
             */
            int[] count = new int[1 << 16];

            /*
             * Compute the histogram.
             */
            for (int i = high; i > low; ++count[a[--i] & 0xFFFF]);

            /*
             * Place values on their final positions.
             */
            for (int value = Short.MIN_VALUE; high > low; ) {
                while (count[--value & 0xFFFF] == 0);
                int num = count[value & 0xFFFF];

                do {
                    a[--high] = (short) value;
                } while (--num > 0);
            }

        } else {

            /*
             * Allocate additional buffer.
             */
            short[] b = new short[size];

            /*
             * Count the number of all digits.
             */
            int[] count1 = new int[1 << 8];
            int[] count2 = new int[1 << 8];

            for (int i = low; i < high; ++i) {
                ++count1[  a[i]        & 0xFF];
                ++count2[((a[i] >>> 8) & 0xFF) ^ 0x80]; // Flip the sign bit
            }

            /*
             * Check digits to be processed.
             */
            boolean processDigit1 = processDigit(count1, size, low);
            boolean processDigit2 = processDigit(count2, size, low);

            /*
             * Process the 1-st digit.
             */
            if (processDigit1) {
                for (int i = high; i > low; ) {
                    b[--count1[a[--i] & 0xFF] - low] = a[i];
                }
            }

            /*
             * Process the 2-nd digit.
             */
            if (processDigit2) {
                if (processDigit1) {
                    for (int i = size; i > 0; ) {
                        a[--count2[((b[--i] >>> 8) & 0xFF) ^ 0x80]] = b[i];
                    }
                } else {
                    for (int i = high; i > low; ) {
                        b[--count2[((a[--i] >>> 8) & 0xFF) ^ 0x80] - low] = a[i];
                    }
                }
            }

            /*
             * Copy the buffer to original array, if we process ood number of digits.
             */
            if (processDigit1 ^ processDigit2) {
                System.arraycopy(b, 0, a, low, size);
            }
        }
    }

// #[float]

    /**
     * The binary representation of float negative zero.
     */
    private static final int FLOAT_NEGATIVE_ZERO = Float.floatToRawIntBits(-0.0f);

    /**
     * Sorts the specified range of the array using parallel merge
     * sort and/or Dual-Pivot Quicksort.<p>
     *
     * To balance the faster splitting and parallelism of merge sort
     * with the faster element partitioning of Quicksort, ranges are
     * subdivided in tiers such that, if there is enough parallelism,
     * the four-way parallel merge is started, still ensuring enough
     * parallelism to process the partitions.
     *
     * @param a the array to be sorted
     * @param parallelism the parallelism level
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(float[] a, int parallelism, int low, int high) {
        /*
         * Phase 1. Count the number of negative zero -0.0,
         * turn them into positive zero, and move all NaNs
         * to the end of the array.
         */
        int negativeZeroCount = 0;

        for (int k = high; k > low; ) {
            float ak = a[--k];

            if (Float.floatToRawIntBits(ak) == FLOAT_NEGATIVE_ZERO) { // ak is -0.0
                negativeZeroCount++;
                a[k] = 0.0f;
            } else if (ak != ak) { // ak is Not-a-Number (NaN)
                a[k] = a[--high];
                a[high] = ak;
            }
        }

        /*
         * Phase 2. Sort everything except NaNs,
         * which are already in place.
         */
        if (parallelism > 1 && high - low > MIN_PARALLEL_SORT_SIZE) {
            new Sorter<>(a, parallelism, low, high - low).invoke();
        } else {
            sort(null, a, 0, low, high);
        }

        /*
         * Phase 3. Turn the required number of positive
         * zeros 0.0 back into negative zeros -0.0.
         */
        if (++negativeZeroCount == 1) {
            return;
        }

        /*
         * Find the position one less than
         * the index of the first zero.
         */
        while (low <= high) {
            int mid = (low + high) >>> 1;

            if (a[mid] < 0.0f) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        /*
         * Replace 0.0 by negative zeros -0.0.
         */
        while (--negativeZeroCount > 0) {
            a[++high] = -0.0f;
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that range is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(Sorter<float[]> sorter, float[] a, int bits, int low, int high) {
        while (true) {
            int size = high - low;

            /*
             * Run adaptive mixed insertion sort on small non-leftmost parts.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                sort(float.class, a, Unsafe.ARRAY_FLOAT_BASE_OFFSET,
                    low, high, DualPivotQuicksort::mixedInsertionSort);
                return;
            }

            /*
             * Invoke adaptive insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits * 5) {
                sort(float.class, a, Unsafe.ARRAY_FLOAT_BASE_OFFSET,
                    low, high, DualPivotQuicksort::insertionSort);
                return;
            }

            /*
             * Try merging sort on large part.
             */
            if (size > MIN_MERGING_SORT_SIZE * bits
                    && tryMergingSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Divide the given array into the golden ratio using
             * an inexpensive approximation to select five sample
             * elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 7);

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = high - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;

            /*
             * Sort these elements in-place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *   1 ---------o---------------o-----------------
             *              |               |
             *   2 ---------|-------o-------o-------o---------
             *              |       |               |
             *   3 ---------|-------|---------------|---------
             *              |       |               |
             *   4 ---------o-------|-------o-------o---------
             *                      |       |
             *   5 -----------------o-------o-----------------
             */
            if (a[e1] > a[e4]) { float t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e2] > a[e5]) { float t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e4] > a[e5]) { float t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e2]) { float t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e2] > a[e4]) { float t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a[e3] < a[e2]) {
                if (a[e3] < a[e1]) {
                    float t = a[e3]; a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = t;
                } else {
                    float t = a[e3]; a[e3] = a[e2]; a[e2] = t;
                }
            } else if (a[e3] > a[e4]) {
                if (a[e3] > a[e5]) {
                    float t = a[e3]; a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = t;
                } else {
                    float t = a[e3]; a[e3] = a[e4]; a[e4] = t;
                }
            }

            /*
             * Switch to heap sort to avoid quadratic time.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            /*
             * indices[0] - the index of the last element of the left part
             * indices[1] - the index of the first element of the right part
             */
            int[] indices;

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                indices = partition(float.class, a, Unsafe.ARRAY_FLOAT_BASE_OFFSET,
                    low, high, e1, e5, DualPivotQuicksort::partitionWithTwoPivots);

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[0] + 1, indices[1]);
                    sorter.fork(bits | 1, indices[1] + 1, high);
                } else {
                    sort(sorter, a, bits | 1, indices[0] + 1, indices[1]);
                    sort(sorter, a, bits | 1, indices[1] + 1, high);
                }

            } else { // Partitioning with one pivot

                indices = partition(float.class, a, Unsafe.ARRAY_FLOAT_BASE_OFFSET,
                    low, high, e3, e3, DualPivotQuicksort::partitionWithOnePivot);

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[1], high);
                } else {
                    sort(sorter, a, bits | 1, indices[1], high);
                }
            }
            high = indices[0]; // Iterate along the left part
        }
    }

    /**
     * Partitions the specified range of the array using two given pivots.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of pivot1, the first pivot
     * @param pivotIndex2 the index of pivot2, the second pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithTwoPivots(
            float[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = --high;
        int lower = low;

        /*
         * Use the first and fifth of the five sorted elements as
         * the pivots. These values are inexpensive approximation
         * of tertiles. Note, that pivot1 < pivot2.
         */
        float pivot1 = a[pivotIndex1];
        float pivot2 = a[pivotIndex2];

        /*
         * The first and the last elements to be sorted are moved
         * to the locations formerly occupied by the pivots. When
         * partitioning is completed, the pivots are swapped back
         * into their final positions, and excluded from the next
         * subsequent sorting.
         */
        a[pivotIndex1] = a[lower];
        a[pivotIndex2] = a[upper];

        /*
         * Skip elements, which are less or greater than the pivots.
         */
        while (a[++lower] < pivot1);
        while (a[--upper] > pivot2);

        /*
         * Backward 3-interval partitioning
         *
         *     left part                     central part          right part
         * +--------------+----------+--------------------------+--------------+
         * |   < pivot1   |    ?     |  pivot1 <= .. <= pivot2  |   > pivot2   |
         * +--------------+----------+--------------------------+--------------+
         *               ^          ^                            ^
         *               |          |                            |
         *             lower        k                          upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int unused = --lower, k = ++upper; --k > lower; ) {
            float ak = a[k];

            if (ak < pivot1) { // Move a[k] to the left part
                while (a[++lower] < pivot1);

                if (lower > k) {
                    lower = k;
                    break;
                }
                if (a[lower] > pivot2) {
                    a[k] = a[--upper];
                    a[upper] = a[lower];
                } else {
                    a[k] = a[lower];
                }
                a[lower] = ak;
            } else if (ak > pivot2) { // Move a[k] to the right part
                a[k] = a[--upper];
                a[upper] = ak;
            }
        }

        /*
         * Swap the pivots into their final positions.
         */
        a[low]  = a[lower]; a[lower] = pivot1;
        a[high] = a[upper]; a[upper] = pivot2;

        return new int[] { lower, upper };
    }

    /**
     * Partitions the specified range of the array using one given pivot.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of single pivot
     * @param pivotIndex2 the index of single pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithOnePivot(
            float[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = high;
        int lower = low;

        /*
         * Use the third of the five sorted elements as the pivot.
         * This value is inexpensive approximation of the median.
         */
        float pivot = a[pivotIndex1];

        /*
         * The first element to be sorted is moved to the
         * location formerly occupied by the pivot. After
         * completion of partitioning the pivot is swapped
         * back into its final position, and excluded from
         * the next subsequent sorting.
         */
        a[pivotIndex1] = a[lower];

        /*
         * Dutch National Flag partitioning
         *
         *     left part               central part    right part
         * +--------------+----------+--------------+-------------+
         * |   < pivot    |    ?     |   == pivot   |   > pivot   |
         * +--------------+----------+--------------+-------------+
         *               ^          ^                ^
         *               |          |                |
         *             lower        k              upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int k = upper; --k > lower; ) {
            float ak = a[k];

            if (ak == pivot) {
                continue;
            }
            a[k] = pivot;

            if (ak < pivot) { // Move a[k] to the left part
                while (a[++lower] < pivot);

                if (a[lower] > pivot) {
                    a[--upper] = a[lower];
                }
                a[lower] = ak;
            } else { // ak > pivot - Move a[k] to the right part
                a[--upper] = ak;
            }
        }

        /*
         * Swap the pivot into its final position.
         */
        a[low] = a[lower]; a[lower] = pivot;

        return new int[] { lower, upper };
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.<p>
     *
     * Mixed insertion sort is combination of pin insertion sort
     * and pair insertion sort.<p>
     *
     * In the context of Dual-Pivot Quicksort, the pivot element
     * from the left part plays the role of sentinel, because it
     * is less than any elements from the given part. Therefore,
     * expensive check of the left range can be skipped on each
     * iteration unless it is the leftmost call.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void mixedInsertionSort(float[] a, int low, int high) {
        /*
         * Split the array for pin and pair insertion sorts.
         */
        int end = high - ((3 * ((high - low) >> 2)) & ~1);

        /*
         * Start with pin insertion sort.
         */
        for (int i, p = high; ++low < end; ) {
            float ai = a[i = low], pin = a[--p];

            /*
             * Swap larger element with pin.
             */
            if (ai > pin) {
                ai = pin;
                a[p] = a[i];
            }

            /*
             * Insert element into sorted part.
             */
            while (ai < a[i - 1]) {
                a[i] = a[--i];
            }
            a[i] = ai;
        }

        /*
         * Finish with pair insertion sort.
         */
        for (int i; low < high; ++low) {
            float a1 = a[i = low], a2 = a[++low];

            /*
             * Insert two elements per iteration: at first, insert the
             * larger element and then insert the smaller element, but
             * from the position where the larger element was inserted.
             */
            if (a1 > a2) {

                while (a1 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a1;

                while (a2 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a2;

            } else if (a1 < a[i - 1]) {

                while (a2 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a2;

                while (a1 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a1;
            }
        }
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(float[] a, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            float ai = a[i = k];

            if (ai < a[i - 1]) {
                do {
                    a[i] = a[--i];
                } while (i > low && ai < a[i - 1]);

                a[i] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryMergingSort(Sorter<float[]> sorter, float[] a, int low, int high) {
        /*
         * The element run[i] holds the start index
         * of i-th sequence in non-descending order.
         */
        int count = 1;
        int[] run = null;

        /*
         * Identify all possible runs.
         */
        for (int k = low + 1, last = low; k < high; ) {
            /*
             * Find the next run.
             */
            if (a[k - 1] < a[k]) {

                // Identify ascending sequence
                while (++k < high && a[k - 1] <= a[k]);

            } else if (a[k - 1] > a[k]) {

                // Identify descending sequence
                while (++k < high && a[k - 1] >= a[k]);

                // Reverse into ascending order
                for (int i = last - 1, j = k; ++i < --j && a[i] > a[j]; ) {
                    float ai = a[i]; a[i] = a[j]; a[j] = ai;
                }

                // Check the next sequence
                if (k < high && a[k - 1] < a[k]) {
                    continue;
                }

            } else { // Identify constant sequence
                for (float ak = a[k]; ++k < high && ak == a[k]; );

                // Check the next sequence
                if (k < high) {
                    continue;
                }
            }

            /*
             * Process the current run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }
                run = new int[Math.min((high - low) >> 6, MAX_RUN_CAPACITY) | 8];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

                if (k - low < count * MIN_RUN_SIZE) {
                    /*
                     * Terminate the scanning,
                     * if the runs are too small.
                     */
                    return false;
                }

                if (++count == run.length) {
                    /*
                     * Array is not highly structured.
                     */
                    return false;
                }
            }

            /*
             * Save the current run.
             */
            run[count] = (last = k);

            /*
             * Check single-element run at the end.
             */
            if (++k == high) {
                --k;
            }
        }

        /*
         * Merge all runs.
         */
        if (count > 1) {
            float[] b; int offset = low;

            if (sorter != null && (b = sorter.b) != null) {
                offset = sorter.offset;
            } else if ((b = tryAllocate(float[].class, high - low)) == null) {
                return false;
            }
            mergeRuns(sorter, a, b, offset, true, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param sorter the parallel context
     * @param a the source array
     * @param b the buffer for merging
     * @param offset the start index in the source, inclusive
     * @param aim whether the original array is used for merging
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     */
    private static void mergeRuns(Sorter<float[]> sorter, float[] a, float[] b, int offset,
            boolean aim, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (!aim) {
                System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            }
            return;
        }

        /*
         * Split the array into two approximately equal parts.
         */
        int mi = lo, key = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= key);

        /*
         * Merge the runs of all parts.
         */
        mergeRuns(sorter, a, b, offset, !aim, run, lo, mi);
        mergeRuns(sorter, a, b, offset, !aim, run, mi, hi);

        float[] dst = aim ? a : b;
        float[] src = aim ? b : a;

        int k  = !aim ? run[lo] - offset : run[lo];
        int lo1 = aim ? run[lo] - offset : run[lo];
        int hi1 = aim ? run[mi] - offset : run[mi];
        int lo2 = aim ? run[mi] - offset : run[mi];
        int hi2 = aim ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && sorter != null) {
            new Merger<>(null, dst, k, src, lo1, hi1, lo2, hi2).invoke();
        } else {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        }
    }

    /**
     * Merges the sorted parts in parallel.
     *
     * @param merger the parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<float[]> merger, float[] dst, int k,
            float[] src, int lo1, int hi1, int lo2, int hi2) {

        while (true) {
            /*
             * The first part must be larger.
             */
            if (hi1 - lo1 < hi2 - lo2) {
                int lo = lo1; lo1 = lo2; lo2 = lo;
                int hi = hi1; hi1 = hi2; hi2 = hi;
            }

            /*
             * Merge the small parts sequentially.
             */
            if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                break;
            }

            /*
             * Find the median of the larger part.
             */
            int mi1 = (lo1 + hi1) >>> 1;
            int mi2 = hi2;
            float key = src[mi1];

            /*
             * Split the smaller part.
             */
            for (int mi0 = lo2; mi0 < mi2; ) {
                int mid = (mi0 + mi2) >>> 1;

                if (key > src[mid]) {
                    mi0 = mid + 1;
                } else {
                    mi2 = mid;
                }
            }

            /*
             * Merge the first parts in parallel.
             */
            merger.fork(k, lo1, mi1, lo2, mi2);

            /*
             * Reserve space for the second parts.
             */
            k += mi2 - lo2 + mi1 - lo1;

            /*
             * Iterate along the second parts.
             */
            lo1 = mi1;
            lo2 = mi2;
        }

        /*
         * Check if the array is already ordered and then merge the parts.
         */
        if (lo1 < hi1 && lo2 < hi2 && src[hi1 - 1] > src[lo2]) {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        } else {
            System.arraycopy(src, lo1, dst, k, hi1 - lo1);
            System.arraycopy(src, lo2, dst, k + hi1 - lo1, hi2 - lo2);
        }
    }

    /**
     * Merges the sorted parts sequentially.
     *
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(float[] dst, int k,
            float[] src, int lo1, int hi1, int lo2, int hi2) {

        if (src[hi1 - 1] < src[hi2 - 1]) {
            while (lo1 < hi1) {
                float next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else if (src[hi1 - 1] > src[hi2 - 1]) {
            while (lo2 < hi2) {
                float next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else {
            while (lo1 < hi1 && lo2 < hi2) {
                float next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        }

        /*
         * Copy the tail of the left and right parts.
         */
        System.arraycopy(src, lo1, dst, k, hi1 - lo1);
        System.arraycopy(src, lo2, dst, k, hi2 - lo2);
    }

    /**
     * Sorts the specified range of the array using heap sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void heapSort(float[] a, int low, int high) {
        for (int k = (low + high) >>> 1; k > low; ) {
            pushDown(a, --k, a[k], low, high);
        }
        while (--high > low) {
            float max = a[low];
            pushDown(a, low, a[high], low, high);
            a[high] = max;
        }
    }

    /**
     * Pushes specified element down during heap sort.
     *
     * @param a the given array
     * @param p the start index
     * @param value the given element
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void pushDown(float[] a, int p, float value, int low, int high) {
        for (int k ;; a[p] = a[p = k]) {
            k = (p << 1) - low + 2; // Index of the right child

            if (k > high) {
                break;
            }
            if (k == high || a[k] < a[k - 1]) {
                --k;
            }
            if (a[k] <= value) {
                break;
            }
        }
        a[p] = value;
    }

// #[double]

    /**
     * The binary representation of double negative zero.
     */
    private static final long DOUBLE_NEGATIVE_ZERO = Double.doubleToRawLongBits(-0.0d);

    /**
     * Sorts the specified range of the array using parallel merge
     * sort and/or Dual-Pivot Quicksort.<p>
     *
     * To balance the faster splitting and parallelism of merge sort
     * with the faster element partitioning of Quicksort, ranges are
     * subdivided in tiers such that, if there is enough parallelism,
     * the four-way parallel merge is started, still ensuring enough
     * parallelism to process the partitions.
     *
     * @param a the array to be sorted
     * @param parallelism the parallelism level
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(double[] a, int parallelism, int low, int high) {
        /*
         * Phase 1. Count the number of negative zero -0.0,
         * turn them into positive zero, and move all NaNs
         * to the end of the array.
         */
        int negativeZeroCount = 0;

        for (int k = high; k > low; ) {
            double ak = a[--k];

            if (Double.doubleToRawLongBits(ak) == DOUBLE_NEGATIVE_ZERO) { // ak is -0.0
                negativeZeroCount++;
                a[k] = 0.0d;
            } else if (ak != ak) { // ak is Not-a-Number (NaN)
                a[k] = a[--high];
                a[high] = ak;
            }
        }

        /*
         * Phase 2. Sort everything except NaNs,
         * which are already in place.
         */
        if (parallelism > 1 && high - low > MIN_PARALLEL_SORT_SIZE) {
            new Sorter<>(a, parallelism, low, high - low).invoke();
        } else {
            sort(null, a, 0, low, high);
        }

        /*
         * Phase 3. Turn the required number of positive
         * zeros 0.0 back into negative zeros -0.0.
         */
        if (++negativeZeroCount == 1) {
            return;
        }

        /*
         * Find the position one less than
         * the index of the first zero.
         */
        while (low <= high) {
            int mid = (low + high) >>> 1;

            if (a[mid] < 0.0d) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        /*
         * Replace 0.0 by negative zeros -0.0.
         */
        while (--negativeZeroCount > 0) {
            a[++high] = -0.0d;
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that range is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(Sorter<double[]> sorter, double[] a, int bits, int low, int high) {
        while (true) {
            int size = high - low;

            /*
             * Run adaptive mixed insertion sort on small non-leftmost parts.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                sort(double.class, a, Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                    low, high, DualPivotQuicksort::mixedInsertionSort);
                return;
            }

            /*
             * Invoke adaptive insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE + bits * 5) {
                sort(double.class, a, Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                    low, high, DualPivotQuicksort::insertionSort);
                return;
            }

            /*
             * Try merging sort on large part.
             */
            if (size > MIN_MERGING_SORT_SIZE * bits
                    && tryMergingSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Divide the given array into the golden ratio using
             * an inexpensive approximation to select five sample
             * elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 7);

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = high - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;

            /*
             * Sort these elements in-place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *   1 ---------o---------------o-----------------
             *              |               |
             *   2 ---------|-------o-------o-------o---------
             *              |       |               |
             *   3 ---------|-------|---------------|---------
             *              |       |               |
             *   4 ---------o-------|-------o-------o---------
             *                      |       |
             *   5 -----------------o-------o-----------------
             */
            if (a[e1] > a[e4]) { double t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e2] > a[e5]) { double t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e4] > a[e5]) { double t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e2]) { double t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e2] > a[e4]) { double t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a[e3] < a[e2]) {
                if (a[e3] < a[e1]) {
                    double t = a[e3]; a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = t;
                } else {
                    double t = a[e3]; a[e3] = a[e2]; a[e2] = t;
                }
            } else if (a[e3] > a[e4]) {
                if (a[e3] > a[e5]) {
                    double t = a[e3]; a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = t;
                } else {
                    double t = a[e3]; a[e3] = a[e4]; a[e4] = t;
                }
            }

            /*
             * Switch to heap sort to avoid quadratic time.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            /*
             * indices[0] - the index of the last element of the left part
             * indices[1] - the index of the first element of the right part
             */
            int[] indices;

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                indices = partition(double.class, a, Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                    low, high, e1, e5, DualPivotQuicksort::partitionWithTwoPivots);

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[0] + 1, indices[1]);
                    sorter.fork(bits | 1, indices[1] + 1, high);
                } else {
                    sort(sorter, a, bits | 1, indices[0] + 1, indices[1]);
                    sort(sorter, a, bits | 1, indices[1] + 1, high);
                }

            } else { // Partitioning with one pivot

                indices = partition(double.class, a, Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                    low, high, e3, e3, DualPivotQuicksort::partitionWithOnePivot);

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, indices[1], high);
                } else {
                    sort(sorter, a, bits | 1, indices[1], high);
                }
            }
            high = indices[0]; // Iterate along the left part
        }
    }

    /**
     * Partitions the specified range of the array using two given pivots.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of pivot1, the first pivot
     * @param pivotIndex2 the index of pivot2, the second pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithTwoPivots(
            double[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = --high;
        int lower = low;

        /*
         * Use the first and fifth of the five sorted elements as
         * the pivots. These values are inexpensive approximation
         * of tertiles. Note, that pivot1 < pivot2.
         */
        double pivot1 = a[pivotIndex1];
        double pivot2 = a[pivotIndex2];

        /*
         * The first and the last elements to be sorted are moved
         * to the locations formerly occupied by the pivots. When
         * partitioning is completed, the pivots are swapped back
         * into their final positions, and excluded from the next
         * subsequent sorting.
         */
        a[pivotIndex1] = a[lower];
        a[pivotIndex2] = a[upper];

        /*
         * Skip elements, which are less or greater than the pivots.
         */
        while (a[++lower] < pivot1);
        while (a[--upper] > pivot2);

        /*
         * Backward 3-interval partitioning
         *
         *     left part                     central part          right part
         * +--------------+----------+--------------------------+--------------+
         * |   < pivot1   |    ?     |  pivot1 <= .. <= pivot2  |   > pivot2   |
         * +--------------+----------+--------------------------+--------------+
         *               ^          ^                            ^
         *               |          |                            |
         *             lower        k                          upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int unused = --lower, k = ++upper; --k > lower; ) {
            double ak = a[k];

            if (ak < pivot1) { // Move a[k] to the left part
                while (a[++lower] < pivot1);

                if (lower > k) {
                    lower = k;
                    break;
                }
                if (a[lower] > pivot2) {
                    a[k] = a[--upper];
                    a[upper] = a[lower];
                } else {
                    a[k] = a[lower];
                }
                a[lower] = ak;
            } else if (ak > pivot2) { // Move a[k] to the right part
                a[k] = a[--upper];
                a[upper] = ak;
            }
        }

        /*
         * Swap the pivots into their final positions.
         */
        a[low]  = a[lower]; a[lower] = pivot1;
        a[high] = a[upper]; a[upper] = pivot2;

        return new int[] { lower, upper };
    }

    /**
     * Partitions the specified range of the array using one given pivot.
     *
     * @param a the array for partitioning
     * @param low the index of the first element, inclusive, for partitioning
     * @param high the index of the last element, exclusive, for partitioning
     * @param pivotIndex1 the index of single pivot
     * @param pivotIndex2 the index of single pivot
     * @return indices of parts after partitioning
     */
    private static int[] partitionWithOnePivot(
            double[] a, int low, int high, int pivotIndex1, int pivotIndex2) {
        /*
         * Pointers to the right and left parts.
         */
        int upper = high;
        int lower = low;

        /*
         * Use the third of the five sorted elements as the pivot.
         * This value is inexpensive approximation of the median.
         */
        double pivot = a[pivotIndex1];

        /*
         * The first element to be sorted is moved to the
         * location formerly occupied by the pivot. After
         * completion of partitioning the pivot is swapped
         * back into its final position, and excluded from
         * the next subsequent sorting.
         */
        a[pivotIndex1] = a[lower];

        /*
         * Dutch National Flag partitioning
         *
         *     left part               central part    right part
         * +--------------+----------+--------------+-------------+
         * |   < pivot    |    ?     |   == pivot   |   > pivot   |
         * +--------------+----------+--------------+-------------+
         *               ^          ^                ^
         *               |          |                |
         *             lower        k              upper
         *
         * Pointer k is the last index of ?-part
         * Pointer lower is the last index of left part
         * Pointer upper is the first index of right part
         */
        for (int k = upper; --k > lower; ) {
            double ak = a[k];

            if (ak == pivot) {
                continue;
            }
            a[k] = pivot;

            if (ak < pivot) { // Move a[k] to the left part
                while (a[++lower] < pivot);

                if (a[lower] > pivot) {
                    a[--upper] = a[lower];
                }
                a[lower] = ak;
            } else { // ak > pivot - Move a[k] to the right part
                a[--upper] = ak;
            }
        }

        /*
         * Swap the pivot into its final position.
         */
        a[low] = a[lower]; a[lower] = pivot;

        return new int[] { lower, upper };
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.<p>
     *
     * Mixed insertion sort is combination of pin insertion sort
     * and pair insertion sort.<p>
     *
     * In the context of Dual-Pivot Quicksort, the pivot element
     * from the left part plays the role of sentinel, because it
     * is less than any elements from the given part. Therefore,
     * expensive check of the left range can be skipped on each
     * iteration unless it is the leftmost call.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void mixedInsertionSort(double[] a, int low, int high) {
        /*
         * Split the array for pin and pair insertion sorts.
         */
        int end = high - ((3 * ((high - low) >> 2)) & ~1);

        /*
         * Start with pin insertion sort.
         */
        for (int i, p = high; ++low < end; ) {
            double ai = a[i = low], pin = a[--p];

            /*
             * Swap larger element with pin.
             */
            if (ai > pin) {
                ai = pin;
                a[p] = a[i];
            }

            /*
             * Insert element into sorted part.
             */
            while (ai < a[i - 1]) {
                a[i] = a[--i];
            }
            a[i] = ai;
        }

        /*
         * Finish with pair insertion sort.
         */
        for (int i; low < high; ++low) {
            double a1 = a[i = low], a2 = a[++low];

            /*
             * Insert two elements per iteration: at first, insert the
             * larger element and then insert the smaller element, but
             * from the position where the larger element was inserted.
             */
            if (a1 > a2) {

                while (a1 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a1;

                while (a2 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a2;

            } else if (a1 < a[i - 1]) {

                while (a2 < a[--i]) {
                    a[i + 2] = a[i];
                }
                a[++i + 1] = a2;

                while (a1 < a[--i]) {
                    a[i + 1] = a[i];
                }
                a[i + 1] = a1;
            }
        }
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(double[] a, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            double ai = a[i = k];

            if (ai < a[i - 1]) {
                do {
                    a[i] = a[--i];
                } while (i > low && ai < a[i - 1]);

                a[i] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter the parallel context
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryMergingSort(Sorter<double[]> sorter, double[] a, int low, int high) {
        /*
         * The element run[i] holds the start index
         * of i-th sequence in non-descending order.
         */
        int count = 1;
        int[] run = null;

        /*
         * Identify all possible runs.
         */
        for (int k = low + 1, last = low; k < high; ) {
            /*
             * Find the next run.
             */
            if (a[k - 1] < a[k]) {

                // Identify ascending sequence
                while (++k < high && a[k - 1] <= a[k]);

            } else if (a[k - 1] > a[k]) {

                // Identify descending sequence
                while (++k < high && a[k - 1] >= a[k]);

                // Reverse into ascending order
                for (int i = last - 1, j = k; ++i < --j && a[i] > a[j]; ) {
                    double ai = a[i]; a[i] = a[j]; a[j] = ai;
                }

                // Check the next sequence
                if (k < high && a[k - 1] < a[k]) {
                    continue;
                }

            } else { // Identify constant sequence
                for (double ak = a[k]; ++k < high && ak == a[k]; );

                // Check the next sequence
                if (k < high) {
                    continue;
                }
            }

            /*
             * Process the current run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }
                run = new int[Math.min((high - low) >> 6, MAX_RUN_CAPACITY) | 8];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

                if (k - low < count * MIN_RUN_SIZE) {
                    /*
                     * Terminate the scanning,
                     * if the runs are too small.
                     */
                    return false;
                }

                if (++count == run.length) {
                    /*
                     * Array is not highly structured.
                     */
                    return false;
                }
            }

            /*
             * Save the current run.
             */
            run[count] = (last = k);

            /*
             * Check single-element run at the end.
             */
            if (++k == high) {
                --k;
            }
        }

        /*
         * Merge all runs.
         */
        if (count > 1) {
            double[] b; int offset = low;

            if (sorter != null && (b = sorter.b) != null) {
                offset = sorter.offset;
            } else if ((b = tryAllocate(double[].class, high - low)) == null) {
                return false;
            }
            mergeRuns(sorter, a, b, offset, true, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param sorter the parallel context
     * @param a the source array
     * @param b the buffer for merging
     * @param offset the start index in the source, inclusive
     * @param aim whether the original array is used for merging
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     */
    private static void mergeRuns(Sorter<double[]> sorter, double[] a, double[] b, int offset,
            boolean aim, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (!aim) {
                System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            }
            return;
        }

        /*
         * Split the array into two approximately equal parts.
         */
        int mi = lo, key = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= key);

        /*
         * Merge the runs of all parts.
         */
        mergeRuns(sorter, a, b, offset, !aim, run, lo, mi);
        mergeRuns(sorter, a, b, offset, !aim, run, mi, hi);

        double[] dst = aim ? a : b;
        double[] src = aim ? b : a;

        int k  = !aim ? run[lo] - offset : run[lo];
        int lo1 = aim ? run[lo] - offset : run[lo];
        int hi1 = aim ? run[mi] - offset : run[mi];
        int lo2 = aim ? run[mi] - offset : run[mi];
        int hi2 = aim ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && sorter != null) {
            new Merger<>(null, dst, k, src, lo1, hi1, lo2, hi2).invoke();
        } else {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        }
    }

    /**
     * Merges the sorted parts in parallel.
     *
     * @param merger the parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<double[]> merger, double[] dst, int k,
            double[] src, int lo1, int hi1, int lo2, int hi2) {

        while (true) {
            /*
             * The first part must be larger.
             */
            if (hi1 - lo1 < hi2 - lo2) {
                int lo = lo1; lo1 = lo2; lo2 = lo;
                int hi = hi1; hi1 = hi2; hi2 = hi;
            }

            /*
             * Merge the small parts sequentially.
             */
            if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                break;
            }

            /*
             * Find the median of the larger part.
             */
            int mi1 = (lo1 + hi1) >>> 1;
            int mi2 = hi2;
            double key = src[mi1];

            /*
             * Split the smaller part.
             */
            for (int mi0 = lo2; mi0 < mi2; ) {
                int mid = (mi0 + mi2) >>> 1;

                if (key > src[mid]) {
                    mi0 = mid + 1;
                } else {
                    mi2 = mid;
                }
            }

            /*
             * Merge the first parts in parallel.
             */
            merger.fork(k, lo1, mi1, lo2, mi2);

            /*
             * Reserve space for the second parts.
             */
            k += mi2 - lo2 + mi1 - lo1;

            /*
             * Iterate along the second parts.
             */
            lo1 = mi1;
            lo2 = mi2;
        }

        /*
         * Check if the array is already ordered and then merge the parts.
         */
        if (lo1 < hi1 && lo2 < hi2 && src[hi1 - 1] > src[lo2]) {
            mergeParts(dst, k, src, lo1, hi1, lo2, hi2);
        } else {
            System.arraycopy(src, lo1, dst, k, hi1 - lo1);
            System.arraycopy(src, lo2, dst, k + hi1 - lo1, hi2 - lo2);
        }
    }

    /**
     * Merges the sorted parts sequentially.
     *
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param src the source array
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(double[] dst, int k,
            double[] src, int lo1, int hi1, int lo2, int hi2) {

        if (src[hi1 - 1] < src[hi2 - 1]) {
            while (lo1 < hi1) {
                double next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else if (src[hi1 - 1] > src[hi2 - 1]) {
            while (lo2 < hi2) {
                double next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        } else {
            while (lo1 < hi1 && lo2 < hi2) {
                double next = src[lo1];

                if (next <= src[lo2]) {
                    dst[k++] = src[lo1++];
                }
                if (next >= src[lo2]) {
                    dst[k++] = src[lo2++];
                }
            }
        }

        /*
         * Copy the tail of the left and right parts.
         */
        System.arraycopy(src, lo1, dst, k, hi1 - lo1);
        System.arraycopy(src, lo2, dst, k, hi2 - lo2);
    }

    /**
     * Sorts the specified range of the array using heap sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void heapSort(double[] a, int low, int high) {
        for (int k = (low + high) >>> 1; k > low; ) {
            pushDown(a, --k, a[k], low, high);
        }
        while (--high > low) {
            double max = a[low];
            pushDown(a, low, a[high], low, high);
            a[high] = max;
        }
    }

    /**
     * Pushes specified element down during heap sort.
     *
     * @param a the given array
     * @param p the start index
     * @param value the given element
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void pushDown(double[] a, int p, double value, int low, int high) {
        for (int k ;; a[p] = a[p = k]) {
            k = (p << 1) - low + 2; // Index of the right child

            if (k > high) {
                break;
            }
            if (k == high || a[k] < a[k - 1]) {
                --k;
            }
            if (a[k] <= value) {
                break;
            }
        }
        a[p] = value;
    }

    /**
     * Checks the count array and then computes the histogram.
     *
     * @param count the count array
     * @param total the total number of elements
     * @param low the index of the first element, inclusive
     * @return {@code true} if the digit must be processed, otherwise {@code false}
     */
    private static boolean processDigit(int[] count, int total, int low) {
        /*
         * Check if we can skip the given digit.
         */
        for (int c : count) {
            if (c == total) {
                return false;
            }
            if (c > 0) {
                break;
            }
        }

        /*
         * Compute the histogram.
         */
        count[0] += low;

        for (int i = 0; ++i < count.length; ) {
            count[i] += count[i - 1];
        }
        return true;
    }

// #[class]

    /**
     * Implementation of parallel sorting.
     *
     * @param <T> the class of array
     */
    private static final class Sorter<T> extends CountedCompleter<Void> {

        @java.io.Serial
        private static final long serialVersionUID = 123456789L;

        @SuppressWarnings("serial")
        private final T a, b;
        private final int low, size, offset, depth;

        @SuppressWarnings("unchecked")
        private Sorter(T a, int parallelism, int low, int size) {
            this.a = a;
            this.low = low;
            this.size = size;
            this.offset = low;
            this.b = (T) tryAllocate(a.getClass(), size);
            this.depth = b == null ? 0 : ((parallelism >> 7) + 2) * (-2);
        }

        private Sorter(CountedCompleter<?> parent,
                T a, T b, int low, int size, int offset, int depth) {
            super(parent);
            this.a = a;
            this.b = b;
            this.low = low;
            this.size = size;
            this.offset = offset;
            this.depth = depth;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void compute() {
            if (depth < 0) {
                setPendingCount(2);
                int half = size >> 1;
                new Sorter<>(this, b, a, low, half, offset, depth + 1).fork();
                new Sorter<>(this, b, a, low + half, size - half, offset, depth + 1).compute();
            } else {
                switch(a) {
                    case int[] ai -> sort((Sorter<int[]>) this, ai, depth, low, low + size);
                    case long[] al -> sort((Sorter<long[]>) this, al, depth, low, low + size);
                    case float[] af -> sort((Sorter<float[]>) this, af, depth, low, low + size);
                    case double[] ad -> sort((Sorter<double[]>) this, ad, depth, low, low + size);
                    default -> throw new IllegalArgumentException("Unknown array: " + a.getClass().getName());
                }
            }
            tryComplete();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (depth < 0) {
                int mi = low + (size >> 1);
                boolean src = (depth & 1) == 0;

                new Merger<>(null,
                    a,
                    src ? low : low - offset,
                    b,
                    src ? low - offset : low,
                    src ? mi - offset : mi,
                    src ? mi - offset : mi,
                    src ? low + size - offset : low + size
                ).invoke();
            }
        }

        private void fork(int depth, int low, int high) {
            addToPendingCount(1);
            new Sorter<>(this, a, b, low, high - low, offset, depth).fork();
        }
    }

    /**
     * Implementation of parallel merging.
     *
     * @param <T> the class of array
     */
    private static final class Merger<T> extends CountedCompleter<Void> {

        @java.io.Serial
        private static final long serialVersionUID = 123456789L;

        @SuppressWarnings("serial")
        private final T dst, src;
        private final int k, lo1, hi1, lo2, hi2;

        private Merger(CountedCompleter<?> parent, T dst, int k,
                T src, int lo1, int hi1, int lo2, int hi2) {
            super(parent);
            this.dst = dst;
            this.k = k;
            this.src = src;
            this.lo1 = lo1;
            this.hi1 = hi1;
            this.lo2 = lo2;
            this.hi2 = hi2;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void compute() {
            switch(dst) {
                case int[] di -> mergeParts((Merger<int[]>) this, di, k, (int[]) src, lo1, hi1, lo2, hi2);
                case long[] dl -> mergeParts((Merger<long[]>) this, dl, k, (long[]) src, lo1, hi1, lo2, hi2);
                case float[] df -> mergeParts((Merger<float[]>) this, df, k, (float[]) src, lo1, hi1, lo2, hi2);
                case double[] dd -> mergeParts((Merger<double[]>) this, dd, k, (double[]) src, lo1, hi1, lo2, hi2);
                default -> throw new IllegalArgumentException("Unknown array: " + dst.getClass().getName());
            }
            propagateCompletion();
        }

        private void fork(int k, int lo1, int hi1, int lo2, int hi2) {
            addToPendingCount(1);
            new Merger<>(this, dst, k, src, lo1, hi1, lo2, hi2).fork();
        }
    }

    /**
     * Tries to allocate additional buffer.
     *
     * @param <T> the class of array
     * @param clazz the given array class
     * @param length the length of additional buffer
     * @return {@code null} if requested buffer is too big or there is no enough memory,
     *         otherwise created buffer
     */
    @SuppressWarnings("unchecked")
    private static <T> T tryAllocate(Class<T> clazz, int length) {
        try {
            int maxLength = MAX_BUFFER_SIZE >>
                (clazz == int[].class || clazz == float[].class ? 2 : 3);
            return length > maxLength ? null :
                (T) U.allocateUninitializedArray(clazz.componentType(), length);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static final Unsafe U = Unsafe.getUnsafe();
}
