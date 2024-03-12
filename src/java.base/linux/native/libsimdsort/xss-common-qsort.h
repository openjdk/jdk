/*
 * Copyright (c) 2021, 2023, Intel Corporation. All rights reserved.
 * Copyright (c) 2021 Serge Sans Paille. All rights reserved.
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
 *
 */

// This implementation is based on x86-simd-sort(https://github.com/intel/x86-simd-sort)

#ifndef XSS_COMMON_QSORT
#define XSS_COMMON_QSORT

/*
 * Quicksort using AVX-512. The ideas and code are based on these two research
 * papers [1] and [2]. On a high level, the idea is to vectorize quicksort
 * partitioning using AVX-512 compressstore instructions. If the array size is
 * < 128, then use Bitonic sorting network implemented on 512-bit registers.
 * The precise network definitions depend on the dtype and are defined in
 * separate files: avx512-16bit-qsort.hpp, avx512-32bit-qsort.hpp and
 * avx512-64bit-qsort.hpp. Article [4] is a good resource for bitonic sorting
 * network. The core implementations of the vectorized qsort functions
 * avx512_qsort<T>(T*, arrsize_t) are modified versions of avx2 quicksort
 * presented in the paper [2] and source code associated with that paper [3].
 *
 * [1] Fast and Robust Vectorized In-Place Sorting of Primitive Types
 *     https://drops.dagstuhl.de/opus/volltexte/2021/13775/
 *
 * [2] A Novel Hybrid Quicksort Algorithm Vectorized using AVX-512 on Intel
 * Skylake https://arxiv.org/pdf/1704.08579.pdf
 *
 * [3] https://github.com/simd-sorting/fast-and-robust: SPDX-License-Identifier:
 * MIT
 *
 * [4] http://mitp-content-server.mit.edu:18180/books/content/sectbyfn?collid=books_pres_0&fn=Chapter%2027.pdf&id=8030
 *
 */

#include "xss-common-includes.h"
#include "xss-pivot-selection.hpp"
#include "xss-network-qsort.hpp"


template <typename T>
bool is_a_nan(T elem) {
    return std::isnan(elem);
}

template <typename T>
X86_SIMD_SORT_INLINE T get_pivot_scalar(T *arr, const int64_t left, const int64_t right) {
    // median of 8 equally spaced elements
    int64_t NUM_ELEMENTS = 8;
    int64_t MID = NUM_ELEMENTS / 2;
    int64_t size = (right - left) / NUM_ELEMENTS;
    T temp[NUM_ELEMENTS];
    for (int64_t i = 0; i < NUM_ELEMENTS; i++) temp[i] = arr[left + (i * size)];
    std::sort(temp, temp + NUM_ELEMENTS);
    return temp[MID];
}

template <typename vtype, typename T = typename vtype::type_t>
bool comparison_func_ge(const T &a, const T &b) {
    return a < b;
}

template <typename vtype, typename T = typename vtype::type_t>
bool comparison_func_gt(const T &a, const T &b) {
    return a <= b;
}

/*
 * COEX == Compare and Exchange two registers by swapping min and max values
 */
template <typename vtype, typename mm_t>
X86_SIMD_SORT_INLINE void COEX(mm_t &a, mm_t &b) {
    mm_t temp = a;
    a = vtype::min(a, b);
    b = vtype::max(temp, b);
}

template <typename vtype, typename reg_t = typename vtype::reg_t,
          typename opmask_t = typename vtype::opmask_t>
X86_SIMD_SORT_INLINE reg_t cmp_merge(reg_t in1, reg_t in2, opmask_t mask) {
    reg_t min = vtype::min(in2, in1);
    reg_t max = vtype::max(in2, in1);
    return vtype::mask_mov(min, mask, max);  // 0 -> min, 1 -> max
}

template <typename vtype, typename type_t, typename reg_t>
int avx512_double_compressstore(type_t *left_addr, type_t *right_addr,
                                typename vtype::opmask_t k, reg_t reg) {
    int amount_ge_pivot = _mm_popcnt_u32((int)k);

    vtype::mask_compressstoreu(left_addr, vtype::knot_opmask(k), reg);
    vtype::mask_compressstoreu(right_addr + vtype::numlanes - amount_ge_pivot,
                               k, reg);

    return amount_ge_pivot;
}

