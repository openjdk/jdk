/*
 * Copyright (c) 2021, 2023, Intel Corporation. All rights reserved.
 * Copyright (c) 2021 Serge Sans Paille. All rights reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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

#ifndef AARCH64_SVE_COMMON_QSORT_HPP
#define AARCH64_SVE_COMMON_QSORT_HPP
#include <algorithm>
#include <cmath>
#include <cstring>
#include <utility>

#include "sve-config.hpp"
#include "classfile_constants.h"
#include "simdsort-support.hpp"
#include "sve-qsort.hpp"
#include "pivot-selection.hpp"
#include "sve-oet-sort.hpp"

template <typename vtype, typename T = typename vtype::type_t>
bool sve_comparison_func_ge(const T &a, const T &b) {
    return a < b;
}

template <typename vtype, typename T = typename vtype::type_t>
bool sve_comparison_func_gt(const T &a, const T &b) {
    return a <= b;
}

/*
 * Partitions a single SIMD vector based on a pivot and returns the number
 * of lanes greater than or equal to the pivot.
 */
template <typename vtype, typename type_t,
          typename reg_t = typename vtype::reg_t>
SVE_SORT_INLINE arrsize_t partition_vec(type_t *l_store, type_t *r_store,
                                        const reg_t curr_vec,
                                        const reg_t pivot_vec,
                                        reg_t &smallest_vec,
                                        reg_t &biggest_vec, bool use_gt) {
    typename vtype::opmask_t mask;
    if (use_gt) {
        mask = vtype::gt(curr_vec, pivot_vec);
    } else {
        mask = vtype::ge(curr_vec, pivot_vec);
    }

    int amount_ge_pivot = vtype::double_compressstore(l_store, r_store, mask, curr_vec);

    smallest_vec = vtype::min(curr_vec, smallest_vec);
    biggest_vec  = vtype::max(curr_vec, biggest_vec);

    return amount_ge_pivot;
}

/*
 * Partition an array based on the pivot and returns the index of the
 * first element that is greater than or equal to the pivot.
 */
template <typename vtype, typename type_t>
SVE_SORT_INLINE arrsize_t sve_vect_partition_(type_t *arr, arrsize_t left,
                                              arrsize_t right, type_t pivot,
                                              type_t *smallest,
                                              type_t *biggest,
                                              bool use_gt) {
    auto comparison_func = use_gt ? sve_comparison_func_gt<vtype> : sve_comparison_func_ge<vtype>;

    // Store the number of lanes in a local variable
    const arrsize_t num_lanes = vtype::numlanes();

    /* make array length divisible by num_lanes, shortening the array */
    for (int32_t i = (right - left) % num_lanes; i > 0; --i) {
        *smallest = std::min(*smallest, arr[left], comparison_func);
        *biggest = std::max(*biggest, arr[left], comparison_func);

        if (!comparison_func(arr[left], pivot)) {
            std::swap(arr[left], arr[--right]);
        } else {
            ++left;
        }
    }

    if (left == right)
        return left; /* less than num_lanes elements in the array */

    using reg_t = typename vtype::reg_t;

    reg_t pivot_vec = vtype::set1(pivot);
    reg_t min_vec = vtype::set1(*smallest);
    reg_t max_vec = vtype::set1(*biggest);

    // If there is only num_lanes worth of elements to be sorted
    if (right - left == num_lanes) {
        reg_t vec = vtype::loadu(arr + left);
        arrsize_t l_store = left;
        arrsize_t r_store = l_store;

        arrsize_t amount_ge_pivot = partition_vec<vtype>(arr + l_store,
                                                         arr + r_store,
                                                         vec, pivot_vec, min_vec, max_vec, use_gt);

        l_store  += (num_lanes - amount_ge_pivot);
        *smallest = vtype::reducemin(min_vec);
        *biggest  = vtype::reducemax(max_vec);

        return l_store;
    }

    // first and last num_lanes values are partitioned at the end
    reg_t vec_left = vtype::loadu(arr + left);
    reg_t vec_right = vtype::loadu(arr + (right - num_lanes));

    // store points of the vectors
    arrsize_t l_store = left;
    arrsize_t r_store = right - num_lanes;

    // indices for loading the elements
    left  += num_lanes;
    right -= num_lanes;

    while (right - left != 0) {
        reg_t curr_vec;
        /*
         * if fewer elements are stored on the right side of the array,
         * then next elements are loaded from the right side,
         * otherwise from the left side
         */
        if ((r_store + num_lanes) - right < left - l_store) {
            right -= num_lanes;
            curr_vec = vtype::loadu(arr + right);
        } else {
            curr_vec = vtype::loadu(arr + left);
            left += num_lanes;
        }
        // partition the current vector and save it on both sides of the array
        arrsize_t amount_ge_pivot = partition_vec<vtype>(arr + l_store,
                                                         arr + r_store,
                                                         curr_vec, pivot_vec, min_vec, max_vec, use_gt);
        l_store += (num_lanes - amount_ge_pivot);
        r_store -= amount_ge_pivot;
    }

    /* partition and save vec_left and vec_right */
    arrsize_t amount_ge_pivot = partition_vec<vtype>(arr + l_store,
                                                     arr + r_store,
                                                     vec_left, pivot_vec, min_vec, max_vec, use_gt);
    l_store += (num_lanes - amount_ge_pivot);
    r_store -= amount_ge_pivot;


    amount_ge_pivot = partition_vec<vtype>(arr + l_store,
                                           arr + r_store,
                                           vec_right, pivot_vec, min_vec, max_vec, use_gt);
    l_store += (num_lanes - amount_ge_pivot);
    r_store -= amount_ge_pivot;

    *smallest = vtype::reducemin(min_vec);
    *biggest  = vtype::reducemax(max_vec);

    return l_store;
}

