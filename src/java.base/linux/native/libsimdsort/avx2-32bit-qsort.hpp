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

#ifndef AVX2_QSORT_32BIT
#define AVX2_QSORT_32BIT

#include "avx2-emu-funcs.hpp"
#include "xss-common-qsort.h"

/*
 * Constants used in sorting 8 elements in a ymm registers. Based on Bitonic
 * sorting network (see
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg)
 */

// ymm                  7, 6, 5, 4, 3, 2, 1, 0
#define NETWORK_32BIT_AVX2_1 4, 5, 6, 7, 0, 1, 2, 3
#define NETWORK_32BIT_AVX2_2 0, 1, 2, 3, 4, 5, 6, 7
#define NETWORK_32BIT_AVX2_3 5, 4, 7, 6, 1, 0, 3, 2
#define NETWORK_32BIT_AVX2_4 3, 2, 1, 0, 7, 6, 5, 4

/*
 * Assumes ymm is random and performs a full sorting network defined in
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg
 */
template <typename vtype, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_INLINE reg_t sort_ymm_32bit(reg_t ymm) {
    const typename vtype::opmask_t oxAA = _mm256_set_epi32(
        0xFFFFFFFF, 0, 0xFFFFFFFF, 0, 0xFFFFFFFF, 0, 0xFFFFFFFF, 0);
    const typename vtype::opmask_t oxCC = _mm256_set_epi32(
        0xFFFFFFFF, 0xFFFFFFFF, 0, 0, 0xFFFFFFFF, 0xFFFFFFFF, 0, 0);
    const typename vtype::opmask_t oxF0 = _mm256_set_epi32(
        0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0, 0, 0, 0);

    const typename vtype::ymmi_t rev_index = vtype::seti(NETWORK_32BIT_AVX2_2);
    ymm = cmp_merge<vtype>(
        ymm, vtype::template shuffle<SHUFFLE_MASK(2, 3, 0, 1)>(ymm), oxAA);
    ymm = cmp_merge<vtype>(
        ymm, vtype::permutexvar(vtype::seti(NETWORK_32BIT_AVX2_1), ymm), oxCC);
    ymm = cmp_merge<vtype>(
        ymm, vtype::template shuffle<SHUFFLE_MASK(2, 3, 0, 1)>(ymm), oxAA);
    ymm = cmp_merge<vtype>(ymm, vtype::permutexvar(rev_index, ymm), oxF0);
    ymm = cmp_merge<vtype>(
        ymm, vtype::permutexvar(vtype::seti(NETWORK_32BIT_AVX2_3), ymm), oxCC);
    ymm = cmp_merge<vtype>(
        ymm, vtype::template shuffle<SHUFFLE_MASK(2, 3, 0, 1)>(ymm), oxAA);
    return ymm;
}

struct avx2_32bit_swizzle_ops;

template <>
struct avx2_vector<int32_t> {
    using type_t = int32_t;
    using reg_t = __m256i;
    using ymmi_t = __m256i;
    using opmask_t = __m256i;
    static const uint8_t numlanes = 8;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 256;
#endif
    static constexpr int partition_unroll_factor = 4;

    using swizzle_ops = avx2_32bit_swizzle_ops;