// Generic function dispatches to AVX2 or AVX512 code
template <typename vtype, typename type_t,
          typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_INLINE arrsize_t partition_vec(type_t *l_store, type_t *r_store,
                                             const reg_t curr_vec,
                                             const reg_t pivot_vec,
                                             reg_t &smallest_vec,
                                             reg_t &biggest_vec, bool use_gt) {
    //typename vtype::opmask_t ge_mask = vtype::ge(curr_vec, pivot_vec);
    typename vtype::opmask_t mask;
    if (use_gt) mask = vtype::gt(curr_vec, pivot_vec);
    else mask = vtype::ge(curr_vec, pivot_vec);

    int amount_ge_pivot =
        vtype::double_compressstore(l_store, r_store, mask, curr_vec);

    smallest_vec = vtype::min(curr_vec, smallest_vec);
    biggest_vec = vtype::max(curr_vec, biggest_vec);

    return amount_ge_pivot;
}

/*
 * Parition an array based on the pivot and returns the index of the
 * first element that is greater than or equal to the pivot.
 */
template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE arrsize_t partition_avx512(type_t *arr, arrsize_t left,
                                                arrsize_t right, type_t pivot,
                                                type_t *smallest,
                                                type_t *biggest,
                                                bool use_gt) {
    auto comparison_func = use_gt ? comparison_func_gt<vtype> : comparison_func_ge<vtype>;
    /* make array length divisible by vtype::numlanes , shortening the array */
    for (int32_t i = (right - left) % vtype::numlanes; i > 0; --i) {
        *smallest = std::min(*smallest, arr[left], comparison_func);
        *biggest = std::max(*biggest, arr[left], comparison_func);
        if (!comparison_func(arr[left], pivot)) {
            std::swap(arr[left], arr[--right]);
        } else {
            ++left;
        }
    }

    if (left == right)
        return left; /* less than vtype::numlanes elements in the array */

    using reg_t = typename vtype::reg_t;
    reg_t pivot_vec = vtype::set1(pivot);
    reg_t min_vec = vtype::set1(*smallest);
    reg_t max_vec = vtype::set1(*biggest);

    if (right - left == vtype::numlanes) {
        reg_t vec = vtype::loadu(arr + left);
        arrsize_t unpartitioned = right - left - vtype::numlanes;
        arrsize_t l_store = left;

        arrsize_t amount_ge_pivot =
            partition_vec<vtype>(arr + l_store, arr + l_store + unpartitioned,
                                 vec, pivot_vec, min_vec, max_vec, use_gt);
        l_store += (vtype::numlanes - amount_ge_pivot);
        *smallest = vtype::reducemin(min_vec);
        *biggest = vtype::reducemax(max_vec);
        return l_store;
    }

    // first and last vtype::numlanes values are partitioned at the end
    reg_t vec_left = vtype::loadu(arr + left);
    reg_t vec_right = vtype::loadu(arr + (right - vtype::numlanes));
    // store points of the vectors
    arrsize_t unpartitioned = right - left - vtype::numlanes;
    arrsize_t l_store = left;
    // indices for loading the elements
    left += vtype::numlanes;
    right -= vtype::numlanes;
    while (right - left != 0) {
        reg_t curr_vec;
        /*
         * if fewer elements are stored on the right side of the array,
         * then next elements are loaded from the right side,
         * otherwise from the left side
         */
        if ((l_store + unpartitioned + vtype::numlanes) - right <
            left - l_store) {
            right -= vtype::numlanes;
            curr_vec = vtype::loadu(arr + right);
        } else {
            curr_vec = vtype::loadu(arr + left);
            left += vtype::numlanes;
        }
        // partition the current vector and save it on both sides of the array
        arrsize_t amount_ge_pivot =
            partition_vec<vtype>(arr + l_store, arr + l_store + unpartitioned,
                                 curr_vec, pivot_vec, min_vec, max_vec, use_gt);
        l_store += (vtype::numlanes - amount_ge_pivot);
        unpartitioned -= vtype::numlanes;
    }

    /* partition and save vec_left and vec_right */
    arrsize_t amount_ge_pivot =
        partition_vec<vtype>(arr + l_store, arr + l_store + unpartitioned,
                             vec_left, pivot_vec, min_vec, max_vec, use_gt);
    l_store += (vtype::numlanes - amount_ge_pivot);
    unpartitioned -= vtype::numlanes;

    amount_ge_pivot =
        partition_vec<vtype>(arr + l_store, arr + l_store + unpartitioned,
                             vec_right, pivot_vec, min_vec, max_vec, use_gt);
    l_store += (vtype::numlanes - amount_ge_pivot);
    unpartitioned -= vtype::numlanes;

    *smallest = vtype::reducemin(min_vec);
    *biggest = vtype::reducemax(max_vec);
    return l_store;
}