// Process a single vector for partitioning
template <typename vtype, typename type_t>
SVE_SORT_INLINE void sve_partition_single_vec(type_t* arr,
                                              arrsize_t& l_store,
                                              arrsize_t& r_store,
                                              typename vtype::reg_t v,
                                              typename vtype::reg_t pivot_vec,
                                              typename vtype::reg_t& min_vec,
                                              typename vtype::reg_t& max_vec,
                                              bool use_gt, arrsize_t num_lanes) {
    arrsize_t amount_ge_pivot = partition_vec<vtype>(arr + l_store,
                                                     arr + r_store,
                                                     v, pivot_vec, min_vec, max_vec, use_gt);

    l_store += num_lanes - amount_ge_pivot;
    r_store -= amount_ge_pivot;
}

// Unrolled version of sve_vect_partition_() with an UNROLL_FACTOR of either 2 or 4
// The UNROLL_FACTOR is 2 if the vector length <= 16B and it is 4 if the vector length > 16B
template <typename vtype, typename type_t, int UNROLL_FACTOR>
SVE_SORT_INLINE arrsize_t
sve_partition_unrolled(type_t* arr, arrsize_t left, arrsize_t right,
                       type_t pivot, type_t* smallest, type_t* biggest, bool use_gt) {
    static_assert(UNROLL_FACTOR == 2 || UNROLL_FACTOR == 4, "unsupported unroll factor");

    const arrsize_t num_lanes = vtype::numlanes();

    if constexpr (UNROLL_FACTOR == 0) {
        return sve_vect_partition_<vtype, type_t>(arr, left, right, pivot, smallest, biggest, use_gt);
    }

    // use regular partition routine for small arrays
    if (right - left < 3 * UNROLL_FACTOR * num_lanes) {
        return sve_vect_partition_<vtype, type_t>(arr, left, right, pivot, smallest, biggest, use_gt);
    }

    auto comparison_func = use_gt ? sve_comparison_func_gt<vtype>
                                  : sve_comparison_func_ge<vtype>;

    // make array length divisible by num_lanes, shortening the array
    for (int32_t i = (right - left) % num_lanes; i > 0; --i) {
        *smallest = std::min(*smallest, arr[left], comparison_func);
        *biggest  = std::max(*biggest,  arr[left], comparison_func);
        if (!comparison_func(arr[left], pivot)) {
            std::swap(arr[left], arr[--right]);
        } else {
            ++left;
        }
    }

    arrsize_t l_store = left;
    arrsize_t r_store = right - num_lanes;

    using reg_t = typename vtype::reg_t;
    reg_t pivot_vec = vtype::set1(pivot);
    reg_t min_vec   = vtype::set1(*smallest);
    reg_t max_vec   = vtype::set1(*biggest);

    /* Calculate and load more registers to make the rest of the array a
     * multiple of num_unroll. These registers will be partitioned at the very
     * end. */
    int vecsToPartition = ((right - left) / num_lanes) % UNROLL_FACTOR;

#define SVE_UNROLL_APPLY(OP)                                                     \
    do {                                                                         \
        if constexpr (UNROLL_FACTOR >= 1) { OP(0); }                             \
        if constexpr (UNROLL_FACTOR >= 2) { OP(1); }                             \
        if constexpr (UNROLL_FACTOR >= 3) { OP(2); }                             \
        if constexpr (UNROLL_FACTOR >= 4) { OP(3); }                             \
    } while (false)

#define SVE_DECLARE_REG_SET(NAME, INIT)                                          \
    [[maybe_unused]] reg_t NAME##0 = (INIT);                                     \
    [[maybe_unused]] reg_t NAME##1 = NAME##0;                                    \
    [[maybe_unused]] reg_t NAME##2 = NAME##0;                                    \
    [[maybe_unused]] reg_t NAME##3 = NAME##0

#define SVE_DECLARE_REG_SET_UNINIT(NAME)                                         \
    reg_t NAME##0;                                                               \
    reg_t NAME##1;                                                               \
    reg_t NAME##2;                                                               \
    reg_t NAME##3

#define SVE_REG(NAME, IDX) NAME##IDX

#define SVE_PARTITION_ONE(REG)                                                   \
    sve_partition_single_vec<vtype, type_t>(arr, l_store, r_store,               \
                                            REG, pivot_vec, min_vec, max_vec,    \
                                            use_gt, num_lanes)

#define SVE_LOAD_BLOCK_FROM(BASE_PTR, NAME, I)                                   \
    SVE_REG(NAME, I) = vtype::loadu((BASE_PTR) + (I) * num_lanes)

#define SVE_LOAD_TAIL(I)                                                         \
    do {                                                                         \
        if (vecsToPartition > (I)) {                                             \
            SVE_LOAD_BLOCK_FROM(arr + left, align_vec, I);                       \
        }                                                                        \
    } while(false)

#define SVE_LOAD_LEFT(I)                                                         \
    SVE_LOAD_BLOCK_FROM(arr + left, left_vec, I)

#define SVE_LOAD_RIGHT(I)                                                        \
    SVE_LOAD_BLOCK_FROM(arr + right_load_start, right_vec, I)

#define SVE_LOAD_BATCH_FROM_RIGHT(I)                                             \
    SVE_LOAD_BLOCK_FROM(arr + right, curr_vec, I)

#define SVE_LOAD_BATCH_FROM_LEFT(I)                                              \
    SVE_LOAD_BLOCK_FROM(arr + left, curr_vec, I)

#define SVE_PARTITION_BATCH(I)                                                   \
    SVE_PARTITION_ONE(SVE_REG(curr_vec, I))
#define SVE_PARTITION_LEFT(I) SVE_PARTITION_ONE(SVE_REG(left_vec, I))
#define SVE_PARTITION_RIGHT(I) SVE_PARTITION_ONE(SVE_REG(right_vec, I))
#define SVE_PARTITION_TAIL(I)                                                    \
    do {                                                                         \
        if (vecsToPartition > (I)) {                                             \
            SVE_PARTITION_ONE(SVE_REG(align_vec, I));                            \
        }                                                                        \
    } while(false)

    // Initialize the vectors to something arbitrary which will be overwritten when
    // the appropriate array elements are loaded in them
    SVE_DECLARE_REG_SET(align_vec, vtype::set1(pivot));

    // Load the align_vec vectors depending on the vecsToPartition value
    SVE_UNROLL_APPLY(SVE_LOAD_TAIL);

    // Initialize the vectors to something arbitrary which will be overwritten when
    // the appropriate array elements are loaded in them
    left += vecsToPartition * num_lanes;

    /* Load left and right vtype::numlanes*num_unroll values into
     * registers to make space for in-place parition. The vec_left and
     * vec_right registers are partitioned at the end.
     * Similar to the align_vec<x> vectors, the left<x> and right<x> vectors
     * are also initialized to an arbitrary value which will eventually be
     * overwritten by array loads. */

    SVE_DECLARE_REG_SET(left_vec, vtype::set1(pivot));
    SVE_DECLARE_REG_SET(right_vec, vtype::set1(pivot));

    const arrsize_t right_load_start = right - UNROLL_FACTOR * num_lanes;

    SVE_UNROLL_APPLY(SVE_LOAD_LEFT);
    SVE_UNROLL_APPLY(SVE_LOAD_RIGHT);

    /* indices for loading the elements */
    left  += UNROLL_FACTOR * num_lanes;
    right -= UNROLL_FACTOR * num_lanes;

    while ((right - left) != 0) {
        if ((r_store + num_lanes) - right < left - l_store) {
            // Load from the right side if there are fewer elements on the right
            // and partition the vectors
            // TODO: Explore if prefetching the next set of vectors would be beneficial here
            right -= (UNROLL_FACTOR * num_lanes);
            SVE_DECLARE_REG_SET_UNINIT(curr_vec);
            SVE_UNROLL_APPLY(SVE_LOAD_BATCH_FROM_RIGHT);
            SVE_UNROLL_APPLY(SVE_PARTITION_BATCH);
        } else {
            // Load from the left side if there are fewer elements on the left
            // and partition the vectors
            SVE_DECLARE_REG_SET_UNINIT(curr_vec);
            SVE_UNROLL_APPLY(SVE_LOAD_BATCH_FROM_LEFT);
            left += UNROLL_FACTOR * num_lanes;
            SVE_UNROLL_APPLY(SVE_PARTITION_BATCH);
        }
    }

    // Partition the left and right vectors
    SVE_UNROLL_APPLY(SVE_PARTITION_LEFT);
    SVE_UNROLL_APPLY(SVE_PARTITION_RIGHT);

    // Partition the align_vec<x> vectors
    SVE_UNROLL_APPLY(SVE_PARTITION_TAIL);

#undef SVE_LOAD_TAIL
#undef SVE_LOAD_LEFT
#undef SVE_LOAD_RIGHT
#undef SVE_PARTITION_LEFT
#undef SVE_PARTITION_RIGHT
#undef SVE_PARTITION_TAIL
#undef SVE_PARTITION_BATCH
#undef SVE_LOAD_BATCH_FROM_LEFT
#undef SVE_LOAD_BATCH_FROM_RIGHT
#undef SVE_PARTITION_ONE
#undef SVE_REG
#undef SVE_DECLARE_REG_SET
#undef SVE_DECLARE_REG_SET_UNINIT
#undef SVE_UNROLL_APPLY

    *smallest = vtype::reducemin(min_vec);
    *biggest  = vtype::reducemax(max_vec);
    return l_store;
}

