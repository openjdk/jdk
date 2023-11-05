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

#ifndef AVX512_64BIT_COMMON
#define AVX512_64BIT_COMMON
#include "avx512-common-qsort.h"

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

template <>
struct zmm_vector<int64_t> {
    using type_t = int64_t;
    using zmm_t = __m512i;
    using zmmi_t = __m512i;
    using ymm_t = __m512i;
    using opmask_t = __mmask8;
    static const uint8_t numlanes = 8;

    static type_t type_max() { return X86_SIMD_SORT_MAX_INT64; }
    static type_t type_min() { return X86_SIMD_SORT_MIN_INT64; }
    static zmm_t zmm_max() {
        return _mm512_set1_epi64(type_max());
    }  // TODO: this should broadcast bits as is?

    static zmmi_t seti(int v1, int v2, int v3, int v4, int v5, int v6, int v7,
                       int v8) {
        return _mm512_set_epi64(v1, v2, v3, v4, v5, v6, v7, v8);
    }
    static opmask_t kxor_opmask(opmask_t x, opmask_t y) {
        return _kxor_mask8(x, y);
    }
    static opmask_t knot_opmask(opmask_t x) { return _knot_mask8(x); }
    static opmask_t le(zmm_t x, zmm_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_LE);
    }
    static opmask_t ge(zmm_t x, zmm_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_NLT);
    }
    static opmask_t gt(zmm_t x, zmm_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_GT);
    }
    static opmask_t eq(zmm_t x, zmm_t y) {
        return _mm512_cmp_epi64_mask(x, y, _MM_CMPINT_EQ);
    }
    template <int scale>
    static zmm_t mask_i64gather(zmm_t src, opmask_t mask, __m512i index,
                                void const *base) {
        return _mm512_mask_i64gather_epi64(src, mask, index, base, scale);
    }
    template <int scale>
    static zmm_t i64gather(__m512i index, void const *base) {
        return _mm512_i64gather_epi64(index, base, scale);
    }
    static zmm_t loadu(void const *mem) { return _mm512_loadu_si512(mem); }
    static zmm_t max(zmm_t x, zmm_t y) { return _mm512_max_epi64(x, y); }
    static void mask_compressstoreu(void *mem, opmask_t mask, zmm_t x) {
        return _mm512_mask_compressstoreu_epi64(mem, mask, x);
    }
    static zmm_t maskz_loadu(opmask_t mask, void const *mem) {
        return _mm512_maskz_loadu_epi64(mask, mem);
    }
    static zmm_t mask_loadu(zmm_t x, opmask_t mask, void const *mem) {
        return _mm512_mask_loadu_epi64(x, mask, mem);
    }
    static zmm_t mask_mov(zmm_t x, opmask_t mask, zmm_t y) {
        return _mm512_mask_mov_epi64(x, mask, y);
    }
    static void mask_storeu(void *mem, opmask_t mask, zmm_t x) {
        return _mm512_mask_storeu_epi64(mem, mask, x);
    }
    static zmm_t min(zmm_t x, zmm_t y) { return _mm512_min_epi64(x, y); }
    static zmm_t permutexvar(__m512i idx, zmm_t zmm) {
        return _mm512_permutexvar_epi64(idx, zmm);
    }
    static type_t reducemax(zmm_t v) { return _mm512_reduce_max_epi64(v); }
    static type_t reducemin(zmm_t v) { return _mm512_reduce_min_epi64(v); }
    static zmm_t set1(type_t v) { return _mm512_set1_epi64(v); }
    template <uint8_t mask>
    static zmm_t shuffle(zmm_t zmm) {
        __m512d temp = _mm512_castsi512_pd(zmm);
        return _mm512_castpd_si512(
            _mm512_shuffle_pd(temp, temp, (_MM_PERM_ENUM)mask));
    }
    static void storeu(void *mem, zmm_t x) { _mm512_storeu_si512(mem, x); }
};
template <>
struct zmm_vector<double> {
    using type_t = double;
    using zmm_t = __m512d;
    using zmmi_t = __m512i;
    using ymm_t = __m512d;
    using opmask_t = __mmask8;
    static const uint8_t numlanes = 8;