template <typename vtype, int num_unroll,
          typename type_t = typename vtype::type_t>
X86_SIMD_SORT_INLINE arrsize_t
partition_avx512_unrolled(type_t *arr, arrsize_t left, arrsize_t right,
                          type_t pivot, type_t *smallest, type_t *biggest, bool use_gt) {
    if constexpr (num_unroll == 0) {
        return partition_avx512<vtype>(arr, left, right, pivot, smallest,
                                       biggest, use_gt);
    }

    /* Use regular partition_avx512 for smaller arrays */
    if (right - left < 3 * num_unroll * vtype::numlanes) {
        return partition_avx512<vtype>(arr, left, right, pivot, smallest,
                                       biggest, use_gt);
    }

    auto comparison_func = use_gt ? comparison_func_gt<vtype> : comparison_func_ge<vtype>;
    /* make array length divisible by vtype::numlanes, shortening the array */
    for (int32_t i = ((right - left) % (vtype::numlanes)); i > 0; --i) {
        *smallest = std::min(*smallest, arr[left], comparison_func);
        *biggest = std::max(*biggest, arr[left], comparison_func);
        if (!comparison_func(arr[left], pivot)) {
            std::swap(arr[left], arr[--right]);
        } else {
            ++left;
        }
    }

    arrsize_t unpartitioned = right - left - vtype::numlanes;
    arrsize_t l_store = left;

    using reg_t = typename vtype::reg_t;
    reg_t pivot_vec = vtype::set1(pivot);
    reg_t min_vec = vtype::set1(*smallest);
    reg_t max_vec = vtype::set1(*biggest);

    /* Calculate and load more registers to make the rest of the array a
     * multiple of num_unroll. These registers will be partitioned at the very
     * end. */
    int vecsToPartition = ((right - left) / vtype::numlanes) % num_unroll;
    reg_t vec_align[num_unroll];
    for (int i = 0; i < vecsToPartition; i++) {
        vec_align[i] = vtype::loadu(arr + left + i * vtype::numlanes);
    }
    left += vecsToPartition * vtype::numlanes;

    /* We will now have atleast 3*num_unroll registers worth of data to
     * process. Load left and right vtype::numlanes*num_unroll values into
     * registers to make space for in-place parition. The vec_left and
     * vec_right registers are partitioned at the end */
    reg_t vec_left[num_unroll], vec_right[num_unroll];
    X86_SIMD_SORT_UNROLL_LOOP(8)
    for (int ii = 0; ii < num_unroll; ++ii) {
        vec_left[ii] = vtype::loadu(arr + left + vtype::numlanes * ii);
        vec_right[ii] =
            vtype::loadu(arr + (right - vtype::numlanes * (num_unroll - ii)));
    }
    /* indices for loading the elements */
    left += num_unroll * vtype::numlanes;
    right -= num_unroll * vtype::numlanes;
    while (right - left != 0) {
        reg_t curr_vec[num_unroll];
        /*
         * if fewer elements are stored on the right side of the array,
         * then next elements are loaded from the right side,
         * otherwise from the left side
         */
        if ((l_store + unpartitioned + vtype::numlanes) - right <
            left - l_store) {
            right -= num_unroll * vtype::numlanes;
            X86_SIMD_SORT_UNROLL_LOOP(8)
            for (int ii = 0; ii < num_unroll; ++ii) {
                curr_vec[ii] = vtype::loadu(arr + right + ii * vtype::numlanes);
                /*
                 * error: '_mm_prefetch' needs target feature mmx on clang-cl
                 */
#if !(defined(_MSC_VER) && defined(__clang__))
                _mm_prefetch((char *)(arr + right + ii * vtype::numlanes -
                                      num_unroll * vtype::numlanes),
                             _MM_HINT_T0);
#endif
            }
        } else {
            X86_SIMD_SORT_UNROLL_LOOP(8)
            for (int ii = 0; ii < num_unroll; ++ii) {
                curr_vec[ii] = vtype::loadu(arr + left + ii * vtype::numlanes);
                /*
                 * error: '_mm_prefetch' needs target feature mmx on clang-cl
                 */
#if !(defined(_MSC_VER) && defined(__clang__))
                _mm_prefetch((char *)(arr + left + ii * vtype::numlanes +
                                      num_unroll * vtype::numlanes),
                             _MM_HINT_T0);
#endif
            }
            left += num_unroll * vtype::numlanes;
        }
        /* partition the current vector and save it on both sides of the array
         * */
        X86_SIMD_SORT_UNROLL_LOOP(8)
        for (int ii = 0; ii < num_unroll; ++ii) {
            arrsize_t amount_ge_pivot = partition_vec<vtype>(
                arr + l_store, arr + l_store + unpartitioned, curr_vec[ii],
                pivot_vec, min_vec, max_vec, use_gt);
            l_store += (vtype::numlanes - amount_ge_pivot);
            unpartitioned -= vtype::numlanes;
        }
    }

    /* partition and save vec_left[num_unroll] and vec_right[num_unroll] */
    X86_SIMD_SORT_UNROLL_LOOP(8)
    for (int ii = 0; ii < num_unroll; ++ii) {
        arrsize_t amount_ge_pivot =
            partition_vec<vtype>(arr + l_store, arr + l_store + unpartitioned,
                                 vec_left[ii], pivot_vec, min_vec, max_vec, use_gt);
        l_store += (vtype::numlanes - amount_ge_pivot);
        unpartitioned -= vtype::numlanes;
    }
    X86_SIMD_SORT_UNROLL_LOOP(8)
    for (int ii = 0; ii < num_unroll; ++ii) {
        arrsize_t amount_ge_pivot =
            partition_vec<vtype>(arr + l_store, arr + l_store + unpartitioned,
                                 vec_right[ii], pivot_vec, min_vec, max_vec, use_gt);
        l_store += (vtype::numlanes - amount_ge_pivot);
        unpartitioned -= vtype::numlanes;
    }

    /* partition and save vec_align[vecsToPartition] */
    X86_SIMD_SORT_UNROLL_LOOP(8)
    for (int ii = 0; ii < vecsToPartition; ++ii) {
        arrsize_t amount_ge_pivot =
            partition_vec<vtype>(arr + l_store, arr + l_store + unpartitioned,
                                 vec_align[ii], pivot_vec, min_vec, max_vec, use_gt);
        l_store += (vtype::numlanes - amount_ge_pivot);
        unpartitioned -= vtype::numlanes;
    }

    *smallest = vtype::reducemin(min_vec);
    *biggest = vtype::reducemax(max_vec);
    return l_store;
}