template <typename vtype, typename type_t>
SVE_SORT_INLINE arrsize_t sve_partition_select(type_t *arr, arrsize_t left, arrsize_t right, type_t pivot,
                                               type_t *smallest, type_t *biggest, bool use_gt) {
    if (vtype::partition_unroll_factor() == 4) {
        return sve_partition_unrolled<vtype, type_t, 4>(arr, left, right, pivot, smallest, biggest, use_gt);
    } else {
        return sve_partition_unrolled<vtype, type_t, 2>(arr, left, right, pivot, smallest, biggest, use_gt);
    }
}

template <typename vtype, typename type_t>
SVE_SORT_INLINE void sve_qsort(type_t* arr, arrsize_t left, arrsize_t right,
                               arrsize_t max_iters) {
    if ((right - left) <= OET_SORT_THRESHOLD)
        return;

    if (max_iters <= 0) {
        std::sort(arr + left, arr + right, sve_comparison_func_ge<vtype>);
        return;
    }

    type_t pivot = get_pivot_blocks<vtype, type_t>(arr, left, right);

    type_t smallest = vtype::type_max();
    type_t biggest = vtype::type_min();

    arrsize_t pivot_index = sve_partition_select<vtype, type_t>(arr, left, right,
                                                                pivot, &smallest,
                                                                &biggest, false);

    if (pivot != smallest) {
        sve_qsort<vtype>(arr, left, pivot_index, max_iters - 1);
    }
    if (pivot != biggest) {
        sve_qsort<vtype>(arr, pivot_index, right, max_iters - 1);
    }
}