    static type_t type_max() { return X86_SIMD_SORT_INFINITY; }
    static type_t type_min() { return -X86_SIMD_SORT_INFINITY; }
    static zmm_t zmm_max() { return _mm512_set1_pd(type_max()); }

    static zmmi_t seti(int v1, int v2, int v3, int v4, int v5, int v6, int v7,
                       int v8) {
        return _mm512_set_epi64(v1, v2, v3, v4, v5, v6, v7, v8);
    }

    static zmm_t maskz_loadu(opmask_t mask, void const *mem) {
        return _mm512_maskz_loadu_pd(mask, mem);
    }
    static opmask_t knot_opmask(opmask_t x) { return _knot_mask8(x); }
    static opmask_t ge(zmm_t x, zmm_t y) {
        return _mm512_cmp_pd_mask(x, y, _CMP_GE_OQ);
    }
    static opmask_t gt(zmm_t x, zmm_t y) {
        return _mm512_cmp_pd_mask(x, y, _CMP_GT_OQ);
    }
    static opmask_t eq(zmm_t x, zmm_t y) {
        return _mm512_cmp_pd_mask(x, y, _CMP_EQ_OQ);
    }
    template <int type>
    static opmask_t fpclass(zmm_t x) {
        return _mm512_fpclass_pd_mask(x, type);
    }
    template <int scale>
    static zmm_t mask_i64gather(zmm_t src, opmask_t mask, __m512i index,
                                void const *base) {
        return _mm512_mask_i64gather_pd(src, mask, index, base, scale);
    }
    template <int scale>
    static zmm_t i64gather(__m512i index, void const *base) {
        return _mm512_i64gather_pd(index, base, scale);
    }
    static zmm_t loadu(void const *mem) { return _mm512_loadu_pd(mem); }
    static zmm_t max(zmm_t x, zmm_t y) { return _mm512_max_pd(x, y); }
    static void mask_compressstoreu(void *mem, opmask_t mask, zmm_t x) {
        return _mm512_mask_compressstoreu_pd(mem, mask, x);
    }
    static zmm_t mask_loadu(zmm_t x, opmask_t mask, void const *mem) {
        return _mm512_mask_loadu_pd(x, mask, mem);
    }
    static zmm_t mask_mov(zmm_t x, opmask_t mask, zmm_t y) {
        return _mm512_mask_mov_pd(x, mask, y);
    }
    static void mask_storeu(void *mem, opmask_t mask, zmm_t x) {
        return _mm512_mask_storeu_pd(mem, mask, x);
    }
    static zmm_t min(zmm_t x, zmm_t y) { return _mm512_min_pd(x, y); }
    static zmm_t permutexvar(__m512i idx, zmm_t zmm) {
        return _mm512_permutexvar_pd(idx, zmm);
    }
    static type_t reducemax(zmm_t v) { return _mm512_reduce_max_pd(v); }
    static type_t reducemin(zmm_t v) { return _mm512_reduce_min_pd(v); }
    static zmm_t set1(type_t v) { return _mm512_set1_pd(v); }
    template <uint8_t mask>
    static zmm_t shuffle(zmm_t zmm) {
        return _mm512_shuffle_pd(zmm, zmm, (_MM_PERM_ENUM)mask);
    }
    static void storeu(void *mem, zmm_t x) { _mm512_storeu_pd(mem, x); }
};

/*
 * Assumes zmm is random and performs a full sorting network defined in
 * https://en.wikipedia.org/wiki/Bitonic_sorter#/media/File:BitonicSort.svg
 */
template <typename vtype, typename zmm_t = typename vtype::zmm_t>
X86_SIMD_SORT_INLINE zmm_t sort_zmm_64bit(zmm_t zmm) {
    const typename vtype::zmmi_t rev_index = vtype::seti(NETWORK_64BIT_2);
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


#endif