template <typename vtype, int maxN>
void sort_n(typename vtype::type_t *arr, int N);

template <typename vtype, typename type_t>
static void qsort_(type_t *arr, arrsize_t left, arrsize_t right,
                   arrsize_t max_iters) {
    /*
     * Resort to std::sort if quicksort isnt making any progress
     */
    if (max_iters <= 0) {
        std::sort(arr + left, arr + right + 1, comparison_func_ge<vtype>);
        return;
    }
    /*
     * Base case: use bitonic networks to sort arrays <=
     * vtype::network_sort_threshold
     */
    if (right + 1 - left <= vtype::network_sort_threshold) {
        sort_n<vtype, vtype::network_sort_threshold>(
            arr + left, (int32_t)(right + 1 - left));
        return;
    }

    type_t pivot = get_pivot_blocks<vtype, type_t>(arr, left, right);
    type_t smallest = vtype::type_max();
    type_t biggest = vtype::type_min();

    arrsize_t pivot_index =
        partition_avx512_unrolled<vtype, vtype::partition_unroll_factor>(
            arr, left, right + 1, pivot, &smallest, &biggest, false);

    if (pivot != smallest)
        qsort_<vtype>(arr, left, pivot_index - 1, max_iters - 1);
    if (pivot != biggest) qsort_<vtype>(arr, pivot_index, right, max_iters - 1);
}

// Hooks for OpenJDK sort
// to_index (exclusive)
template <typename vtype, typename type_t>
static int64_t vectorized_partition(type_t *arr, int64_t from_index, int64_t to_index, type_t pivot, bool use_gt) {
    type_t smallest = vtype::type_max();
    type_t biggest = vtype::type_min();
    int64_t pivot_index = partition_avx512_unrolled<vtype, 2>(
            arr, from_index, to_index, pivot, &smallest, &biggest, use_gt);
    return pivot_index;
}

