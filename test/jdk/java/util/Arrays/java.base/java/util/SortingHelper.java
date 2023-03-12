/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This class provides access to package-private
 * methods of DualPivotQuicksort class.
 *
 * @author Vladimir Yaroslavskiy
 *
 * @version 2022.06.14
 *
 * @since 14 ^ 22
 */
public enum SortingHelper {

    DUAL_PIVOT_QUICKSORT("Dual-Pivot Quicksort") {
        @Override
        public void sort(Object a, int low, int high) {
            sort(a, SEQUENTIAL, low, high);
        }
    },

    PARALLEL_SORT("Parallel sort") {
        @Override
        public void sort(Object a, int low, int high) {
            sort(a, PARALLEL, low, high);
        }
    },

    MIXED_INSERTION_SORT("Mixed insertion sort") {
        @Override
        public void sort(Object a, int low, int high) {
            if (a instanceof int[]) {
                DualPivotQuicksort.mixedInsertionSort((int[]) a, low, high);
            } else if (a instanceof long[]) {
                DualPivotQuicksort.mixedInsertionSort((long[]) a, low, high);
            } else if (a instanceof byte[]) {
                DualPivotQuicksort.sort((byte[]) a, low, high);
            } else if (a instanceof char[]) {
                DualPivotQuicksort.sort((char[]) a, low, high);
            } else if (a instanceof short[]) {
                DualPivotQuicksort.sort((short[]) a, low, high);
            } else if (a instanceof float[]) {
                DualPivotQuicksort.mixedInsertionSort((float[]) a, low, high);
            } else if (a instanceof double[]) {
                DualPivotQuicksort.mixedInsertionSort((double[]) a, low, high);
            } else {
                fail(a);
            }
        }
    },

    INSERTION_SORT("Insertion sort") {
        @Override
        public void sort(Object a, int low, int high) {
            if (a instanceof int[]) {
                DualPivotQuicksort.insertionSort((int[]) a, low, high);
            } else if (a instanceof long[]) {
                DualPivotQuicksort.insertionSort((long[]) a, low, high);
            } else if (a instanceof byte[]) {
                DualPivotQuicksort.insertionSort((byte[]) a, low, high);
            } else if (a instanceof char[]) {
                DualPivotQuicksort.insertionSort((char[]) a, low, high);
            } else if (a instanceof short[]) {
                DualPivotQuicksort.insertionSort((short[]) a, low, high);
            } else if (a instanceof float[]) {
                DualPivotQuicksort.insertionSort((float[]) a, low, high);
            } else if (a instanceof double[]) {
                DualPivotQuicksort.insertionSort((double[]) a, low, high);
            } else {
                fail(a);
            }
        }
    },

    MERGING_SORT("Merging sort") {
        @Override
        public void sort(Object a, int low, int high) {
            if (a instanceof int[]) {
                check("Merging", DualPivotQuicksort.tryMergingSort(null, (int[]) a, low, high - low));
            } else if (a instanceof long[]) {
                check("Merging", DualPivotQuicksort.tryMergingSort(null, (long[]) a, low, high - low));
            } else if (a instanceof byte[]) {
                DualPivotQuicksort.sort((byte[]) a, low, high);
            } else if (a instanceof char[]) {
                DualPivotQuicksort.sort((char[]) a, low, high);
            } else if (a instanceof short[]) {
                DualPivotQuicksort.sort((short[]) a, low, high);
            } else if (a instanceof float[]) {
                check("Merging", DualPivotQuicksort.tryMergingSort(null, (float[]) a, low, high - low));
            } else if (a instanceof double[]) {
                check("Merging", DualPivotQuicksort.tryMergingSort(null, (double[]) a, low, high - low));
            } else {
                fail(a);
            }
        }
    },

    RADIX_SORT("Radix sort") {
        @Override
        public void sort(Object a, int low, int high) {
            if (a instanceof int[]) {
                check("Radix", DualPivotQuicksort.tryRadixSort(null, (int[]) a, low, high));
            } else if (a instanceof long[]) {
                check("Radix", DualPivotQuicksort.tryRadixSort(null, (long[]) a, low, high));
            } else if (a instanceof byte[]) {
                DualPivotQuicksort.sort((byte[]) a, low, high);
            } else if (a instanceof char[]) {
                DualPivotQuicksort.sort((char[]) a, low, high);
            } else if (a instanceof short[]) {
                DualPivotQuicksort.sort((short[]) a, low, high);
            } else if (a instanceof float[]) {
                check("Radix", DualPivotQuicksort.tryRadixSort(null, (float[]) a, low, high));
            } else if (a instanceof double[]) {
                check("Radix", DualPivotQuicksort.tryRadixSort(null, (double[]) a, low, high));
            } else {
                fail(a);
            }
        }
    },

    HEAP_SORT("Heap sort") {
        @Override
        public void sort(Object a, int low, int high) {
            if (a instanceof int[]) {
                DualPivotQuicksort.heapSort((int[]) a, low, high);
            } else if (a instanceof long[]) {
                DualPivotQuicksort.heapSort((long[]) a, low, high);
            } else if (a instanceof byte[]) {
                DualPivotQuicksort.sort((byte[]) a, low, high);
            } else if (a instanceof char[]) {
                DualPivotQuicksort.sort((char[]) a, low, high);
            } else if (a instanceof short[]) {
                DualPivotQuicksort.sort((short[]) a, low, high);
            } else if (a instanceof float[]) {
                DualPivotQuicksort.heapSort((float[]) a, low, high);
            } else if (a instanceof double[]) {
                DualPivotQuicksort.heapSort((double[]) a, low, high);
            } else {
                fail(a);
            }
        }
    },

