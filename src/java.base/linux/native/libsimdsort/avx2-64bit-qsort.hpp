/*******************************************************************
 * Copyright (C) 2022 Intel Corporation
 * SPDX-License-Identifier: BSD-3-Clause
 * Authors: Raghuveer Devulapalli <raghuveer.devulapalli@intel.com>
 *          Matthew Sterrett <matthew.sterrett@intel.com>
 * ****************************************************************/

#ifndef AVX2_QSORT_64BIT
#define AVX2_QSORT_64BIT

#include "xss-common-qsort.h"
#include "avx2-emu-funcs.hpp"

/*
 * Constants used in sorting 8 elements in a ymm registers. Based on Bitonic
 * sorting network (see
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg)
 */
// ymm                  3, 2, 1, 0
#define NETWORK_64BIT_R 0, 1, 2, 3
#define NETWORK_64BIT_1 1, 0, 3, 2

/*
 * Assumes ymm is random and performs a full sorting network defined in
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg
 */
template <typename vtype, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_INLINE reg_t sort_ymm_64bit(reg_t ymm)
{
    const typename vtype::opmask_t oxAA
            = _mm256_set_epi64x(0xFFFFFFFFFFFFFFFF, 0, 0xFFFFFFFFFFFFFFFF, 0);
    const typename vtype::opmask_t oxCC
            = _mm256_set_epi64x(0xFFFFFFFFFFFFFFFF, 0xFFFFFFFFFFFFFFFF, 0, 0);
    ymm = cmp_merge<vtype>(
            ymm,
            vtype::template permutexvar<SHUFFLE_MASK(2, 3, 0, 1)>(ymm),
            oxAA);
    ymm = cmp_merge<vtype>(
            ymm,
            vtype::template permutexvar<SHUFFLE_MASK(0, 1, 2, 3)>(ymm),
            oxCC);
    ymm = cmp_merge<vtype>(
            ymm,
            vtype::template permutexvar<SHUFFLE_MASK(2, 3, 0, 1)>(ymm),
            oxAA);
    return ymm;
}

struct avx2_64bit_swizzle_ops;

template <>
struct avx2_vector<int64_t> {
    using type_t = int64_t;
    using reg_t = __m256i;
    using ymmi_t = __m256i;
    using opmask_t = __m256i;
    static const uint8_t numlanes = 4;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 64;
#endif
    static constexpr int partition_unroll_factor = 8;

    using swizzle_ops = avx2_64bit_swizzle_ops;