    static type_t type_max() { return X86_SIMD_SORT_MAX_INT32; }
    static type_t type_min() { return X86_SIMD_SORT_MIN_INT32; }
    static reg_t zmm_max() {
        return _mm256_set1_epi32(type_max());
    }  // TODO: this should broadcast bits as is?
    static opmask_t get_partial_loadmask(uint64_t num_to_read) {
        auto mask = ((0x1ull << num_to_read) - 0x1ull);
        return convert_int_to_avx2_mask(mask);
    }
    static ymmi_t seti(int v1, int v2, int v3, int v4, int v5, int v6, int v7,
                       int v8) {
        return _mm256_set_epi32(v1, v2, v3, v4, v5, v6, v7, v8);
    }
    static opmask_t kxor_opmask(opmask_t x, opmask_t y) {
        return _mm256_xor_si256(x, y);
    }
    static opmask_t ge(reg_t x, reg_t y) {
        opmask_t equal = eq(x, y);
        opmask_t greater = _mm256_cmpgt_epi32(x, y);
        return _mm256_castps_si256(_mm256_or_ps(_mm256_castsi256_ps(equal),
                                                _mm256_castsi256_ps(greater)));
    }
    static opmask_t gt(reg_t x, reg_t y) { return _mm256_cmpgt_epi32(x, y); }
    static opmask_t eq(reg_t x, reg_t y) { return _mm256_cmpeq_epi32(x, y); }
    template <int scale>
    static reg_t mask_i64gather(reg_t src, opmask_t mask, __m256i index,
                                void const *base) {
        return _mm256_mask_i32gather_epi32(src, base, index, mask, scale);
    }
    template <int scale>
    static reg_t i64gather(__m256i index, void const *base) {
        return _mm256_i32gather_epi32((int const *)base, index, scale);
    }
    static reg_t loadu(void const *mem) {
        return _mm256_loadu_si256((reg_t const *)mem);
    }
    static reg_t max(reg_t x, reg_t y) { return _mm256_max_epi32(x, y); }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x) {
        return avx2_emu_mask_compressstoreu32<type_t>(mem, mask, x);
    }
    static reg_t maskz_loadu(opmask_t mask, void const *mem) {
        return _mm256_maskload_epi32((const int *)mem, mask);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem) {
        reg_t dst = _mm256_maskload_epi32((type_t *)mem, mask);
        return mask_mov(x, mask, dst);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y) {
        return _mm256_castps_si256(_mm256_blendv_ps(_mm256_castsi256_ps(x),
                                                    _mm256_castsi256_ps(y),
                                                    _mm256_castsi256_ps(mask)));
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x) {
        return _mm256_maskstore_epi32((type_t *)mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y) { return _mm256_min_epi32(x, y); }
    static reg_t permutexvar(__m256i idx, reg_t ymm) {
        return _mm256_permutevar8x32_epi32(ymm, idx);
        // return avx2_emu_permutexvar_epi32(idx, ymm);
    }
    static reg_t permutevar(reg_t ymm, __m256i idx) {
        return _mm256_permutevar8x32_epi32(ymm, idx);
    }
    static reg_t reverse(reg_t ymm) {
        const __m256i rev_index = _mm256_set_epi32(NETWORK_32BIT_AVX2_2);
        return permutexvar(rev_index, ymm);
    }
    static type_t reducemax(reg_t v) {
        return avx2_emu_reduce_max32<type_t>(v);
    }
    static type_t reducemin(reg_t v) {
        return avx2_emu_reduce_min32<type_t>(v);
    }
    static reg_t set1(type_t v) { return _mm256_set1_epi32(v); }
    template <uint8_t mask>
    static reg_t shuffle(reg_t ymm) {
        return _mm256_shuffle_epi32(ymm, mask);
    }
    static void storeu(void *mem, reg_t x) {
        _mm256_storeu_si256((__m256i *)mem, x);
    }
    static reg_t sort_vec(reg_t x) {
        return sort_ymm_32bit<avx2_vector<type_t>>(x);
    }
    static reg_t cast_from(__m256i v) { return v; }
    static __m256i cast_to(reg_t v) { return v; }
    static int double_compressstore(type_t *left_addr, type_t *right_addr,
                                    opmask_t k, reg_t reg) {
        return avx2_double_compressstore32<type_t>(left_addr, right_addr, k,
                                                   reg);
    }
};

template <>
struct avx2_vector<float> {
    using type_t = float;
    using reg_t = __m256;
    using ymmi_t = __m256i;
    using opmask_t = __m256i;
    static const uint8_t numlanes = 8;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 256;
#endif
    static constexpr int partition_unroll_factor = 4;

    using swizzle_ops = avx2_32bit_swizzle_ops;

    static type_t type_max() { return X86_SIMD_SORT_INFINITYF; }
    static type_t type_min() { return -X86_SIMD_SORT_INFINITYF; }
    static reg_t zmm_max() { return _mm256_set1_ps(type_max()); }

    static ymmi_t seti(int v1, int v2, int v3, int v4, int v5, int v6, int v7,
                       int v8) {
        return _mm256_set_epi32(v1, v2, v3, v4, v5, v6, v7, v8);
    }

