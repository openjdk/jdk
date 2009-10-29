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
 * @version 2009.10.22 m765.827.v4
 */
final class DualPivotQuicksort {

    // Suppresses default constructor, ensuring non-instantiability.
    private DualPivotQuicksort() {}

    /*
     * Tuning Parameters.
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
     * Sorting methods for the seven primitive types.
     */

    /**
     * Sorts the specified range of the array into ascending order.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    static void sort(int[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int k = left + 1; k <= right; k++) {
                int ak = a[k];
                int j;

                for (j = k - 1; j >= left && ak < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ak;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order
     * by Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    private static void dualPivotQuicksort(int[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements in place using a 5-element sorting network
        if (a[e1] > a[e2]) { int t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
        if (a[e4] > a[e5]) { int t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
        if (a[e1] > a[e3]) { int t = a[e1]; a[e1] = a[e3]; a[e3] = t; }
        if (a[e2] > a[e3]) { int t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e1] > a[e4]) { int t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
        if (a[e3] > a[e4]) { int t = a[e3]; a[e3] = a[e4]; a[e4] = t; }
        if (a[e2] > a[e5]) { int t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
        if (a[e2] > a[e3]) { int t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e4] > a[e5]) { int t = a[e4]; a[e4] = a[e5]; a[e5] = t; }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the sorted elements are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        int pivot1 = a[e2]; a[e2] = a[left];
        int pivot2 = a[e4]; a[e4] = a[right];

        /*
         * Partitioning
         *
         *   left part         center part                  right part
         * ------------------------------------------------------------
         * [ < pivot1  |  pivot1 <= && <= pivot2  |   ?   |  > pivot2 ]
         * ------------------------------------------------------------
         *              ^                          ^     ^
         *              |                          |     |
         *             less                        k   great
         */

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = pivot1 != pivot2;

