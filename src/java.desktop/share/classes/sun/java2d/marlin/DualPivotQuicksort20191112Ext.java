/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.marlin;

import java.util.Arrays;

/**
 * This class implements powerful and fully optimized versions, both
 * sequential and parallel, of the Dual-Pivot Quicksort algorithm by
 * Vladimir Yaroslavskiy, Jon Bentley and Josh Bloch. This algorithm
 * offers O(n log(n)) performance on all data sets, and is typically
 * faster than traditional (one-pivot) Quicksort implementations.
 *
 * There are also additional algorithms, invoked from the Dual-Pivot
 * Quicksort, such as mixed insertion sort, merging of runs and heap
 * sort, counting sort and parallel merge sort.
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 * @author Doug Lea
 *
 * @version 2018.08.18
 *
 * @since 1.7 * 14
 */
public final class DualPivotQuicksort20191112Ext {

    private static final boolean FAST_ISORT = true;

    /*
    From OpenJDK14 source code:
    8226297: Dual-pivot quicksort improvements
        Reviewed-by: dl, lbourges
        Contributed-by: Vladimir Yaroslavskiy <vlv.spb.ru@mail.ru>
        Tue, 12 Nov 2019 13:49:40 -0800
     */
    /**
     * Prevents instantiation.
     */
    private DualPivotQuicksort20191112Ext() {
    }

    /**
     * Max array size to use mixed insertion sort.
     */
    private static final int MAX_MIXED_INSERTION_SORT_SIZE = 65;

    /**
     * Max array size to use insertion sort.
     */
    private static final int MAX_INSERTION_SORT_SIZE = 44;

    /**
     * Min array size to try merging of runs.
     */
    private static final int MIN_TRY_MERGE_SIZE = 4 << 10;

    /**
     * Min size of the first run to continue with scanning.
     */
    private static final int MIN_FIRST_RUN_SIZE = 16;

    /**
     * Min factor for the first runs to continue scanning.
     */
    private static final int MIN_FIRST_RUNS_FACTOR = 7;

    /**
     * Max capacity of the index array for tracking runs.
     */
    /* private */ static final int MAX_RUN_CAPACITY = 5 << 10;

    /**
     * Threshold of mixed insertion sort is incremented by this value.
     */
    private static final int DELTA = 3 << 1;

    /**
     * Max recursive partitioning depth before using heap sort.
     */
    private static final int MAX_RECURSION_DEPTH = 64 * DELTA;


    /**
     * Sorts the specified range of the array.
     *
     * @param sorter sorter context
     * @param a the array to be sorted
     * @param auxA auxiliary storage for the array to be sorted
     * @param b the secondary array to be ordered
     * @param auxB auxiliary storage for the permutation array to be handled
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void sort(DPQSSorterContext sorter, int[] a, int[] auxA, int[] b, int[] auxB, int low, int high) {
        /*
         * LBO Shortcut: Invoke insertion sort on the leftmost part.
         */
        if (FAST_ISORT && ((high - low) <= MAX_INSERTION_SORT_SIZE)) {
            insertionSort(a, b, low, high);
            return;
        }

