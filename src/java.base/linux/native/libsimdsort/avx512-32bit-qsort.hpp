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

#ifndef AVX512_QSORT_32BIT
#define AVX512_QSORT_32BIT

#include "xss-common-qsort.h"

/*
 * Constants used in sorting 16 elements in a ZMM registers. Based on Bitonic
 * sorting network (see
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg)
 */
#define NETWORK_32BIT_1 14, 15, 12, 13, 10, 11, 8, 9, 6, 7, 4, 5, 2, 3, 0, 1
#define NETWORK_32BIT_2 12, 13, 14, 15, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3
#define NETWORK_32BIT_3 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7
#define NETWORK_32BIT_4 13, 12, 15, 14, 9, 8, 11, 10, 5, 4, 7, 6, 1, 0, 3, 2
#define NETWORK_32BIT_5 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
#define NETWORK_32BIT_6 11, 10, 9, 8, 15, 14, 13, 12, 3, 2, 1, 0, 7, 6, 5, 4
#define NETWORK_32BIT_7 7, 6, 5, 4, 3, 2, 1, 0, 15, 14, 13, 12, 11, 10, 9, 8

template <typename vtype, typename reg_t>
X86_SIMD_SORT_INLINE reg_t sort_zmm_32bit(reg_t zmm);

struct avx512_32bit_swizzle_ops;

template <>
struct zmm_vector<int32_t> {
    using type_t = int32_t;
    using reg_t = __m512i;
    using halfreg_t = __m256i;
    using opmask_t = __mmask16;
    static const uint8_t numlanes = 16;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 512;
#endif
    static constexpr int partition_unroll_factor = 8;

    using swizzle_ops = avx512_32bit_swizzle_ops;

    static type_t type_max() { return X86_SIMD_SORT_MAX_INT32; }
    static type_t type_min() { return X86_SIMD_SORT_MIN_INT32; }
    static reg_t zmm_max() { return _mm512_set1_epi32(type_max()); }

    static opmask_t knot_opmask(opmask_t x) { return _mm512_knot(x); }

    static opmask_t ge(reg_t x, reg_t y) {
        return _mm512_cmp_epi32_mask(x, y, _MM_CMPINT_NLT);
    }

    static opmask_t gt(reg_t x, reg_t y) {
        return _mm512_cmp_epi32_mask(x, y, _MM_CMPINT_GT);
    }