    ARRAYS_SORT("Arrays.sort") {
        @Override
        public void sort(Object a) {
            if (a instanceof int[]) {
                Arrays.sort((int[]) a);
            } else if (a instanceof long[]) {
                Arrays.sort((long[]) a);
            } else if (a instanceof byte[]) {
                Arrays.sort((byte[]) a);
            } else if (a instanceof char[]) {
                Arrays.sort((char[]) a);
            } else if (a instanceof short[]) {
                Arrays.sort((short[]) a);
            } else if (a instanceof float[]) {
                Arrays.sort((float[]) a);
            } else if (a instanceof double[]) {
                Arrays.sort((double[]) a);
            } else {
                fail(a);
            }
        }

        @Override
        public void sort(Object a, int low, int high) {
            if (a instanceof int[]) {
                Arrays.sort((int[]) a, low, high);
            } else if (a instanceof long[]) {
                Arrays.sort((long[]) a, low, high);
            } else if (a instanceof byte[]) {
                Arrays.sort((byte[]) a, low, high);
            } else if (a instanceof char[]) {
                Arrays.sort((char[]) a, low, high);
            } else if (a instanceof short[]) {
                Arrays.sort((short[]) a, low, high);
            } else if (a instanceof float[]) {
                Arrays.sort((float[]) a, low, high);
            } else if (a instanceof double[]) {
                Arrays.sort((double[]) a, low, high);
            } else {
                fail(a);
            }
        }
    },

    ARRAYS_PARALLEL_SORT("Arrays.parallelSort") {
        @Override
        public void sort(Object a) {
            if (a instanceof int[]) {
                Arrays.parallelSort((int[]) a);
            } else if (a instanceof long[]) {
                Arrays.parallelSort((long[]) a);
            } else if (a instanceof byte[]) {
                Arrays.parallelSort((byte[]) a);
            } else if (a instanceof char[]) {
                Arrays.parallelSort((char[]) a);
            } else if (a instanceof short[]) {
                Arrays.parallelSort((short[]) a);
            } else if (a instanceof float[]) {
                Arrays.parallelSort((float[]) a);
            } else if (a instanceof double[]) {
                Arrays.parallelSort((double[]) a);
            } else {
                fail(a);
            }
        }

        @Override
        public void sort(Object a, int low, int high) {
            if (a instanceof int[]) {
                Arrays.parallelSort((int[]) a, low, high);
            } else if (a instanceof long[]) {
                Arrays.parallelSort((long[]) a, low, high);
            } else if (a instanceof byte[]) {
                Arrays.parallelSort((byte[]) a, low, high);
            } else if (a instanceof char[]) {
                Arrays.parallelSort((char[]) a, low, high);
            } else if (a instanceof short[]) {
                Arrays.parallelSort((short[]) a, low, high);
            } else if (a instanceof float[]) {
                Arrays.parallelSort((float[]) a, low, high);
            } else if (a instanceof double[]) {
                Arrays.parallelSort((double[]) a, low, high);
            } else {
                fail(a);
            }
        }
    };

    abstract public void sort(Object a, int low, int high);

    public void sort(Object a) {
        if (a instanceof int[]) {
            sort(a, 0, ((int[]) a).length);
        } else if (a instanceof long[]) {
            sort(a, 0, ((long[]) a).length);
        } else if (a instanceof byte[]) {
            sort(a, 0, ((byte[]) a).length);
        } else if (a instanceof char[]) {
            sort(a, 0, ((char[]) a).length);
        } else if (a instanceof short[]) {
            sort(a, 0, ((short[]) a).length);
        } else if (a instanceof float[]) {
            sort(a, 0, ((float[]) a).length);
        } else if (a instanceof double[]) {
            sort(a, 0, ((double[]) a).length);
        } else {
            fail(a);
        }
    }

    SortingHelper(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    static void sort(Object a, int parallelism, int low, int high) {
        if (a instanceof int[]) {
            DualPivotQuicksort.sort((int[]) a, parallelism, low, high);
        } else if (a instanceof long[]) {
            DualPivotQuicksort.sort((long[]) a, parallelism, low, high);
        } else if (a instanceof byte[]) {
            DualPivotQuicksort.sort((byte[]) a, low, high);
        } else if (a instanceof char[]) {
            DualPivotQuicksort.sort((char[]) a, low, high);
        } else if (a instanceof short[]) {
            DualPivotQuicksort.sort((short[]) a, low, high);
        } else if (a instanceof float[]) {
            DualPivotQuicksort.sort((float[]) a, parallelism, low, high);
        } else if (a instanceof double[]) {
            DualPivotQuicksort.sort((double[]) a, parallelism, low, high);
        } else {
            fail(a);
        }
    }

    private static void check(String name, boolean result) {
        if (!result) {
            fail(name + " sort must return true");
        }
    }

    private static void fail(Object a) {
        fail("Unknown array: " + a.getClass().getName());
    }

    private static void fail(String message) {
        throw new RuntimeException(message);
    }

    private final String name;

    /**
     * Parallelism level for sequential sorting.
     */
    private static final int SEQUENTIAL = 0;

    /**
     * Parallelism level for parallel sorting.
     */
    private static final int PARALLEL = 88;
}