    static reg_t maskz_loadu(opmask_t mask, void const *mem) {
        return _mm256_maskload_ps((const float *)mem, mask);
    }
    static opmask_t ge(reg_t x, reg_t y) {
        return _mm256_castps_si256(_mm256_cmp_ps(x, y, _CMP_GE_OQ));
    }
    static opmask_t gt(reg_t x, reg_t y) {
        return _mm256_castps_si256(_mm256_cmp_ps(x, y, _CMP_GT_OQ));
    }
    static opmask_t eq(reg_t x, reg_t y) {
        return _mm256_castps_si256(_mm256_cmp_ps(x, y, _CMP_EQ_OQ));
    }
    static opmask_t get_partial_loadmask(uint64_t num_to_read) {
        auto mask = ((0x1ull << num_to_read) - 0x1ull);
        return convert_int_to_avx2_mask(mask);
    }
    static int32_t convert_mask_to_int(opmask_t mask) {
        return convert_avx2_mask_to_int(mask);
    }
    template <int type>
    static opmask_t fpclass(reg_t x) {
        if constexpr (type == (0x01 | 0x80)) {
            return _mm256_castps_si256(_mm256_cmp_ps(x, x, _CMP_UNORD_Q));
        } else {
            static_assert(type == (0x01 | 0x80), "should not reach here");
        }
    }
    template <int scale>
    static reg_t mask_i64gather(reg_t src, opmask_t mask, __m256i index,
                                void const *base) {
        return _mm256_mask_i32gather_ps(src, base, index,
                                        _mm256_castsi256_ps(mask), scale);
        ;
    }
    template <int scale>
    static reg_t i64gather(__m256i index, void const *base) {
        return _mm256_i32gather_ps((float *)base, index, scale);
    }
    static reg_t loadu(void const *mem) {
        return _mm256_loadu_ps((float const *)mem);
    }
    static reg_t max(reg_t x, reg_t y) { return _mm256_max_ps(x, y); }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x) {
        return avx2_emu_mask_compressstoreu32<type_t>(mem, mask, x);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem) {
        reg_t dst = _mm256_maskload_ps((type_t *)mem, mask);
        return mask_mov(x, mask, dst);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y) {
        return _mm256_blendv_ps(x, y, _mm256_castsi256_ps(mask));
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x) {
        return _mm256_maskstore_ps((type_t *)mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y) { return _mm256_min_ps(x, y); }
    static reg_t permutexvar(__m256i idx, reg_t ymm) {
        return _mm256_permutevar8x32_ps(ymm, idx);
    }
    static reg_t permutevar(reg_t ymm, __m256i idx) {
        return _mm256_permutevar8x32_ps(ymm, idx);
    }
    static reg_t reverse(reg_t ymm) {
        const __m256i rev_index = _mm256_set_epi32(NETWORK_32BIT_AVX2_2);
        return permutexvar(rev_index, ymm);
    }
    static type_t reducemax(reg_t v) {
        return avx2_emu_reduce_max32<type_t>(v);
    }
    static type_t reducemin(reg_t v) {
        return avx2_emu_reduce_min32<type_t>(v);
    }
    static reg_t set1(type_t v) { return _mm256_set1_ps(v); }
    template <uint8_t mask>
    static reg_t shuffle(reg_t ymm) {
        return _mm256_castsi256_ps(
            _mm256_shuffle_epi32(_mm256_castps_si256(ymm), mask));
    }
    static void storeu(void *mem, reg_t x) {
        _mm256_storeu_ps((float *)mem, x);
    }
    static reg_t sort_vec(reg_t x) {
        return sort_ymm_32bit<avx2_vector<type_t>>(x);
    }
    static reg_t cast_from(__m256i v) { return _mm256_castsi256_ps(v); }
    static __m256i cast_to(reg_t v) { return _mm256_castps_si256(v); }
    static int double_compressstore(type_t *left_addr, type_t *right_addr,
                                    opmask_t k, reg_t reg) {
        return avx2_double_compressstore32<type_t>(left_addr, right_addr, k,
                                                   reg);
    }
};

struct avx2_32bit_swizzle_ops {
    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t swap_n(
        typename vtype::reg_t reg) {
        __m256i v = vtype::cast_to(reg);

        if constexpr (scale == 2) {
            __m256 vf = _mm256_castsi256_ps(v);
            vf = _mm256_permute_ps(vf, 0b10110001);
            v = _mm256_castps_si256(vf);
        } else if constexpr (scale == 4) {
            __m256 vf = _mm256_castsi256_ps(v);
            vf = _mm256_permute_ps(vf, 0b01001110);
            v = _mm256_castps_si256(vf);
        } else if constexpr (scale == 8) {
            v = _mm256_permute2x128_si256(v, v, 0b00000001);
        } else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v);
    }

    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t reverse_n(
        typename vtype::reg_t reg) {
        __m256i v = vtype::cast_to(reg);

        if constexpr (scale == 2) {
            return swap_n<vtype, 2>(reg);
        } else if constexpr (scale == 4) {
            constexpr uint64_t mask = 0b00011011;
            __m256 vf = _mm256_castsi256_ps(v);
            vf = _mm256_permute_ps(vf, mask);
            v = _mm256_castps_si256(vf);
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
        __m256i v1 = vtype::cast_to(reg);
        __m256i v2 = vtype::cast_to(other);

        if constexpr (scale == 2) {
            v1 = _mm256_blend_epi32(v1, v2, 0b01010101);
        } else if constexpr (scale == 4) {
            v1 = _mm256_blend_epi32(v1, v2, 0b00110011);
        } else if constexpr (scale == 8) {
            v1 = _mm256_blend_epi32(v1, v2, 0b00001111);
        } else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v1);
    }
};

#endif  // AVX2_QSORT_32BIT