    static type_t type_max()
    {
        return X86_SIMD_SORT_MAX_INT64;
    }
    static type_t type_min()
    {
        return X86_SIMD_SORT_MIN_INT64;
    }
    static reg_t zmm_max()
    {
        return _mm256_set1_epi64x(type_max());
    } // TODO: this should broadcast bits as is?
    static opmask_t get_partial_loadmask(uint64_t num_to_read)
    {
        auto mask = ((0x1ull << num_to_read) - 0x1ull);
        return convert_int_to_avx2_mask_64bit(mask);
    }
    static ymmi_t seti(int v1, int v2, int v3, int v4)
    {
        return _mm256_set_epi64x(v1, v2, v3, v4);
    }
    static opmask_t kxor_opmask(opmask_t x, opmask_t y)
    {
        return _mm256_xor_si256(x, y);
    }
    static opmask_t gt(reg_t x, reg_t y)
    {
        return _mm256_cmpgt_epi64(x, y);
    }
    static opmask_t ge(reg_t x, reg_t y)
    {
        opmask_t equal = eq(x, y);
        opmask_t greater = _mm256_cmpgt_epi64(x, y);
        return _mm256_or_si256(equal, greater);
    }
    static opmask_t eq(reg_t x, reg_t y)
    {
        return _mm256_cmpeq_epi64(x, y);
    }
    template <int scale>
    static reg_t
    mask_i64gather(reg_t src, opmask_t mask, __m256i index, void const *base)
    {
        return _mm256_mask_i64gather_epi64(src, base, index, mask, scale);
    }
    template <int scale>
    static reg_t i64gather(__m256i index, void const *base)
    {
        return _mm256_i64gather_epi64((int64_t const *)base, index, scale);
    }
    static reg_t loadu(void const *mem)
    {
        return _mm256_loadu_si256((reg_t const *)mem);
    }
    static reg_t max(reg_t x, reg_t y)
    {
        return avx2_emu_max<type_t>(x, y);
    }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x)
    {
        return avx2_emu_mask_compressstoreu64<type_t>(mem, mask, x);
    }
    static int32_t double_compressstore(void *left_addr,
                                        void *right_addr,
                                        opmask_t k,
                                        reg_t reg)
    {
        return avx2_double_compressstore64<type_t>(
                left_addr, right_addr, k, reg);
    }
    static reg_t maskz_loadu(opmask_t mask, void const *mem)
    {
        return _mm256_maskload_epi64((const long long int *)mem, mask);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem)
    {
        reg_t dst = _mm256_maskload_epi64((long long int *)mem, mask);
        return mask_mov(x, mask, dst);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y)
    {
        return _mm256_castpd_si256(_mm256_blendv_pd(_mm256_castsi256_pd(x),
                                                    _mm256_castsi256_pd(y),
                                                    _mm256_castsi256_pd(mask)));
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x)
    {
        return _mm256_maskstore_epi64((long long int *)mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y)
    {
        return avx2_emu_min<type_t>(x, y);
    }
    template <int32_t idx>
    static reg_t permutexvar(reg_t ymm)
    {
        return _mm256_permute4x64_epi64(ymm, idx);
    }
    template <int32_t idx>
    static reg_t permutevar(reg_t ymm)
    {
        return _mm256_permute4x64_epi64(ymm, idx);
    }
    static reg_t reverse(reg_t ymm)
    {
        const int32_t rev_index = SHUFFLE_MASK(0, 1, 2, 3);
        return permutexvar<rev_index>(ymm);
    }
    static type_t reducemax(reg_t v)
    {
        return avx2_emu_reduce_max64<type_t>(v);
    }
    static type_t reducemin(reg_t v)
    {
        return avx2_emu_reduce_min64<type_t>(v);
    }
    static reg_t set1(type_t v)
    {
        return _mm256_set1_epi64x(v);
    }
    template <uint8_t mask>
    static reg_t shuffle(reg_t ymm)
    {
        return _mm256_castpd_si256(
                _mm256_permute_pd(_mm256_castsi256_pd(ymm), mask));
    }
    static void storeu(void *mem, reg_t x)
    {
        _mm256_storeu_si256((__m256i *)mem, x);
    }
    static reg_t sort_vec(reg_t x)
    {
        return sort_ymm_64bit<avx2_vector<type_t>>(x);
    }
    static reg_t cast_from(__m256i v)
    {
        return v;
    }
    static __m256i cast_to(reg_t v)
    {
        return v;
    }
};

template <>
struct avx2_vector<double> {
    using type_t = double;
    using reg_t = __m256d;
    using ymmi_t = __m256i;
    using opmask_t = __m256i;
    static const uint8_t numlanes = 4;
#ifdef XSS_MINIMAL_NETWORK_SORT
    static constexpr int network_sort_threshold = numlanes;
#else
    static constexpr int network_sort_threshold = 64;
#endif
    static constexpr int partition_unroll_factor = 8;

    using swizzle_ops = avx2_64bit_swizzle_ops;

    static type_t type_max()
    {
        return X86_SIMD_SORT_INFINITY;
    }
    static type_t type_min()
    {
        return -X86_SIMD_SORT_INFINITY;
    }
    static reg_t zmm_max()
    {
        return _mm256_set1_pd(type_max());
    }
    static opmask_t get_partial_loadmask(uint64_t num_to_read)
    {
        auto mask = ((0x1ull << num_to_read) - 0x1ull);
        return convert_int_to_avx2_mask_64bit(mask);
    }
    static int32_t convert_mask_to_int(opmask_t mask)
    {
        return convert_avx2_mask_to_int_64bit(mask);
    }
    template <int type>
    static opmask_t fpclass(reg_t x)
    {
        if constexpr (type == (0x01 | 0x80)) {
            return _mm256_castpd_si256(_mm256_cmp_pd(x, x, _CMP_UNORD_Q));
        }
        else {
            static_assert(type == (0x01 | 0x80), "should not reach here");
        }
    }
    static ymmi_t seti(int v1, int v2, int v3, int v4)
    {
        return _mm256_set_epi64x(v1, v2, v3, v4);
    }

