/*
 * Copyright (c) 2021, 2023, Intel Corporation. All rights reserved.
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

#ifndef AVX512_QSORT_64BIT
#define AVX512_QSORT_64BIT

#include "xss-common-includes.h"
#include "xss-common-qsort.h"

/*
 * Constants used in sorting 8 elements in a ZMM registers. Based on Bitonic
 * sorting network (see
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg)
 */
// ZMM                  7, 6, 5, 4, 3, 2, 1, 0
#define NETWORK_64BIT_1 4, 5, 6, 7, 0, 1, 2, 3
#define NETWORK_64BIT_2 0, 1, 2, 3, 4, 5, 6, 7
#define NETWORK_64BIT_3 5, 4, 7, 6, 1, 0, 3, 2
#define NETWORK_64BIT_4 3, 2, 1, 0, 7, 6, 5, 4

template <typename vtype, typename reg_t>
X86_SIMD_SORT_INLINE reg_t sort_zmm_64bit(reg_t zmm);

struct avx512_64bit_swizzle_ops;

template <>
struct zmm_vector<int64_t> {
    using type_t = int64_t;
    using reg_t = __m512i;
    using regi_t = __m512i;
    using halfreg_t = __m512i;
    using opmask_t = __mmask8;
    static const uint8_t numlanes = 8;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 256;
#endif
    static constexpr int partition_unroll_factor = 8;

    using swizzle_ops = avx512_64bit_swizzle_ops;

    static type_t type_max() { return X86_SIMD_SORT_MAX_INT64; }
    static type_t type_min() { return X86_SIMD_SORT_MIN_INT64; }
    static reg_t zmm_max() {
        return _mm512_set1_epi64(type_max());
    }  // TODO: this should broadcast bits as is?

    static regi_t seti(int v1, int v2, int v3, int v4, int v5, int v6, int v7,
                       int v8) {
        return _mm512_set_epi64(v1, v2, v3, v4, v5, v6, v7, v8);
    }
    static reg_t set(type_t v1, type_t v2, type_t v3, type_t v4, type_t v5,
                     type_t v6, type_t v7, type_t v8) {
        return _mm512_set_epi64(v1, v2, v3, v4, v5, v6, v7, v8);
    }
    static opmask_t kxor_opmask(opmask_t x, opmask_t y) {
        return _kxor_mask8(x, y);
    }
    static opmask_t knot_opmask(opmask_t x) { return _knot_mask8(x); }
    static opmask_t le(reg_t x, reg_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_LE);
    }
    static opmask_t ge(reg_t x, reg_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_NLT);
    }
    static opmask_t gt(reg_t x, reg_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_GT);
    }
    static opmask_t get_partial_loadmask(uint64_t num_to_read) {
        return ((0x1ull << num_to_read) - 0x1ull);
    }
    static opmask_t eq(reg_t x, reg_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_EQ);
    }
    template <int scale>
    static reg_t mask_i64gather(reg_t src, opmask_t mask, __m512i index,
                                void const *base) {
        return _mm512_mask_i64gather_epi64(src, mask, index, base, scale);
    }
    template <int scale>
    static reg_t mask_i64gather(reg_t src, opmask_t mask, __m256i index,
                                void const *base) {
        return _mm512_mask_i32gather_epi64(src, mask, index, base, scale);
    }
    static reg_t i64gather(type_t *arr, arrsize_t *ind) {
        return set(arr[ind[7]], arr[ind[6]], arr[ind[5]], arr[ind[4]],
                   arr[ind[3]], arr[ind[2]], arr[ind[1]], arr[ind[0]]);
    }
    static reg_t loadu(void const *mem) { return _mm512_loadu_si512(mem); }
    static reg_t max(reg_t x, reg_t y) { return _mm512_max_epi64(x, y); }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_compressstoreu_epi64(mem, mask, x);
    }
    static reg_t maskz_loadu(opmask_t mask, void const *mem) {
        return _mm512_maskz_loadu_epi64(mask, mem);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem) {
        return _mm512_mask_loadu_epi64(x, mask, mem);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y) {
        return _mm512_mask_mov_epi64(x, mask, y);
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_storeu_epi64(mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y) { return _mm512_min_epi64(x, y); }
    static reg_t permutexvar(__m512i idx, reg_t zmm) {
        return _mm512_permutexvar_epi64(idx, zmm);
    }
    static type_t reducemax(reg_t v) { return _mm512_reduce_max_epi64(v); }
    static type_t reducemin(reg_t v) { return _mm512_reduce_min_epi64(v); }
    static reg_t set1(type_t v) { return _mm512_set1_epi64(v); }
    template <uint8_t mask>
    static reg_t shuffle(reg_t zmm) {
        __m512d temp = _mm512_castsi512_pd(zmm);
        return _mm512_castpd_si512(
            _mm512_shuffle_pd(temp, temp, (_MM_PERM_ENUM)mask));
    }
    static void storeu(void *mem, reg_t x) { _mm512_storeu_si512(mem, x); }
    static reg_t reverse(reg_t zmm) {
        const regi_t rev_index = seti(NETWORK_64BIT_2);
        return permutexvar(rev_index, zmm);
    }
    static reg_t sort_vec(reg_t x) {
        return sort_zmm_64bit<zmm_vector<type_t>>(x);
    }
    static reg_t cast_from(__m512i v) { return v; }
    static __m512i cast_to(reg_t v) { return v; }
    static int double_compressstore(type_t *left_addr, type_t *right_addr,
                                    opmask_t k, reg_t reg) {
        return avx512_double_compressstore<zmm_vector<type_t>>(
            left_addr, right_addr, k, reg);
    }
};
template <>
struct zmm_vector<double> {
    using type_t = double;
    using reg_t = __m512d;
    using regi_t = __m512i;
    using halfreg_t = __m512d;
    using opmask_t = __mmask8;
    static const uint8_t numlanes = 8;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 256;
#endif
    static constexpr int partition_unroll_factor = 8;

