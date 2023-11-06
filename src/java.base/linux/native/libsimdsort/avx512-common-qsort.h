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
#ifndef AVX512_QSORT_COMMON
#define AVX512_QSORT_COMMON

/*
 * Quicksort using AVX-512. The ideas and code are based on these two research
 * papers [1] and [2]. On a high level, the idea is to vectorize quicksort
 * partitioning using AVX-512 compressstore instructions. If the array size is
 * < 128, then use Bitonic sorting network implemented on 512-bit registers.
 * The precise network definitions depend on the dtype and are defined in
 * separate files: avx512-16bit-qsort.hpp, avx512-32bit-qsort.hpp and
 * avx512-64bit-qsort.hpp. Article [4] is a good resource for bitonic sorting
 * network. The core implementations of the vectorized qsort functions
 * avx512_qsort<T>(T*, int64_t) are modified versions of avx2 quicksort
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
 * [4]
 * http://mitp-content-server.mit.edu:18180/books/content/sectbyfn?collid=books_pres_0&fn=Chapter%2027.pdf&id=8030
 *
 */

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>

/*
Workaround for the bug in GCC12 (that was fixed in GCC 12.3.1).
More details are available at: https://gcc.gnu.org/bugzilla/show_bug.cgi?id=105593
*/
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wmaybe-uninitialized"
#pragma GCC diagnostic ignored "-Wuninitialized"
#include <immintrin.h>
#pragma GCC diagnostic pop

#define X86_SIMD_SORT_INFINITY std::numeric_limits<double>::infinity()
#define X86_SIMD_SORT_INFINITYF std::numeric_limits<float>::infinity()
#define X86_SIMD_SORT_INFINITYH 0x7c00
#define X86_SIMD_SORT_NEGINFINITYH 0xfc00
#define X86_SIMD_SORT_MAX_UINT16 std::numeric_limits<uint16_t>::max()
#define X86_SIMD_SORT_MAX_INT16 std::numeric_limits<int16_t>::max()
#define X86_SIMD_SORT_MIN_INT16 std::numeric_limits<int16_t>::min()
#define X86_SIMD_SORT_MAX_UINT32 std::numeric_limits<uint32_t>::max()
#define X86_SIMD_SORT_MAX_INT32 std::numeric_limits<int32_t>::max()
#define X86_SIMD_SORT_MIN_INT32 std::numeric_limits<int32_t>::min()
#define X86_SIMD_SORT_MAX_UINT64 std::numeric_limits<uint64_t>::max()
#define X86_SIMD_SORT_MAX_INT64 std::numeric_limits<int64_t>::max()
#define X86_SIMD_SORT_MIN_INT64 std::numeric_limits<int64_t>::min()
#define ZMM_MAX_DOUBLE _mm512_set1_pd(X86_SIMD_SORT_INFINITY)
#define ZMM_MAX_UINT64 _mm512_set1_epi64(X86_SIMD_SORT_MAX_UINT64)
#define ZMM_MAX_INT64 _mm512_set1_epi64(X86_SIMD_SORT_MAX_INT64)
#define ZMM_MAX_FLOAT _mm512_set1_ps(X86_SIMD_SORT_INFINITYF)
#define ZMM_MAX_UINT _mm512_set1_epi32(X86_SIMD_SORT_MAX_UINT32)
#define ZMM_MAX_INT _mm512_set1_epi32(X86_SIMD_SORT_MAX_INT32)
#define ZMM_MAX_HALF _mm512_set1_epi16(X86_SIMD_SORT_INFINITYH)
#define YMM_MAX_HALF _mm256_set1_epi16(X86_SIMD_SORT_INFINITYH)
#define ZMM_MAX_UINT16 _mm512_set1_epi16(X86_SIMD_SORT_MAX_UINT16)
#define ZMM_MAX_INT16 _mm512_set1_epi16(X86_SIMD_SORT_MAX_INT16)
#define SHUFFLE_MASK(a, b, c, d) (a << 6) | (b << 4) | (c << 2) | d

#ifdef _MSC_VER
#define X86_SIMD_SORT_INLINE static inline
#define X86_SIMD_SORT_FINLINE static __forceinline
#elif defined(__CYGWIN__)
/*
 * Force inline in cygwin to work around a compiler bug. See
 * https://github.com/numpy/numpy/pull/22315#issuecomment-1267757584
 */