        sorter.initBuffers(high, auxA, auxB);
        sort(sorter, a, b, 0, low, high);
    }

    /**
     * Sorts the specified array using the Dual-Pivot Quicksort and/or
     * other sorts in special-cases, possibly with parallel partitions.
     *
     * @param sorter sorter context
     * @param a the array to be sorted
     * @param b the secondary array to be ordered
     * @param bits the combination of recursion depth and bit flag, where
     *        the right bit "0" indicates that array is the leftmost part
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void sort(DPQSSorterContext sorter, int[] a, int[] b, int bits, int low, int high) {
        while (true) {
            int end = high - 1, size = high - low;

            /*
             * Run mixed insertion sort on small non-leftmost parts.
             */
            if (size < MAX_MIXED_INSERTION_SORT_SIZE + bits && (bits & 1) > 0) {
                mixedInsertionSort(a, b, low, high - 3 * ((size >> 5) << 3), high);
                return;
            }

            /*
             * Invoke insertion sort on small leftmost part.
             */
            if (size < MAX_INSERTION_SORT_SIZE) {
                insertionSort(a, b, low, high);
                return;
            }

            /*
             * Check if the whole array or large non-leftmost
             * parts are nearly sorted and then merge runs.
             */
            if ((bits == 0 || size > MIN_TRY_MERGE_SIZE && (bits & 1) > 0)
                    && tryMergeRuns(sorter, a, b, low, size)) {
                return;
            }

            /*
             * Switch to heap sort if execution
             * time is becoming quadratic.
             */
            if ((bits += DELTA) > MAX_RECURSION_DEPTH) {
                heapSort(a, b, low, high);
                return;
            }

            /*
             * Use an inexpensive approximation of the golden ratio
             * to select five sample elements and determine pivots.
             */
            int step = (size >> 3) * 3 + 3;

            /*
             * Five elements around (and including) the central element
             * will be used for pivot selection as described below. The
             * unequal choice of spacing these elements was empirically
             * determined to work well on a wide variety of inputs.
             */
            int e1 = low + step;
            int e5 = end - step;
            int e3 = (e1 + e5) >>> 1;
            int e2 = (e1 + e3) >>> 1;
            int e4 = (e3 + e5) >>> 1;
            int a3 = a[e3];

            /*
             * Sort these elements in place by the combination
             * of 4-element sorting network and insertion sort.
             *
             *    5 ------o-----------o------------
             *            |           |
             *    4 ------|-----o-----o-----o------
             *            |     |           |
             *    2 ------o-----|-----o-----o------
             *                  |     |
             *    1 ------------o-----o------------
             */
            if (a[e5] < a[e2]) { int t = a[e5]; a[e5] = a[e2]; a[e2] = t; }
            if (a[e4] < a[e1]) { int t = a[e4]; a[e4] = a[e1]; a[e1] = t; }
            if (a[e5] < a[e4]) { int t = a[e5]; a[e5] = a[e4]; a[e4] = t; }
            if (a[e2] < a[e1]) { int t = a[e2]; a[e2] = a[e1]; a[e1] = t; }
            if (a[e4] < a[e2]) { int t = a[e4]; a[e4] = a[e2]; a[e2] = t; }

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
             * Partitioning with 2 pivots in case of different elements.
             */
            if (a[e1] < a[e2] && a[e2] < a[e3] && a[e3] < a[e4] && a[e4] < a[e5]) {

                /*
                 * Use the first and fifth of the five sorted elements as
                 * the pivots. These values are inexpensive approximation
                 * of tertiles. Note, that pivot1 < pivot2.
                 */
                int pivotA1 = a[e1];
                int pivotA2 = a[e5];
                int pivotB1 = b[e1];
                int pivotB2 = b[e5];

                /*
                 * The first and the last elements to be sorted are moved
                 * to the locations formerly occupied by the pivots. When
                 * partitioning is completed, the pivots are swapped back
                 * into their final positions, and excluded from the next
                 * subsequent sorting.
                 */
                a[e1] = a[lower];
                a[e5] = a[upper];
                b[e1] = b[lower];
                b[e5] = b[upper];

                /*
                 * Skip elements, which are less or greater than the pivots.
                 */
                while (a[++lower] < pivotA1);
                while (a[--upper] > pivotA2);

                /*
                 * Backward 3-interval partitioning
                 *
                 *   left part                 central part          right part
                 * +------------------------------------------------------------+
                 * |  < pivot1  |   ?   |  pivot1 <= && <= pivot2  |  > pivot2  |
                 * +------------------------------------------------------------+
                 *             ^       ^                            ^
                 *             |       |                            |
                 *           lower     k                          upper
                 *
                 * Invariants:
                 *
                 *              all in (low, lower] < pivot1
                 *    pivot1 <= all in (k, upper)  <= pivot2
                 *              all in [upper, end) > pivot2
                 *
                 * Pointer k is the last index of ?-part
                 */
                for (int unused = --lower, k = ++upper; --k > lower; ) {
                    int ak = a[k];
                    int bk = b[k];

                    if (ak < pivotA1) { // Move a[k] to the left side
                        while (lower < k) {
                            if (a[++lower] >= pivotA1) {
                                if (a[lower] > pivotA2) {
                                    a[k] = a[--upper];
                                    a[upper] = a[lower];
                                    b[k] = b[  upper];
                                    b[upper] = b[lower];
                                } else {
                                    a[k] = a[lower];
                                    b[k] = b[lower];
                                }
                                a[lower] = ak;
                                b[lower] = bk;
                                break;
                            }
                        }
                    } else if (ak > pivotA2) { // Move a[k] to the right side
                        a[k] = a[--upper];
                        a[upper] = ak;
                        b[k] = b[  upper];
                        b[upper] = bk;
                    }
                }

                /*
                 * Swap the pivots into their final positions.
                 */
                a[low] = a[lower]; a[lower] = pivotA1;
                a[end] = a[upper]; a[upper] = pivotA2;

                b[low] = b[lower]; b[lower] = pivotB1;
                b[end] = b[upper]; b[upper] = pivotB2;

                /*
                 * Sort non-left parts recursively (possibly in parallel),
                 * excluding known pivots.
                 */
                sort(sorter, a, b, bits | 1, lower + 1, upper);
                sort(sorter, a, b, bits | 1, upper + 1, high);

            } else { // Use single pivot in case of many equal elements

                /*
                 * Use the third of the five sorted elements as the pivot.
                 * This value is inexpensive approximation of the median.
                 */
                int pivotA = a[e3];
                int pivotB = b[e3];

                /*
                 * The first element to be sorted is moved to the
                 * location formerly occupied by the pivot. After
                 * completion of partitioning the pivot is swapped
                 * back into its final position, and excluded from
                 * the next subsequent sorting.
                 */
                a[e3] = a[lower];
                b[e3] = b[lower];

                /*
                 * Traditional 3-way (Dutch National Flag) partitioning
                 *
                 *   left part                 central part    right part
                 * +------------------------------------------------------+
                 * |   < pivot   |     ?     |   == pivot   |   > pivot   |
                 * +------------------------------------------------------+
                 *              ^           ^                ^
                 *              |           |                |
                 *            lower         k              upper
                 *
                 * Invariants:
                 *
                 *   all in (low, lower] < pivot
                 *   all in (k, upper)  == pivot
                 *   all in [upper, end] > pivot
                 *
                 * Pointer k is the last index of ?-part
                 */
                for (int k = ++upper; --k > lower; ) {
                    int ak = a[k];

                    if (ak != pivotA) {
                        a[k] = pivotA;
                        int bk = b[k];

                        if (ak < pivotA) { // Move a[k] to the left side
                            while (a[++lower] < pivotA);

                            if (a[lower] > pivotA) {
                                a[k] = a[--upper];
                                a[upper] = a[lower];
                                b[k] = b[  upper];
                                b[upper] = b[lower];
                            } else {
                                a[k] = a[lower];
                                b[k] = b[lower];
                            }
                            a[lower] = ak;
                            b[lower] = bk;
                        } else { // ak > pivot - Move a[k] to the right side
                            a[k] = a[--upper];
                            a[upper] = ak;
                            b[k] = b[  upper];
                            b[upper] = bk;
                        }
                    }
                }

                /*
                 * Swap the pivot into its final position.
                 */
                a[low] = a[lower]; a[lower] = pivotA;
                b[low] = b[lower]; b[lower] = pivotB;

                /*
                 * Sort the right part (possibly in parallel), excluding
                 * known pivot. All elements from the central part are
                 * equal and therefore already sorted.
                 */
                sort(sorter, a, b, bits | 1, upper, high);
            }
            high = lower; // Iterate along the left part
        }
    }

    /**
     * Sorts the specified range of the array using mixed insertion sort.
     *
     * Mixed insertion sort is combination of simple insertion sort,
     * pin insertion sort and pair insertion sort.
     *
     * In the context of Dual-Pivot Quicksort, the pivot element
     * from the left part plays the role of sentinel, because it
     * is less than any elements from the given part. Therefore,
     * expensive check of the left range can be skipped on each
     * iteration unless it is the leftmost call.
     *
     * @param a the array to be sorted
     * @param b the secondary array to be ordered
     * @param low the index of the first element, inclusive, to be sorted
     * @param end the index of the last element for simple insertion sort
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void mixedInsertionSort(int[] a, int[] b, int low, int end, int high) {
        if (end == high) {

            /*
             * Invoke simple insertion sort on tiny array.
             */
            for (int i; ++low < end; ) {
                int ai = a[i = low];

                if (ai < a[i - 1]) {
                    int bi = b[i];

                    while (ai < a[--i]) {
                        a[i + 1] = a[i];
                        b[i + 1] = b[i];
                    }
                    a[i + 1] = ai;
                    b[i + 1] = bi;
                }
            }
        } else {

            /*
             * Start with pin insertion sort on small part.
             *
             * Pin insertion sort is extended simple insertion sort.
             * The main idea of this sort is to put elements larger
             * than an element called pin to the end of array (the
             * proper area for such elements). It avoids expensive
             * movements of these elements through the whole array.
             */
            int pin = a[end];

            for (int i, p = high; ++low < end; ) {
                int ai = a[i = low];
                int bi = b[i];

                if (ai < a[i - 1]) { // Small element

                    /*
                     * Insert small element into sorted part.
                     */
                    a[i] = a[i - 1];
                    b[i] = b[--i];

                    while (ai < a[--i]) {
                        a[i + 1] = a[i];
                        b[i + 1] = b[i];
                    }
                    a[i + 1] = ai;
                    b[i + 1] = bi;

                } else if (p > i && ai > pin) { // Large element

                    /*
                     * Find element smaller than pin.
                     */
                    while (a[--p] > pin);

                    /*
                     * Swap it with large element.
                     */
                    if (p > i) {
                        ai = a[p];
                        a[p] = a[i];
                        bi = b[p];
                        b[p] = b[i];
                    }

                    /*
                     * Insert small element into sorted part.
                     */
                    while (ai < a[--i]) {
                        a[i + 1] = a[i];
                        b[i + 1] = b[i];
                    }
                    a[i + 1] = ai;
                    b[i + 1] = bi;
                }
            }

            /*
             * Continue with pair insertion sort on remain part.
             */
            for (int i; low < high; ++low) {
                int a1 = a[i = low], a2 = a[++low];
                int b1 = b[i],       b2 = b[  low];

                /*
                 * Insert two elements per iteration: at first, insert the
                 * larger element and then insert the smaller element, but
                 * from the position where the larger element was inserted.
                 */
                if (a1 > a2) {

                    while (a1 < a[--i]) {
                        a[i + 2] = a[i];
                        b[i + 2] = b[i];
                    }
                    a[++i + 1] = a1;
                    b[  i + 1] = b1;

                    while (a2 < a[--i]) {
                        a[i + 1] = a[i];
                        b[i + 1] = b[i];
                    }
                    a[i + 1] = a2;
                    b[i + 1] = b2;

                } else if (a1 < a[i - 1]) {

                    while (a2 < a[--i]) {
                        a[i + 2] = a[i];
                        b[i + 2] = b[i];
                    }
                    a[++i + 1] = a2;
                    b[  i + 1] = b2;

                    while (a1 < a[--i]) {
                        a[i + 1] = a[i];
                        b[i + 1] = b[i];
                    }
                    a[i + 1] = a1;
                    b[i + 1] = b1;
                }
            }
        }
    }

    /**
     * Sorts the specified range of the array using insertion sort.
     *
     * @param a the array to be sorted
     * @param b the secondary array to be ordered
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    static void insertionSort(int[] a, int[] b, int low, int high) {
        for (int i, k = low; ++k < high; ) {
            int ai = a[i = k];

            if (ai < a[i - 1]) {
                int bi = b[i];

                while (--i >= low && ai < a[i]) {
                    a[i + 1] = a[i];
                    b[i + 1] = b[i];
                }
                a[i + 1] = ai;
                b[i + 1] = bi;
            }
        }
    }

    /**
     * Sorts the specified range of the array using heap sort.
     *
     * @param a the array to be sorted
     * @param b the secondary array to be ordered
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void heapSort(int[] a, int[] b, int low, int high) {
        for (int k = (low + high) >>> 1; k > low; ) {
            pushDown(a, b, --k, a[k], b[k], low, high);
        }
        while (--high > low) {
            int maxA = a[low];
            int maxB = b[low];
            pushDown(a, b, low, a[high], b[high], low, high);
            a[high] = maxA;
            b[high] = maxB;
        }
    }

    /**
     * Pushes specified element down during heap sort.
     *
     * @param a the array to be sorted
     * @param b the secondary array to be ordered
     * @param p the start index
     * @param valueA the given element in a
     * @param valueB the given element in b
     * @param low the index of the first element, inclusive, to be sorted
     * @param high the index of the last element, exclusive, to be sorted
     */
    private static void pushDown(int[] a, int[] b, int p, int valueA, int valueB, int low, int high) {
        for (int k;; a[p] = a[k], b[p] = b[p = k]) {
            k = (p << 1) - low + 2; // Index of the right child

            if (k > high) {
                break;
            }
            if (k == high || a[k] < a[k - 1]) {
                --k;
            }
            if (a[k] <= valueA) {
                break;
            }
        }
        a[p] = valueA;
        b[p] = valueB;
    }

    /**
     * Tries to sort the specified range of the array.
     *
     * @param sorter sorter context
     * @param a the array to be sorted
     * @param b the secondary array to be ordered
     * @param low the index of the first element to be sorted
     * @param size the array size
     * @return true if finally sorted, false otherwise
     */
    private static boolean tryMergeRuns(DPQSSorterContext sorter, int[] a, int[] b, int low, int size) {

        /*
         * The run array is constructed only if initial runs are
         * long enough to continue, run[i] then holds start index
         * of the i-th sequence of elements in non-descending order.
         */
        int[] run = null;
        int high = low + size;
        int count = 1, last = low;

        /*
         * Identify all possible runs.
         */
        for (int k = low + 1; k < high; ) {

            /*
             * Find the end index of the current run.
             */
            if (a[k - 1] < a[k]) {

                // Identify ascending sequence
                while (++k < high && a[k - 1] <= a[k]);

            } else if (a[k - 1] > a[k]) {

                // Identify descending sequence
                while (++k < high && a[k - 1] >= a[k]);

                // Reverse into ascending order
                for (int i = last - 1, j = k, t; ++i < --j && a[i] > a[j]; ) {
                    t = a[i]; a[i] = a[j]; a[j] = t;
                    t = b[i]; b[i] = b[j]; b[j] = t;
                }
            } else { // Identify constant sequence
                for (int ak = a[k]; ++k < high && ak == a[k]; );

                if (k < high) {
                    continue;
                }
            }

            /*
             * Check special cases.
             */
            if (sorter.runInit || run == null) {
                sorter.runInit = false; // LBO

                if (k == high) {

                    /*
                     * The array is monotonous sequence,
                     * and therefore already sorted.
                     */
                    return true;
                }

                if (k - low < MIN_FIRST_RUN_SIZE) {

                    /*
                     * The first run is too small
                     * to proceed with scanning.
                     */
                    return false;
                }

//                System.out.println("alloc run");
//                run = new int[((size >> 10) | 0x7F) & 0x3FF];
                run = sorter.run; // LBO: prealloc
                run[0] = low;

            } else if (a[last - 1] > a[last]) {

                if (count > (k - low) >> MIN_FIRST_RUNS_FACTOR) {

                    /*
                     * The first runs are not long
                     * enough to continue scanning.
                     */
                    return false;
                }

                if (++count == MAX_RUN_CAPACITY) {

                    /*
                     * Array is not highly structured.
                     */
                    return false;
                }

                if (false && count == run.length) {

                    /*
                     * Increase capacity of index array.
                     */
//                  System.out.println("alloc run (resize)");
                    run = Arrays.copyOf(run, count << 1);
                }
            }
            run[count] = (last = k);

            // fix ALMOST_CONTIGUOUS ie consecutive (ascending / descending runs)
            if (k < high - 1) {
                k++; // LBO
            }
        }

        /*
         * Merge runs of highly structured array.
         */
        if (count > 1) {
            int[] auxA = sorter.auxA;
            int[] auxB = sorter.auxB;
            int offset = low;

            // LBO: prealloc
            if ((auxA.length < size || auxB.length < size)) {
//                System.out.println("alloc aux: "+size);
                auxA = new int[size];
                auxB = new int[size];
            }
            mergeRuns(a, auxA, b, auxB, offset, 1, run, 0, count);
        }
        return true;
    }

    /**
     * Merges the specified runs.
     *
     * @param srcA the source array for the array to be sorted (a)
     * @param dstA the temporary buffer used in merging (a)
     * @param srcB the source array for the secondary array to be ordered (b)
     * @param offset the start index in the source, inclusive
     * @param dstB the temporary buffer used in merging (b)
     * @param aim specifies merging: to source ( > 0), buffer ( < 0) or any ( == 0)
     * @param run the start indexes of the runs, inclusive
     * @param lo the start index of the first run, inclusive
     * @param hi the start index of the last run, inclusive
     * @return the destination where runs are merged
     */
    private static int[] mergeRuns(int[] srcA, int[] dstA, int[] srcB, int[] dstB, int offset,
                                   int aim, int[] run, int lo, int hi) {

        if (hi - lo == 1) {
            if (aim >= 0) {
                return srcA;
            }
            for (int i = run[hi], j = i - offset, low = run[lo]; i > low;
                --j, --i, dstA[j] = srcA[i], dstB[j] = srcB[i]
            );
            return dstA;
        }

        /*
         * Split into approximately equal parts.
         */
        int mi = lo, rmi = (run[lo] + run[hi]) >>> 1;
        while (run[++mi + 1] <= rmi);

        /*
         * Merge the left and right parts.
         */
        int[] a1, a2;
        a1 = mergeRuns(srcA, dstA, srcB, dstB, offset, -aim, run, lo, mi);
        a2 = mergeRuns(srcA, dstA, srcB, dstB, offset,    0, run, mi, hi);

        int[] b1, b2;
        b1 = a1 == srcA ? srcB : dstB;
        b2 = a2 == srcA ? srcB : dstB;

        int[] resA = a1 == srcA ? dstA : srcA;
        int[] resB = a1 == srcA ? dstB : srcB;

        int k   = a1 == srcA ? run[lo] - offset : run[lo];
        int lo1 = a1 == dstA ? run[lo] - offset : run[lo];
        int hi1 = a1 == dstA ? run[mi] - offset : run[mi];
        int lo2 = a2 == dstA ? run[mi] - offset : run[mi];
        int hi2 = a2 == dstA ? run[hi] - offset : run[hi];

        mergeParts(resA, resB, k, a1, b1, lo1, hi1, a2, b2, lo2, hi2);

        return resA;
    }

    /**
     * Merges the sorted parts.
     *
     * @param dstA the destination where parts are merged (a)
     * @param dstB the destination where parts are merged (b)
     * @param k the start index of the destination, inclusive
     * @param a1 the first part (a)
     * @param b1 the first part (b)
     * @param lo1 the start index of the first part, inclusive
     * @param hi1 the end index of the first part, exclusive
     * @param a2 the second part (a)
     * @param b2 the second part (b)
     * @param lo2 the start index of the second part, inclusive
     * @param hi2 the end index of the second part, exclusive
     */
    private static void mergeParts(int[] dstA, int[] dstB, int k,
                                   int[] a1, int[] b1, int lo1, int hi1, int[] a2, int[] b2, int lo2, int hi2) {
// ...
        /*
         * Merge small parts sequentially.
         */
        while (lo1 < hi1 && lo2 < hi2) {
            if (a1[lo1] < a2[lo2]) {
                dstA[k] = a1[lo1];
                dstB[k] = b1[lo1];
                k++; lo1++;
            } else {
                dstA[k] = a2[lo2];
                dstB[k] = b2[lo2];
                k++; lo2++;
            }
        }
        if (dstA != a1 || k < lo1) {
            while (lo1 < hi1) {
                dstA[k] = a1[lo1];
                dstB[k] = b1[lo1];
                k++; lo1++;
            }
        }
        if (dstA != a2 || k < lo2) {
            while (lo2 < hi2) {
                dstA[k] = a2[lo2];
                dstB[k] = b2[lo2];
                k++; lo2++;
            }
        }
    }
}