    using swizzle_ops = avx512_64bit_swizzle_ops;

    static type_t type_max() { return X86_SIMD_SORT_INFINITY; }
    static type_t type_min() { return -X86_SIMD_SORT_INFINITY; }
    static reg_t zmm_max() { return _mm512_set1_pd(type_max()); }
    static regi_t seti(int v1, int v2, int v3, int v4, int v5, int v6, int v7,
                       int v8) {
        return _mm512_set_epi64(v1, v2, v3, v4, v5, v6, v7, v8);
    }
    static reg_t set(type_t v1, type_t v2, type_t v3, type_t v4, type_t v5,
                     type_t v6, type_t v7, type_t v8) {
        return _mm512_set_pd(v1, v2, v3, v4, v5, v6, v7, v8);
    }
    static reg_t maskz_loadu(opmask_t mask, void const *mem) {
        return _mm512_maskz_loadu_pd(mask, mem);
    }
    static opmask_t knot_opmask(opmask_t x) { return _knot_mask8(x); }
    static opmask_t ge(reg_t x, reg_t y) {
        return _mm512_cmp_pd_mask(x, y, _CMP_GE_OQ);
    }
    static opmask_t gt(reg_t x, reg_t y) {
        return _mm512_cmp_pd_mask(x, y, _CMP_GT_OQ);
    }
    static opmask_t eq(reg_t x, reg_t y) {
        return _mm512_cmp_pd_mask(x, y, _CMP_EQ_OQ);
    }
    static opmask_t get_partial_loadmask(uint64_t num_to_read) {
        return ((0x1ull << num_to_read) - 0x1ull);
    }
    static int32_t convert_mask_to_int(opmask_t mask) { return mask; }
    template <int type>
    static opmask_t fpclass(reg_t x) {
        return _mm512_fpclass_pd_mask(x, type);
    }
    template <int scale>
    static reg_t mask_i64gather(reg_t src, opmask_t mask, __m512i index,
                                void const *base) {
        return _mm512_mask_i64gather_pd(src, mask, index, base, scale);
    }
    template <int scale>
    static reg_t mask_i64gather(reg_t src, opmask_t mask, __m256i index,
                                void const *base) {
        return _mm512_mask_i32gather_pd(src, mask, index, base, scale);
    }
    static reg_t i64gather(type_t *arr, arrsize_t *ind) {
        return set(arr[ind[7]], arr[ind[6]], arr[ind[5]], arr[ind[4]],
                   arr[ind[3]], arr[ind[2]], arr[ind[1]], arr[ind[0]]);
    }
    static reg_t loadu(void const *mem) { return _mm512_loadu_pd(mem); }
    static reg_t max(reg_t x, reg_t y) { return _mm512_max_pd(x, y); }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_compressstoreu_pd(mem, mask, x);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem) {
        return _mm512_mask_loadu_pd(x, mask, mem);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y) {
        return _mm512_mask_mov_pd(x, mask, y);
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_storeu_pd(mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y) { return _mm512_min_pd(x, y); }
    static reg_t permutexvar(__m512i idx, reg_t zmm) {
        return _mm512_permutexvar_pd(idx, zmm);
    }
    static type_t reducemax(reg_t v) { return _mm512_reduce_max_pd(v); }
    static type_t reducemin(reg_t v) { return _mm512_reduce_min_pd(v); }
    static reg_t set1(type_t v) { return _mm512_set1_pd(v); }
    template <uint8_t mask>
    static reg_t shuffle(reg_t zmm) {
        return _mm512_shuffle_pd(zmm, zmm, (_MM_PERM_ENUM)mask);
    }
    static void storeu(void *mem, reg_t x) { _mm512_storeu_pd(mem, x); }
    static reg_t reverse(reg_t zmm) {
        const regi_t rev_index = seti(NETWORK_64BIT_2);
        return permutexvar(rev_index, zmm);
    }
    static reg_t sort_vec(reg_t x) {
        return sort_zmm_64bit<zmm_vector<type_t>>(x);
    }
    static reg_t cast_from(__m512i v) { return _mm512_castsi512_pd(v); }
    static __m512i cast_to(reg_t v) { return _mm512_castpd_si512(v); }
    static int double_compressstore(type_t *left_addr, type_t *right_addr,
                                    opmask_t k, reg_t reg) {
        return avx512_double_compressstore<zmm_vector<type_t>>(
            left_addr, right_addr, k, reg);
    }
};