        if (pivotsDiffer) {
            /*
             * Invariants:
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                int ak = a[k];

                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else if (ak > pivot2) {
                    while (a[great] > pivot2 && k < great) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") partition:
             *
             *   left part   center part            right part
             * -------------------------------------------------
             * [  < pivot  |  == pivot  |    ?    |  > pivot   ]
             * -------------------------------------------------
             *
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
                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else {
                    while (a[great] > pivot1) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        sort(a, left,   less - 2);
        sort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 5/6 of
         * the array), swap internal pivot values to ends
         */
        if (less < e1 && e5 < great) {
            while (a[less] == pivot1) {
                less++;
            }
            for (int k = less + 1; k <= great; k++) {
                if (a[k] == pivot1) {
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
            while (a[great] == pivot2) {
                great--;
            }
            for (int k = great - 1; k >= less; k--) {
                if (a[k] == pivot2) {
                    a[k] = a[great];
                    a[great--] = pivot2;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        sort(a, less, great);
    }

    /**
     * Sorts the specified range of the array into ascending order.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    static void sort(long[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int k = left + 1; k <= right; k++) {
                long ak = a[k];
                int j;

                for (j = k - 1; j >= left && ak < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ak;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order
     * by Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    private static void dualPivotQuicksort(long[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements in place using a 5-element sorting network
        if (a[e1] > a[e2]) { long t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
        if (a[e4] > a[e5]) { long t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
        if (a[e1] > a[e3]) { long t = a[e1]; a[e1] = a[e3]; a[e3] = t; }
        if (a[e2] > a[e3]) { long t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e1] > a[e4]) { long t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
        if (a[e3] > a[e4]) { long t = a[e3]; a[e3] = a[e4]; a[e4] = t; }
        if (a[e2] > a[e5]) { long t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
        if (a[e2] > a[e3]) { long t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e4] > a[e5]) { long t = a[e4]; a[e4] = a[e5]; a[e5] = t; }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the sorted elements are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        long pivot1 = a[e2]; a[e2] = a[left];
        long pivot2 = a[e4]; a[e4] = a[right];

        /*
         * Partitioning
         *
         *   left part         center part                  right part
         * ------------------------------------------------------------
         * [ < pivot1  |  pivot1 <= && <= pivot2  |   ?   |  > pivot2 ]
         * ------------------------------------------------------------
         *              ^                          ^     ^
         *              |                          |     |
         *             less                        k   great
         */

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = pivot1 != pivot2;

        if (pivotsDiffer) {
            /*
             * Invariants:
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                long ak = a[k];

                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else if (ak > pivot2) {
                    while (a[great] > pivot2 && k < great) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") partition:
             *
             *   left part   center part            right part
             * -------------------------------------------------
             * [  < pivot  |  == pivot  |    ?    |  > pivot   ]
             * -------------------------------------------------
             *
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
                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else {
                    while (a[great] > pivot1) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        sort(a, left,   less - 2);
        sort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 5/6 of
         * the array), swap internal pivot values to ends
         */
        if (less < e1 && e5 < great) {
            while (a[less] == pivot1) {
                less++;
            }
            for (int k = less + 1; k <= great; k++) {
                if (a[k] == pivot1) {
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
            while (a[great] == pivot2) {
                great--;
            }
            for (int k = great - 1; k >= less; k--) {
                if (a[k] == pivot2) {
                    a[k] = a[great];
                    a[great--] = pivot2;
                }
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /** The number of distinct short values */
    private static final int NUM_SHORT_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array into ascending order.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    static void sort(short[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int k = left + 1; k <= right; k++) {
                short ak = a[k];
                int j;

                for (j = k - 1; j >= left && ak < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ak;
            }
        } else if (right - left + 1 > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            // Use counting sort on huge arrays
            int[] count = new int[NUM_SHORT_VALUES];

            for (int i = left; i <= right; i++) {
                count[a[i] - Short.MIN_VALUE]++;
            }
            for (int i = 0, k = left; i < count.length && k < right; i++) {
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
     * Sorts the specified range of the array into ascending order
     * by Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    private static void dualPivotQuicksort(short[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements in place using a 5-element sorting network
        if (a[e1] > a[e2]) { short t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
        if (a[e4] > a[e5]) { short t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
        if (a[e1] > a[e3]) { short t = a[e1]; a[e1] = a[e3]; a[e3] = t; }
        if (a[e2] > a[e3]) { short t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e1] > a[e4]) { short t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
        if (a[e3] > a[e4]) { short t = a[e3]; a[e3] = a[e4]; a[e4] = t; }
        if (a[e2] > a[e5]) { short t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
        if (a[e2] > a[e3]) { short t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e4] > a[e5]) { short t = a[e4]; a[e4] = a[e5]; a[e5] = t; }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the sorted elements are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        short pivot1 = a[e2]; a[e2] = a[left];
        short pivot2 = a[e4]; a[e4] = a[right];

        /*
         * Partitioning
         *
         *   left part         center part                  right part
         * ------------------------------------------------------------
         * [ < pivot1  |  pivot1 <= && <= pivot2  |   ?   |  > pivot2 ]
         * ------------------------------------------------------------
         *              ^                          ^     ^
         *              |                          |     |
         *             less                        k   great
         */

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = pivot1 != pivot2;

        if (pivotsDiffer) {
            /*
             * Invariants:
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                short ak = a[k];

                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else if (ak > pivot2) {
                    while (a[great] > pivot2 && k < great) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") partition:
             *
             *   left part   center part            right part
             * -------------------------------------------------
             * [  < pivot  |  == pivot  |    ?    |  > pivot   ]
             * -------------------------------------------------
             *
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
                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else {
                    while (a[great] > pivot1) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        sort(a, left,   less - 2);
        sort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 5/6 of
         * the array), swap internal pivot values to ends
         */
        if (less < e1 && e5 < great) {
            while (a[less] == pivot1) {
                less++;
            }
            for (int k = less + 1; k <= great; k++) {
                if (a[k] == pivot1) {
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
            while (a[great] == pivot2) {
                great--;
            }
            for (int k = great - 1; k >= less; k--) {
                if (a[k] == pivot2) {
                    a[k] = a[great];
                    a[great--] = pivot2;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        sort(a, less, great);
    }

    /** The number of distinct byte values */
    private static final int NUM_BYTE_VALUES = 1 << 8;

    /**
     * Sorts the specified range of the array into ascending order.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    static void sort(byte[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int k = left + 1; k <= right; k++) {
                byte ak = a[k];
                int j;

                for (j = k - 1; j >= left && ak < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ak;
            }
        } else if (right - left + 1 > COUNTING_SORT_THRESHOLD_FOR_BYTE) {
            // Use counting sort on large arrays
            int[] count = new int[NUM_BYTE_VALUES];

            for (int i = left; i <= right; i++) {
                count[a[i] - Byte.MIN_VALUE]++;
            }
            for (int i = 0, k = left; i < count.length && k < right; i++) {
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
     * Sorts the specified range of the array into ascending order
     * by Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    private static void dualPivotQuicksort(byte[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements in place using a 5-element sorting network
        if (a[e1] > a[e2]) { byte t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
        if (a[e4] > a[e5]) { byte t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
        if (a[e1] > a[e3]) { byte t = a[e1]; a[e1] = a[e3]; a[e3] = t; }
        if (a[e2] > a[e3]) { byte t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e1] > a[e4]) { byte t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
        if (a[e3] > a[e4]) { byte t = a[e3]; a[e3] = a[e4]; a[e4] = t; }
        if (a[e2] > a[e5]) { byte t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
        if (a[e2] > a[e3]) { byte t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e4] > a[e5]) { byte t = a[e4]; a[e4] = a[e5]; a[e5] = t; }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the sorted elements are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        byte pivot1 = a[e2]; a[e2] = a[left];
        byte pivot2 = a[e4]; a[e4] = a[right];

        /*
         * Partitioning
         *
         *   left part         center part                  right part
         * ------------------------------------------------------------
         * [ < pivot1  |  pivot1 <= && <= pivot2  |   ?   |  > pivot2 ]
         * ------------------------------------------------------------
         *              ^                          ^     ^
         *              |                          |     |
         *             less                        k   great
         */

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = pivot1 != pivot2;

        if (pivotsDiffer) {
            /*
             * Invariants:
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                byte ak = a[k];

                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else if (ak > pivot2) {
                    while (a[great] > pivot2 && k < great) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") partition:
             *
             *   left part   center part            right part
             * -------------------------------------------------
             * [  < pivot  |  == pivot  |    ?    |  > pivot   ]
             * -------------------------------------------------
             *
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
                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else {
                    while (a[great] > pivot1) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        sort(a, left,   less - 2);
        sort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 5/6 of
         * the array), swap internal pivot values to ends
         */
        if (less < e1 && e5 < great) {
            while (a[less] == pivot1) {
                less++;
            }
            for (int k = less + 1; k <= great; k++) {
                if (a[k] == pivot1) {
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
            while (a[great] == pivot2) {
                great--;
            }
            for (int k = great - 1; k >= less; k--) {
                if (a[k] == pivot2) {
                    a[k] = a[great];
                    a[great--] = pivot2;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        sort(a, less, great);
    }

    /** The number of distinct char values */
    private static final int NUM_CHAR_VALUES = 1 << 16;

    /**
     * Sorts the specified range of the array into ascending order.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    static void sort(char[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int k = left + 1; k <= right; k++) {
                char ak = a[k];
                int j;

                for (j = k - 1; j >= left && ak < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ak;
            }
        } else if (right - left + 1 > COUNTING_SORT_THRESHOLD_FOR_SHORT_OR_CHAR) {
            // Use counting sort on huge arrays
            int[] count = new int[NUM_CHAR_VALUES];

            for (int i = left; i <= right; i++) {
                count[a[i]]++;
            }
            for (int i = 0, k = left; i < count.length && k < right; i++) {
                for (int s = count[i]; s > 0; s--) {
                    a[k++] = (char) i;
               }
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order
     * by Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    private static void dualPivotQuicksort(char[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements in place using a 5-element sorting network
        if (a[e1] > a[e2]) { char t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
        if (a[e4] > a[e5]) { char t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
        if (a[e1] > a[e3]) { char t = a[e1]; a[e1] = a[e3]; a[e3] = t; }
        if (a[e2] > a[e3]) { char t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e1] > a[e4]) { char t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
        if (a[e3] > a[e4]) { char t = a[e3]; a[e3] = a[e4]; a[e4] = t; }
        if (a[e2] > a[e5]) { char t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
        if (a[e2] > a[e3]) { char t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e4] > a[e5]) { char t = a[e4]; a[e4] = a[e5]; a[e5] = t; }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the sorted elements are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        char pivot1 = a[e2]; a[e2] = a[left];
        char pivot2 = a[e4]; a[e4] = a[right];

        /*
         * Partitioning
         *
         *   left part         center part                  right part
         * ------------------------------------------------------------
         * [ < pivot1  |  pivot1 <= && <= pivot2  |   ?   |  > pivot2 ]
         * ------------------------------------------------------------
         *              ^                          ^     ^
         *              |                          |     |
         *             less                        k   great
         */

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = pivot1 != pivot2;

        if (pivotsDiffer) {
            /*
             * Invariants:
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                char ak = a[k];

                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else if (ak > pivot2) {
                    while (a[great] > pivot2 && k < great) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") partition:
             *
             *   left part   center part            right part
             * -------------------------------------------------
             * [  < pivot  |  == pivot  |    ?    |  > pivot   ]
             * -------------------------------------------------
             *
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
                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else {
                    while (a[great] > pivot1) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        sort(a, left,   less - 2);
        sort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 5/6 of
         * the array), swap internal pivot values to ends
         */
        if (less < e1 && e5 < great) {
            while (a[less] == pivot1) {
                less++;
            }
            for (int k = less + 1; k <= great; k++) {
                if (a[k] == pivot1) {
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
            while (a[great] == pivot2) {
                great--;
            }
            for (int k = great - 1; k >= less; k--) {
                if (a[k] == pivot2) {
                    a[k] = a[great];
                    a[great--] = pivot2;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        sort(a, less, great);
    }

    /**
     * Sorts the specified range of the array into ascending order.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    static void sort(float[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int k = left + 1; k <= right; k++) {
                float ak = a[k];
                int j;

                for (j = k - 1; j >= left && ak < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ak;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order
     * by Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    private static void dualPivotQuicksort(float[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements in place using a 5-element sorting network
        if (a[e1] > a[e2]) { float t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
        if (a[e4] > a[e5]) { float t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
        if (a[e1] > a[e3]) { float t = a[e1]; a[e1] = a[e3]; a[e3] = t; }
        if (a[e2] > a[e3]) { float t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e1] > a[e4]) { float t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
        if (a[e3] > a[e4]) { float t = a[e3]; a[e3] = a[e4]; a[e4] = t; }
        if (a[e2] > a[e5]) { float t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
        if (a[e2] > a[e3]) { float t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e4] > a[e5]) { float t = a[e4]; a[e4] = a[e5]; a[e5] = t; }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the sorted elements are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        float pivot1 = a[e2]; a[e2] = a[left];
        float pivot2 = a[e4]; a[e4] = a[right];

        /*
         * Partitioning
         *
         *   left part         center part                  right part
         * ------------------------------------------------------------
         * [ < pivot1  |  pivot1 <= && <= pivot2  |   ?   |  > pivot2 ]
         * ------------------------------------------------------------
         *              ^                          ^     ^
         *              |                          |     |
         *             less                        k   great
         */

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = pivot1 != pivot2;

        if (pivotsDiffer) {
            /*
             * Invariants:
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                float ak = a[k];

                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else if (ak > pivot2) {
                    while (a[great] > pivot2 && k < great) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") partition:
             *
             *   left part   center part            right part
             * -------------------------------------------------
             * [  < pivot  |  == pivot  |    ?    |  > pivot   ]
             * -------------------------------------------------
             *
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
                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else {
                    while (a[great] > pivot1) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        sort(a, left,   less - 2);
        sort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 5/6 of
         * the array), swap internal pivot values to ends
         */
        if (less < e1 && e5 < great) {
            while (a[less] == pivot1) {
                less++;
            }
            for (int k = less + 1; k <= great; k++) {
                if (a[k] == pivot1) {
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
            while (a[great] == pivot2) {
                great--;
            }
            for (int k = great - 1; k >= less; k--) {
                if (a[k] == pivot2) {
                    a[k] = a[great];
                    a[great--] = pivot2;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        sort(a, less, great);
    }

    /**
     * Sorts the specified range of the array into ascending order.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    static void sort(double[] a, int left, int right) {
        // Use insertion sort on tiny arrays
        if (right - left + 1 < INSERTION_SORT_THRESHOLD) {
            for (int k = left + 1; k <= right; k++) {
                double ak = a[k];
                int j;

                for (j = k - 1; j >= left && ak < a[j]; j--) {
                    a[j + 1] = a[j];
                }
                a[j + 1] = ak;
            }
        } else { // Use Dual-Pivot Quicksort on large arrays
            dualPivotQuicksort(a, left, right);
        }
    }

    /**
     * Sorts the specified range of the array into ascending order
     * by Dual-Pivot Quicksort.
     *
     * @param a the array to be sorted
     * @param left the index of the first element, inclusively, to be sorted
     * @param right the index of the last element, inclusively, to be sorted
     */
    private static void dualPivotQuicksort(double[] a, int left, int right) {
        // Compute indices of five evenly spaced elements
        int sixth = (right - left + 1) / 6;
        int e1 = left  + sixth;
        int e5 = right - sixth;
        int e3 = (left + right) >>> 1; // The midpoint
        int e4 = e3 + sixth;
        int e2 = e3 - sixth;

        // Sort these elements in place using a 5-element sorting network
        if (a[e1] > a[e2]) { double t = a[e1]; a[e1] = a[e2]; a[e2] = t; }
        if (a[e4] > a[e5]) { double t = a[e4]; a[e4] = a[e5]; a[e5] = t; }
        if (a[e1] > a[e3]) { double t = a[e1]; a[e1] = a[e3]; a[e3] = t; }
        if (a[e2] > a[e3]) { double t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e1] > a[e4]) { double t = a[e1]; a[e1] = a[e4]; a[e4] = t; }
        if (a[e3] > a[e4]) { double t = a[e3]; a[e3] = a[e4]; a[e4] = t; }
        if (a[e2] > a[e5]) { double t = a[e2]; a[e2] = a[e5]; a[e5] = t; }
        if (a[e2] > a[e3]) { double t = a[e2]; a[e2] = a[e3]; a[e3] = t; }
        if (a[e4] > a[e5]) { double t = a[e4]; a[e4] = a[e5]; a[e5] = t; }

        /*
         * Use the second and fourth of the five sorted elements as pivots.
         * These values are inexpensive approximations of the first and
         * second terciles of the array. Note that pivot1 <= pivot2.
         *
         * The pivots are stored in local variables, and the first and
         * the last of the sorted elements are moved to the locations
         * formerly occupied by the pivots. When partitioning is complete,
         * the pivots are swapped back into their final positions, and
         * excluded from subsequent sorting.
         */
        double pivot1 = a[e2]; a[e2] = a[left];
        double pivot2 = a[e4]; a[e4] = a[right];

        /*
         * Partitioning
         *
         *   left part         center part                  right part
         * ------------------------------------------------------------
         * [ < pivot1  |  pivot1 <= && <= pivot2  |   ?   |  > pivot2 ]
         * ------------------------------------------------------------
         *              ^                          ^     ^
         *              |                          |     |
         *             less                        k   great
         */

        // Pointers
        int less  = left  + 1; // The index of first element of center part
        int great = right - 1; // The index before first element of right part

        boolean pivotsDiffer = pivot1 != pivot2;

        if (pivotsDiffer) {
            /*
             * Invariants:
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part
             */
            for (int k = less; k <= great; k++) {
                double ak = a[k];

                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else if (ak > pivot2) {
                    while (a[great] > pivot2 && k < great) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        } else { // Pivots are equal
            /*
             * Partition degenerates to the traditional 3-way
             * (or "Dutch National Flag") partition:
             *
             *   left part   center part            right part
             * -------------------------------------------------
             * [  < pivot  |  == pivot  |    ?    |  > pivot   ]
             * -------------------------------------------------
             *
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
                if (ak < pivot1) {
                    a[k] = a[less];
                    a[less++] = ak;
                } else {
                    while (a[great] > pivot1) {
                        great--;
                    }
                    a[k] = a[great];
                    a[great--] = ak;
                    ak = a[k];

                    if (ak < pivot1) {
                        a[k] = a[less];
                        a[less++] = ak;
                    }
                }
            }
        }

        // Swap pivots into their final positions
        a[left]  = a[less  - 1]; a[less  - 1] = pivot1;
        a[right] = a[great + 1]; a[great + 1] = pivot2;

        // Sort left and right parts recursively, excluding known pivot values
        sort(a, left,   less - 2);
        sort(a, great + 2, right);

        /*
         * If pivot1 == pivot2, all elements from center
         * part are equal and, therefore, already sorted
         */
        if (!pivotsDiffer) {
            return;
        }

        /*
         * If center part is too large (comprises > 5/6 of
         * the array), swap internal pivot values to ends
         */
        if (less < e1 && e5 < great) {
            while (a[less] == pivot1) {
                less++;
            }
            for (int k = less + 1; k <= great; k++) {
                if (a[k] == pivot1) {
                    a[k] = a[less];
                    a[less++] = pivot1;
                }
            }
            while (a[great] == pivot2) {
                great--;
            }
            for (int k = great - 1; k >= less; k--) {
                if (a[k] == pivot2) {
                    a[k] = a[great];
                    a[great--] = pivot2;
                }
            }
        }

        // Sort center part recursively, excluding known pivot values
        sort(a, less, great);
    }
}
