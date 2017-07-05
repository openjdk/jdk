/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.util;

/**
 * This class implements the Dual-Pivot Quicksort algorithm by
 * Vladimir Yaroslavskiy, Jon Bentley, and Joshua Bloch. The algorithm
 * offers O(n log(n)) performance on many data sets that cause other
 * quicksorts to degrade to quadratic performance, and is typically
 * faster than traditional (one-pivot) Quicksort implementations.
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 *
 * @version 2009.11.29 m765.827.12i
 */
final class DualPivotQuicksort {

    /**
     * Prevents instantiation.
     */
    private DualPivotQuicksort() {}

    /*
     * Tuning parameters.
     */

    /**
     * If the length of an array to be sorted is less than this
     * constant, insertion sort is used in preference to Quicksort.
     */
    private static final int INSERTION_SORT_THRESHOLD = 32;

    /**
     * If the length of a byte array to be sorted is greater than
     * this constant, counting sort is used in preference to Quicksort.
     */
    private static final int COUNTING_SORT_THRESHOLD_FOR_BYTE = 128;

    /**
     * If the length of a short or char array to be sorted is greater
     * than this constant, counting sort is used in preference to Quicksort.
     */
    private static final int COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR = 32768;

    /*
     * Sorting methods for 7 primitive types.
     */

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(int[] a) {
        doSort(a, 0, a.length - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The range
     * to be sorted extends from the index {@code fromIndex}, inclusive, to
     * the index {@code toIndex}, exclusive. If {@code fromIndex == toIndex},
     * the range to be sorted is empty (and the call is a no-op).
     *
     * @param a the array to be sorted
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param toIndex the index of the last element, exclusive, to be sorted
     * @throws IllegalArgumentException if {@code fromIndex > toIndex}
     * @throws ArrayIndexOutOfBoundsException
     *     if {@code fromIndex < 0} or {@code toIndex > a.length}
     */
    public static void sort(int[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        doSort(a, fromIndex, toIndex - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. This
     * method differs from the public {@code sort} method in that the
     * {@code right} index is inclusive, and it does no range checking
     * on {@code left} or {@code right}.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void doSort(int[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int i = left + 1; i <= right; i++) {
                int ai = a[i];
                int j;
                for (j = i - 1; j >= left && ai < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ai;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void dualPivotQuicksort(int[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements using a 5-element sorting network
        int ae1 = a[e1], ae2 = a[e2], ae3 = a[e3], ae4 = a[e4], ae5 = a[e5];

        if (ae1 > ae2) { int t = ae1; ae1 = ae2; ae2 = t; }
        if (ae4 > ae5) { int t = ae4; ae4 = ae5; ae5 = t; }
        if (ae1 > ae3) { int t = ae1; ae1 = ae3; ae3 = t; }
        if (ae2 > ae3) { int t = ae2; ae2 = ae3; ae3 = t; }
        if (ae1 > ae4) { int t = ae1; ae1 = ae4; ae4 = t; }
        if (ae3 > ae4) { int t = ae3; ae3 = ae4; ae4 = t; }
        if (ae2 > ae5) { int t = ae2; ae2 = ae5; ae5 = t; }
        if (ae2 > ae3) { int t = ae2; ae2 = ae3; ae3 = t; }
        if (ae4 > ae5) { int t = ae4; ae4 = ae5; ae5 = t; }

        a[e1] = ae1; a[e3] = ae3; a[e5] = ae5;

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the elements to be sorted are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        int pivot1 = ae2; a[e2] = a[left];
        int pivot2 = ae4; a[e4] = a[right];

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = (pivot1 != pivot2);

        if (pivotsDiffer) {
            /*
             * Partitioning:
             *
             *   left part         center part                    right part
             * +------------------------------------------------------------+
             * | < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2 |
             * +------------------------------------------------------------+
             *              ^                          ^       ^
             *              |                          |       |
             *             less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                int ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                        a[great--] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way,
             * or "Dutch National Flag", partition:
             *
             *   left part   center part            right part
             * +----------------------------------------------+
             * |  < pivot  |  == pivot  |    ?    |  > pivot  |
             * +----------------------------------------------+
             *              ^            ^       ^
             *              |            |       |
             *             less          k     great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                int ak = a[k];
                if (ak == pivot1) {
                    continue;
                }
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else { // (a[k] > pivot1) -  Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // a[great] == pivot1
                        a[k] = pivot1;
                        a[great--] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        doSort(a, left,   less - 2);
        doSort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 2/3 of the array),
         * swap internal pivot values to ends
         */
        if (less < e1 && great > e5) {
            while (a[less] == pivot1) {
                less++;
            }
            while (a[great] == pivot2) {
                great--;
            }

            /*
             * Partitioning:
             *
             *   left part       center part                   right part
             * +----------------------------------------------------------+
             * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
             * +----------------------------------------------------------+
             *              ^                        ^       ^
             *              |                        |       |
             *             less                      k     great
             *
             * Invariants:
             *
             *              all in (*, less)  == pivot1
             *     pivot1 < all in [less, k)   < pivot2
             *              all in (great, *) == pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                int ak = a[k];
                if (ak == pivot2) { // Move a[k] to right part
                    while (a[great] == pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] == pivot1) {
                        a[k] = a[less];
                        a[less++] = pivot1;
                    } else { // pivot1 < a[great] < pivot2
                        a[k] = a[great];
                    }
                    a[great--] = pivot2;
                } else if (ak == pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        doSort(a, less, great);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(long[] a) {
        doSort(a, 0, a.length - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The range
     * to be sorted extends from the index {@code fromIndex}, inclusive, to
     * the index {@code toIndex}, exclusive. If {@code fromIndex == toIndex},
     * the range to be sorted is empty (and the call is a no-op).
     *
     * @param a the array to be sorted
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param toIndex the index of the last element, exclusive, to be sorted
     * @throws IllegalArgumentException if {@code fromIndex > toIndex}
     * @throws ArrayIndexOutOfBoundsException
     *     if {@code fromIndex < 0} or {@code toIndex > a.length}
     */
    public static void sort(long[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        doSort(a, fromIndex, toIndex - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. This
     * method differs from the public {@code sort} method in that the
     * {@code right} index is inclusive, and it does no range checking on
     * {@code left} or {@code right}.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void doSort(long[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int i = left + 1; i <= right; i++) {
                long ai = a[i];
                int j;
                for (j = i - 1; j >= left && ai < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ai;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void dualPivotQuicksort(long[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements using a 5-element sorting network
        long ae1 = a[e1], ae2 = a[e2], ae3 = a[e3], ae4 = a[e4], ae5 = a[e5];

        if (ae1 > ae2) { long t = ae1; ae1 = ae2; ae2 = t; }
        if (ae4 > ae5) { long t = ae4; ae4 = ae5; ae5 = t; }
        if (ae1 > ae3) { long t = ae1; ae1 = ae3; ae3 = t; }
        if (ae2 > ae3) { long t = ae2; ae2 = ae3; ae3 = t; }
        if (ae1 > ae4) { long t = ae1; ae1 = ae4; ae4 = t; }
        if (ae3 > ae4) { long t = ae3; ae3 = ae4; ae4 = t; }
        if (ae2 > ae5) { long t = ae2; ae2 = ae5; ae5 = t; }
        if (ae2 > ae3) { long t = ae2; ae2 = ae3; ae3 = t; }
        if (ae4 > ae5) { long t = ae4; ae4 = ae5; ae5 = t; }

        a[e1] = ae1; a[e3] = ae3; a[e5] = ae5;

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the elements to be sorted are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        long pivot1 = ae2; a[e2] = a[left];
        long pivot2 = ae4; a[e4] = a[right];

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = (pivot1 != pivot2);

        if (pivotsDiffer) {
            /*
             * Partitioning:
             *
             *   left part         center part                    right part
             * +------------------------------------------------------------+
             * | < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2 |
             * +------------------------------------------------------------+
             *              ^                          ^       ^
             *              |                          |       |
             *             less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                long ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                        a[great--] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way,
             * or "Dutch National Flag", partition:
             *
             *   left part   center part            right part
             * +----------------------------------------------+
             * |  < pivot  |  == pivot  |    ?    |  > pivot  |
             * +----------------------------------------------+
             *              ^            ^       ^
             *              |            |       |
             *             less          k     great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                long ak = a[k];
                if (ak == pivot1) {
                    continue;
                }
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else { // (a[k] > pivot1) -  Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // a[great] == pivot1
                        a[k] = pivot1;
                        a[great--] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        doSort(a, left,   less - 2);
        doSort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 2/3 of the array),
         * swap internal pivot values to ends
         */
        if (less < e1 && great > e5) {
            while (a[less] == pivot1) {
                less++;
            }
            while (a[great] == pivot2) {
                great--;
            }

            /*
             * Partitioning:
             *
             *   left part       center part                   right part
             * +----------------------------------------------------------+
             * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
             * +----------------------------------------------------------+
             *              ^                        ^       ^
             *              |                        |       |
             *             less                      k     great
             *
             * Invariants:
             *
             *              all in (*, less)  == pivot1
             *     pivot1 < all in [less, k)   < pivot2
             *              all in (great, *) == pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                long ak = a[k];
                if (ak == pivot2) { // Move a[k] to right part
                    while (a[great] == pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] == pivot1) {
                        a[k] = a[less];
                        a[less++] = pivot1;
                    } else { // pivot1 < a[great] < pivot2
                        a[k] = a[great];
                    }
                    a[great--] = pivot2;
                } else if (ak == pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        doSort(a, less, great);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(short[] a) {
        doSort(a, 0, a.length - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The range
     * to be sorted extends from the index {@code fromIndex}, inclusive, to
     * the index {@code toIndex}, exclusive. If {@code fromIndex == toIndex},
     * the range to be sorted is empty (and the call is a no-op).
     *
     * @param a the array to be sorted
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param toIndex the index of the last element, exclusive, to be sorted
     * @throws IllegalArgumentException if {@code fromIndex > toIndex}
     * @throws ArrayIndexOutOfBoundsException
     *     if {@code fromIndex < 0} or {@code toIndex > a.length}
     */
    public static void sort(short[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        doSort(a, fromIndex, toIndex - 1);
    }

    /** The number of distinct short values. */
    private static final int NUM_SHORT_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array into ascending order. This
     * method differs from the public {@code sort} method in that the
     * {@code right} index is inclusive, and it does no range checking on
     * {@code left} or {@code right}.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void doSort(short[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int i = left + 1; i <= right; i++) {
                short ai = a[i];
                int j;
                for (j = i - 1; j >= left && ai < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ai;
            }
        } else if (right-left+1 > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            // Use counting sort on huge arrays
            int[] count = new int[NUM_SHORT_VALUES];

            for (int i = left; i <= right; i++) {
                count[a[i] - Short.MIN_VALUE]++;
            }
            for (int i = 0, k = left; i < count.length && k <= right; i++) {
                short value = (short) (i + Short.MIN_VALUE);

                for (int s = count[i]; s > 0; s--) {
                    a[k++] = value;
               }
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void dualPivotQuicksort(short[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements using a 5-element sorting network
        short ae1 = a[e1], ae2 = a[e2], ae3 = a[e3], ae4 = a[e4], ae5 = a[e5];

        if (ae1 > ae2) { short t = ae1; ae1 = ae2; ae2 = t; }
        if (ae4 > ae5) { short t = ae4; ae4 = ae5; ae5 = t; }
        if (ae1 > ae3) { short t = ae1; ae1 = ae3; ae3 = t; }
        if (ae2 > ae3) { short t = ae2; ae2 = ae3; ae3 = t; }
        if (ae1 > ae4) { short t = ae1; ae1 = ae4; ae4 = t; }
        if (ae3 > ae4) { short t = ae3; ae3 = ae4; ae4 = t; }
        if (ae2 > ae5) { short t = ae2; ae2 = ae5; ae5 = t; }
        if (ae2 > ae3) { short t = ae2; ae2 = ae3; ae3 = t; }
        if (ae4 > ae5) { short t = ae4; ae4 = ae5; ae5 = t; }

        a[e1] = ae1; a[e3] = ae3; a[e5] = ae5;

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the elements to be sorted are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        short pivot1 = ae2; a[e2] = a[left];
        short pivot2 = ae4; a[e4] = a[right];

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = (pivot1 != pivot2);

        if (pivotsDiffer) {
            /*
             * Partitioning:
             *
             *   left part         center part                    right part
             * +------------------------------------------------------------+
             * | < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2 |
             * +------------------------------------------------------------+
             *              ^                          ^       ^
             *              |                          |       |
             *             less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                short ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                        a[great--] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way,
             * or "Dutch National Flag", partition:
             *
             *   left part   center part            right part
             * +----------------------------------------------+
             * |  < pivot  |  == pivot  |    ?    |  > pivot  |
             * +----------------------------------------------+
             *              ^            ^       ^
             *              |            |       |
             *             less          k     great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                short ak = a[k];
                if (ak == pivot1) {
                    continue;
                }
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else { // (a[k] > pivot1) -  Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // a[great] == pivot1
                        a[k] = pivot1;
                        a[great--] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        doSort(a, left,   less - 2);
        doSort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 2/3 of the array),
         * swap internal pivot values to ends
         */
        if (less < e1 && great > e5) {
            while (a[less] == pivot1) {
                less++;
            }
            while (a[great] == pivot2) {
                great--;
            }

            /*
             * Partitioning:
             *
             *   left part       center part                   right part
             * +----------------------------------------------------------+
             * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
             * +----------------------------------------------------------+
             *              ^                        ^       ^
             *              |                        |       |
             *             less                      k     great
             *
             * Invariants:
             *
             *              all in (*, less)  == pivot1
             *     pivot1 < all in [less, k)   < pivot2
             *              all in (great, *) == pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                short ak = a[k];
                if (ak == pivot2) { // Move a[k] to right part
                    while (a[great] == pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] == pivot1) {
                        a[k] = a[less];
                        a[less++] = pivot1;
                    } else { // pivot1 < a[great] < pivot2
                        a[k] = a[great];
                    }
                    a[great--] = pivot2;
                } else if (ak == pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        doSort(a, less, great);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(char[] a) {
        doSort(a, 0, a.length - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The range
     * to be sorted extends from the index {@code fromIndex}, inclusive, to
     * the index {@code toIndex}, exclusive. If {@code fromIndex == toIndex},
     * the range to be sorted is empty (and the call is a no-op).
     *
     * @param a the array to be sorted
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param toIndex the index of the last element, exclusive, to be sorted
     * @throws IllegalArgumentException if {@code fromIndex > toIndex}
     * @throws ArrayIndexOutOfBoundsException
     *     if {@code fromIndex < 0} or {@code toIndex > a.length}
     */
    public static void sort(char[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        doSort(a, fromIndex, toIndex - 1);
    }

    /** The number of distinct char values. */
    private static final int NUM_CHAR_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array into ascending order. This
     * method differs from the public {@code sort} method in that the
     * {@code right} index is inclusive, and it does no range checking on
     * {@code left} or {@code right}.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void doSort(char[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int i = left + 1; i <= right; i++) {
                char ai = a[i];
                int j;
                for (j = i - 1; j >= left && ai < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ai;
            }
        } else if (right-left+1 > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            // Use counting sort on huge arrays
            int[] count = new int[NUM_CHAR_VALUES];

            for (int i = left; i <= right; i++) {
                count[a[i]]++;
            }
            for (int i = 0, k = left; i < count.length && k <= right; i++) {
                for (int s = count[i]; s > 0; s--) {
                    a[k++] = (char) i;
               }
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void dualPivotQuicksort(char[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements using a 5-element sorting network
        char ae1 = a[e1], ae2 = a[e2], ae3 = a[e3], ae4 = a[e4], ae5 = a[e5];

        if (ae1 > ae2) { char t = ae1; ae1 = ae2; ae2 = t; }
        if (ae4 > ae5) { char t = ae4; ae4 = ae5; ae5 = t; }
        if (ae1 > ae3) { char t = ae1; ae1 = ae3; ae3 = t; }
        if (ae2 > ae3) { char t = ae2; ae2 = ae3; ae3 = t; }
        if (ae1 > ae4) { char t = ae1; ae1 = ae4; ae4 = t; }
        if (ae3 > ae4) { char t = ae3; ae3 = ae4; ae4 = t; }
        if (ae2 > ae5) { char t = ae2; ae2 = ae5; ae5 = t; }
        if (ae2 > ae3) { char t = ae2; ae2 = ae3; ae3 = t; }
        if (ae4 > ae5) { char t = ae4; ae4 = ae5; ae5 = t; }

        a[e1] = ae1; a[e3] = ae3; a[e5] = ae5;

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the elements to be sorted are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        char pivot1 = ae2; a[e2] = a[left];
        char pivot2 = ae4; a[e4] = a[right];

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = (pivot1 != pivot2);

        if (pivotsDiffer) {
            /*
             * Partitioning:
             *
             *   left part         center part                    right part
             * +------------------------------------------------------------+
             * | < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2 |
             * +------------------------------------------------------------+
             *              ^                          ^       ^
             *              |                          |       |
             *             less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                char ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                        a[great--] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way,
             * or "Dutch National Flag", partition:
             *
             *   left part   center part            right part
             * +----------------------------------------------+
             * |  < pivot  |  == pivot  |    ?    |  > pivot  |
             * +----------------------------------------------+
             *              ^            ^       ^
             *              |            |       |
             *             less          k     great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                char ak = a[k];
                if (ak == pivot1) {
                    continue;
                }
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else { // (a[k] > pivot1) -  Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // a[great] == pivot1
                        a[k] = pivot1;
                        a[great--] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        doSort(a, left,   less - 2);
        doSort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 2/3 of the array),
         * swap internal pivot values to ends
         */
        if (less < e1 && great > e5) {
            while (a[less] == pivot1) {
                less++;
            }
            while (a[great] == pivot2) {
                great--;
            }

            /*
             * Partitioning:
             *
             *   left part       center part                   right part
             * +----------------------------------------------------------+
             * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
             * +----------------------------------------------------------+
             *              ^                        ^       ^
             *              |                        |       |
             *             less                      k     great
             *
             * Invariants:
             *
             *              all in (*, less)  == pivot1
             *     pivot1 < all in [less, k)   < pivot2
             *              all in (great, *) == pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                char ak = a[k];
                if (ak == pivot2) { // Move a[k] to right part
                    while (a[great] == pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] == pivot1) {
                        a[k] = a[less];
                        a[less++] = pivot1;
                    } else { // pivot1 < a[great] < pivot2
                        a[k] = a[great];
                    }
                    a[great--] = pivot2;
                } else if (ak == pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        doSort(a, less, great);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(byte[] a) {
        doSort(a, 0, a.length - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The range
     * to be sorted extends from the index {@code fromIndex}, inclusive, to
     * the index {@code toIndex}, exclusive. If {@code fromIndex == toIndex},
     * the range to be sorted is empty (and the call is a no-op).
     *
     * @param a the array to be sorted
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param toIndex the index of the last element, exclusive, to be sorted
     * @throws IllegalArgumentException if {@code fromIndex > toIndex}
     * @throws ArrayIndexOutOfBoundsException
     *     if {@code fromIndex < 0} or {@code toIndex > a.length}
     */
    public static void sort(byte[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        doSort(a, fromIndex, toIndex - 1);
    }

    /** The number of distinct byte values. */
    private static final int NUM_BYTE_VALUES = 1 << 8;

    /**
     * Sorts the specified range of the array into ascending order. This
     * method differs from the public {@code sort} method in that the
     * {@code right} index is inclusive, and it does no range checking on
     * {@code left} or {@code right}.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void doSort(byte[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int i = left + 1; i <= right; i++) {
                byte ai = a[i];
                int j;
                for (j = i - 1; j >= left && ai < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ai;
            }
        } else if (right - left + 1 > COUNTING_SORT_THRESHOLD_FOR_BYTE) {
            // Use counting sort on huge arrays
            int[] count = new int[NUM_BYTE_VALUES];

            for (int i = left; i <= right; i++) {
                count[a[i] - Byte.MIN_VALUE]++;
            }
            for (int i = 0, k = left; i < count.length && k <= right; i++) {
                byte value = (byte) (i + Byte.MIN_VALUE);

                for (int s = count[i]; s > 0; s--) {
                    a[k++] = value;
               }
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void dualPivotQuicksort(byte[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements using a 5-element sorting network
        byte ae1 = a[e1], ae2 = a[e2], ae3 = a[e3], ae4 = a[e4], ae5 = a[e5];

        if (ae1 > ae2) { byte t = ae1; ae1 = ae2; ae2 = t; }
        if (ae4 > ae5) { byte t = ae4; ae4 = ae5; ae5 = t; }
        if (ae1 > ae3) { byte t = ae1; ae1 = ae3; ae3 = t; }
        if (ae2 > ae3) { byte t = ae2; ae2 = ae3; ae3 = t; }
        if (ae1 > ae4) { byte t = ae1; ae1 = ae4; ae4 = t; }
        if (ae3 > ae4) { byte t = ae3; ae3 = ae4; ae4 = t; }
        if (ae2 > ae5) { byte t = ae2; ae2 = ae5; ae5 = t; }
        if (ae2 > ae3) { byte t = ae2; ae2 = ae3; ae3 = t; }
        if (ae4 > ae5) { byte t = ae4; ae4 = ae5; ae5 = t; }

        a[e1] = ae1; a[e3] = ae3; a[e5] = ae5;

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the elements to be sorted are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        byte pivot1 = ae2; a[e2] = a[left];
        byte pivot2 = ae4; a[e4] = a[right];

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = (pivot1 != pivot2);

        if (pivotsDiffer) {
            /*
             * Partitioning:
             *
             *   left part         center part                    right part
             * +------------------------------------------------------------+
             * | < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2 |
             * +------------------------------------------------------------+
             *              ^                          ^       ^
             *              |                          |       |
             *             less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                byte ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                        a[great--] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way,
             * or "Dutch National Flag", partition:
             *
             *   left part   center part            right part
             * +----------------------------------------------+
             * |  < pivot  |  == pivot  |    ?    |  > pivot  |
             * +----------------------------------------------+
             *              ^            ^       ^
             *              |            |       |
             *             less          k     great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                byte ak = a[k];
                if (ak == pivot1) {
                    continue;
                }
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else { // (a[k] > pivot1) -  Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // a[great] == pivot1
                        a[k] = pivot1;
                        a[great--] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        doSort(a, left,   less - 2);
        doSort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 2/3 of the array),
         * swap internal pivot values to ends
         */
        if (less < e1 && great > e5) {
            while (a[less] == pivot1) {
                less++;
            }
            while (a[great] == pivot2) {
                great--;
            }

            /*
             * Partitioning:
             *
             *   left part       center part                   right part
             * +----------------------------------------------------------+
             * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
             * +----------------------------------------------------------+
             *              ^                        ^       ^
             *              |                        |       |
             *             less                      k     great
             *
             * Invariants:
             *
             *              all in (*, less)  == pivot1
             *     pivot1 < all in [less, k)   < pivot2
             *              all in (great, *) == pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                byte ak = a[k];
                if (ak == pivot2) { // Move a[k] to right part
                    while (a[great] == pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] == pivot1) {
                        a[k] = a[less];
                        a[less++] = pivot1;
                    } else { // pivot1 < a[great] < pivot2
                        a[k] = a[great];
                    }
                    a[great--] = pivot2;
                } else if (ak == pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        doSort(a, less, great);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * <p>The {@code <} relation does not provide a total order on all float
     * values: {@code -0.0f == 0.0f} is {@code true} and a {@code Float.NaN}
     * value compares neither less than, greater than, nor equal to any value,
     * even itself. This method uses the total order imposed by the method
     * {@link Float#compareTo}: {@code -0.0f} is treated as less than value
     * {@code 0.0f} and {@code Float.NaN} is considered greater than any
     * other value and all {@code Float.NaN} values are considered equal.
     *
     * @param a the array to be sorted
     */
    public static void sort(float[] a) {
        sortNegZeroAndNaN(a, 0, a.length - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The range
     * to be sorted extends from the index {@code fromIndex}, inclusive, to
     * the index {@code toIndex}, exclusive. If {@code fromIndex == toIndex},
     * the range to be sorted is empty  and the call is a no-op).
     *
     * <p>The {@code <} relation does not provide a total order on all float
     * values: {@code -0.0f == 0.0f} is {@code true} and a {@code Float.NaN}
     * value compares neither less than, greater than, nor equal to any value,
     * even itself. This method uses the total order imposed by the method
     * {@link Float#compareTo}: {@code -0.0f} is treated as less than value
     * {@code 0.0f} and {@code Float.NaN} is considered greater than any
     * other value and all {@code Float.NaN} values are considered equal.
     *
     * @param a the array to be sorted
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param toIndex the index of the last element, exclusive, to be sorted
     * @throws IllegalArgumentException if {@code fromIndex > toIndex}
     * @throws ArrayIndexOutOfBoundsException
     *     if {@code fromIndex < 0} or {@code toIndex > a.length}
     */
    public static void sort(float[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        sortNegZeroAndNaN(a, fromIndex, toIndex - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The
     * sort is done in three phases to avoid expensive comparisons in the
     * inner loop. The comparisons would be expensive due to anomalies
     * associated with negative zero {@code -0.0f} and {@code Float.NaN}.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void sortNegZeroAndNaN(float[] a, int left, int right) {
        /*
         * Phase 1: Count negative zeros and move NaNs to end of array
         */
        final int NEGATIVE_ZERO = Float.floatToIntBits(-0.0f);
        int numNegativeZeros = 0;
        int n = right;

        for (int k = left; k <= n; k++) {
            float ak = a[k];
            if (ak == 0.0f && NEGATIVE_ZERO == Float.floatToIntBits(ak)) {
                a[k] = 0.0f;
                numNegativeZeros++;
            } else if (ak != ak) { // i.e., ak is NaN
                a[k--] = a[n];
                a[n--] = Float.NaN;
            }
        }

        /*
         * Phase 2: Sort everything except NaNs (which are already in place)
         */
        doSort(a, left, n);

        /*
         * Phase 3: Turn positive zeros back into negative zeros as appropriate
         */
        if (numNegativeZeros == 0) {
            return;
        }

        // Find first zero element
        int zeroIndex = findAnyZero(a, left, n);

        for (int i = zeroIndex - 1; i >= left && a[i] == 0.0f; i--) {
            zeroIndex = i;
        }

        // Turn the right number of positive zeros back into negative zeros
        for (int i = zeroIndex, m = zeroIndex + numNegativeZeros; i < m; i++) {
            a[i] = -0.0f;
        }
    }

    /**
     * Returns the index of some zero element in the specified range via
     * binary search. The range is assumed to be sorted, and must contain
     * at least one zero.
     *
     * @param a the array to be searched
     * @param low the index of the first element, inclusive, to be searched
     * @param high the index of the last element, inclusive, to be searched
     */
    private static int findAnyZero(float[] a, int low, int high) {
        while (true) {
            int middle = (low + high) >>> 1;
            float middleValue = a[middle];

            if (middleValue < 0.0f) {
                low = middle + 1;
            } else if (middleValue > 0.0f) {
                high = middle - 1;
            } else { // middleValue == 0.0f
                return middle;
            }
        }
    }

    /**
     * Sorts the specified range of the array into ascending order. This
     * method differs from the public {@code sort} method in three ways:
     * {@code right} index is inclusive, it does no range checking on
     * {@code left} or {@code right}, and it does not handle negative
     * zeros or NaNs in the array.
     *
     * @param a the array to be sorted, which must not contain -0.0f or NaN
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void doSort(float[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int i = left + 1; i <= right; i++) {
                float ai = a[i];
                int j;
                for (j = i - 1; j >= left && ai < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ai;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void dualPivotQuicksort(float[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements using a 5-element sorting network
        float ae1 = a[e1], ae2 = a[e2], ae3 = a[e3], ae4 = a[e4], ae5 = a[e5];

        if (ae1 > ae2) { float t = ae1; ae1 = ae2; ae2 = t; }
        if (ae4 > ae5) { float t = ae4; ae4 = ae5; ae5 = t; }
        if (ae1 > ae3) { float t = ae1; ae1 = ae3; ae3 = t; }
        if (ae2 > ae3) { float t = ae2; ae2 = ae3; ae3 = t; }
        if (ae1 > ae4) { float t = ae1; ae1 = ae4; ae4 = t; }
        if (ae3 > ae4) { float t = ae3; ae3 = ae4; ae4 = t; }
        if (ae2 > ae5) { float t = ae2; ae2 = ae5; ae5 = t; }
        if (ae2 > ae3) { float t = ae2; ae2 = ae3; ae3 = t; }
        if (ae4 > ae5) { float t = ae4; ae4 = ae5; ae5 = t; }

        a[e1] = ae1; a[e3] = ae3; a[e5] = ae5;

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the elements to be sorted are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        float pivot1 = ae2; a[e2] = a[left];
        float pivot2 = ae4; a[e4] = a[right];

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = (pivot1 != pivot2);

        if (pivotsDiffer) {
            /*
             * Partitioning:
             *
             *   left part         center part                    right part
             * +------------------------------------------------------------+
             * | < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2 |
             * +------------------------------------------------------------+
             *              ^                          ^       ^
             *              |                          |       |
             *             less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                float ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                        a[great--] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way,
             * or "Dutch National Flag", partition:
             *
             *   left part   center part            right part
             * +----------------------------------------------+
             * |  < pivot  |  == pivot  |    ?    |  > pivot  |
             * +----------------------------------------------+
             *              ^            ^       ^
             *              |            |       |
             *             less          k     great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                float ak = a[k];
                if (ak == pivot1) {
                    continue;
                }
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else { // (a[k] > pivot1) -  Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // a[great] == pivot1
                        a[k] = pivot1;
                        a[great--] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        doSort(a, left,   less - 2);
        doSort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 2/3 of the array),
         * swap internal pivot values to ends
         */
        if (less < e1 && great > e5) {
            while (a[less] == pivot1) {
                less++;
            }
            while (a[great] == pivot2) {
                great--;
            }

            /*
             * Partitioning:
             *
             *   left part       center part                   right part
             * +----------------------------------------------------------+
             * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
             * +----------------------------------------------------------+
             *              ^                        ^       ^
             *              |                        |       |
             *             less                      k     great
             *
             * Invariants:
             *
             *              all in (*, less)  == pivot1
             *     pivot1 < all in [less, k)   < pivot2
             *              all in (great, *) == pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                float ak = a[k];
                if (ak == pivot2) { // Move a[k] to right part
                    while (a[great] == pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] == pivot1) {
                        a[k] = a[less];
                        a[less++] = pivot1;
                    } else { // pivot1 < a[great] < pivot2
                        a[k] = a[great];
                    }
                    a[great--] = pivot2;
                } else if (ak == pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        doSort(a, less, great);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * <p>The {@code <} relation does not provide a total order on all double
     * values: {@code -0.0d == 0.0d} is {@code true} and a {@code Double.NaN}
     * value compares neither less than, greater than, nor equal to any value,
     * even itself. This method uses the total order imposed by the method
     * {@link Double#compareTo}: {@code -0.0d} is treated as less than value
     * {@code 0.0d} and {@code Double.NaN} is considered greater than any
     * other value and all {@code Double.NaN} values are considered equal.
     *
     * @param a the array to be sorted
     */
    public static void sort(double[] a) {
        sortNegZeroAndNaN(a, 0, a.length - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The range
     * to be sorted extends from the index {@code fromIndex}, inclusive, to
     * the index {@code toIndex}, exclusive. If {@code fromIndex == toIndex},
     * the range to be sorted is empty (and the call is a no-op).
     *
     * <p>The {@code <} relation does not provide a total order on all double
     * values: {@code -0.0d == 0.0d} is {@code true} and a {@code Double.NaN}
     * value compares neither less than, greater than, nor equal to any value,
     * even itself. This method uses the total order imposed by the method
     * {@link Double#compareTo}: {@code -0.0d} is treated as less than value
     * {@code 0.0d} and {@code Double.NaN} is considered greater than any
     * other value and all {@code Double.NaN} values are considered equal.
     *
     * @param a the array to be sorted
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param toIndex the index of the last element, exclusive, to be sorted
     * @throws IllegalArgumentException if {@code fromIndex > toIndex}
     * @throws ArrayIndexOutOfBoundsException
     *     if {@code fromIndex < 0} or {@code toIndex > a.length}
     */
    public static void sort(double[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        sortNegZeroAndNaN(a, fromIndex, toIndex - 1);
    }

    /**
     * Sorts the specified range of the array into ascending order. The
     * sort is done in three phases to avoid expensive comparisons in the
     * inner loop. The comparisons would be expensive due to anomalies
     * associated with negative zero {@code -0.0d} and {@code Double.NaN}.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void sortNegZeroAndNaN(double[] a, int left, int right) {
        /*
         * Phase 1: Count negative zeros and move NaNs to end of array
         */
        final long NEGATIVE_ZERO = Double.doubleToLongBits(-0.0d);
        int numNegativeZeros = 0;
        int n = right;

        for (int k = left; k <= n; k++) {
            double ak = a[k];
            if (ak == 0.0d && NEGATIVE_ZERO == Double.doubleToLongBits(ak)) {
                a[k] = 0.0d;
                numNegativeZeros++;
            } else if (ak != ak) { // i.e., ak is NaN
                a[k--] = a[n];
                a[n--] = Double.NaN;
            }
        }

        /*
         * Phase 2: Sort everything except NaNs (which are already in place)
         */
        doSort(a, left, n);

        /*
         * Phase 3: Turn positive zeros back into negative zeros as appropriate
         */
        if (numNegativeZeros == 0) {
            return;
        }

        // Find first zero element
        int zeroIndex = findAnyZero(a, left, n);

        for (int i = zeroIndex - 1; i >= left && a[i] == 0.0d; i--) {
            zeroIndex = i;
        }

        // Turn the right number of positive zeros back into negative zeros
        for (int i = zeroIndex, m = zeroIndex + numNegativeZeros; i < m; i++) {
            a[i] = -0.0d;
        }
    }

    /**
     * Returns the index of some zero element in the specified range via
     * binary search. The range is assumed to be sorted, and must contain
     * at least one zero.
     *
     * @param a the array to be searched
     * @param low the index of the first element, inclusive, to be searched
     * @param high the index of the last element, inclusive, to be searched
     */
    private static int findAnyZero(double[] a, int low, int high) {
        while (true) {
            int middle = (low + high) >>> 1;
            double middleValue = a[middle];

            if (middleValue < 0.0d) {
                low = middle + 1;
            } else if (middleValue > 0.0d) {
                high = middle - 1;
            } else { // middleValue == 0.0d
                return middle;
            }
        }
    }

    /**
     * Sorts the specified range of the array into ascending order. This
     * method differs from the public {@code sort} method in three ways:
     * {@code right} index is inclusive, it does no range checking on
     * {@code left} or {@code right}, and it does not handle negative
     * zeros or NaNs in the array.
     *
     * @param a the array to be sorted, which must not contain -0.0d and NaN
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void doSort(double[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int i = left + 1; i <= right; i++) {
                double ai = a[i];
                int j;
                for (j = i - 1; j >= left && ai < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ai;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void dualPivotQuicksort(double[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements using a 5-element sorting network
        double ae1 = a[e1], ae2 = a[e2], ae3 = a[e3], ae4 = a[e4], ae5 = a[e5];

        if (ae1 > ae2) { double t = ae1; ae1 = ae2; ae2 = t; }
        if (ae4 > ae5) { double t = ae4; ae4 = ae5; ae5 = t; }
        if (ae1 > ae3) { double t = ae1; ae1 = ae3; ae3 = t; }
        if (ae2 > ae3) { double t = ae2; ae2 = ae3; ae3 = t; }
        if (ae1 > ae4) { double t = ae1; ae1 = ae4; ae4 = t; }
        if (ae3 > ae4) { double t = ae3; ae3 = ae4; ae4 = t; }
        if (ae2 > ae5) { double t = ae2; ae2 = ae5; ae5 = t; }
        if (ae2 > ae3) { double t = ae2; ae2 = ae3; ae3 = t; }
        if (ae4 > ae5) { double t = ae4; ae4 = ae5; ae5 = t; }

        a[e1] = ae1; a[e3] = ae3; a[e5] = ae5;

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the elements to be sorted are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        double pivot1 = ae2; a[e2] = a[left];
        double pivot2 = ae4; a[e4] = a[right];

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = (pivot1 != pivot2);

        if (pivotsDiffer) {
            /*
             * Partitioning:
             *
             *   left part         center part                    right part
             * +------------------------------------------------------------+
             * | < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2 |
             * +------------------------------------------------------------+
             *              ^                          ^       ^
             *              |                          |       |
             *             less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                double ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                        a[great--] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way,
             * or "Dutch National Flag", partition:
             *
             *   left part   center part            right part
             * +----------------------------------------------+
             * |  < pivot  |  == pivot  |    ?    |  > pivot  |
             * +----------------------------------------------+
             *              ^            ^       ^
             *              |            |       |
             *             less          k     great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                double ak = a[k];
                if (ak == pivot1) {
                    continue;
                }
                if (ak < pivot1) { // Move a[k] to left part
                    if (k != less) {
                        a[k] = a[less];
                        a[less] = ak;
                    }
                    less++;
                } else { // (a[k] > pivot1) -  Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less++] = a[great];
                        a[great--] = ak;
                    } else { // a[great] == pivot1
                        a[k] = pivot1;
                        a[great--] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        doSort(a, left,   less - 2);
        doSort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 2/3 of the array),
         * swap internal pivot values to ends
         */
        if (less < e1 && great > e5) {
            while (a[less] == pivot1) {
                less++;
            }
            while (a[great] == pivot2) {
                great--;
            }

            /*
             * Partitioning:
             *
             *   left part       center part                   right part
             * +----------------------------------------------------------+
             * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
             * +----------------------------------------------------------+
             *              ^                        ^       ^
             *              |                        |       |
             *             less                      k     great
             *
             * Invariants:
             *
             *              all in (*, less)  == pivot1
             *     pivot1 < all in [less, k)   < pivot2
             *              all in (great, *) == pivot2
             *
             * Pointer k is the first index of ?-part
             */
            outer:
            for (int k = less; k <= great; k++) {
                double ak = a[k];
                if (ak == pivot2) { // Move a[k] to right part
                    while (a[great] == pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] == pivot1) {
                        a[k] = a[less];
                        a[less++] = pivot1;
                    } else { // pivot1 < a[great] < pivot2
                        a[k] = a[great];
                    }
                    a[great--] = pivot2;
                } else if (ak == pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        doSort(a, less, great);
    }

    /**
     * Checks that {@code fromIndex} and {@code toIndex} are in
     * the range and throws an appropriate exception, if they aren't.
     */
    private static void rangeCheck(int length, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                "fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > length) {
            throw new ArrayIndexOutOfBoundsException(toIndex);
        }
    }
}