    static opmask_t get_partial_loadmask(uint64_t num_to_read) {
        return ((0x1ull << num_to_read) - 0x1ull);
    }
    template <int scale>
    static halfreg_t i64gather(__m512i index, void const *base) {
        return _mm512_i64gather_epi32(index, base, scale);
    }
    static reg_t merge(halfreg_t y1, halfreg_t y2) {
        reg_t z1 = _mm512_castsi256_si512(y1);
        return _mm512_inserti32x8(z1, y2, 1);
    }
    static reg_t loadu(void const *mem) { return _mm512_loadu_si512(mem); }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_compressstoreu_epi32(mem, mask, x);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem) {
        return _mm512_mask_loadu_epi32(x, mask, mem);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y) {
        return _mm512_mask_mov_epi32(x, mask, y);
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_storeu_epi32(mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y) { return _mm512_min_epi32(x, y); }
    static reg_t max(reg_t x, reg_t y) { return _mm512_max_epi32(x, y); }
    static reg_t permutexvar(__m512i idx, reg_t zmm) {
        return _mm512_permutexvar_epi32(idx, zmm);
    }
    static type_t reducemax(reg_t v) { return _mm512_reduce_max_epi32(v); }
    static type_t reducemin(reg_t v) { return _mm512_reduce_min_epi32(v); }
    static reg_t set1(type_t v) { return _mm512_set1_epi32(v); }
    template <uint8_t mask>
    static reg_t shuffle(reg_t zmm) {
        return _mm512_shuffle_epi32(zmm, (_MM_PERM_ENUM)mask);
    }
    static void storeu(void *mem, reg_t x) {
        return _mm512_storeu_si512(mem, x);
    }

    static halfreg_t max(halfreg_t x, halfreg_t y) {
        return _mm256_max_epi32(x, y);
    }
    static halfreg_t min(halfreg_t x, halfreg_t y) {
        return _mm256_min_epi32(x, y);
    }
    static reg_t reverse(reg_t zmm) {
        const auto rev_index = _mm512_set_epi32(NETWORK_32BIT_5);
        return permutexvar(rev_index, zmm);
    }
    static reg_t sort_vec(reg_t x) {
        return sort_zmm_32bit<zmm_vector<type_t>>(x);
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
struct zmm_vector<float> {
    using type_t = float;
    using reg_t = __m512;
    using halfreg_t = __m256;
    using opmask_t = __mmask16;
    static const uint8_t numlanes = 16;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 512;
#endif
    static constexpr int partition_unroll_factor = 8;

    using swizzle_ops = avx512_32bit_swizzle_ops;

    static type_t type_max() { return X86_SIMD_SORT_INFINITYF; }
    static type_t type_min() { return -X86_SIMD_SORT_INFINITYF; }
    static reg_t zmm_max() { return _mm512_set1_ps(type_max()); }

    static opmask_t knot_opmask(opmask_t x) { return _mm512_knot(x); }
    static opmask_t ge(reg_t x, reg_t y) {
        return _mm512_cmp_ps_mask(x, y, _CMP_GE_OQ);
    }
    static opmask_t gt(reg_t x, reg_t y) {
        return _mm512_cmp_ps_mask(x, y, _CMP_GT_OQ);
    }
    static opmask_t get_partial_loadmask(uint64_t num_to_read) {
        return ((0x1ull << num_to_read) - 0x1ull);
    }
    static int32_t convert_mask_to_int(opmask_t mask) { return mask; }
    template <int type>
    static opmask_t fpclass(reg_t x) {
        return _mm512_fpclass_ps_mask(x, type);
    }
    template <int scale>
    static halfreg_t i64gather(__m512i index, void const *base) {
        return _mm512_i64gather_ps(index, base, scale);
    }
    static reg_t merge(halfreg_t y1, halfreg_t y2) {
        reg_t z1 = _mm512_castsi512_ps(
            _mm512_castsi256_si512(_mm256_castps_si256(y1)));
        return _mm512_insertf32x8(z1, y2, 1);
    }
    static reg_t loadu(void const *mem) { return _mm512_loadu_ps(mem); }
    static reg_t max(reg_t x, reg_t y) { return _mm512_max_ps(x, y); }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_compressstoreu_ps(mem, mask, x);
    }
    static reg_t maskz_loadu(opmask_t mask, void const *mem) {
        return _mm512_maskz_loadu_ps(mask, mem);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem) {
        return _mm512_mask_loadu_ps(x, mask, mem);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y) {
        return _mm512_mask_mov_ps(x, mask, y);
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x) {
        return _mm512_mask_storeu_ps(mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y) { return _mm512_min_ps(x, y); }
    static reg_t permutexvar(__m512i idx, reg_t zmm) {
        return _mm512_permutexvar_ps(idx, zmm);
    }
    static type_t reducemax(reg_t v) { return _mm512_reduce_max_ps(v); }
    static type_t reducemin(reg_t v) { return _mm512_reduce_min_ps(v); }
    static reg_t set1(type_t v) { return _mm512_set1_ps(v); }
    template <uint8_t mask>
    static reg_t shuffle(reg_t zmm) {
        return _mm512_shuffle_ps(zmm, zmm, (_MM_PERM_ENUM)mask);
    }
    static void storeu(void *mem, reg_t x) { return _mm512_storeu_ps(mem, x); }

    static halfreg_t max(halfreg_t x, halfreg_t y) {
        return _mm256_max_ps(x, y);
    }
    static halfreg_t min(halfreg_t x, halfreg_t y) {
        return _mm256_min_ps(x, y);
    }
    static reg_t reverse(reg_t zmm) {
        const auto rev_index = _mm512_set_epi32(NETWORK_32BIT_5);
        return permutexvar(rev_index, zmm);
    }
    static reg_t sort_vec(reg_t x) {
        return sort_zmm_32bit<zmm_vector<type_t>>(x);
    }
    static reg_t cast_from(__m512i v) { return _mm512_castsi512_ps(v); }
    static __m512i cast_to(reg_t v) { return _mm512_castps_si512(v); }
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
X86_SIMD_SORT_INLINE reg_t sort_zmm_32bit(reg_t zmm) {
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(2, 3, 0, 1)>(zmm), 0xAAAA);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(0, 1, 2, 3)>(zmm), 0xCCCC);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(2, 3, 0, 1)>(zmm), 0xAAAA);
    zmm = cmp_merge<vtype>(
        zmm, vtype::permutexvar(_mm512_set_epi32(NETWORK_32BIT_3), zmm),
        0xF0F0);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(1, 0, 3, 2)>(zmm), 0xCCCC);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(2, 3, 0, 1)>(zmm), 0xAAAA);
    zmm = cmp_merge<vtype>(
        zmm, vtype::permutexvar(_mm512_set_epi32(NETWORK_32BIT_5), zmm),
        0xFF00);
    zmm = cmp_merge<vtype>(
        zmm, vtype::permutexvar(_mm512_set_epi32(NETWORK_32BIT_6), zmm),
        0xF0F0);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(1, 0, 3, 2)>(zmm), 0xCCCC);
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(2, 3, 0, 1)>(zmm), 0xAAAA);
    return zmm;
}

struct avx512_32bit_swizzle_ops {
    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t swap_n(
        typename vtype::reg_t reg) {
        __m512i v = vtype::cast_to(reg);

        if constexpr (scale == 2) {
            v = _mm512_shuffle_epi32(v, (_MM_PERM_ENUM)0b10110001);
        } else if constexpr (scale == 4) {
            v = _mm512_shuffle_epi32(v, (_MM_PERM_ENUM)0b01001110);
        } else if constexpr (scale == 8) {
            v = _mm512_shuffle_i64x2(v, v, 0b10110001);
        } else if constexpr (scale == 16) {
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
            __m512i mask = _mm512_set_epi32(12, 13, 14, 15, 8, 9, 10, 11, 4, 5,
                                            6, 7, 0, 1, 2, 3);
            v = _mm512_permutexvar_epi32(mask, v);
        } else if constexpr (scale == 8) {
            __m512i mask = _mm512_set_epi32(8, 9, 10, 11, 12, 13, 14, 15, 0, 1,
                                            2, 3, 4, 5, 6, 7);
            v = _mm512_permutexvar_epi32(mask, v);
        } else if constexpr (scale == 16) {
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
            v1 = _mm512_mask_blend_epi32(0b0101010101010101, v1, v2);
        } else if constexpr (scale == 4) {
            v1 = _mm512_mask_blend_epi32(0b0011001100110011, v1, v2);
        } else if constexpr (scale == 8) {
            v1 = _mm512_mask_blend_epi32(0b0000111100001111, v1, v2);
        } else if constexpr (scale == 16) {
            v1 = _mm512_mask_blend_epi32(0b0000000011111111, v1, v2);
        } else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v1);
    }
};

#endif  // AVX512_QSORT_32BIT