    static reg_t maskz_loadu(opmask_t mask, void const *mem)
    {
        return _mm256_maskload_pd((const double *)mem, mask);
    }
    static opmask_t ge(reg_t x, reg_t y)
    {
        return _mm256_castpd_si256(_mm256_cmp_pd(x, y, _CMP_GE_OQ));
    }
    static opmask_t gt(reg_t x, reg_t y)
    {
        return _mm256_castpd_si256(_mm256_cmp_pd(x, y, _CMP_GT_OQ));
    }
    static opmask_t eq(reg_t x, reg_t y)
    {
        return _mm256_castpd_si256(_mm256_cmp_pd(x, y, _CMP_EQ_OQ));
    }
    template <int scale>
    static reg_t
    mask_i64gather(reg_t src, opmask_t mask, __m256i index, void const *base)
    {
        return _mm256_mask_i64gather_pd(
                src, base, index, _mm256_castsi256_pd(mask), scale);
        ;
    }
    template <int scale>
    static reg_t i64gather(__m256i index, void const *base)
    {
        return _mm256_i64gather_pd((double *)base, index, scale);
    }
    static reg_t loadu(void const *mem)
    {
        return _mm256_loadu_pd((double const *)mem);
    }
    static reg_t max(reg_t x, reg_t y)
    {
        return _mm256_max_pd(x, y);
    }
    static void mask_compressstoreu(void *mem, opmask_t mask, reg_t x)
    {
        return avx2_emu_mask_compressstoreu64<type_t>(mem, mask, x);
    }
    static int32_t double_compressstore(void *left_addr,
                                        void *right_addr,
                                        opmask_t k,
                                        reg_t reg)
    {
        return avx2_double_compressstore64<type_t>(
                left_addr, right_addr, k, reg);
    }
    static reg_t mask_loadu(reg_t x, opmask_t mask, void const *mem)
    {
        reg_t dst = _mm256_maskload_pd((type_t *)mem, mask);
        return mask_mov(x, mask, dst);
    }
    static reg_t mask_mov(reg_t x, opmask_t mask, reg_t y)
    {
        return _mm256_blendv_pd(x, y, _mm256_castsi256_pd(mask));
    }
    static void mask_storeu(void *mem, opmask_t mask, reg_t x)
    {
        return _mm256_maskstore_pd((type_t *)mem, mask, x);
    }
    static reg_t min(reg_t x, reg_t y)
    {
        return _mm256_min_pd(x, y);
    }
    template <int32_t idx>
    static reg_t permutexvar(reg_t ymm)
    {
        return _mm256_permute4x64_pd(ymm, idx);
    }
    template <int32_t idx>
    static reg_t permutevar(reg_t ymm)
    {
        return _mm256_permute4x64_pd(ymm, idx);
    }
    static reg_t reverse(reg_t ymm)
    {
        const int32_t rev_index = SHUFFLE_MASK(0, 1, 2, 3);
        return permutexvar<rev_index>(ymm);
    }
    static type_t reducemax(reg_t v)
    {
        return avx2_emu_reduce_max64<type_t>(v);
    }
    static type_t reducemin(reg_t v)
    {
        return avx2_emu_reduce_min64<type_t>(v);
    }
    static reg_t set1(type_t v)
    {
        return _mm256_set1_pd(v);
    }
    template <uint8_t mask>
    static reg_t shuffle(reg_t ymm)
    {
        return _mm256_permute_pd(ymm, mask);
    }
    static void storeu(void *mem, reg_t x)
    {
        _mm256_storeu_pd((double *)mem, x);
    }
    static reg_t sort_vec(reg_t x)
    {
        return sort_ymm_64bit<avx2_vector<type_t>>(x);
    }
    static reg_t cast_from(__m256i v)
    {
        return _mm256_castsi256_pd(v);
    }
    static __m256i cast_to(reg_t v)
    {
        return _mm256_castpd_si256(v);
    }
};

struct avx2_64bit_swizzle_ops {
    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t swap_n(typename vtype::reg_t reg)
    {
        __m256i v = vtype::cast_to(reg);

        if constexpr (scale == 2) {
            v = _mm256_permute4x64_epi64(v, 0b10110001);
        }
        else if constexpr (scale == 4) {
            v = _mm256_permute4x64_epi64(v, 0b01001110);
        }
        else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v);
    }

    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t
    reverse_n(typename vtype::reg_t reg)
    {
        __m256i v = vtype::cast_to(reg);

        if constexpr (scale == 2) { return swap_n<vtype, 2>(reg); }
        else if constexpr (scale == 4) {
            return vtype::reverse(reg);
        }
        else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(v);
    }

    template <typename vtype, int scale>
    X86_SIMD_SORT_INLINE typename vtype::reg_t
    merge_n(typename vtype::reg_t reg, typename vtype::reg_t other)
    {
        __m256d v1 = _mm256_castsi256_pd(vtype::cast_to(reg));
        __m256d v2 = _mm256_castsi256_pd(vtype::cast_to(other));

        if constexpr (scale == 2) { v1 = _mm256_blend_pd(v1, v2, 0b0101); }
        else if constexpr (scale == 4) {
            v1 = _mm256_blend_pd(v1, v2, 0b0011);
        }
        else {
            static_assert(scale == -1, "should not be reached");
        }

        return vtype::cast_from(_mm256_castpd_si256(v1));
    }
};

#endif // AVX2_QSORT_32BIT