template <typename vtype, typename type_t>
SVE_SORT_INLINE int64_t sve_vect_partition(type_t* arr, int64_t from_index, int64_t to_index, type_t pivot, bool use_gt) {
    type_t smallest = vtype::type_max();
    type_t biggest = vtype::type_min();
    int64_t pivot_index = sve_partition_select<vtype, type_t>(arr, from_index, to_index,
                                                              pivot, &smallest, &biggest, use_gt);
    return pivot_index;
}

template <typename vtype, typename T>
SVE_SORT_INLINE void sve_dual_pivot_partition(T* arr, int64_t from_index, int64_t to_index,
                                              int32_t *pivot_indices, int64_t index_pivot1, int64_t index_pivot2){
    const T pivot1 = arr[index_pivot1];
    const T pivot2 = arr[index_pivot2];

    const int64_t low = from_index;
    const int64_t high = to_index;
    const int64_t start = low + 1;
    const int64_t end = high - 1;

    std::swap(arr[index_pivot1], arr[low]);
    std::swap(arr[index_pivot2], arr[end]);

    const int64_t pivot_index2 = sve_vect_partition<vtype, T>(arr, start, end, pivot2, true); // use_gt = true
    std::swap(arr[end], arr[pivot_index2]);
    int64_t upper = pivot_index2;

    // if all other elements are greater than pivot2 (and pivot1), no need to do further partitioning
    if (upper == start) {
        pivot_indices[0] = low;
        pivot_indices[1] = upper;
        return;
    }

    const int64_t pivot_index1 = sve_vect_partition<vtype, T>(arr, start, upper, pivot1, false); // use_ge (use_gt = false)
    int64_t lower = pivot_index1 - 1;
    std::swap(arr[low], arr[lower]);

    pivot_indices[0] = lower;
    pivot_indices[1] = upper;
}

