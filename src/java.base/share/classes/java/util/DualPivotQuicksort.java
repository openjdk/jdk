/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.util.concurrent.CountedCompleter;
import jdk.internal.misc.Unsafe;

/**
 * This class implements powerful and fully optimized versions, both
 * sequential and parallel, of the Dual-Pivot Quicksort algorithm by
 * Vladimir Yaroslavskiy, Jon Bentley and Josh Bloch. This algorithm
 * offers O(n log(n)) performance on all data sets, and is typically
 * faster than traditional (one-pivot) Quicksort implementations.
 *
 * There are also additional algorithms, invoked from the Dual-Pivot
 * Quicksort such as merging sort, sorting network, Radix sort, heap
 * sort, mixed (simple, pin, pair) insertion sort, counting sort and
 * parallel merge sort.
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 * @author Doug Lea
 *
 * @version 2022.06.14
 *
 * @since 1.7 * 14 ^ 22
 */
final class DualPivotQuicksort {

    /**
     * Prevents instantiation.
     */
    private DualPivotQuicksort() {}

    /* ---------------- Insertion sort section ---------------- */

    /**
     * Max array size to use mixed insertion sort.
     */
    private static final int MAX_MIXED_INSERTION_SORT_SIZE = 124;

    /**
     * Max array size to use insertion sort.
     */
    private static final int MAX_INSERTION_SORT_SIZE = 44;

    /* ----------------- Merging sort section ----------------- */

    /**
     * Min array size to use merging sort.
     */
    private static final int MIN_MERGING_SORT_SIZE = 512;

    /**
     * Min size of run to continue scanning.
     */
    private static final int MIN_RUN_SIZE = 128;

    /* ------------------ Radix sort section ------------------ */

    /**
     * Min array size to use Radix sort.
     */
    private static final int MIN_RADIX_SORT_SIZE = 800;

    /* ------------------ Counting sort section --------------- */

    /**
     * Min size of a byte array to use counting sort.
     */
    private static final int MIN_BYTE_COUNTING_SORT_SIZE = 36;

    /**
     * Min size of a char array to use counting sort.
     */
    private static final int MIN_CHAR_COUNTING_SORT_SIZE = 1700;

    /**
     * Min size of a short array to use counting sort.
     */
    private static final int MIN_SHORT_COUNTING_SORT_SIZE = 2100;

    /* -------------------- Common section -------------------- */

    /**
     * Min array size to perform sorting in parallel.
     */
    private static final int MIN_PARALLEL_SORT_SIZE = 1024;

    /**
     * Max recursive depth before switching to heap sort.
     */
    private static final int MAX_RECURSION_DEPTH = 64 << 1;

    /**
     * Max size of additional buffer,
     *      limited by max_heap / 64 or 2 GB max.
     */
    private static final int MAX_BUFFER_SIZE =
            (int) Math.min(Runtime.getRuntime().maxMemory() >> 6, Integer.MAX_VALUE);