#define X86_SIMD_SORT_INLINE static __attribute__((always_inline))
#define X86_SIMD_SORT_FINLINE static __attribute__((always_inline))
#elif defined(__GNUC__)
#define X86_SIMD_SORT_INLINE static inline
#define X86_SIMD_SORT_FINLINE static __attribute__((always_inline))
#else
#define X86_SIMD_SORT_INLINE static
#define X86_SIMD_SORT_FINLINE static
#endif

#define LIKELY(x) __builtin_expect((x), 1)
#define UNLIKELY(x) __builtin_expect((x), 0)

template <typename type>
struct zmm_vector;

template <typename type>
struct ymm_vector;

// Regular quicksort routines:
template <typename T>
void avx512_qsort(T *arr, int64_t arrsize);

template <typename T>
void inline avx512_qsort(T *arr, int64_t from_index, int64_t to_index);

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
static void COEX(mm_t &a, mm_t &b) {
    mm_t temp = a;
    a = vtype::min(a, b);
    b = vtype::max(temp, b);
}
template <typename vtype, typename zmm_t = typename vtype::zmm_t,
          typename opmask_t = typename vtype::opmask_t>
static inline zmm_t cmp_merge(zmm_t in1, zmm_t in2, opmask_t mask) {
    zmm_t min = vtype::min(in2, in1);
    zmm_t max = vtype::max(in2, in1);
    return vtype::mask_mov(min, mask, max);  // 0 -> min, 1 -> max
}
/*
 * Parition one ZMM register based on the pivot and returns the
 * number of elements that are greater than or equal to the pivot.
 */
template <typename vtype, typename type_t, typename zmm_t>
static inline int32_t partition_vec(type_t *arr, int64_t left, int64_t right,
                                    const zmm_t curr_vec, const zmm_t pivot_vec,
                                    zmm_t *smallest_vec, zmm_t *biggest_vec, bool use_gt) {
    /* which elements are larger than or equal to the pivot */
    typename vtype::opmask_t mask;
    if (use_gt) mask = vtype::gt(curr_vec, pivot_vec);
    else mask = vtype::ge(curr_vec, pivot_vec);
    //mask = vtype::ge(curr_vec, pivot_vec);
    int32_t amount_ge_pivot = _mm_popcnt_u32((int32_t)mask);
    vtype::mask_compressstoreu(arr + left, vtype::knot_opmask(mask),
                               curr_vec);
    vtype::mask_compressstoreu(arr + right - amount_ge_pivot, mask,
                               curr_vec);
    *smallest_vec = vtype::min(curr_vec, *smallest_vec);
    *biggest_vec = vtype::max(curr_vec, *biggest_vec);
    return amount_ge_pivot;
}
/*
 * Parition an array based on the pivot and returns the index of the
 * first element that is greater than or equal to the pivot.
 */