template <typename vtype, typename T>
SVE_SORT_INLINE void sve_single_pivot_partition(T* arr, int64_t from_index, int64_t to_index,
                                                int32_t *pivot_indices, int64_t index_pivot) {
    const T pivot = arr[index_pivot];

    const int64_t low = from_index;
    const int64_t high = to_index;
    const int64_t end = high - 1;


    const int64_t pivot_index1 = sve_vect_partition<vtype, T>(arr, low, high, pivot, false); // use_gt = false (use_ge)
    int64_t lower = pivot_index1;

    const int64_t pivot_index2 = sve_vect_partition<vtype, T>(arr, pivot_index1, high, pivot, true); // use_gt = true
    int64_t upper = pivot_index2;

    pivot_indices[0] = lower;
    pivot_indices[1] = upper;
}

template <typename T>
SVE_SORT_INLINE void insertion_sort(T* arr, int32_t from_index, int32_t to_index) {
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

template <typename T>
SVE_SORT_INLINE void sve_fast_sort(T* arr, arrsize_t from_index, arrsize_t to_index, const arrsize_t INS_SORT_THRESHOLD) {
    arrsize_t arrsize = to_index - from_index;

    if (arrsize <= INS_SORT_THRESHOLD) {
        insertion_sort<T>(arr, from_index, to_index);
    } else {
        sve_qsort<sve_vector<T>, T>(arr, from_index, to_index, 2 * (arrsize_t) (63 - __builtin_clzll((unsigned long long) arrsize)));
        sve_oet_sort<sve_vector<T>, T>(arr, from_index, to_index);
    }
}

template <typename T>
SVE_SORT_INLINE void sve_fast_partition(T* arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot1, int64_t index_pivot2) {
    if (index_pivot1 != index_pivot2) {
        sve_dual_pivot_partition<sve_vector<T>, T>(arr, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
    }
    else {
        sve_single_pivot_partition<sve_vector<T>, T>(arr, from_index, to_index, pivot_indices, index_pivot1);
    }
}
#endif // AARCH64_SVE_COMMON_QSORT_HPP