/*
 * Assumes zmm is random and performs a full sorting network defined in
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg
 */
template <typename vtype, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_INLINE reg_t sort_zmm_64bit(reg_t zmm) {
    const typename vtype::regi_t rev_index = vtype::seti(NETWORK_64BIT_2);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(1, 1, 1, 1)>(zmm), 0xAA);
    zmm = cmp_merge<vtype>(
        zmm, vtype::permutexvar(vtype::seti(NETWORK_64BIT_1), zmm), 0xCC);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(1, 1, 1, 1)>(zmm), 0xAA);
    zmm = cmp_merge<vtype>(zmm, vtype::permutexvar(rev_index, zmm), 0xF0);
    zmm = cmp_merge<vtype>(
        zmm, vtype::permutexvar(vtype::seti(NETWORK_64BIT_3), zmm), 0xCC);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(1, 1, 1, 1)>(zmm), 0xAA);
    return zmm;
}

struct avx512_64bit_swizzle_ops {
    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t swap_n(
        typename vtype::reg_t reg) {
        __m512i v = vtype::cast_to(reg);

        if constexpr (scale == 2) {
            v = _mm512_shuffle_epi32(v, (_MM_PERM_ENUM)0b01001110);
        } else if constexpr (scale == 4) {
            v = _mm512_shuffle_i64x2(v, v, 0b10110001);
        } else if constexpr (scale == 8) {
            v = _mm512_shuffle_i64x2(v, v, 0b01001110);
        } else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v);
    }

    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t reverse_n(
        typename vtype::reg_t reg) {
        __m512i v = vtype::cast_to(reg);

        if constexpr (scale == 2) {
            return swap_n<vtype, 2>(reg);
        } else if constexpr (scale == 4) {
            constexpr uint64_t mask = 0b00011011;
            v = _mm512_permutex_epi64(v, mask);
        } else if constexpr (scale == 8) {
            return vtype::reverse(reg);
        } else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v);
    }

    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t merge_n(
        typename vtype::reg_t reg, typename vtype::reg_t other) {
        __m512i v1 = vtype::cast_to(reg);
        __m512i v2 = vtype::cast_to(other);

        if constexpr (scale == 2) {
            v1 = _mm512_mask_blend_epi64(0b01010101, v1, v2);
        } else if constexpr (scale == 4) {
            v1 = _mm512_mask_blend_epi64(0b00110011, v1, v2);
        } else if constexpr (scale == 8) {
            v1 = _mm512_mask_blend_epi64(0b00001111, v1, v2);
        } else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v1);
    }
};

#endif
