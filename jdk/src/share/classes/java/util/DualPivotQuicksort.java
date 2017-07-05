/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class implements the Dual-Pivot Quicksort algorithm by
 * Vladimir Yaroslavskiy, Jon Bentley, and Josh Bloch. The algorithm
 * offers O(n log(n)) performance on many data sets that cause other
 * quicksorts to degrade to quadratic performance, and is typically
 * faster than traditional (one-pivot) Quicksort implementations.
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 *
 * @version 2010.06.21 m765.827.12i:5\7
 * @since 1.7
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
     * Sorting methods for seven primitive types.
     */

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(int[] a) {
        sort(a, 0, a.length - 1, true);
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
        sort(a, fromIndex, toIndex - 1, true);
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm. This method differs from the public
     * {@code sort} method in that the {@code right} index is inclusive,
     * it does no range checking on {@code left} or {@code right}, and has
     * boolean flag whether insertion sort with sentinel is used or not.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if the part is the most left in the range
     */
    private static void sort(int[] a, int left, int right, boolean leftmost) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (!leftmost) {
                /*
                 * Every element in adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid
                 * the j >= left check on each iteration.
                 */
                for (int j, i = left + 1; i <= right; i++) {
                    int ai = a[i];
                    for (j = i - 1; ai < a[j]; j--) {
                        // assert j >= left;
                        a[j + 1] = a[j];
                    }
                    a[j + 1] = ai;
                }
            } else {
                /*
                 * For case of leftmost part traditional (without a sentinel)
                 * insertion sort, optimized for server JVM, is used.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    int ai = a[i + 1];
                    while (ai < a[j]) {
                        a[j + 1] = a[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    a[j + 1] = ai;
                }
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >>> 3) + (length >>> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (a[e2] < a[e1]) { int t = a[e2]; a[e2] = a[e1]; a[e1] = t; }

        if (a[e3] < a[e2]) { int t = a[e3]; a[e3] = a[e2]; a[e2] = t;
            if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
        }
        if (a[e4] < a[e3]) { int t = a[e4]; a[e4] = a[e3]; a[e3] = t;
            if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
            }
        }
        if (a[e5] < a[e4]) { int t = a[e5]; a[e5] = a[e4]; a[e4] = t;
            if (t < a[e3]) { a[e4] = a[e3]; a[e3] = t;
                if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                    if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
                }
            }
        }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         */
        int pivot1 = a[e2];
        int pivot2 = a[e4];

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (pivot1 != pivot2) {
            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            a[e2] = a[left];
            a[e4] = a[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (a[++less] < pivot1);
            while (a[--great] > pivot2);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less; k <= great; k++) {
                int ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Swap pivots into their final positions
            a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
            a[right] = a[great + 1]; a[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost);
            sort(a, great + 2, right, false);

            /*
             * If center part is too large (comprises > 5/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (a[less] == pivot1) {
                    less++;
                }
                while (a[great] == pivot2) {
                    great--;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less; k <= great; k++) {
                    int ak = a[k];
                    if (ak == pivot1) { // Move a[k] to left part
                        a[k] = a[less];
                        a[less] = ak;
                        less++;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (a[great] == pivot2) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (a[great] == pivot1) {
                            a[k] = a[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            a[less] = pivot1;
                            less++;
                        } else { // pivot1 < a[great] < pivot2
                            a[k] = a[great];
                        }
                        a[great] = ak;
                        great--;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false);

        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = left; k <= great; k++) {
                if (a[k] == pivot1) {
                    continue;
                }
                int ak = a[k];

                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else { // a[k] > pivot1 - Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        // assert great > k;
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // a[great] == pivot1
                        /*
                         * Even though a[great] equals to pivot1, the
                         * assignment a[k] = pivot1 may be incorrect,
                         * if a[great] and pivot1 are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        a[k] = pivot1;
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Sort left and right parts recursively
            sort(a, left, less - 1, leftmost);
            sort(a, great + 1, right, false);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(long[] a) {
        sort(a, 0, a.length - 1, true);
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
        sort(a, fromIndex, toIndex - 1, true);
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm. This method differs from the public
     * {@code sort} method in that the {@code right} index is inclusive,
     * it does no range checking on {@code left} or {@code right}, and has
     * boolean flag whether insertion sort with sentinel is used or not.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if the part is the most left in the range
     */
    private static void sort(long[] a, int left, int right, boolean leftmost) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (!leftmost) {
                /*
                 * Every element in adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid
                 * the j >= left check on each iteration.
                 */
                for (int j, i = left + 1; i <= right; i++) {
                    long ai = a[i];
                    for (j = i - 1; ai < a[j]; j--) {
                        // assert j >= left;
                        a[j + 1] = a[j];
                    }
                    a[j + 1] = ai;
                }
            } else {
                /*
                 * For case of leftmost part traditional (without a sentinel)
                 * insertion sort, optimized for server JVM, is used.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    long ai = a[i + 1];
                    while (ai < a[j]) {
                        a[j + 1] = a[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    a[j + 1] = ai;
                }
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >>> 3) + (length >>> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (a[e2] < a[e1]) { long t = a[e2]; a[e2] = a[e1]; a[e1] = t; }

        if (a[e3] < a[e2]) { long t = a[e3]; a[e3] = a[e2]; a[e2] = t;
            if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
        }
        if (a[e4] < a[e3]) { long t = a[e4]; a[e4] = a[e3]; a[e3] = t;
            if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
            }
        }
        if (a[e5] < a[e4]) { long t = a[e5]; a[e5] = a[e4]; a[e4] = t;
            if (t < a[e3]) { a[e4] = a[e3]; a[e3] = t;
                if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                    if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
                }
            }
        }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         */
        long pivot1 = a[e2];
        long pivot2 = a[e4];

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (pivot1 != pivot2) {
            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            a[e2] = a[left];
            a[e4] = a[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (a[++less] < pivot1);
            while (a[--great] > pivot2);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less; k <= great; k++) {
                long ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Swap pivots into their final positions
            a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
            a[right] = a[great + 1]; a[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost);
            sort(a, great + 2, right, false);

            /*
             * If center part is too large (comprises > 5/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (a[less] == pivot1) {
                    less++;
                }
                while (a[great] == pivot2) {
                    great--;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less; k <= great; k++) {
                    long ak = a[k];
                    if (ak == pivot1) { // Move a[k] to left part
                        a[k] = a[less];
                        a[less] = ak;
                        less++;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (a[great] == pivot2) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (a[great] == pivot1) {
                            a[k] = a[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            a[less] = pivot1;
                            less++;
                        } else { // pivot1 < a[great] < pivot2
                            a[k] = a[great];
                        }
                        a[great] = ak;
                        great--;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false);

        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = left; k <= great; k++) {
                if (a[k] == pivot1) {
                    continue;
                }
                long ak = a[k];

                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else { // a[k] > pivot1 - Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        // assert great > k;
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // a[great] == pivot1
                        /*
                         * Even though a[great] equals to pivot1, the
                         * assignment a[k] = pivot1 may be incorrect,
                         * if a[great] and pivot1 are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        a[k] = pivot1;
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Sort left and right parts recursively
            sort(a, left, less - 1, leftmost);
            sort(a, great + 1, right, false);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(short[] a) {
        if (a.length > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            countingSort(a, 0, a.length - 1);
        } else {
            sort(a, 0, a.length - 1, true);
        }
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

        if (toIndex - fromIndex > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            countingSort(a, fromIndex, toIndex - 1);
        } else {
            sort(a, fromIndex, toIndex - 1, true);
        }
    }

    /** The number of distinct short values. */
    private static final int NUM_SHORT_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array by counting sort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void countingSort(short[] a, int left, int right) {
        int[] count = new int[NUM_SHORT_VALUES];

        for (int i = left; i <= right; i++) {
            count[a[i] - Short.MIN_VALUE]++;
        }
        for (int i = NUM_SHORT_VALUES - 1, k = right; k >= left; i--) {
            while (count[i] == 0) {
                i--;
            }
            short value = (short) (i + Short.MIN_VALUE);
            int s = count[i];

            do {
                a[k--] = value;
            } while (--s > 0);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm. This method differs from the public
     * {@code sort} method in that the {@code right} index is inclusive,
     * it does no range checking on {@code left} or {@code right}, and has
     * boolean flag whether insertion sort with sentinel is used or not.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if the part is the most left in the range
     */
    private static void sort(short[] a, int left, int right,boolean leftmost) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (!leftmost) {
                /*
                 * Every element in adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid
                 * the j >= left check on each iteration.
                 */
                for (int j, i = left + 1; i <= right; i++) {
                    short ai = a[i];
                    for (j = i - 1; ai < a[j]; j--) {
                        // assert j >= left;
                        a[j + 1] = a[j];
                    }
                    a[j + 1] = ai;
                }
            } else {
                /*
                 * For case of leftmost part traditional (without a sentinel)
                 * insertion sort, optimized for server JVM, is used.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    short ai = a[i + 1];
                    while (ai < a[j]) {
                        a[j + 1] = a[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    a[j + 1] = ai;
                }
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >>> 3) + (length >>> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (a[e2] < a[e1]) { short t = a[e2]; a[e2] = a[e1]; a[e1] = t; }

        if (a[e3] < a[e2]) { short t = a[e3]; a[e3] = a[e2]; a[e2] = t;
            if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
        }
        if (a[e4] < a[e3]) { short t = a[e4]; a[e4] = a[e3]; a[e3] = t;
            if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
            }
        }
        if (a[e5] < a[e4]) { short t = a[e5]; a[e5] = a[e4]; a[e4] = t;
            if (t < a[e3]) { a[e4] = a[e3]; a[e3] = t;
                if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                    if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
                }
            }
        }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         */
        short pivot1 = a[e2];
        short pivot2 = a[e4];

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (pivot1 != pivot2) {
            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            a[e2] = a[left];
            a[e4] = a[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (a[++less] < pivot1);
            while (a[--great] > pivot2);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less; k <= great; k++) {
                short ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Swap pivots into their final positions
            a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
            a[right] = a[great + 1]; a[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost);
            sort(a, great + 2, right, false);

            /*
             * If center part is too large (comprises > 5/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (a[less] == pivot1) {
                    less++;
                }
                while (a[great] == pivot2) {
                    great--;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less; k <= great; k++) {
                    short ak = a[k];
                    if (ak == pivot1) { // Move a[k] to left part
                        a[k] = a[less];
                        a[less] = ak;
                        less++;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (a[great] == pivot2) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (a[great] == pivot1) {
                            a[k] = a[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            a[less] = pivot1;
                            less++;
                        } else { // pivot1 < a[great] < pivot2
                            a[k] = a[great];
                        }
                        a[great] = ak;
                        great--;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false);

        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = left; k <= great; k++) {
                if (a[k] == pivot1) {
                    continue;
                }
                short ak = a[k];

                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else { // a[k] > pivot1 - Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        // assert great > k;
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // a[great] == pivot1
                        /*
                         * Even though a[great] equals to pivot1, the
                         * assignment a[k] = pivot1 may be incorrect,
                         * if a[great] and pivot1 are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        a[k] = pivot1;
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Sort left and right parts recursively
            sort(a, left, less - 1, leftmost);
            sort(a, great + 1, right, false);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(char[] a) {
        if (a.length > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            countingSort(a, 0, a.length - 1);
        } else {
            sort(a, 0, a.length - 1, true);
        }
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

        if (toIndex - fromIndex > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            countingSort(a, fromIndex, toIndex - 1);
        } else {
            sort(a, fromIndex, toIndex - 1, true);
        }
    }

    /** The number of distinct char values. */
    private static final int NUM_CHAR_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array by counting sort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void countingSort(char[] a, int left, int right) {
        int[] count = new int[NUM_CHAR_VALUES];

        for (int i = left; i <= right; i++) {
            count[a[i]]++;
        }
        for (int i = 0, k = left; k <= right; i++) {
            while (count[i] == 0) {
                i++;
            }
            char value = (char) i;
            int s = count[i];

            do {
                a[k++] = value;
            } while (--s > 0);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm. This method differs from the public
     * {@code sort} method in that the {@code right} index is inclusive,
     * it does no range checking on {@code left} or {@code right}, and has
     * boolean flag whether insertion sort with sentinel is used or not.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if the part is the most left in the range
     */
    private static void sort(char[] a, int left, int right, boolean leftmost) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (!leftmost) {
                /*
                 * Every element in adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid
                 * the j >= left check on each iteration.
                 */
                for (int j, i = left + 1; i <= right; i++) {
                    char ai = a[i];
                    for (j = i - 1; ai < a[j]; j--) {
                        // assert j >= left;
                        a[j + 1] = a[j];
                    }
                    a[j + 1] = ai;
                }
            } else {
                /*
                 * For case of leftmost part traditional (without a sentinel)
                 * insertion sort, optimized for server JVM, is used.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    char ai = a[i + 1];
                    while (ai < a[j]) {
                        a[j + 1] = a[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    a[j + 1] = ai;
                }
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >>> 3) + (length >>> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (a[e2] < a[e1]) { char t = a[e2]; a[e2] = a[e1]; a[e1] = t; }

        if (a[e3] < a[e2]) { char t = a[e3]; a[e3] = a[e2]; a[e2] = t;
            if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
        }
        if (a[e4] < a[e3]) { char t = a[e4]; a[e4] = a[e3]; a[e3] = t;
            if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
            }
        }
        if (a[e5] < a[e4]) { char t = a[e5]; a[e5] = a[e4]; a[e4] = t;
            if (t < a[e3]) { a[e4] = a[e3]; a[e3] = t;
                if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                    if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
                }
            }
        }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         */
        char pivot1 = a[e2];
        char pivot2 = a[e4];

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (pivot1 != pivot2) {
            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            a[e2] = a[left];
            a[e4] = a[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (a[++less] < pivot1);
            while (a[--great] > pivot2);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less; k <= great; k++) {
                char ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Swap pivots into their final positions
            a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
            a[right] = a[great + 1]; a[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost);
            sort(a, great + 2, right, false);

            /*
             * If center part is too large (comprises > 5/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (a[less] == pivot1) {
                    less++;
                }
                while (a[great] == pivot2) {
                    great--;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less; k <= great; k++) {
                    char ak = a[k];
                    if (ak == pivot1) { // Move a[k] to left part
                        a[k] = a[less];
                        a[less] = ak;
                        less++;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (a[great] == pivot2) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (a[great] == pivot1) {
                            a[k] = a[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            a[less] = pivot1;
                            less++;
                        } else { // pivot1 < a[great] < pivot2
                            a[k] = a[great];
                        }
                        a[great] = ak;
                        great--;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false);

        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = left; k <= great; k++) {
                if (a[k] == pivot1) {
                    continue;
                }
                char ak = a[k];

                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else { // a[k] > pivot1 - Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        // assert great > k;
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // a[great] == pivot1
                        /*
                         * Even though a[great] equals to pivot1, the
                         * assignment a[k] = pivot1 may be incorrect,
                         * if a[great] and pivot1 are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        a[k] = pivot1;
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Sort left and right parts recursively
            sort(a, left, less - 1, leftmost);
            sort(a, great + 1, right, false);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     *
     * @param a the array to be sorted
     */
    public static void sort(byte[] a) {
        if (a.length > COUNTING_SORT_THRESHOLD_FOR_BYTE) {
            countingSort(a, 0, a.length - 1);
        } else {
            sort(a, 0, a.length - 1, true);
        }
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

        if (toIndex - fromIndex > COUNTING_SORT_THRESHOLD_FOR_BYTE) {
            countingSort(a, fromIndex, toIndex - 1);
        } else {
            sort(a, fromIndex, toIndex - 1, true);
        }
    }

    /** The number of distinct byte values. */
    private static final int NUM_BYTE_VALUES = 1 << 8;

    /**
     * Sorts the specified range of the array by counting sort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     */
    private static void countingSort(byte[] a, int left, int right) {
        int[] count = new int[NUM_BYTE_VALUES];

        for (int i = left; i <= right; i++) {
            count[a[i] - Byte.MIN_VALUE]++;
        }
        for (int i = NUM_BYTE_VALUES - 1, k = right; k >= left; i--) {
            while (count[i] == 0) {
                i--;
            }
            byte value = (byte) (i + Byte.MIN_VALUE);
            int s = count[i];

            do {
                a[k--] = value;
            } while (--s > 0);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm. This method differs from the public
     * {@code sort} method in that the {@code right} index is inclusive,
     * it does no range checking on {@code left} or {@code right}, and has
     * boolean flag whether insertion sort with sentinel is used or not.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if the part is the most left in the range
     */
    private static void sort(byte[] a, int left, int right, boolean leftmost) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (!leftmost) {
                /*
                 * Every element in adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid
                 * the j >= left check on each iteration.
                 */
                for (int j, i = left + 1; i <= right; i++) {
                    byte ai = a[i];
                    for (j = i - 1; ai < a[j]; j--) {
                        // assert j >= left;
                        a[j + 1] = a[j];
                    }
                    a[j + 1] = ai;
                }
            } else {
                /*
                 * For case of leftmost part traditional (without a sentinel)
                 * insertion sort, optimized for server JVM, is used.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    byte ai = a[i + 1];
                    while (ai < a[j]) {
                        a[j + 1] = a[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    a[j + 1] = ai;
                }
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >>> 3) + (length >>> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (a[e2] < a[e1]) { byte t = a[e2]; a[e2] = a[e1]; a[e1] = t; }

        if (a[e3] < a[e2]) { byte t = a[e3]; a[e3] = a[e2]; a[e2] = t;
            if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
        }
        if (a[e4] < a[e3]) { byte t = a[e4]; a[e4] = a[e3]; a[e3] = t;
            if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
            }
        }
        if (a[e5] < a[e4]) { byte t = a[e5]; a[e5] = a[e4]; a[e4] = t;
            if (t < a[e3]) { a[e4] = a[e3]; a[e3] = t;
                if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                    if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
                }
            }
        }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         */
        byte pivot1 = a[e2];
        byte pivot2 = a[e4];

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (pivot1 != pivot2) {
            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            a[e2] = a[left];
            a[e4] = a[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (a[++less] < pivot1);
            while (a[--great] > pivot2);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less; k <= great; k++) {
                byte ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Swap pivots into their final positions
            a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
            a[right] = a[great + 1]; a[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost);
            sort(a, great + 2, right, false);

            /*
             * If center part is too large (comprises > 5/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (a[less] == pivot1) {
                    less++;
                }
                while (a[great] == pivot2) {
                    great--;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less; k <= great; k++) {
                    byte ak = a[k];
                    if (ak == pivot1) { // Move a[k] to left part
                        a[k] = a[less];
                        a[less] = ak;
                        less++;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (a[great] == pivot2) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (a[great] == pivot1) {
                            a[k] = a[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            a[less] = pivot1;
                            less++;
                        } else { // pivot1 < a[great] < pivot2
                            a[k] = a[great];
                        }
                        a[great] = ak;
                        great--;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false);

        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = left; k <= great; k++) {
                if (a[k] == pivot1) {
                    continue;
                }
                byte ak = a[k];

                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else { // a[k] > pivot1 - Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        // assert great > k;
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // a[great] == pivot1
                        /*
                         * Even though a[great] equals to pivot1, the
                         * assignment a[k] = pivot1 may be incorrect,
                         * if a[great] and pivot1 are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        a[k] = pivot1;
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Sort left and right parts recursively
            sort(a, left, less - 1, leftmost);
            sort(a, great + 1, right, false);
        }
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
     * the range to be sorted is empty (and the call is a no-op).
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
         * Phase 1: Move NaNs to the end of the array.
         */
        while (left <= right && Float.isNaN(a[right])) {
            right--;
        }
        for (int k = right - 1; k >= left; k--) {
            float ak = a[k];
            if (ak != ak) { // a[k] is NaN
                a[k] = a[right];
                a[right] = ak;
                right--;
            }
        }

        /*
         * Phase 2: Sort everything except NaNs (which are already in place).
         */
        sort(a, left, right, true);

        /*
         * Phase 3: Place negative zeros before positive zeros.
         */
        int hi = right;

        /*
         * Search first zero, or first positive, or last negative element.
         */
        while (left < hi) {
            int middle = (left + hi) >>> 1;
            float middleValue = a[middle];

            if (middleValue < 0.0f) {
                left = middle + 1;
            } else {
                hi = middle;
            }
        }

        /*
         * Skip the last negative value (if any) or all leading negative zeros.
         */
        while (left <= right && Float.floatToRawIntBits(a[left]) < 0) {
            left++;
        }

        /*
         * Move negative zeros to the beginning of the sub-range.
         *
         * Partitioning:
         *
         * +---------------------------------------------------+
         * |   < 0.0   |   -0.0   |    0.0    |  ?  ( >= 0.0 ) |
         * +---------------------------------------------------+
         *              ^          ^           ^
         *              |          |           |
         *             left        p           k
         *
         * Invariants:
         *
         *   all in (*,  left)  <  0.0
         *   all in [left,  p) == -0.0
         *   all in [p,     k) ==  0.0
         *   all in [k, right] >=  0.0
         *
         * Pointer k is the first index of ?-part.
         */
        for (int k = left + 1, p = left; k <= right; k++) {
            float ak = a[k];
            if (ak != 0.0f) {
                break;
            }
            if (Float.floatToRawIntBits(ak) < 0) { // ak is -0.0f
                a[k] = 0.0f;
                a[p++] = -0.0f;
            }
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm. This method differs from the public
     * {@code sort} method in that the {@code right} index is inclusive,
     * it does no range checking on {@code left} or {@code right}, and has
     * boolean flag whether insertion sort with sentinel is used or not.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if the part is the most left in the range
     */
    private static void sort(float[] a, int left, int right,boolean leftmost) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (!leftmost) {
                /*
                 * Every element in adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid
                 * the j >= left check on each iteration.
                 */
                for (int j, i = left + 1; i <= right; i++) {
                    float ai = a[i];
                    for (j = i - 1; ai < a[j]; j--) {
                        // assert j >= left;
                        a[j + 1] = a[j];
                    }
                    a[j + 1] = ai;
                }
            } else {
                /*
                 * For case of leftmost part traditional (without a sentinel)
                 * insertion sort, optimized for server JVM, is used.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    float ai = a[i + 1];
                    while (ai < a[j]) {
                        a[j + 1] = a[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    a[j + 1] = ai;
                }
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >>> 3) + (length >>> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (a[e2] < a[e1]) { float t = a[e2]; a[e2] = a[e1]; a[e1] = t; }

        if (a[e3] < a[e2]) { float t = a[e3]; a[e3] = a[e2]; a[e2] = t;
            if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
        }
        if (a[e4] < a[e3]) { float t = a[e4]; a[e4] = a[e3]; a[e3] = t;
            if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
            }
        }
        if (a[e5] < a[e4]) { float t = a[e5]; a[e5] = a[e4]; a[e4] = t;
            if (t < a[e3]) { a[e4] = a[e3]; a[e3] = t;
                if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                    if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
                }
            }
        }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         */
        float pivot1 = a[e2];
        float pivot2 = a[e4];

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (pivot1 != pivot2) {
            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            a[e2] = a[left];
            a[e4] = a[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (a[++less] < pivot1);
            while (a[--great] > pivot2);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less; k <= great; k++) {
                float ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Swap pivots into their final positions
            a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
            a[right] = a[great + 1]; a[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost);
            sort(a, great + 2, right, false);

            /*
             * If center part is too large (comprises > 5/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (a[less] == pivot1) {
                    less++;
                }
                while (a[great] == pivot2) {
                    great--;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less; k <= great; k++) {
                    float ak = a[k];
                    if (ak == pivot1) { // Move a[k] to left part
                        a[k] = a[less];
                        a[less] = ak;
                        less++;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (a[great] == pivot2) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (a[great] == pivot1) {
                            a[k] = a[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            a[less] = a[great];
                            less++;
                        } else { // pivot1 < a[great] < pivot2
                            a[k] = a[great];
                        }
                        a[great] = ak;
                        great--;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false);

        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = left; k <= great; k++) {
                if (a[k] == pivot1) {
                    continue;
                }
                float ak = a[k];

                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else { // a[k] > pivot1 - Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        // assert great > k;
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // a[great] == pivot1
                        /*
                         * Even though a[great] equals to pivot1, the
                         * assignment a[k] = pivot1 may be incorrect,
                         * if a[great] and pivot1 are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Sort left and right parts recursively
            sort(a, left, less - 1, leftmost);
            sort(a, great + 1, right, false);
        }
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
         * Phase 1: Move NaNs to the end of the array.
         */
        while (left <= right && Double.isNaN(a[right])) {
            right--;
        }
        for (int k = right - 1; k >= left; k--) {
            double ak = a[k];
            if (ak != ak) { // a[k] is NaN
                a[k] = a[right];
                a[right] = ak;
                right--;
            }
        }

        /*
         * Phase 2: Sort everything except NaNs (which are already in place).
         */
        sort(a, left, right, true);

        /*
         * Phase 3: Place negative zeros before positive zeros.
         */
        int hi = right;

        /*
         * Search first zero, or first positive, or last negative element.
         */
        while (left < hi) {
            int middle = (left + hi) >>> 1;
            double middleValue = a[middle];

            if (middleValue < 0.0d) {
                left = middle + 1;
            } else {
                hi = middle;
            }
        }

        /*
         * Skip the last negative value (if any) or all leading negative zeros.
         */
        while (left <= right && Double.doubleToRawLongBits(a[left]) < 0) {
            left++;
        }

        /*
         * Move negative zeros to the beginning of the sub-range.
         *
         * Partitioning:
         *
         * +---------------------------------------------------+
         * |   < 0.0   |   -0.0   |    0.0    |  ?  ( >= 0.0 ) |
         * +---------------------------------------------------+
         *              ^          ^           ^
         *              |          |           |
         *             left        p           k
         *
         * Invariants:
         *
         *   all in (*,  left)  <  0.0
         *   all in [left,  p) == -0.0
         *   all in [p,     k) ==  0.0
         *   all in [k, right] >=  0.0
         *
         * Pointer k is the first index of ?-part.
         */
        for (int k = left + 1, p = left; k <= right; k++) {
            double ak = a[k];
            if (ak != 0.0d) {
                break;
            }
            if (Double.doubleToRawLongBits(ak) < 0) { // ak is -0.0d
                a[k] = 0.0d;
                a[p++] = -0.0d;
            }
        }
    }

    /**
     * Sorts the specified range of the array into ascending order by the
     * Dual-Pivot Quicksort algorithm. This method differs from the public
     * {@code sort} method in that the {@code right} index is inclusive,
     * it does no range checking on {@code left} or {@code right}, and has
     * boolean flag whether insertion sort with sentinel is used or not.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if the part is the most left in the range
     */
    private static void sort(double[] a, int left,int right,boolean leftmost) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (!leftmost) {
                /*
                 * Every element in adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid
                 * the j >= left check on each iteration.
                 */
                for (int j, i = left + 1; i <= right; i++) {
                    double ai = a[i];
                    for (j = i - 1; ai < a[j]; j--) {
                        // assert j >= left;
                        a[j + 1] = a[j];
                    }
                    a[j + 1] = ai;
                }
            } else {
                /*
                 * For case of leftmost part traditional (without a sentinel)
                 * insertion sort, optimized for server JVM, is used.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    double ai = a[i + 1];
                    while (ai < a[j]) {
                        a[j + 1] = a[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    a[j + 1] = ai;
                }
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >>> 3) + (length >>> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (a[e2] < a[e1]) { double t = a[e2]; a[e2] = a[e1]; a[e1] = t; }

        if (a[e3] < a[e2]) { double t = a[e3]; a[e3] = a[e2]; a[e2] = t;
            if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
        }
        if (a[e4] < a[e3]) { double t = a[e4]; a[e4] = a[e3]; a[e3] = t;
            if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
            }
        }
        if (a[e5] < a[e4]) { double t = a[e5]; a[e5] = a[e4]; a[e4] = t;
            if (t < a[e3]) { a[e4] = a[e3]; a[e3] = t;
                if (t < a[e2]) { a[e3] = a[e2]; a[e2] = t;
                    if (t < a[e1]) { a[e2] = a[e1]; a[e1] = t; }
                }
            }
        }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         */
        double pivot1 = a[e2];
        double pivot2 = a[e4];

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (pivot1 != pivot2) {
            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            a[e2] = a[left];
            a[e4] = a[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (a[++less] < pivot1);
            while (a[--great] > pivot2);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less; k <= great; k++) {
                double ak = a[k];
                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else if (ak > pivot2) { // Move a[k] to right part
                    while (a[great] > pivot2) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // pivot1 <= a[great] <= pivot2
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Swap pivots into their final positions
            a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
            a[right] = a[great + 1]; a[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost);
            sort(a, great + 2, right, false);

            /*
             * If center part is too large (comprises > 5/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (a[less] == pivot1) {
                    less++;
                }
                while (a[great] == pivot2) {
                    great--;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less; k <= great; k++) {
                    double ak = a[k];
                    if (ak == pivot1) { // Move a[k] to left part
                        a[k] = a[less];
                        a[less] = ak;
                        less++;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (a[great] == pivot2) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (a[great] == pivot1) {
                            a[k] = a[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            a[less] = a[great];
                            less++;
                        } else { // pivot1 < a[great] < pivot2
                            a[k] = a[great];
                        }
                        a[great] = ak;
                        great--;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false);

        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = left; k <= great; k++) {
                if (a[k] == pivot1) {
                    continue;
                }
                double ak = a[k];

                if (ak < pivot1) { // Move a[k] to left part
                    a[k] = a[less];
                    a[less] = ak;
                    less++;
                } else { // a[k] > pivot1 - Move a[k] to right part
                    /*
                     * We know that pivot1 == a[e3] == pivot2. Thus, we know
                     * that great will still be >= k when the following loop
                     * terminates, even though we don't test for it explicitly.
                     * In other words, a[e3] acts as a sentinel for great.
                     */
                    while (a[great] > pivot1) {
                        // assert great > k;
                        great--;
                    }
                    if (a[great] < pivot1) {
                        a[k] = a[less];
                        a[less] = a[great];
                        less++;
                    } else { // a[great] == pivot1
                        /*
                         * Even though a[great] equals to pivot1, the
                         * assignment a[k] = pivot1 may be incorrect,
                         * if a[great] and pivot1 are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        a[k] = a[great];
                    }
                    a[great] = ak;
                    great--;
                }
            }

            // Sort left and right parts recursively
            sort(a, left, less - 1, leftmost);
            sort(a, great + 1, right, false);
        }
    }

    /**
     * Checks that {@code fromIndex} and {@code toIndex} are in the range,
     * otherwise throws an appropriate exception.
     */
    private static void rangeCheck(int length, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                "fromIndex: " + fromIndex + " > toIndex: " + toIndex);
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > length) {
            throw new ArrayIndexOutOfBoundsException(toIndex);
        }
    }
}