template <typename vtype, typename type_t>
static inline int64_t partition_avx512(type_t *arr, int64_t left, int64_t right,
                                       type_t pivot, type_t *smallest,
                                       type_t *biggest, bool use_gt) {
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

    using zmm_t = typename vtype::zmm_t;
    zmm_t pivot_vec = vtype::set1(pivot);
    zmm_t min_vec = vtype::set1(*smallest);
    zmm_t max_vec = vtype::set1(*biggest);

    if (right - left == vtype::numlanes) {
        zmm_t vec = vtype::loadu(arr + left);
        int32_t amount_ge_pivot =
            partition_vec<vtype>(arr, left, left + vtype::numlanes, vec,
                                 pivot_vec, &min_vec, &max_vec, use_gt);
        *smallest = vtype::reducemin(min_vec);
        *biggest = vtype::reducemax(max_vec);
        return left + (vtype::numlanes - amount_ge_pivot);
    }

    // first and last vtype::numlanes values are partitioned at the end
    zmm_t vec_left = vtype::loadu(arr + left);
    zmm_t vec_right = vtype::loadu(arr + (right - vtype::numlanes));
    // store points of the vectors
    int64_t r_store = right - vtype::numlanes;
    int64_t l_store = left;
    // indices for loading the elements
    left += vtype::numlanes;
    right -= vtype::numlanes;
    while (right - left != 0) {
        zmm_t curr_vec;
        /*
         * if fewer elements are stored on the right side of the array,
         * then next elements are loaded from the right side,
         * otherwise from the left side
         */
        if ((r_store + vtype::numlanes) - right < left - l_store) {
            right -= vtype::numlanes;
            curr_vec = vtype::loadu(arr + right);
        } else {
            curr_vec = vtype::loadu(arr + left);
            left += vtype::numlanes;
        }
        // partition the current vector and save it on both sides of the array
        int32_t amount_ge_pivot =
            partition_vec<vtype>(arr, l_store, r_store + vtype::numlanes,
                                 curr_vec, pivot_vec, &min_vec, &max_vec, use_gt);
        ;
        r_store -= amount_ge_pivot;
        l_store += (vtype::numlanes - amount_ge_pivot);
    }

    /* partition and save vec_left and vec_right */
    int32_t amount_ge_pivot =
        partition_vec<vtype>(arr, l_store, r_store + vtype::numlanes, vec_left,
                             pivot_vec, &min_vec, &max_vec, use_gt);
    l_store += (vtype::numlanes - amount_ge_pivot);
    amount_ge_pivot =
        partition_vec<vtype>(arr, l_store, l_store + vtype::numlanes, vec_right,
                             pivot_vec, &min_vec, &max_vec, use_gt);
    l_store += (vtype::numlanes - amount_ge_pivot);
    *smallest = vtype::reducemin(min_vec);
    *biggest = vtype::reducemax(max_vec);
    return l_store;
}

template <typename vtype, int num_unroll,
          typename type_t = typename vtype::type_t>