    /**
     * Sorts the specified range of the array using parallel merge
     * sort and/or Dual-Pivot Quicksort.
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
            new Sorter<>(a, parallelism, low, high - low, 0).invoke();
        } else {
            sort(null, a, 0, low, high);
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter parallel context
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
            if (size < MAX_MIXED_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                mixedInsertionSort(a, low, high);
                return;
            }

            /*
             * Invoke insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE) {
                insertionSort(a, low, high);
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
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 8) + 1;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int end = high - 1;
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;
            int a3 = a[e3];

            boolean isRandom =
                a[e1] > a[e2] || a[e2] > a3 || a3 > a[e4] || a[e4] > a[e5];

            /*
             * Sort these elements in place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *    1  ------------o-----o------------
             *                   |     |
             *    2  ------o-----|-----o-----o------
             *             |     |           |
             *    4  ------|-----o-----o-----o------
             *             |           |
             *    5  ------o-----------o------------
             */
            if (a[e2] > a[e5]) { int t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e4]) { int t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e1] > a[e2]) { int t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e4] > a[e5]) { int t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e2] > a[e4]) { int t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a3 < a[e2]) {
                if (a3 < a[e1]) {
                    a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = a3;
                } else {
                    a[e3] = a[e2]; a[e2] = a3;
                }
            } else if (a3 > a[e4]) {
                if (a3 > a[e5]) {
                    a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = a3;
                } else {
                    a[e3] = a[e4]; a[e4] = a3;
                }
            }

            /*
             * Try Radix sort on large fully random data,
             * taking into account parallel context.
             */
            isRandom &= a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5];

            if (size > MIN_RADIX_SORT_SIZE && isRandom && (sorter == null || bits > 0)
                    && tryRadixSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Switch to heap sort, if execution time is quadratic.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            // Pointers
            int lower = low; // The index of the last element of the left part
            int upper = end; // The index of the first element of the right part

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                int pivot1 = a[e1];
                int pivot2 = a[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivot1);
                while (a[--upper] > pivot2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *     left part                    central part          right part
                 * +------------------------------------------------------------------+
                 * |   < pivot1   |    ?    |  pivot1 <= && <= pivot2  |   > pivot2   |
                 * +------------------------------------------------------------------+
                 *               ^         ^                            ^
                 *               |         |                            |
                 *             lower       k                          upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot1
                 *     all in (k, upper)   in [pivot1, pivot2]
                 *     all in [upper, end)  >  pivot2
                 */
                for (int unused = --lower, k = ++upper; --k > lower; ) {
                    int ak = a[k];

                    if (ak < pivot1) { // Move a[k] to the left side
                        while (a[++lower] < pivot1) {
                            if (lower == k) {
                                break;
                            }
                        }
                        if (a[lower] > pivot2) {
                            a[k] = a[--upper];
                            a[upper] = a[lower];
                        } else {
                            a[k] = a[lower];
                        }
                        a[lower] = ak;
                    } else if (ak > pivot2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower]; a[lower] = pivot1;
                a[end] = a[upper]; a[upper] = pivot2;

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, lower + 1, upper);
                    sorter.fork(bits | 1, upper + 1, high);
                } else {
                    sort(sorter, a, bits | 1, lower + 1, upper);
                    sort(sorter, a, bits | 1, upper + 1, high);
                }

            } else { // Partitioning with one pivot

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                int pivot = a[e3];

                /*
                 * The first element to be sorted is moved to the
                 * location formerly occupied by the pivot. After
                 * completion of partitioning the pivot is swapped
                 * back into its final position, and excluded from
                 * the next subsequent sorting.
                 */
                a[e3] = a[lower];

                /*
                 * Dutch National Flag partitioning
                 *
                 *    left part                central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot
                 *     all in (k, upper)   ==  pivot
                 *     all in [upper, end]  >  pivot
                 */
                for (int k = ++upper; --k > lower; ) {
                    int ak = a[k];

                    if (ak != pivot) {
                        a[k] = pivot;

                        if (ak < pivot) { // Move a[k] to the left side
                            while (a[++lower] < pivot);

                            if (a[lower] > pivot) {
                                a[--upper] = a[lower];
                            }
                            a[lower] = ak;
                        } else { // ak > pivot - Move a[k] to the right side
                            a[--upper] = ak;
                        }
                    }
                }

                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower]; a[lower] = pivot;

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, upper, high);
                } else {
                    sort(sorter, a, bits | 1, upper, high);
                }
            }
            high = lower; // Iterate along the left part
        }
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.
     *
     * Mixed insertion sort is combination of pin insertion sort,
     * simple insertion sort and pair insertion sort.
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
         * Split part for pin and pair insertion sorts.
         */
        int end = high - 3 * ((high - low) >> 3 << 1);

        /*
         * Invoke simple insertion sort on small part.
         */
        if (end == high) {
            for (int i; ++low < high; ) {
                int ai = a[i = low];

                while (ai < a[i - 1]) {
                    a[i] = a[--i];
                }
                a[i] = ai;
            }
            return;
        }

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

                a[i ] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter parallel context
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
            } else { // Identify constant sequence
                for (int ak = a[k]; ++k < high && ak == a[k]; );

                if (k < high) {
                    continue;
                }
            }

            /*
             * Check if the runs are too
             * long to continue scanning.
             */
            if (count > 6 && k - low < count * MIN_RUN_SIZE) {
                return false;
            }

            /*
             * Process the run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }

                run = new int[((high - low) >> 9) & 0x1FF | 0x3F];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

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
            mergeRuns(a, b, offset, 1, sorter != null, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param a the source array
     * @param b the temporary buffer used in merging
     * @param offset the start index in the source, inclusive
     * @param aim specifies merging: to source ( > 0), buffer ( < 0) or any ( == 0)
     * @param parallel indicates whether merging is performed in parallel
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     * @return the destination where runs are merged
     */
    private static int[] mergeRuns(int[] a, int[] b, int offset,
            int aim, boolean parallel, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (aim >= 0) {
                return a;
            }
            System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            return b;
        }

        /*
         * Split into approximately equal parts.
         */
        int mi = lo, rmi = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= rmi);

        /*
         * Merge runs of each part.
         */
        int[] a1 = mergeRuns(a, b, offset, -aim, parallel, run, lo, mi);
        int[] a2 = mergeRuns(a, b, offset,    0, parallel, run, mi, hi);
        int[] dst = a1 == a ? b : a;

        int k   = a1 == a ? run[lo] - offset : run[lo];
        int lo1 = a1 == b ? run[lo] - offset : run[lo];
        int hi1 = a1 == b ? run[mi] - offset : run[mi];
        int lo2 = a2 == b ? run[mi] - offset : run[mi];
        int hi2 = a2 == b ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && parallel) {
            new Merger<>(null, dst, k, a1, lo1, hi1, a2, lo2, hi2).invoke();
        } else {
            mergeParts(null, dst, k, a1, lo1, hi1, a2, lo2, hi2);
        }
        return dst;
    }

    /**
     * Merges the sorted parts.
     *
     * @param merger parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param a1 the first part
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param a2 the second part
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<int[]> merger, int[] dst, int k,
            int[] a1, int lo1, int hi1, int[] a2, int lo2, int hi2) {

        if (merger != null && a1 == a2) {

            while (true) {

                /*
                 * The first part must be larger.
                 */
                if (hi1 - lo1 < hi2 - lo2) {
                    int lo = lo1; lo1 = lo2; lo2 = lo;
                    int hi = hi1; hi1 = hi2; hi2 = hi;
                }

                /*
                 * Small parts will be merged sequentially.
                 */
                if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                    break;
                }

                /*
                 * Find the median of the larger part.
                 */
                int mi1 = (lo1 + hi1) >>> 1;
                int key = a1[mi1];
                int mi2 = hi2;

                /*
                 * Divide the smaller part.
                 */
                for (int loo = lo2; loo < mi2; ) {
                    int t = (loo + mi2) >>> 1;

                    if (key > a2[t]) {
                        loo = t + 1;
                    } else {
                        mi2 = t;
                    }
                }

                /*
                 * Reserve space for the left part.
                 */
                int d = mi2 - lo2 + mi1 - lo1;

                /*
                 * Merge the right part in parallel.
                 */
                merger.fork(k + d, mi1, hi1, mi2, hi2);

                /*
                 * Iterate along the left part.
                 */
                hi1 = mi1;
                hi2 = mi2;
            }
        }

        /*
         * Merge small parts sequentially.
         */
        while (lo1 < hi1 && lo2 < hi2) {
            dst[k++] = a1[lo1] < a2[lo2] ? a1[lo1++] : a2[lo2++];
        }
        if (dst != a1 || k < lo1) {
            while (lo1 < hi1) {
                dst[k++] = a1[lo1++];
            }
        }
        if (dst != a2 || k < lo2) {
            while (lo2 < hi2) {
                dst[k++] = a2[lo2++];
            }
        }
    }

    /**
     * Tries to sort the specified range of the array
     * using LSD (The Least Significant Digit) Radix sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryRadixSort(Sorter<int[]> sorter, int[] a, int low, int high) {
        int[] b; int offset = low, size = high - low;

        /*
         * Allocate additional buffer.
         */
        if (sorter != null && (b = sorter.b) != null) {
            offset = sorter.offset;
        } else if ((b = tryAllocate(int[].class, size)) == null) {
            return false;
        }

        int start = low - offset;
        int last = high - offset;

        /*
         * Count the number of all digits.
         */
        int[] count1 = new int[1024];
        int[] count2 = new int[2048];
        int[] count3 = new int[2048];

        for (int i = low; i < high; ++i) {
            ++count1[ a[i]         & 0x3FF];
            ++count2[(a[i] >>> 10) & 0x7FF];
            ++count3[(a[i] >>> 21) ^ 0x400]; // Reverse the sign bit
        }

        /*
         * Detect digits to be processed.
         */
        boolean processDigit1 = processDigit(count1, size, low);
        boolean processDigit2 = processDigit(count2, size, low);
        boolean processDigit3 = processDigit(count3, size, low);

        /*
         * Process the 1-st digit.
         */
        if (processDigit1) {
            for (int i = high; i > low; ) {
                b[--count1[a[--i] & 0x3FF] - offset] = a[i];
            }
        }

        /*
         * Process the 2-nd digit.
         */
        if (processDigit2) {
            if (processDigit1) {
                for (int i = last; i > start; ) {
                    a[--count2[(b[--i] >>> 10) & 0x7FF]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count2[(a[--i] >>> 10) & 0x7FF] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 3-rd digit.
         */
        if (processDigit3) {
            if (processDigit1 ^ processDigit2) {
                for (int i = last; i > start; ) {
                    a[--count3[(b[--i] >>> 21) ^ 0x400]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count3[(a[--i] >>> 21) ^ 0x400] - offset] = a[i];
                }
            }
        }

        /*
         * Copy the buffer to original array, if we process ood number of digits.
         */
        if (processDigit1 ^ processDigit2 ^ processDigit3) {
            System.arraycopy(b, low - offset, a, low, size);
        }
        return true;
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
         * Check if we can skip given digit.
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
     * sort and/or Dual-Pivot Quicksort.
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
            new Sorter<>(a, parallelism, low, high - low, 0).invoke();
        } else {
            sort(null, a, 0, low, high);
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter parallel context
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
            if (size < MAX_MIXED_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                mixedInsertionSort(a, low, high);
                return;
            }

            /*
             * Invoke insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE) {
                insertionSort(a, low, high);
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
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 8) + 1;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int end = high - 1;
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;
            long a3 = a[e3];

            boolean isRandom =
                a[e1] > a[e2] || a[e2] > a3 || a3 > a[e4] || a[e4] > a[e5];

            /*
             * Sort these elements in place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *    1  ------------o-----o------------
             *                   |     |
             *    2  ------o-----|-----o-----o------
             *             |     |           |
             *    4  ------|-----o-----o-----o------
             *             |           |
             *    5  ------o-----------o------------
             */
            if (a[e2] > a[e5]) { long t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e4]) { long t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e1] > a[e2]) { long t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e4] > a[e5]) { long t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e2] > a[e4]) { long t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a3 < a[e2]) {
                if (a3 < a[e1]) {
                    a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = a3;
                } else {
                    a[e3] = a[e2]; a[e2] = a3;
                }
            } else if (a3 > a[e4]) {
                if (a3 > a[e5]) {
                    a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = a3;
                } else {
                    a[e3] = a[e4]; a[e4] = a3;
                }
            }

            /*
             * Try Radix sort on large fully random data,
             * taking into account parallel context.
             */
            isRandom &= a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5];

            if (size > MIN_RADIX_SORT_SIZE && isRandom && (sorter == null || bits > 0)
                    && tryRadixSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Switch to heap sort, if execution time is quadratic.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            // Pointers
            int lower = low; // The index of the last element of the left part
            int upper = end; // The index of the first element of the right part

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                long pivot1 = a[e1];
                long pivot2 = a[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivot1);
                while (a[--upper] > pivot2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *     left part                    central part          right part
                 * +------------------------------------------------------------------+
                 * |   < pivot1   |    ?    |  pivot1 <= && <= pivot2  |   > pivot2   |
                 * +------------------------------------------------------------------+
                 *               ^         ^                            ^
                 *               |         |                            |
                 *             lower       k                          upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot1
                 *     all in (k, upper)   in [pivot1, pivot2]
                 *     all in [upper, end)  >  pivot2
                 */
                for (int unused = --lower, k = ++upper; --k > lower; ) {
                    long ak = a[k];

                    if (ak < pivot1) { // Move a[k] to the left side
                        while (a[++lower] < pivot1) {
                            if (lower == k) {
                                break;
                            }
                        }
                        if (a[lower] > pivot2) {
                            a[k] = a[--upper];
                            a[upper] = a[lower];
                        } else {
                            a[k] = a[lower];
                        }
                        a[lower] = ak;
                    } else if (ak > pivot2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower]; a[lower] = pivot1;
                a[end] = a[upper]; a[upper] = pivot2;

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, lower + 1, upper);
                    sorter.fork(bits | 1, upper + 1, high);
                } else {
                    sort(sorter, a, bits | 1, lower + 1, upper);
                    sort(sorter, a, bits | 1, upper + 1, high);
                }

            } else { // Partitioning with one pivot

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                long pivot = a[e3];

                /*
                 * The first element to be sorted is moved to the
                 * location formerly occupied by the pivot. After
                 * completion of partitioning the pivot is swapped
                 * back into its final position, and excluded from
                 * the next subsequent sorting.
                 */
                a[e3] = a[lower];

                /*
                 * Dutch National Flag partitioning
                 *
                 *    left part                central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot
                 *     all in (k, upper)   ==  pivot
                 *     all in [upper, end]  >  pivot
                 */
                for (int k = ++upper; --k > lower; ) {
                    long ak = a[k];

                    if (ak != pivot) {
                        a[k] = pivot;

                        if (ak < pivot) { // Move a[k] to the left side
                            while (a[++lower] < pivot);

                            if (a[lower] > pivot) {
                                a[--upper] = a[lower];
                            }
                            a[lower] = ak;
                        } else { // ak > pivot - Move a[k] to the right side
                            a[--upper] = ak;
                        }
                    }
                }

                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower]; a[lower] = pivot;

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, upper, high);
                } else {
                    sort(sorter, a, bits | 1, upper, high);
                }
            }
            high = lower; // Iterate along the left part
        }
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.
     *
     * Mixed insertion sort is combination of pin insertion sort,
     * simple insertion sort and pair insertion sort.
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
         * Split part for pin and pair insertion sorts.
         */
        int end = high - 3 * ((high - low) >> 3 << 1);

        /*
         * Invoke simple insertion sort on small part.
         */
        if (end == high) {
            for (int i; ++low < high; ) {
                long ai = a[i = low];

                while (ai < a[i - 1]) {
                    a[i] = a[--i];
                }
                a[i] = ai;
            }
            return;
        }

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

                a[i ] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter parallel context
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
            } else { // Identify constant sequence
                for (long ak = a[k]; ++k < high && ak == a[k]; );

                if (k < high) {
                    continue;
                }
            }

            /*
             * Check if the runs are too
             * long to continue scanning.
             */
            if (count > 6 && k - low < count * MIN_RUN_SIZE) {
                return false;
            }

            /*
             * Process the run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }

                run = new int[((high - low) >> 9) & 0x1FF | 0x3F];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

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
            mergeRuns(a, b, offset, 1, sorter != null, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param a the source array
     * @param b the temporary buffer used in merging
     * @param offset the start index in the source, inclusive
     * @param aim specifies merging: to source ( > 0), buffer ( < 0) or any ( == 0)
     * @param parallel indicates whether merging is performed in parallel
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     * @return the destination where runs are merged
     */
    private static long[] mergeRuns(long[] a, long[] b, int offset,
            int aim, boolean parallel, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (aim >= 0) {
                return a;
            }
            System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            return b;
        }

        /*
         * Split into approximately equal parts.
         */
        int mi = lo, rmi = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= rmi);

        /*
         * Merge runs of each part.
         */
        long[] a1 = mergeRuns(a, b, offset, -aim, parallel, run, lo, mi);
        long[] a2 = mergeRuns(a, b, offset,    0, parallel, run, mi, hi);
        long[] dst = a1 == a ? b : a;

        int k   = a1 == a ? run[lo] - offset : run[lo];
        int lo1 = a1 == b ? run[lo] - offset : run[lo];
        int hi1 = a1 == b ? run[mi] - offset : run[mi];
        int lo2 = a2 == b ? run[mi] - offset : run[mi];
        int hi2 = a2 == b ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && parallel) {
            new Merger<>(null, dst, k, a1, lo1, hi1, a2, lo2, hi2).invoke();
        } else {
            mergeParts(null, dst, k, a1, lo1, hi1, a2, lo2, hi2);
        }
        return dst;
    }

    /**
     * Merges the sorted parts.
     *
     * @param merger parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param a1 the first part
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param a2 the second part
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<long[]> merger, long[] dst, int k,
            long[] a1, int lo1, int hi1, long[] a2, int lo2, int hi2) {

        if (merger != null && a1 == a2) {

            while (true) {

                /*
                 * The first part must be larger.
                 */
                if (hi1 - lo1 < hi2 - lo2) {
                    int lo = lo1; lo1 = lo2; lo2 = lo;
                    int hi = hi1; hi1 = hi2; hi2 = hi;
                }

                /*
                 * Small parts will be merged sequentially.
                 */
                if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                    break;
                }

                /*
                 * Find the median of the larger part.
                 */
                int mi1 = (lo1 + hi1) >>> 1;
                long key = a1[mi1];
                int mi2 = hi2;

                /*
                 * Divide the smaller part.
                 */
                for (int loo = lo2; loo < mi2; ) {
                    int t = (loo + mi2) >>> 1;

                    if (key > a2[t]) {
                        loo = t + 1;
                    } else {
                        mi2 = t;
                    }
                }

                /*
                 * Reserve space for the left part.
                 */
                int d = mi2 - lo2 + mi1 - lo1;

                /*
                 * Merge the right part in parallel.
                 */
                merger.fork(k + d, mi1, hi1, mi2, hi2);

                /*
                 * Iterate along the left part.
                 */
                hi1 = mi1;
                hi2 = mi2;
            }
        }

        /*
         * Merge small parts sequentially.
         */
        while (lo1 < hi1 && lo2 < hi2) {
            dst[k++] = a1[lo1] < a2[lo2] ? a1[lo1++] : a2[lo2++];
        }
        if (dst != a1 || k < lo1) {
            while (lo1 < hi1) {
                dst[k++] = a1[lo1++];
            }
        }
        if (dst != a2 || k < lo2) {
            while (lo2 < hi2) {
                dst[k++] = a2[lo2++];
            }
        }
    }

    /**
     * Tries to sort the specified range of the array
     * using LSD (The Least Significant Digit) Radix sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryRadixSort(Sorter<long[]> sorter, long[] a, int low, int high) {
        long[] b; int offset = low, size = high - low;

        /*
         * Allocate additional buffer.
         */
        if (sorter != null && (b = sorter.b) != null) {
            offset = sorter.offset;
        } else if ((b = tryAllocate(long[].class, size)) == null) {
            return false;
        }

        int start = low - offset;
        int last = high - offset;

        /*
         * Count the number of all digits.
         */
        int[] count1 = new int[1024];
        int[] count2 = new int[2048];
        int[] count3 = new int[2048];
        int[] count4 = new int[2048];
        int[] count5 = new int[2048];
        int[] count6 = new int[1024];

        for (int i = low; i < high; ++i) {
            ++count1[(int)  (a[i]         & 0x3FF)];
            ++count2[(int) ((a[i] >>> 10) & 0x7FF)];
            ++count3[(int) ((a[i] >>> 21) & 0x7FF)];
            ++count4[(int) ((a[i] >>> 32) & 0x7FF)];
            ++count5[(int) ((a[i] >>> 43) & 0x7FF)];
            ++count6[(int) ((a[i] >>> 54) ^ 0x200)]; // Reverse the sign bit
        }

        /*
         * Detect digits to be processed.
         */
        boolean processDigit1 = processDigit(count1, size, low);
        boolean processDigit2 = processDigit(count2, size, low);
        boolean processDigit3 = processDigit(count3, size, low);
        boolean processDigit4 = processDigit(count4, size, low);
        boolean processDigit5 = processDigit(count5, size, low);
        boolean processDigit6 = processDigit(count6, size, low);

        /*
         * Process the 1-st digit.
         */
        if (processDigit1) {
            for (int i = high; i > low; ) {
                b[--count1[(int) (a[--i] & 0x3FF)] - offset] = a[i];
            }
        }

        /*
         * Process the 2-nd digit.
         */
        if (processDigit2) {
            if (processDigit1) {
                for (int i = last; i > start; ) {
                    a[--count2[(int) ((b[--i] >>> 10) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count2[(int) ((a[--i] >>> 10) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 3-rd digit.
         */
        if (processDigit3) {
            if (processDigit1 ^ processDigit2) {
                for (int i = last; i > start; ) {
                    a[--count3[(int) ((b[--i] >>> 21) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count3[(int) ((a[--i] >>> 21) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 4-th digit.
         */
        if (processDigit4) {
            if (processDigit1 ^ processDigit2 ^ processDigit3) {
                for (int i = last; i > start; ) {
                    a[--count4[(int) ((b[--i] >>> 32) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count4[(int) ((a[--i] >>> 32) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 5-th digit.
         */
        if (processDigit5) {
            if (processDigit1 ^ processDigit2 ^ processDigit3 ^ processDigit4) {
                for (int i = last; i > start; ) {
                    a[--count5[(int) ((b[--i] >>> 43) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count5[(int) ((a[--i] >>> 43) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 6-th digit.
         */
        if (processDigit6) {
            if (processDigit1 ^ processDigit2 ^ processDigit3 ^ processDigit4 ^ processDigit5) {
                for (int i = last; i > start; ) {
                    a[--count6[(int) ((b[--i] >>> 54) ^ 0x200)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count6[(int) ((a[--i] >>> 54) ^ 0x200)] - offset] = a[i];
                }
            }
        }

        /*
         * Copy the buffer to original array, if we process ood number of digits.
         */
        if (processDigit1 ^ processDigit2 ^ processDigit3 ^ processDigit4 ^ processDigit5 ^ processDigit6) {
            System.arraycopy(b, low - offset, a, low, size);
        }
        return true;
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
     * Sorts the specified range of the array using
     * counting sort or insertion sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(byte[] a, int low, int high) {
        if (high - low > MIN_BYTE_COUNTING_SORT_SIZE) {
            countingSort(a, low, high);
        } else {
            insertionSort(a, low, high);
        }
    }

    /**
     * The number of distinct byte values.
     */
    private static final int NUM_BYTE_VALUES = 1 << 8;

    /**
     * Sorts the specified range of the array using counting sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void countingSort(byte[] a, int low, int high) {
        int[] count = new int[NUM_BYTE_VALUES];

        /*
         * Compute the histogram.
         */
        for (int i = high; i > low; ++count[a[--i] & 0xFF]);

        /*
         * Put values on their final positions.
         */
        for (int i = Byte.MAX_VALUE + 1; high > low; ) {
            while (count[--i & 0xFF] == 0);

            int num = count[i & 0xFF];

            do {
                a[--high] = (byte) i;
            } while (--num > 0);
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

                a[i ] = ai;
            }
        }
    }

// #[char]

    /**
     * Sorts the specified range of the array using
     * counting sort or Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(char[] a, int low, int high) {
        if (high - low > MIN_CHAR_COUNTING_SORT_SIZE) {
            countingSort(a, low, high);
        } else {
            sort(a, 0, low, high);
        }
    }

    /**
     * The number of distinct char values.
     */
    private static final int NUM_CHAR_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array using counting sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void countingSort(char[] a, int low, int high) {
        int[] count = new int[NUM_CHAR_VALUES];

        /*
         * Compute the histogram.
         */
        for (int i = high; i > low; ++count[a[--i]]);

        /*
         * Put values on their final positions.
         */
        if (high - low > NUM_CHAR_VALUES) {
            for (int i = NUM_CHAR_VALUES; i > 0; ) {
                for (low = high - count[--i]; high > low; ) {
                    a[--high] = (char) i;
                }
            }
        } else {
            for (int i = NUM_CHAR_VALUES; high > low; ) {
                while (count[--i] == 0);

                int num = count[i];

                do {
                    a[--high] = (char) i;
                } while (--num > 0);
            }
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
    static void sort(char[] a, int bits, int low, int high) {
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
             * Switch to counting sort, if execution time is quadratic.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                countingSort(a, low, high);
                return;
            }

            /*
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 8) + 1;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int end = high - 1;
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;
            char a3 = a[e3];

            /*
             * Sort these elements in place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *    1  ------------o-----o------------
             *                   |     |
             *    2  ------o-----|-----o-----o------
             *             |     |           |
             *    4  ------|-----o-----o-----o------
             *             |           |
             *    5  ------o-----------o------------
             */
            if (a[e2] > a[e5]) { char t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e4]) { char t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e1] > a[e2]) { char t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e4] > a[e5]) { char t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e2] > a[e4]) { char t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a3 < a[e2]) {
                if (a3 < a[e1]) {
                    a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = a3;
                } else {
                    a[e3] = a[e2]; a[e2] = a3;
                }
            } else if (a3 > a[e4]) {
                if (a3 > a[e5]) {
                    a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = a3;
                } else {
                    a[e3] = a[e4]; a[e4] = a3;
                }
            }

            // Pointers
            int lower = low; // The index of the last element of the left part
            int upper = end; // The index of the first element of the right part

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                char pivot1 = a[e1];
                char pivot2 = a[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivot1);
                while (a[--upper] > pivot2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *     left part                    central part          right part
                 * +------------------------------------------------------------------+
                 * |   < pivot1   |    ?    |  pivot1 <= && <= pivot2  |   > pivot2   |
                 * +------------------------------------------------------------------+
                 *               ^         ^                            ^
                 *               |         |                            |
                 *             lower       k                          upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot1
                 *     all in (k, upper)   in [pivot1, pivot2]
                 *     all in [upper, end)  >  pivot2
                 */
                for (int unused = --lower, k = ++upper; --k > lower; ) {
                    char ak = a[k];

                    if (ak < pivot1) { // Move a[k] to the left side
                        while (a[++lower] < pivot1) {
                            if (lower == k) {
                                break;
                            }
                        }
                        if (a[lower] > pivot2) {
                            a[k] = a[--upper];
                            a[upper] = a[lower];
                        } else {
                            a[k] = a[lower];
                        }
                        a[lower] = ak;
                    } else if (ak > pivot2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower]; a[lower] = pivot1;
                a[end] = a[upper]; a[upper] = pivot2;

                /*
                 * Sort non-left parts recursively,
                 * excluding known pivots.
                 */
                sort(a, bits | 1, lower + 1, upper);
                sort(a, bits | 1, upper + 1, high);

            } else { // Partitioning with one pivot

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                char pivot = a[e3];

                /*
                 * The first element to be sorted is moved to the
                 * location formerly occupied by the pivot. After
                 * completion of partitioning the pivot is swapped
                 * back into its final position, and excluded from
                 * the next subsequent sorting.
                 */
                a[e3] = a[lower];

                /*
                 * Dutch National Flag partitioning
                 *
                 *    left part                central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot
                 *     all in (k, upper)   ==  pivot
                 *     all in [upper, end]  >  pivot
                 */
                for (int k = ++upper; --k > lower; ) {
                    char ak = a[k];

                    if (ak != pivot) {
                        a[k] = pivot;

                        if (ak < pivot) { // Move a[k] to the left side
                            while (a[++lower] < pivot);

                            if (a[lower] > pivot) {
                                a[--upper] = a[lower];
                            }
                            a[lower] = ak;
                        } else { // ak > pivot - Move a[k] to the right side
                            a[--upper] = ak;
                        }
                    }
                }

                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower]; a[lower] = pivot;

                /*
                 * Sort the right part, excluding known pivot.
                 * All elements from the central part are
                 * equal and therefore already sorted.
                 */
                sort(a, bits | 1, upper, high);
            }
            high = lower; // Iterate along the left part
        }
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

                a[i ] = ai;
            }
        }
    }

// #[short]

    /**
     * Sorts the specified range of the array using
     * counting sort or Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(short[] a, int low, int high) {
        if (high - low > MIN_SHORT_COUNTING_SORT_SIZE) {
            countingSort(a, low, high);
        } else {
            sort(a, 0, low, high);
        }
    }

    /**
     * The number of distinct short values.
     */
    private static final int NUM_SHORT_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array using counting sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void countingSort(short[] a, int low, int high) {
        int[] count = new int[NUM_SHORT_VALUES];

        /*
         * Compute the histogram.
         */
        for (int i = high; i > low; ++count[a[--i] & 0xFFFF]);

        /*
         * Place values on their final positions.
         */
        if (high - low > NUM_SHORT_VALUES) {
            for (int i = Short.MAX_VALUE; i >= Short.MIN_VALUE; --i) {
                for (low = high - count[i & 0xFFFF]; high > low;
                    a[--high] = (short) i
                );
            }
        } else {
            for (int i = Short.MAX_VALUE + 1; high > low; ) {
                while (count[--i & 0xFFFF] == 0);

                int num = count[i & 0xFFFF];

                do {
                    a[--high] = (short) i;
                } while (--num > 0);
            }
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
    static void sort(short[] a, int bits, int low, int high) {
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
             * Switch to counting sort, if execution time is quadratic.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                countingSort(a, low, high);
                return;
            }

            /*
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 8) + 1;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int end = high - 1;
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;
            short a3 = a[e3];

            /*
             * Sort these elements in place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *    1  ------------o-----o------------
             *                   |     |
             *    2  ------o-----|-----o-----o------
             *             |     |           |
             *    4  ------|-----o-----o-----o------
             *             |           |
             *    5  ------o-----------o------------
             */
            if (a[e2] > a[e5]) { short t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e4]) { short t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e1] > a[e2]) { short t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e4] > a[e5]) { short t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e2] > a[e4]) { short t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a3 < a[e2]) {
                if (a3 < a[e1]) {
                    a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = a3;
                } else {
                    a[e3] = a[e2]; a[e2] = a3;
                }
            } else if (a3 > a[e4]) {
                if (a3 > a[e5]) {
                    a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = a3;
                } else {
                    a[e3] = a[e4]; a[e4] = a3;
                }
            }

            // Pointers
            int lower = low; // The index of the last element of the left part
            int upper = end; // The index of the first element of the right part

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                short pivot1 = a[e1];
                short pivot2 = a[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivot1);
                while (a[--upper] > pivot2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *     left part                    central part          right part
                 * +------------------------------------------------------------------+
                 * |   < pivot1   |    ?    |  pivot1 <= && <= pivot2  |   > pivot2   |
                 * +------------------------------------------------------------------+
                 *               ^         ^                            ^
                 *               |         |                            |
                 *             lower       k                          upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot1
                 *     all in (k, upper)   in [pivot1, pivot2]
                 *     all in [upper, end)  >  pivot2
                 */
                for (int unused = --lower, k = ++upper; --k > lower; ) {
                    short ak = a[k];

                    if (ak < pivot1) { // Move a[k] to the left side
                        while (a[++lower] < pivot1) {
                            if (lower == k) {
                                break;
                            }
                        }
                        if (a[lower] > pivot2) {
                            a[k] = a[--upper];
                            a[upper] = a[lower];
                        } else {
                            a[k] = a[lower];
                        }
                        a[lower] = ak;
                    } else if (ak > pivot2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower]; a[lower] = pivot1;
                a[end] = a[upper]; a[upper] = pivot2;

                /*
                 * Sort non-left parts recursively,
                 * excluding known pivots.
                 */
                sort(a, bits | 1, lower + 1, upper);
                sort(a, bits | 1, upper + 1, high);

            } else { // Partitioning with one pivot

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                short pivot = a[e3];

                /*
                 * The first element to be sorted is moved to the
                 * location formerly occupied by the pivot. After
                 * completion of partitioning the pivot is swapped
                 * back into its final position, and excluded from
                 * the next subsequent sorting.
                 */
                a[e3] = a[lower];

                /*
                 * Dutch National Flag partitioning
                 *
                 *    left part                central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot
                 *     all in (k, upper)   ==  pivot
                 *     all in [upper, end]  >  pivot
                 */
                for (int k = ++upper; --k > lower; ) {
                    short ak = a[k];

                    if (ak != pivot) {
                        a[k] = pivot;

                        if (ak < pivot) { // Move a[k] to the left side
                            while (a[++lower] < pivot);

                            if (a[lower] > pivot) {
                                a[--upper] = a[lower];
                            }
                            a[lower] = ak;
                        } else { // ak > pivot - Move a[k] to the right side
                            a[--upper] = ak;
                        }
                    }
                }

                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower]; a[lower] = pivot;

                /*
                 * Sort the right part, excluding known pivot.
                 * All elements from the central part are
                 * equal and therefore already sorted.
                 */
                sort(a, bits | 1, upper, high);
            }
            high = lower; // Iterate along the left part
        }
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

                a[i ] = ai;
            }
        }
    }

// #[float]

    /**
     * Sorts the specified range of the array using parallel merge
     * sort and/or Dual-Pivot Quicksort.
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
         * Phase 1. Count the number of negative zero -0.0f,
         * turn them into positive zero, and move all NaNs
         * to the end of the array.
         */
        int numNegativeZero = 0;

        for (int k = high; k > low; ) {
            float ak = a[--k];

            if (ak == 0.0f && Float.floatToRawIntBits(ak) < 0) { // ak is -0.0f
                numNegativeZero += 1;
                a[k] = 0.0f;
            } else if (ak != ak) { // ak is NaN
                a[k] = a[--high];
                a[high] = ak;
            }
        }

        /*
         * Phase 2. Sort everything except NaNs,
         * which are already in place.
         */
        if (parallelism > 1 && high - low > MIN_PARALLEL_SORT_SIZE) {
            new Sorter<>(a, parallelism, low, high - low, 0).invoke();
        } else {
            sort(null, a, 0, low, high);
        }

        /*
         * Phase 3. Turn positive zero 0.0f
         * back into negative zero -0.0f.
         */
        if (++numNegativeZero == 1) {
            return;
        }

        /*
         * Find the position one less than
         * the index of the first zero.
         */
        while (low <= high) {
            int middle = (low + high) >>> 1;

            if (a[middle] < 0) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        /*
         * Replace the required number of 0.0f by -0.0f.
         */
        while (--numNegativeZero > 0) {
            a[++high] = -0.0f;
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter parallel context
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
            if (size < MAX_MIXED_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                mixedInsertionSort(a, low, high);
                return;
            }

            /*
             * Invoke insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE) {
                insertionSort(a, low, high);
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
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 8) + 1;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int end = high - 1;
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;
            float a3 = a[e3];

            boolean isRandom =
                a[e1] > a[e2] || a[e2] > a3 || a3 > a[e4] || a[e4] > a[e5];

            /*
             * Sort these elements in place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *    1  ------------o-----o------------
             *                   |     |
             *    2  ------o-----|-----o-----o------
             *             |     |           |
             *    4  ------|-----o-----o-----o------
             *             |           |
             *    5  ------o-----------o------------
             */
            if (a[e2] > a[e5]) { float t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e4]) { float t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e1] > a[e2]) { float t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e4] > a[e5]) { float t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e2] > a[e4]) { float t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a3 < a[e2]) {
                if (a3 < a[e1]) {
                    a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = a3;
                } else {
                    a[e3] = a[e2]; a[e2] = a3;
                }
            } else if (a3 > a[e4]) {
                if (a3 > a[e5]) {
                    a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = a3;
                } else {
                    a[e3] = a[e4]; a[e4] = a3;
                }
            }

            /*
             * Try Radix sort on large fully random data,
             * taking into account parallel context.
             */
            isRandom &= a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5];

            if (size > MIN_RADIX_SORT_SIZE && isRandom && (sorter == null || bits > 0)
                    && tryRadixSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Switch to heap sort, if execution time is quadratic.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            // Pointers
            int lower = low; // The index of the last element of the left part
            int upper = end; // The index of the first element of the right part

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                float pivot1 = a[e1];
                float pivot2 = a[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivot1);
                while (a[--upper] > pivot2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *     left part                    central part          right part
                 * +------------------------------------------------------------------+
                 * |   < pivot1   |    ?    |  pivot1 <= && <= pivot2  |   > pivot2   |
                 * +------------------------------------------------------------------+
                 *               ^         ^                            ^
                 *               |         |                            |
                 *             lower       k                          upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot1
                 *     all in (k, upper)   in [pivot1, pivot2]
                 *     all in [upper, end)  >  pivot2
                 */
                for (int unused = --lower, k = ++upper; --k > lower; ) {
                    float ak = a[k];

                    if (ak < pivot1) { // Move a[k] to the left side
                        while (a[++lower] < pivot1) {
                            if (lower == k) {
                                break;
                            }
                        }
                        if (a[lower] > pivot2) {
                            a[k] = a[--upper];
                            a[upper] = a[lower];
                        } else {
                            a[k] = a[lower];
                        }
                        a[lower] = ak;
                    } else if (ak > pivot2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower]; a[lower] = pivot1;
                a[end] = a[upper]; a[upper] = pivot2;

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, lower + 1, upper);
                    sorter.fork(bits | 1, upper + 1, high);
                } else {
                    sort(sorter, a, bits | 1, lower + 1, upper);
                    sort(sorter, a, bits | 1, upper + 1, high);
                }

            } else { // Partitioning with one pivot

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                float pivot = a[e3];

                /*
                 * The first element to be sorted is moved to the
                 * location formerly occupied by the pivot. After
                 * completion of partitioning the pivot is swapped
                 * back into its final position, and excluded from
                 * the next subsequent sorting.
                 */
                a[e3] = a[lower];

                /*
                 * Dutch National Flag partitioning
                 *
                 *    left part                central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot
                 *     all in (k, upper)   ==  pivot
                 *     all in [upper, end]  >  pivot
                 */
                for (int k = ++upper; --k > lower; ) {
                    float ak = a[k];

                    if (ak != pivot) {
                        a[k] = pivot;

                        if (ak < pivot) { // Move a[k] to the left side
                            while (a[++lower] < pivot);

                            if (a[lower] > pivot) {
                                a[--upper] = a[lower];
                            }
                            a[lower] = ak;
                        } else { // ak > pivot - Move a[k] to the right side
                            a[--upper] = ak;
                        }
                    }
                }

                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower]; a[lower] = pivot;

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, upper, high);
                } else {
                    sort(sorter, a, bits | 1, upper, high);
                }
            }
            high = lower; // Iterate along the left part
        }
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.
     *
     * Mixed insertion sort is combination of pin insertion sort,
     * simple insertion sort and pair insertion sort.
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
         * Split part for pin and pair insertion sorts.
         */
        int end = high - 3 * ((high - low) >> 3 << 1);

        /*
         * Invoke simple insertion sort on small part.
         */
        if (end == high) {
            for (int i; ++low < high; ) {
                float ai = a[i = low];

                while (ai < a[i - 1]) {
                    a[i] = a[--i];
                }
                a[i] = ai;
            }
            return;
        }

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

                a[i ] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter parallel context
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
            } else { // Identify constant sequence
                for (float ak = a[k]; ++k < high && ak == a[k]; );

                if (k < high) {
                    continue;
                }
            }

            /*
             * Check if the runs are too
             * long to continue scanning.
             */
            if (count > 6 && k - low < count * MIN_RUN_SIZE) {
                return false;
            }

            /*
             * Process the run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }

                run = new int[((high - low) >> 9) & 0x1FF | 0x3F];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

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
            mergeRuns(a, b, offset, 1, sorter != null, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param a the source array
     * @param b the temporary buffer used in merging
     * @param offset the start index in the source, inclusive
     * @param aim specifies merging: to source ( > 0), buffer ( < 0) or any ( == 0)
     * @param parallel indicates whether merging is performed in parallel
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     * @return the destination where runs are merged
     */
    private static float[] mergeRuns(float[] a, float[] b, int offset,
            int aim, boolean parallel, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (aim >= 0) {
                return a;
            }
            System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            return b;
        }

        /*
         * Split into approximately equal parts.
         */
        int mi = lo, rmi = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= rmi);

        /*
         * Merge runs of each part.
         */
        float[] a1 = mergeRuns(a, b, offset, -aim, parallel, run, lo, mi);
        float[] a2 = mergeRuns(a, b, offset,    0, parallel, run, mi, hi);
        float[] dst = a1 == a ? b : a;

        int k   = a1 == a ? run[lo] - offset : run[lo];
        int lo1 = a1 == b ? run[lo] - offset : run[lo];
        int hi1 = a1 == b ? run[mi] - offset : run[mi];
        int lo2 = a2 == b ? run[mi] - offset : run[mi];
        int hi2 = a2 == b ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && parallel) {
            new Merger<>(null, dst, k, a1, lo1, hi1, a2, lo2, hi2).invoke();
        } else {
            mergeParts(null, dst, k, a1, lo1, hi1, a2, lo2, hi2);
        }
        return dst;
    }

    /**
     * Merges the sorted parts.
     *
     * @param merger parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param a1 the first part
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param a2 the second part
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<float[]> merger, float[] dst, int k,
            float[] a1, int lo1, int hi1, float[] a2, int lo2, int hi2) {

        if (merger != null && a1 == a2) {

            while (true) {

                /*
                 * The first part must be larger.
                 */
                if (hi1 - lo1 < hi2 - lo2) {
                    int lo = lo1; lo1 = lo2; lo2 = lo;
                    int hi = hi1; hi1 = hi2; hi2 = hi;
                }

                /*
                 * Small parts will be merged sequentially.
                 */
                if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                    break;
                }

                /*
                 * Find the median of the larger part.
                 */
                int mi1 = (lo1 + hi1) >>> 1;
                float key = a1[mi1];
                int mi2 = hi2;

                /*
                 * Divide the smaller part.
                 */
                for (int loo = lo2; loo < mi2; ) {
                    int t = (loo + mi2) >>> 1;

                    if (key > a2[t]) {
                        loo = t + 1;
                    } else {
                        mi2 = t;
                    }
                }

                /*
                 * Reserve space for the left part.
                 */
                int d = mi2 - lo2 + mi1 - lo1;

                /*
                 * Merge the right part in parallel.
                 */
                merger.fork(k + d, mi1, hi1, mi2, hi2);

                /*
                 * Iterate along the left part.
                 */
                hi1 = mi1;
                hi2 = mi2;
            }
        }

        /*
         * Merge small parts sequentially.
         */
        while (lo1 < hi1 && lo2 < hi2) {
            dst[k++] = a1[lo1] < a2[lo2] ? a1[lo1++] : a2[lo2++];
        }
        if (dst != a1 || k < lo1) {
            while (lo1 < hi1) {
                dst[k++] = a1[lo1++];
            }
        }
        if (dst != a2 || k < lo2) {
            while (lo2 < hi2) {
                dst[k++] = a2[lo2++];
            }
        }
    }

    /**
     * Tries to sort the specified range of the array
     * using LSD (The Least Significant Digit) Radix sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryRadixSort(Sorter<float[]> sorter, float[] a, int low, int high) {
        float[] b; int offset = low, size = high - low;

        /*
         * Allocate additional buffer.
         */
        if (sorter != null && (b = sorter.b) != null) {
            offset = sorter.offset;
        } else if ((b = tryAllocate(float[].class, size)) == null) {
            return false;
        }

        int start = low - offset;
        int last = high - offset;

        /*
         * Count the number of all digits.
         */
        int[] count1 = new int[1024];
        int[] count2 = new int[2048];
        int[] count3 = new int[2048];

        for (int i = low; i < high; ++i) {
            ++count1[ fti(a[i])         & 0x3FF];
            ++count2[(fti(a[i]) >>> 10) & 0x7FF];
            ++count3[(fti(a[i]) >>> 21) & 0x7FF];
        }

        /*
         * Detect digits to be processed.
         */
        boolean processDigit1 = processDigit(count1, size, low);
        boolean processDigit2 = processDigit(count2, size, low);
        boolean processDigit3 = processDigit(count3, size, low);

        /*
         * Process the 1-st digit.
         */
        if (processDigit1) {
            for (int i = high; i > low; ) {
                b[--count1[fti(a[--i]) & 0x3FF] - offset] = a[i];
            }
        }

        /*
         * Process the 2-nd digit.
         */
        if (processDigit2) {
            if (processDigit1) {
                for (int i = last; i > start; ) {
                    a[--count2[(fti(b[--i]) >>> 10) & 0x7FF]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count2[(fti(a[--i]) >>> 10) & 0x7FF] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 3-rd digit.
         */
        if (processDigit3) {
            if (processDigit1 ^ processDigit2) {
                for (int i = last; i > start; ) {
                    a[--count3[(fti(b[--i]) >>> 21) & 0x7FF]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count3[(fti(a[--i]) >>> 21) & 0x7FF] - offset] = a[i];
                }
            }
        }

        /*
         * Copy the buffer to original array, if we process ood number of digits.
         */
        if (processDigit1 ^ processDigit2 ^ processDigit3) {
            System.arraycopy(b, low - offset, a, low, size);
        }
        return true;
    }

    /**
     * Returns masked bits that represent the float value.
     *
     * @param f the given value
     * @return masked bits
     */
    private static int fti(float f) {
        int x = Float.floatToRawIntBits(f);
        return x ^ ((x >> 31) | 0x80000000);
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
     * Sorts the specified range of the array using parallel merge
     * sort and/or Dual-Pivot Quicksort.
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
         * Phase 1. Count the number of negative zero -0.0d,
         * turn them into positive zero, and move all NaNs
         * to the end of the array.
         */
        int numNegativeZero = 0;

        for (int k = high; k > low; ) {
            double ak = a[--k];

            if (ak == 0.0d && Double.doubleToRawLongBits(ak) < 0) { // ak is -0.0d
                numNegativeZero += 1;
                a[k] = 0.0d;
            } else if (ak != ak) { // ak is NaN
                a[k] = a[--high];
                a[high] = ak;
            }
        }

        /*
         * Phase 2. Sort everything except NaNs,
         * which are already in place.
         */
        if (parallelism > 1 && high - low > MIN_PARALLEL_SORT_SIZE) {
            new Sorter<>(a, parallelism, low, high - low, 0).invoke();
        } else {
            sort(null, a, 0, low, high);
        }

        /*
         * Phase 3. Turn positive zero 0.0d
         * back into negative zero -0.0d.
         */
        if (++numNegativeZero == 1) {
            return;
        }

        /*
         * Find the position one less than
         * the index of the first zero.
         */
        while (low <= high) {
            int middle = (low + high) >>> 1;

            if (a[middle] < 0) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        /*
         * Replace the required number of 0.0d by -0.0d.
         */
        while (--numNegativeZero > 0) {
            a[++high] = -0.0d;
        }
    }

    /**
     * Sorts the specified range of the array using Dual-Pivot Quicksort.
     *
     * @param sorter parallel context
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
            if (size < MAX_MIXED_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                mixedInsertionSort(a, low, high);
                return;
            }

            /*
             * Invoke insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE) {
                insertionSort(a, low, high);
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
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 2) + (size >> 3) + (size >> 8) + 1;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int end = high - 1;
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;
            double a3 = a[e3];

            boolean isRandom =
                a[e1] > a[e2] || a[e2] > a3 || a3 > a[e4] || a[e4] > a[e5];

            /*
             * Sort these elements in place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *    1  ------------o-----o------------
             *                   |     |
             *    2  ------o-----|-----o-----o------
             *             |     |           |
             *    4  ------|-----o-----o-----o------
             *             |           |
             *    5  ------o-----------o------------
             */
            if (a[e2] > a[e5]) { double t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
            if (a[e1] > a[e4]) { double t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
            if (a[e1] > a[e2]) { double t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
            if (a[e4] > a[e5]) { double t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
            if (a[e2] > a[e4]) { double t = a[e2]; a[e2] = a[e4]; a[e4] = t; }

            /*
             * Insert the third element.
             */
            if (a3 < a[e2]) {
                if (a3 < a[e1]) {
                    a[e3] = a[e2]; a[e2] = a[e1]; a[e1] = a3;
                } else {
                    a[e3] = a[e2]; a[e2] = a3;
                }
            } else if (a3 > a[e4]) {
                if (a3 > a[e5]) {
                    a[e3] = a[e4]; a[e4] = a[e5]; a[e5] = a3;
                } else {
                    a[e3] = a[e4]; a[e4] = a3;
                }
            }

            /*
             * Try Radix sort on large fully random data,
             * taking into account parallel context.
             */
            isRandom &= a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5];

            if (size > MIN_RADIX_SORT_SIZE && isRandom && (sorter == null || bits > 0)
                    && tryRadixSort(sorter, a, low, high)) {
                return;
            }

            /*
             * Switch to heap sort, if execution time is quadratic.
             */
            if ((bits += 2) > MAX_RECURSION_DEPTH) {
                heapSort(a, low, high);
                return;
            }

            // Pointers
            int lower = low; // The index of the last element of the left part
            int upper = end; // The index of the first element of the right part

            /*
             * Partitioning with two pivots on array of fully random elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                double pivot1 = a[e1];
                double pivot2 = a[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivot1);
                while (a[--upper] > pivot2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *     left part                    central part          right part
                 * +------------------------------------------------------------------+
                 * |   < pivot1   |    ?    |  pivot1 <= && <= pivot2  |   > pivot2   |
                 * +------------------------------------------------------------------+
                 *               ^         ^                            ^
                 *               |         |                            |
                 *             lower       k                          upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot1
                 *     all in (k, upper)   in [pivot1, pivot2]
                 *     all in [upper, end)  >  pivot2
                 */
                for (int unused = --lower, k = ++upper; --k > lower; ) {
                    double ak = a[k];

                    if (ak < pivot1) { // Move a[k] to the left side
                        while (a[++lower] < pivot1) {
                            if (lower == k) {
                                break;
                            }
                        }
                        if (a[lower] > pivot2) {
                            a[k] = a[--upper];
                            a[upper] = a[lower];
                        } else {
                            a[k] = a[lower];
                        }
                        a[lower] = ak;
                    } else if (ak > pivot2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower]; a[lower] = pivot1;
                a[end] = a[upper]; a[upper] = pivot2;

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, lower + 1, upper);
                    sorter.fork(bits | 1, upper + 1, high);
                } else {
                    sort(sorter, a, bits | 1, lower + 1, upper);
                    sort(sorter, a, bits | 1, upper + 1, high);
                }

            } else { // Partitioning with one pivot

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                double pivot = a[e3];

                /*
                 * The first element to be sorted is moved to the
                 * location formerly occupied by the pivot. After
                 * completion of partitioning the pivot is swapped
                 * back into its final position, and excluded from
                 * the next subsequent sorting.
                 */
                a[e3] = a[lower];

                /*
                 * Dutch National Flag partitioning
                 *
                 *    left part                central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Pointer k is the last index of ?-part
                 * Pointer lower is the last index of left part
                 * Pointer upper is the first index of right part
                 *
                 * Invariants:
                 *
                 *     all in (low, lower]  <  pivot
                 *     all in (k, upper)   ==  pivot
                 *     all in [upper, end]  >  pivot
                 */
                for (int k = ++upper; --k > lower; ) {
                    double ak = a[k];

                    if (ak != pivot) {
                        a[k] = pivot;

                        if (ak < pivot) { // Move a[k] to the left side
                            while (a[++lower] < pivot);

                            if (a[lower] > pivot) {
                                a[--upper] = a[lower];
                            }
                            a[lower] = ak;
                        } else { // ak > pivot - Move a[k] to the right side
                            a[--upper] = ak;
                        }
                    }
                }

                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower]; a[lower] = pivot;

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                if (size > MIN_PARALLEL_SORT_SIZE && sorter != null) {
                    sorter.fork(bits | 1, upper, high);
                } else {
                    sort(sorter, a, bits | 1, upper, high);
                }
            }
            high = lower; // Iterate along the left part
        }
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.
     *
     * Mixed insertion sort is combination of pin insertion sort,
     * simple insertion sort and pair insertion sort.
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
         * Split part for pin and pair insertion sorts.
         */
        int end = high - 3 * ((high - low) >> 3 << 1);

        /*
         * Invoke simple insertion sort on small part.
         */
        if (end == high) {
            for (int i; ++low < high; ) {
                double ai = a[i = low];

                while (ai < a[i - 1]) {
                    a[i] = a[--i];
                }
                a[i] = ai;
            }
            return;
        }

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

                a[i ] = ai;
            }
        }
    }

    /**
     * Tries to sort the specified range of the array using merging sort.
     *
     * @param sorter parallel context
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
            } else { // Identify constant sequence
                for (double ak = a[k]; ++k < high && ak == a[k]; );

                if (k < high) {
                    continue;
                }
            }

            /*
             * Check if the runs are too
             * long to continue scanning.
             */
            if (count > 6 && k - low < count * MIN_RUN_SIZE) {
                return false;
            }

            /*
             * Process the run.
             */
            if (run == null) {

                if (k == high) {
                    /*
                     * Array is monotonous sequence
                     * and therefore already sorted.
                     */
                    return true;
                }

                run = new int[((high - low) >> 9) & 0x1FF | 0x3F];
                run[0] = low;

            } else if (a[last - 1] > a[last]) { // Start the new run

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
            mergeRuns(a, b, offset, 1, sorter != null, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param a the source array
     * @param b the temporary buffer used in merging
     * @param offset the start index in the source, inclusive
     * @param aim specifies merging: to source ( > 0), buffer ( < 0) or any ( == 0)
     * @param parallel indicates whether merging is performed in parallel
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     * @return the destination where runs are merged
     */
    private static double[] mergeRuns(double[] a, double[] b, int offset,
            int aim, boolean parallel, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (aim >= 0) {
                return a;
            }
            System.arraycopy(a, run[lo], b, run[lo] - offset, run[hi] - run[lo]);
            return b;
        }

        /*
         * Split into approximately equal parts.
         */
        int mi = lo, rmi = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= rmi);

        /*
         * Merge runs of each part.
         */
        double[] a1 = mergeRuns(a, b, offset, -aim, parallel, run, lo, mi);
        double[] a2 = mergeRuns(a, b, offset,    0, parallel, run, mi, hi);
        double[] dst = a1 == a ? b : a;

        int k   = a1 == a ? run[lo] - offset : run[lo];
        int lo1 = a1 == b ? run[lo] - offset : run[lo];
        int hi1 = a1 == b ? run[mi] - offset : run[mi];
        int lo2 = a2 == b ? run[mi] - offset : run[mi];
        int hi2 = a2 == b ? run[hi] - offset : run[hi];

        /*
         * Merge the left and right parts.
         */
        if (hi1 - lo1 > MIN_PARALLEL_SORT_SIZE && parallel) {
            new Merger<>(null, dst, k, a1, lo1, hi1, a2, lo2, hi2).invoke();
        } else {
            mergeParts(null, dst, k, a1, lo1, hi1, a2, lo2, hi2);
        }
        return dst;
    }

    /**
     * Merges the sorted parts.
     *
     * @param merger parallel context
     * @param dst the destination where parts are merged
     * @param k the start index of the destination, inclusive
     * @param a1 the first part
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param a2 the second part
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(Merger<double[]> merger, double[] dst, int k,
            double[] a1, int lo1, int hi1, double[] a2, int lo2, int hi2) {

        if (merger != null && a1 == a2) {

            while (true) {

                /*
                 * The first part must be larger.
                 */
                if (hi1 - lo1 < hi2 - lo2) {
                    int lo = lo1; lo1 = lo2; lo2 = lo;
                    int hi = hi1; hi1 = hi2; hi2 = hi;
                }

                /*
                 * Small parts will be merged sequentially.
                 */
                if (hi1 - lo1 < MIN_PARALLEL_SORT_SIZE) {
                    break;
                }

                /*
                 * Find the median of the larger part.
                 */
                int mi1 = (lo1 + hi1) >>> 1;
                double key = a1[mi1];
                int mi2 = hi2;

                /*
                 * Divide the smaller part.
                 */
                for (int loo = lo2; loo < mi2; ) {
                    int t = (loo + mi2) >>> 1;

                    if (key > a2[t]) {
                        loo = t + 1;
                    } else {
                        mi2 = t;
                    }
                }

                /*
                 * Reserve space for the left part.
                 */
                int d = mi2 - lo2 + mi1 - lo1;

                /*
                 * Merge the right part in parallel.
                 */
                merger.fork(k + d, mi1, hi1, mi2, hi2);

                /*
                 * Iterate along the left part.
                 */
                hi1 = mi1;
                hi2 = mi2;
            }
        }

        /*
         * Merge small parts sequentially.
         */
        while (lo1 < hi1 && lo2 < hi2) {
            dst[k++] = a1[lo1] < a2[lo2] ? a1[lo1++] : a2[lo2++];
        }
        if (dst != a1 || k < lo1) {
            while (lo1 < hi1) {
                dst[k++] = a1[lo1++];
            }
        }
        if (dst != a2 || k < lo2) {
            while (lo2 < hi2) {
                dst[k++] = a2[lo2++];
            }
        }
    }

    /**
     * Tries to sort the specified range of the array
     * using LSD (The Least Significant Digit) Radix sort.
     *
     * @param a the array to be sorted
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     * @return {@code true} if the array is finally sorted, otherwise {@code false}
     */
    static boolean tryRadixSort(Sorter<double[]> sorter, double[] a, int low, int high) {
        double[] b; int offset = low, size = high - low;

        /*
         * Allocate additional buffer.
         */
        if (sorter != null && (b = sorter.b) != null) {
            offset = sorter.offset;
        } else if ((b = tryAllocate(double[].class, size)) == null) {
            return false;
        }

        int start = low - offset;
        int last = high - offset;

        /*
         * Count the number of all digits.
         */
        int[] count1 = new int[1024];
        int[] count2 = new int[2048];
        int[] count3 = new int[2048];
        int[] count4 = new int[2048];
        int[] count5 = new int[2048];
        int[] count6 = new int[1024];

        for (int i = low; i < high; ++i) {
            ++count1[(int)  (dtl(a[i])         & 0x3FF)];
            ++count2[(int) ((dtl(a[i]) >>> 10) & 0x7FF)];
            ++count3[(int) ((dtl(a[i]) >>> 21) & 0x7FF)];
            ++count4[(int) ((dtl(a[i]) >>> 32) & 0x7FF)];
            ++count5[(int) ((dtl(a[i]) >>> 43) & 0x7FF)];
            ++count6[(int) ((dtl(a[i]) >>> 54) & 0x3FF)];
        }

        /*
         * Detect digits to be processed.
         */
        boolean processDigit1 = processDigit(count1, size, low);
        boolean processDigit2 = processDigit(count2, size, low);
        boolean processDigit3 = processDigit(count3, size, low);
        boolean processDigit4 = processDigit(count4, size, low);
        boolean processDigit5 = processDigit(count5, size, low);
        boolean processDigit6 = processDigit(count6, size, low);

        /*
         * Process the 1-st digit.
         */
        if (processDigit1) {
            for (int i = high; i > low; ) {
                b[--count1[(int) (dtl(a[--i]) & 0x3FF)] - offset] = a[i];
            }
        }

        /*
         * Process the 2-nd digit.
         */
        if (processDigit2) {
            if (processDigit1) {
                for (int i = last; i > start; ) {
                    a[--count2[(int) ((dtl(b[--i]) >>> 10) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count2[(int) ((dtl(a[--i]) >>> 10) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 3-rd digit.
         */
        if (processDigit3) {
            if (processDigit1 ^ processDigit2) {
                for (int i = last; i > start; ) {
                    a[--count3[(int) ((dtl(b[--i]) >>> 21) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count3[(int) ((dtl(a[--i]) >>> 21) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 4-th digit.
         */
        if (processDigit4) {
            if (processDigit1 ^ processDigit2 ^ processDigit3) {
                for (int i = last; i > start; ) {
                    a[--count4[(int) ((dtl(b[--i]) >>> 32) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count4[(int) ((dtl(a[--i]) >>> 32) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 5-th digit.
         */
        if (processDigit5) {
            if (processDigit1 ^ processDigit2 ^ processDigit3 ^ processDigit4) {
                for (int i = last; i > start; ) {
                    a[--count5[(int) ((dtl(b[--i]) >>> 43) & 0x7FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count5[(int) ((dtl(a[--i]) >>> 43) & 0x7FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Process the 6-th digit.
         */
        if (processDigit6) {
            if (processDigit1 ^ processDigit2 ^ processDigit3 ^ processDigit4 ^ processDigit5) {
                for (int i = last; i > start; ) {
                    a[--count6[(int) ((dtl(b[--i]) >>> 54) & 0x3FF)]] = b[i];
                }
            } else {
                for (int i = high; i > low; ) {
                    b[--count6[(int) ((dtl(a[--i]) >>> 54) & 0x3FF)] - offset] = a[i];
                }
            }
        }

        /*
         * Copy the buffer to original array, if we process ood number of digits.
         */
        if (processDigit1 ^ processDigit2 ^ processDigit3 ^ processDigit4 ^ processDigit5 ^ processDigit6) {
            System.arraycopy(b, low - offset, a, low, size);
        }
        return true;
    }

    /**
     * Returns masked bits that represent the double value.
     *
     * @param d the given value
     * @return masked bits
     */
    private static long dtl(double d) {
        long x = Double.doubleToRawLongBits(d);
        return x ^ ((x >> 63) | 0x8000000000000000L);
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

// #[class]

    /**
     * This class implements parallel sorting.
     */
    private static final class Sorter<T> extends CountedCompleter<Void> {

        private static final long serialVersionUID = 123456789L;

        @SuppressWarnings("serial")
        private final T a, b;
        private final int low, size, offset, depth;

        @SuppressWarnings("unchecked")
        private Sorter(T a, int parallelism, int low, int size, int depth) {
            this.a = a;
            this.low = low;
            this.size = size;
            this.offset = low;

            while ((parallelism >>= 2) > 0 && (size >>= 2) > 0) {
                depth -= 2;
            }
            this.b = (T) tryAllocate(a.getClass(), this.size);
            this.depth = b == null ? 0 : depth;
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
                if (a instanceof int[]) {
                    sort((Sorter<int[]>) this, (int[]) a, depth, low, low + size);
                } else if (a instanceof long[]) {
                    sort((Sorter<long[]>) this, (long[]) a, depth, low, low + size);
                } else if (a instanceof float[]) {
                    sort((Sorter<float[]>) this, (float[]) a, depth, low, low + size);
                } else if (a instanceof double[]) {
                    sort((Sorter<double[]>) this, (double[]) a, depth, low, low + size);
                } else {
                    throw new IllegalArgumentException("Unknown array: " + a.getClass().getName());
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
                    b,
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
     * This class implements parallel merging.
     */
    private static final class Merger<T> extends CountedCompleter<Void> {

        private static final long serialVersionUID = 123456789L;

        @SuppressWarnings("serial")
        private final T dst, a1, a2;
        private final int k, lo1, hi1, lo2, hi2;

        private Merger(CountedCompleter<?> parent, T dst, int k,
                T a1, int lo1, int hi1, T a2, int lo2, int hi2) {
            super(parent);
            this.dst = dst;
            this.k = k;
            this.a1 = a1;
            this.lo1 = lo1;
            this.hi1 = hi1;
            this.a2 = a2;
            this.lo2 = lo2;
            this.hi2 = hi2;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void compute() {
            if (dst instanceof int[]) {
                mergeParts((Merger<int[]>) this, (int[]) dst, k,
                    (int[]) a1, lo1, hi1, (int[]) a2, lo2, hi2);
            } else if (dst instanceof long[]) {
                mergeParts((Merger<long[]>) this, (long[]) dst, k,
                    (long[]) a1, lo1, hi1, (long[]) a2, lo2, hi2);
            } else if (dst instanceof float[]) {
                mergeParts((Merger<float[]>) this, (float[]) dst, k,
                    (float[]) a1, lo1, hi1, (float[]) a2, lo2, hi2);
            } else if (dst instanceof double[]) {
                mergeParts((Merger<double[]>) this, (double[]) dst, k,
                    (double[]) a1, lo1, hi1, (double[]) a2, lo2, hi2);
            } else {
                throw new IllegalArgumentException("Unknown array: " + dst.getClass().getName());
            }
            propagateCompletion();
        }

        private void fork(int k, int lo1, int hi1, int lo2, int hi2) {
            addToPendingCount(1);
            new Merger<>(this, dst, k, a1, lo1, hi1, a2, lo2, hi2).fork();
        }
    }

    /**
     * Tries to allocate additional buffer.
     *
     * @param clazz the given array class
     * @param size the size of additional buffer
     * @return {@code null} if requested size is too large or there is not enough memory,
     *         otherwise created buffer
    */
    @SuppressWarnings("unchecked")
    private static <T> T tryAllocate(Class<T> clazz, int size) {
        try {
            return size > MAX_BUFFER_SIZE ? null :
                (T) U.allocateUninitializedArray(clazz.componentType(), size);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static final Unsafe U = Unsafe.getUnsafe();
}