// partitioning functions
template <typename vtype, typename T>
X86_SIMD_SORT_INLINE void simd_dual_pivot_partition(T *arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot1, int64_t index_pivot2){
    const T pivot1 = arr[index_pivot1];
    const T pivot2 = arr[index_pivot2];

    const int64_t low = from_index;
    const int64_t high = to_index;
    const int64_t start = low + 1;
    const int64_t end = high - 1;


    std::swap(arr[index_pivot1], arr[low]);
    std::swap(arr[index_pivot2], arr[end]);


    const int64_t pivot_index2 = vectorized_partition<vtype, T>(arr, start, end, pivot2, true); // use_gt = true
    std::swap(arr[end], arr[pivot_index2]);
    int64_t upper = pivot_index2;

    // if all other elements are greater than pivot2 (and pivot1), no need to do further partitioning
    if (upper == start) {
        pivot_indices[0] = low;
        pivot_indices[1] = upper;
        return;
    }

    const int64_t pivot_index1 = vectorized_partition<vtype, T>(arr, start, upper, pivot1, false); // use_ge (use_gt = false)
    int64_t lower = pivot_index1 - 1;
    std::swap(arr[low], arr[lower]);

    pivot_indices[0] = lower;
    pivot_indices[1] = upper;
}

template <typename vtype, typename T>
X86_SIMD_SORT_INLINE void simd_single_pivot_partition(T *arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot) {
    const T pivot = arr[index_pivot];

    const int64_t low = from_index;
    const int64_t high = to_index;
    const int64_t end = high - 1;


    const int64_t pivot_index1 = vectorized_partition<vtype, T>(arr, low, high, pivot, false); // use_gt = false (use_ge)
    int64_t lower = pivot_index1;

    const int64_t pivot_index2 = vectorized_partition<vtype, T>(arr, pivot_index1, high, pivot, true); // use_gt = true
    int64_t upper = pivot_index2;

    pivot_indices[0] = lower;
    pivot_indices[1] = upper;
}

template <typename vtype, typename T>
X86_SIMD_SORT_INLINE void simd_fast_partition(T *arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot1, int64_t index_pivot2) {
    if (index_pivot1 != index_pivot2) {
        simd_dual_pivot_partition<vtype, T>(arr, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
    }
    else {
        simd_single_pivot_partition<vtype, T>(arr, from_index, to_index, pivot_indices, index_pivot1);
    }
}

template <typename T>
X86_SIMD_SORT_INLINE void insertion_sort(T *arr, int32_t from_index, int32_t to_index) {
    for (int i, k = from_index; ++k < to_index; ) {
        T ai = arr[i = k];
        if (ai < arr[i - 1]) {
            while (--i >= from_index && ai < arr[i]) {
                arr[i + 1] = arr[i];
            }
            arr[i + 1] = ai;
        }
    }
}

template <typename vtype, typename T>
X86_SIMD_SORT_INLINE void simd_fast_sort(T *arr, arrsize_t from_index, arrsize_t to_index, const arrsize_t INS_SORT_THRESHOLD)
{
    arrsize_t arrsize = to_index - from_index;
    if (arrsize <= INS_SORT_THRESHOLD) {
        insertion_sort<T>(arr, from_index, to_index);
    } else {
        qsort_<vtype, T>(arr, from_index, to_index - 1, 2 * (arrsize_t)log2(arrsize));
    }
}

#define DEFINE_METHODS(ISA, VTYPE) \
    template <typename T> \
    X86_SIMD_SORT_INLINE void ISA##_fast_sort( \
            T *arr, arrsize_t from_index, arrsize_t to_index, const arrsize_t INS_SORT_THRESHOLD) \
    { \
        simd_fast_sort<VTYPE, T>(arr, from_index, to_index, INS_SORT_THRESHOLD); \
    } \
    template <typename T> \
    X86_SIMD_SORT_INLINE void ISA##_fast_partition( \
            T *arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot1, int64_t index_pivot2) \
    { \
        simd_fast_partition<VTYPE, T>(arr, from_index, to_index, pivot_indices, index_pivot1, index_pivot2); \
    }

DEFINE_METHODS(avx2, avx2_vector<T>)
DEFINE_METHODS(avx512, zmm_vector<T>)

#endif  // XSS_COMMON_QSORT