static inline int64_t partition_avx512_unrolled(type_t *arr, int64_t left,
                                                int64_t right, type_t pivot,
                                                type_t *smallest,
                                                type_t *biggest, bool use_gt) {
    if (right - left <= 2 * num_unroll * vtype::numlanes) {
        return partition_avx512<vtype>(arr, left, right, pivot, smallest,
                                       biggest, use_gt);
    }

    auto comparison_func = use_gt ? comparison_func_gt<vtype> : comparison_func_ge<vtype>;
    /* make array length divisible by 8*vtype::numlanes , shortening the array
     */
    for (int32_t i = ((right - left) % (num_unroll * vtype::numlanes)); i > 0;
         --i) {
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

    using zmm_t = typename vtype::zmm_t;
    zmm_t pivot_vec = vtype::set1(pivot);
    zmm_t min_vec = vtype::set1(*smallest);
    zmm_t max_vec = vtype::set1(*biggest);

    // We will now have atleast 16 registers worth of data to process:
    // left and right vtype::numlanes values are partitioned at the end
    zmm_t vec_left[num_unroll], vec_right[num_unroll];
#pragma GCC unroll 8
    for (int ii = 0; ii < num_unroll; ++ii) {
        vec_left[ii] = vtype::loadu(arr + left + vtype::numlanes * ii);
        vec_right[ii] =
            vtype::loadu(arr + (right - vtype::numlanes * (num_unroll - ii)));
    }
    // store points of the vectors
    int64_t r_store = right - vtype::numlanes;
    int64_t l_store = left;
    // indices for loading the elements
    left += num_unroll * vtype::numlanes;
    right -= num_unroll * vtype::numlanes;
    while (right - left != 0) {
        zmm_t curr_vec[num_unroll];
        /*
         * if fewer elements are stored on the right side of the array,
         * then next elements are loaded from the right side,
         * otherwise from the left side
         */
        if ((r_store + vtype::numlanes) - right < left - l_store) {
            right -= num_unroll * vtype::numlanes;
#pragma GCC unroll 8
            for (int ii = 0; ii < num_unroll; ++ii) {
                curr_vec[ii] = vtype::loadu(arr + right + ii * vtype::numlanes);
            }
        } else {
#pragma GCC unroll 8
            for (int ii = 0; ii < num_unroll; ++ii) {
                curr_vec[ii] = vtype::loadu(arr + left + ii * vtype::numlanes);
            }
            left += num_unroll * vtype::numlanes;
        }
// partition the current vector and save it on both sides of the array
#pragma GCC unroll 8
        for (int ii = 0; ii < num_unroll; ++ii) {
            int32_t amount_ge_pivot = partition_vec<vtype>(
                arr, l_store, r_store + vtype::numlanes, curr_vec[ii],
                pivot_vec, &min_vec, &max_vec, use_gt);
            l_store += (vtype::numlanes - amount_ge_pivot);
            r_store -= amount_ge_pivot;
        }
    }

/* partition and save vec_left[8] and vec_right[8] */
#pragma GCC unroll 8
    for (int ii = 0; ii < num_unroll; ++ii) {
        int32_t amount_ge_pivot =
            partition_vec<vtype>(arr, l_store, r_store + vtype::numlanes,
                                 vec_left[ii], pivot_vec, &min_vec, &max_vec, use_gt);
        l_store += (vtype::numlanes - amount_ge_pivot);
        r_store -= amount_ge_pivot;
    }
#pragma GCC unroll 8
    for (int ii = 0; ii < num_unroll; ++ii) {
        int32_t amount_ge_pivot =
            partition_vec<vtype>(arr, l_store, r_store + vtype::numlanes,
                                 vec_right[ii], pivot_vec, &min_vec, &max_vec, use_gt);
        l_store += (vtype::numlanes - amount_ge_pivot);
        r_store -= amount_ge_pivot;
    }
    *smallest = vtype::reducemin(min_vec);
    *biggest = vtype::reducemax(max_vec);
    return l_store;
}

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
template <typename T>
void avx512_dual_pivot_partition(T *arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot1, int64_t index_pivot2){
    const T pivot1 = arr[index_pivot1];
    const T pivot2 = arr[index_pivot2];

    const int64_t low = from_index;
    const int64_t high = to_index;
    const int64_t start = low + 1;
    const int64_t end = high - 1;


    std::swap(arr[index_pivot1], arr[low]);
    std::swap(arr[index_pivot2], arr[end]);


    const int64_t pivot_index2 = vectorized_partition<zmm_vector<T>, T>(arr, start, end, pivot2, true); // use_gt = true
    std::swap(arr[end], arr[pivot_index2]);
    int64_t upper = pivot_index2;

    // if all other elements are greater than pivot2 (and pivot1), no need to do further partitioning
    if (upper == start) {
        pivot_indices[0] = low;
        pivot_indices[1] = upper;
        return;
    }

    const int64_t pivot_index1 = vectorized_partition<zmm_vector<T>, T>(arr, start, upper, pivot1, false); // use_ge (use_gt = false)
    int64_t lower = pivot_index1 - 1;
    std::swap(arr[low], arr[lower]);

    pivot_indices[0] = lower;
    pivot_indices[1] = upper;
}

template <typename T>
void avx512_single_pivot_partition(T *arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot){
    const T pivot = arr[index_pivot];

    const int64_t low = from_index;
    const int64_t high = to_index;
    const int64_t end = high - 1;


    const int64_t pivot_index1 = vectorized_partition<zmm_vector<T>, T>(arr, low, high, pivot, false); // use_gt = false (use_ge)
    int64_t lower = pivot_index1;

    const int64_t pivot_index2 = vectorized_partition<zmm_vector<T>, T>(arr, pivot_index1, high, pivot, true); // use_gt = true
    int64_t upper = pivot_index2;

    pivot_indices[0] = lower;
    pivot_indices[1] = upper;
}

template <typename T>
void inline avx512_fast_partition(T *arr, int64_t from_index, int64_t to_index, int32_t *pivot_indices, int64_t index_pivot1, int64_t index_pivot2) {
    if (index_pivot1 != index_pivot2) {
        avx512_dual_pivot_partition<T>(arr, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
    }
    else {
        avx512_single_pivot_partition<T>(arr, from_index, to_index, pivot_indices, index_pivot1);
    }
}

template <typename T>
void inline insertion_sort(T *arr, int32_t from_index, int32_t to_index) {
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
void inline avx512_fast_sort(T *arr, int64_t from_index, int64_t to_index, const int32_t INS_SORT_THRESHOLD) {
    int32_t size = to_index - from_index;

    if (size <= INS_SORT_THRESHOLD) {
        insertion_sort<T>(arr, from_index, to_index);
    }
    else {
        avx512_qsort<T>(arr, from_index, to_index);
    }
}



#endif  // AVX512_QSORT_COMMON
