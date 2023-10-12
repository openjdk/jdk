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

#include "avx512-64bit-common.h"

// Assumes zmm is bitonic and performs a recursive half cleaner
template <typename vtype, typename zmm_t = typename vtype::zmm_t>
X86_SIMD_SORT_INLINE zmm_t bitonic_merge_zmm_64bit(zmm_t zmm) {
    // 1) half_cleaner[8]: compare 0-4, 1-5, 2-6, 3-7
    zmm = cmp_merge<vtype>(
        zmm, vtype::permutexvar(_mm512_set_epi64(NETWORK_64BIT_4), zmm), 0xF0);
    // 2) half_cleaner[4]
    zmm = cmp_merge<vtype>(
        zmm, vtype::permutexvar(_mm512_set_epi64(NETWORK_64BIT_3), zmm), 0xCC);
    // 3) half_cleaner[1]
    zmm = cmp_merge<vtype>(
        zmm, vtype::template shuffle<SHUFFLE_MASK(1, 1, 1, 1)>(zmm), 0xAA);
    return zmm;
}
// Assumes zmm1 and zmm2 are sorted and performs a recursive half cleaner
template <typename vtype, typename zmm_t = typename vtype::zmm_t>
X86_SIMD_SORT_INLINE void bitonic_merge_two_zmm_64bit(zmm_t &zmm1,
                                                      zmm_t &zmm2) {
    const __m512i rev_index = _mm512_set_epi64(NETWORK_64BIT_2);
    // 1) First step of a merging network: coex of zmm1 and zmm2 reversed
    zmm2 = vtype::permutexvar(rev_index, zmm2);
    zmm_t zmm3 = vtype::min(zmm1, zmm2);
    zmm_t zmm4 = vtype::max(zmm1, zmm2);
    // 2) Recursive half cleaner for each
    zmm1 = bitonic_merge_zmm_64bit<vtype>(zmm3);
    zmm2 = bitonic_merge_zmm_64bit<vtype>(zmm4);
}
// Assumes [zmm0, zmm1] and [zmm2, zmm3] are sorted and performs a recursive
// half cleaner
template <typename vtype, typename zmm_t = typename vtype::zmm_t>
X86_SIMD_SORT_INLINE void bitonic_merge_four_zmm_64bit(zmm_t *zmm) {
    const __m512i rev_index = _mm512_set_epi64(NETWORK_64BIT_2);
    // 1) First step of a merging network
    zmm_t zmm2r = vtype::permutexvar(rev_index, zmm[2]);
    zmm_t zmm3r = vtype::permutexvar(rev_index, zmm[3]);
    zmm_t zmm_t1 = vtype::min(zmm[0], zmm3r);
    zmm_t zmm_t2 = vtype::min(zmm[1], zmm2r);
    // 2) Recursive half clearer: 16
    zmm_t zmm_t3 = vtype::permutexvar(rev_index, vtype::max(zmm[1], zmm2r));
    zmm_t zmm_t4 = vtype::permutexvar(rev_index, vtype::max(zmm[0], zmm3r));
    zmm_t zmm0 = vtype::min(zmm_t1, zmm_t2);
    zmm_t zmm1 = vtype::max(zmm_t1, zmm_t2);
    zmm_t zmm2 = vtype::min(zmm_t3, zmm_t4);
    zmm_t zmm3 = vtype::max(zmm_t3, zmm_t4);
    zmm[0] = bitonic_merge_zmm_64bit<vtype>(zmm0);
    zmm[1] = bitonic_merge_zmm_64bit<vtype>(zmm1);
    zmm[2] = bitonic_merge_zmm_64bit<vtype>(zmm2);
    zmm[3] = bitonic_merge_zmm_64bit<vtype>(zmm3);
}
template <typename vtype, typename zmm_t = typename vtype::zmm_t>
X86_SIMD_SORT_INLINE void bitonic_merge_eight_zmm_64bit(zmm_t *zmm) {
    const __m512i rev_index = _mm512_set_epi64(NETWORK_64BIT_2);
    zmm_t zmm4r = vtype::permutexvar(rev_index, zmm[4]);
    zmm_t zmm5r = vtype::permutexvar(rev_index, zmm[5]);
    zmm_t zmm6r = vtype::permutexvar(rev_index, zmm[6]);
    zmm_t zmm7r = vtype::permutexvar(rev_index, zmm[7]);
    zmm_t zmm_t1 = vtype::min(zmm[0], zmm7r);
    zmm_t zmm_t2 = vtype::min(zmm[1], zmm6r);
    zmm_t zmm_t3 = vtype::min(zmm[2], zmm5r);
    zmm_t zmm_t4 = vtype::min(zmm[3], zmm4r);
    zmm_t zmm_t5 = vtype::permutexvar(rev_index, vtype::max(zmm[3], zmm4r));
    zmm_t zmm_t6 = vtype::permutexvar(rev_index, vtype::max(zmm[2], zmm5r));
    zmm_t zmm_t7 = vtype::permutexvar(rev_index, vtype::max(zmm[1], zmm6r));
    zmm_t zmm_t8 = vtype::permutexvar(rev_index, vtype::max(zmm[0], zmm7r));
    COEX<vtype>(zmm_t1, zmm_t3);
    COEX<vtype>(zmm_t2, zmm_t4);
    COEX<vtype>(zmm_t5, zmm_t7);
    COEX<vtype>(zmm_t6, zmm_t8);
    COEX<vtype>(zmm_t1, zmm_t2);
    COEX<vtype>(zmm_t3, zmm_t4);
    COEX<vtype>(zmm_t5, zmm_t6);
    COEX<vtype>(zmm_t7, zmm_t8);
    zmm[0] = bitonic_merge_zmm_64bit<vtype>(zmm_t1);
    zmm[1] = bitonic_merge_zmm_64bit<vtype>(zmm_t2);
    zmm[2] = bitonic_merge_zmm_64bit<vtype>(zmm_t3);
    zmm[3] = bitonic_merge_zmm_64bit<vtype>(zmm_t4);
    zmm[4] = bitonic_merge_zmm_64bit<vtype>(zmm_t5);
    zmm[5] = bitonic_merge_zmm_64bit<vtype>(zmm_t6);
    zmm[6] = bitonic_merge_zmm_64bit<vtype>(zmm_t7);
    zmm[7] = bitonic_merge_zmm_64bit<vtype>(zmm_t8);
}
template <typename vtype, typename zmm_t = typename vtype::zmm_t>
X86_SIMD_SORT_INLINE void bitonic_merge_sixteen_zmm_64bit(zmm_t *zmm) {
    const __m512i rev_index = _mm512_set_epi64(NETWORK_64BIT_2);
    zmm_t zmm8r = vtype::permutexvar(rev_index, zmm[8]);
    zmm_t zmm9r = vtype::permutexvar(rev_index, zmm[9]);
    zmm_t zmm10r = vtype::permutexvar(rev_index, zmm[10]);
    zmm_t zmm11r = vtype::permutexvar(rev_index, zmm[11]);
    zmm_t zmm12r = vtype::permutexvar(rev_index, zmm[12]);
    zmm_t zmm13r = vtype::permutexvar(rev_index, zmm[13]);
    zmm_t zmm14r = vtype::permutexvar(rev_index, zmm[14]);
    zmm_t zmm15r = vtype::permutexvar(rev_index, zmm[15]);
    zmm_t zmm_t1 = vtype::min(zmm[0], zmm15r);
    zmm_t zmm_t2 = vtype::min(zmm[1], zmm14r);
    zmm_t zmm_t3 = vtype::min(zmm[2], zmm13r);
    zmm_t zmm_t4 = vtype::min(zmm[3], zmm12r);
    zmm_t zmm_t5 = vtype::min(zmm[4], zmm11r);
    zmm_t zmm_t6 = vtype::min(zmm[5], zmm10r);
    zmm_t zmm_t7 = vtype::min(zmm[6], zmm9r);
    zmm_t zmm_t8 = vtype::min(zmm[7], zmm8r);
    zmm_t zmm_t9 = vtype::permutexvar(rev_index, vtype::max(zmm[7], zmm8r));
    zmm_t zmm_t10 = vtype::permutexvar(rev_index, vtype::max(zmm[6], zmm9r));
    zmm_t zmm_t11 = vtype::permutexvar(rev_index, vtype::max(zmm[5], zmm10r));
    zmm_t zmm_t12 = vtype::permutexvar(rev_index, vtype::max(zmm[4], zmm11r));
    zmm_t zmm_t13 = vtype::permutexvar(rev_index, vtype::max(zmm[3], zmm12r));
    zmm_t zmm_t14 = vtype::permutexvar(rev_index, vtype::max(zmm[2], zmm13r));
    zmm_t zmm_t15 = vtype::permutexvar(rev_index, vtype::max(zmm[1], zmm14r));
    zmm_t zmm_t16 = vtype::permutexvar(rev_index, vtype::max(zmm[0], zmm15r));
    // Recusive half clear 16 zmm regs
    COEX<vtype>(zmm_t1, zmm_t5);
    COEX<vtype>(zmm_t2, zmm_t6);
    COEX<vtype>(zmm_t3, zmm_t7);
    COEX<vtype>(zmm_t4, zmm_t8);
    COEX<vtype>(zmm_t9, zmm_t13);
    COEX<vtype>(zmm_t10, zmm_t14);
    COEX<vtype>(zmm_t11, zmm_t15);
    COEX<vtype>(zmm_t12, zmm_t16);
    //
    COEX<vtype>(zmm_t1, zmm_t3);
    COEX<vtype>(zmm_t2, zmm_t4);
    COEX<vtype>(zmm_t5, zmm_t7);
    COEX<vtype>(zmm_t6, zmm_t8);
    COEX<vtype>(zmm_t9, zmm_t11);
    COEX<vtype>(zmm_t10, zmm_t12);
    COEX<vtype>(zmm_t13, zmm_t15);
    COEX<vtype>(zmm_t14, zmm_t16);
    //
    COEX<vtype>(zmm_t1, zmm_t2);
    COEX<vtype>(zmm_t3, zmm_t4);
    COEX<vtype>(zmm_t5, zmm_t6);
    COEX<vtype>(zmm_t7, zmm_t8);
    COEX<vtype>(zmm_t9, zmm_t10);
    COEX<vtype>(zmm_t11, zmm_t12);
    COEX<vtype>(zmm_t13, zmm_t14);
    COEX<vtype>(zmm_t15, zmm_t16);
    //
    zmm[0] = bitonic_merge_zmm_64bit<vtype>(zmm_t1);
    zmm[1] = bitonic_merge_zmm_64bit<vtype>(zmm_t2);
    zmm[2] = bitonic_merge_zmm_64bit<vtype>(zmm_t3);
    zmm[3] = bitonic_merge_zmm_64bit<vtype>(zmm_t4);
    zmm[4] = bitonic_merge_zmm_64bit<vtype>(zmm_t5);
    zmm[5] = bitonic_merge_zmm_64bit<vtype>(zmm_t6);
    zmm[6] = bitonic_merge_zmm_64bit<vtype>(zmm_t7);
    zmm[7] = bitonic_merge_zmm_64bit<vtype>(zmm_t8);
    zmm[8] = bitonic_merge_zmm_64bit<vtype>(zmm_t9);
    zmm[9] = bitonic_merge_zmm_64bit<vtype>(zmm_t10);
    zmm[10] = bitonic_merge_zmm_64bit<vtype>(zmm_t11);
    zmm[11] = bitonic_merge_zmm_64bit<vtype>(zmm_t12);
    zmm[12] = bitonic_merge_zmm_64bit<vtype>(zmm_t13);
    zmm[13] = bitonic_merge_zmm_64bit<vtype>(zmm_t14);
    zmm[14] = bitonic_merge_zmm_64bit<vtype>(zmm_t15);
    zmm[15] = bitonic_merge_zmm_64bit<vtype>(zmm_t16);
}

template <typename vtype, typename zmm_t = typename vtype::zmm_t>
X86_SIMD_SORT_INLINE void bitonic_merge_32_zmm_64bit(zmm_t *zmm) {
    const __m512i rev_index = _mm512_set_epi64(NETWORK_64BIT_2);
    zmm_t zmm16r = vtype::permutexvar(rev_index, zmm[16]);
    zmm_t zmm17r = vtype::permutexvar(rev_index, zmm[17]);
    zmm_t zmm18r = vtype::permutexvar(rev_index, zmm[18]);
    zmm_t zmm19r = vtype::permutexvar(rev_index, zmm[19]);
    zmm_t zmm20r = vtype::permutexvar(rev_index, zmm[20]);
    zmm_t zmm21r = vtype::permutexvar(rev_index, zmm[21]);
    zmm_t zmm22r = vtype::permutexvar(rev_index, zmm[22]);
    zmm_t zmm23r = vtype::permutexvar(rev_index, zmm[23]);
    zmm_t zmm24r = vtype::permutexvar(rev_index, zmm[24]);
    zmm_t zmm25r = vtype::permutexvar(rev_index, zmm[25]);
    zmm_t zmm26r = vtype::permutexvar(rev_index, zmm[26]);
    zmm_t zmm27r = vtype::permutexvar(rev_index, zmm[27]);
    zmm_t zmm28r = vtype::permutexvar(rev_index, zmm[28]);
    zmm_t zmm29r = vtype::permutexvar(rev_index, zmm[29]);
    zmm_t zmm30r = vtype::permutexvar(rev_index, zmm[30]);
    zmm_t zmm31r = vtype::permutexvar(rev_index, zmm[31]);
    zmm_t zmm_t1 = vtype::min(zmm[0], zmm31r);
    zmm_t zmm_t2 = vtype::min(zmm[1], zmm30r);
    zmm_t zmm_t3 = vtype::min(zmm[2], zmm29r);
    zmm_t zmm_t4 = vtype::min(zmm[3], zmm28r);
    zmm_t zmm_t5 = vtype::min(zmm[4], zmm27r);
    zmm_t zmm_t6 = vtype::min(zmm[5], zmm26r);
    zmm_t zmm_t7 = vtype::min(zmm[6], zmm25r);
    zmm_t zmm_t8 = vtype::min(zmm[7], zmm24r);
    zmm_t zmm_t9 = vtype::min(zmm[8], zmm23r);
    zmm_t zmm_t10 = vtype::min(zmm[9], zmm22r);
    zmm_t zmm_t11 = vtype::min(zmm[10], zmm21r);
    zmm_t zmm_t12 = vtype::min(zmm[11], zmm20r);
    zmm_t zmm_t13 = vtype::min(zmm[12], zmm19r);
    zmm_t zmm_t14 = vtype::min(zmm[13], zmm18r);
    zmm_t zmm_t15 = vtype::min(zmm[14], zmm17r);
    zmm_t zmm_t16 = vtype::min(zmm[15], zmm16r);
    zmm_t zmm_t17 = vtype::permutexvar(rev_index, vtype::max(zmm[15], zmm16r));
    zmm_t zmm_t18 = vtype::permutexvar(rev_index, vtype::max(zmm[14], zmm17r));
    zmm_t zmm_t19 = vtype::permutexvar(rev_index, vtype::max(zmm[13], zmm18r));
    zmm_t zmm_t20 = vtype::permutexvar(rev_index, vtype::max(zmm[12], zmm19r));
    zmm_t zmm_t21 = vtype::permutexvar(rev_index, vtype::max(zmm[11], zmm20r));
    zmm_t zmm_t22 = vtype::permutexvar(rev_index, vtype::max(zmm[10], zmm21r));
    zmm_t zmm_t23 = vtype::permutexvar(rev_index, vtype::max(zmm[9], zmm22r));
    zmm_t zmm_t24 = vtype::permutexvar(rev_index, vtype::max(zmm[8], zmm23r));
    zmm_t zmm_t25 = vtype::permutexvar(rev_index, vtype::max(zmm[7], zmm24r));
    zmm_t zmm_t26 = vtype::permutexvar(rev_index, vtype::max(zmm[6], zmm25r));
    zmm_t zmm_t27 = vtype::permutexvar(rev_index, vtype::max(zmm[5], zmm26r));
    zmm_t zmm_t28 = vtype::permutexvar(rev_index, vtype::max(zmm[4], zmm27r));
    zmm_t zmm_t29 = vtype::permutexvar(rev_index, vtype::max(zmm[3], zmm28r));
    zmm_t zmm_t30 = vtype::permutexvar(rev_index, vtype::max(zmm[2], zmm29r));
    zmm_t zmm_t31 = vtype::permutexvar(rev_index, vtype::max(zmm[1], zmm30r));
    zmm_t zmm_t32 = vtype::permutexvar(rev_index, vtype::max(zmm[0], zmm31r));
    // Recusive half clear 16 zmm regs
    COEX<vtype>(zmm_t1, zmm_t9);
    COEX<vtype>(zmm_t2, zmm_t10);
    COEX<vtype>(zmm_t3, zmm_t11);
    COEX<vtype>(zmm_t4, zmm_t12);
    COEX<vtype>(zmm_t5, zmm_t13);
    COEX<vtype>(zmm_t6, zmm_t14);
    COEX<vtype>(zmm_t7, zmm_t15);
    COEX<vtype>(zmm_t8, zmm_t16);
    COEX<vtype>(zmm_t17, zmm_t25);
    COEX<vtype>(zmm_t18, zmm_t26);
    COEX<vtype>(zmm_t19, zmm_t27);
    COEX<vtype>(zmm_t20, zmm_t28);
    COEX<vtype>(zmm_t21, zmm_t29);
    COEX<vtype>(zmm_t22, zmm_t30);
    COEX<vtype>(zmm_t23, zmm_t31);
    COEX<vtype>(zmm_t24, zmm_t32);
    //
    COEX<vtype>(zmm_t1, zmm_t5);
    COEX<vtype>(zmm_t2, zmm_t6);
    COEX<vtype>(zmm_t3, zmm_t7);
    COEX<vtype>(zmm_t4, zmm_t8);
    COEX<vtype>(zmm_t9, zmm_t13);
    COEX<vtype>(zmm_t10, zmm_t14);
    COEX<vtype>(zmm_t11, zmm_t15);
    COEX<vtype>(zmm_t12, zmm_t16);
    COEX<vtype>(zmm_t17, zmm_t21);
    COEX<vtype>(zmm_t18, zmm_t22);
    COEX<vtype>(zmm_t19, zmm_t23);
    COEX<vtype>(zmm_t20, zmm_t24);
    COEX<vtype>(zmm_t25, zmm_t29);
    COEX<vtype>(zmm_t26, zmm_t30);
    COEX<vtype>(zmm_t27, zmm_t31);
    COEX<vtype>(zmm_t28, zmm_t32);
    //
    COEX<vtype>(zmm_t1, zmm_t3);
    COEX<vtype>(zmm_t2, zmm_t4);
    COEX<vtype>(zmm_t5, zmm_t7);
    COEX<vtype>(zmm_t6, zmm_t8);
    COEX<vtype>(zmm_t9, zmm_t11);
    COEX<vtype>(zmm_t10, zmm_t12);
    COEX<vtype>(zmm_t13, zmm_t15);
    COEX<vtype>(zmm_t14, zmm_t16);
    COEX<vtype>(zmm_t17, zmm_t19);
    COEX<vtype>(zmm_t18, zmm_t20);
    COEX<vtype>(zmm_t21, zmm_t23);
    COEX<vtype>(zmm_t22, zmm_t24);
    COEX<vtype>(zmm_t25, zmm_t27);
    COEX<vtype>(zmm_t26, zmm_t28);
    COEX<vtype>(zmm_t29, zmm_t31);
    COEX<vtype>(zmm_t30, zmm_t32);
    //
    COEX<vtype>(zmm_t1, zmm_t2);
    COEX<vtype>(zmm_t3, zmm_t4);
    COEX<vtype>(zmm_t5, zmm_t6);
    COEX<vtype>(zmm_t7, zmm_t8);
    COEX<vtype>(zmm_t9, zmm_t10);
    COEX<vtype>(zmm_t11, zmm_t12);
    COEX<vtype>(zmm_t13, zmm_t14);
    COEX<vtype>(zmm_t15, zmm_t16);
    COEX<vtype>(zmm_t17, zmm_t18);
    COEX<vtype>(zmm_t19, zmm_t20);
    COEX<vtype>(zmm_t21, zmm_t22);
    COEX<vtype>(zmm_t23, zmm_t24);
    COEX<vtype>(zmm_t25, zmm_t26);
    COEX<vtype>(zmm_t27, zmm_t28);
    COEX<vtype>(zmm_t29, zmm_t30);
    COEX<vtype>(zmm_t31, zmm_t32);
    //
    zmm[0] = bitonic_merge_zmm_64bit<vtype>(zmm_t1);
    zmm[1] = bitonic_merge_zmm_64bit<vtype>(zmm_t2);
    zmm[2] = bitonic_merge_zmm_64bit<vtype>(zmm_t3);
    zmm[3] = bitonic_merge_zmm_64bit<vtype>(zmm_t4);
    zmm[4] = bitonic_merge_zmm_64bit<vtype>(zmm_t5);
    zmm[5] = bitonic_merge_zmm_64bit<vtype>(zmm_t6);
    zmm[6] = bitonic_merge_zmm_64bit<vtype>(zmm_t7);
    zmm[7] = bitonic_merge_zmm_64bit<vtype>(zmm_t8);
    zmm[8] = bitonic_merge_zmm_64bit<vtype>(zmm_t9);
    zmm[9] = bitonic_merge_zmm_64bit<vtype>(zmm_t10);
    zmm[10] = bitonic_merge_zmm_64bit<vtype>(zmm_t11);
    zmm[11] = bitonic_merge_zmm_64bit<vtype>(zmm_t12);
    zmm[12] = bitonic_merge_zmm_64bit<vtype>(zmm_t13);
    zmm[13] = bitonic_merge_zmm_64bit<vtype>(zmm_t14);
    zmm[14] = bitonic_merge_zmm_64bit<vtype>(zmm_t15);
    zmm[15] = bitonic_merge_zmm_64bit<vtype>(zmm_t16);
    zmm[16] = bitonic_merge_zmm_64bit<vtype>(zmm_t17);
    zmm[17] = bitonic_merge_zmm_64bit<vtype>(zmm_t18);
    zmm[18] = bitonic_merge_zmm_64bit<vtype>(zmm_t19);
    zmm[19] = bitonic_merge_zmm_64bit<vtype>(zmm_t20);
    zmm[20] = bitonic_merge_zmm_64bit<vtype>(zmm_t21);
    zmm[21] = bitonic_merge_zmm_64bit<vtype>(zmm_t22);
    zmm[22] = bitonic_merge_zmm_64bit<vtype>(zmm_t23);
    zmm[23] = bitonic_merge_zmm_64bit<vtype>(zmm_t24);
    zmm[24] = bitonic_merge_zmm_64bit<vtype>(zmm_t25);
    zmm[25] = bitonic_merge_zmm_64bit<vtype>(zmm_t26);
    zmm[26] = bitonic_merge_zmm_64bit<vtype>(zmm_t27);
    zmm[27] = bitonic_merge_zmm_64bit<vtype>(zmm_t28);
    zmm[28] = bitonic_merge_zmm_64bit<vtype>(zmm_t29);
    zmm[29] = bitonic_merge_zmm_64bit<vtype>(zmm_t30);
    zmm[30] = bitonic_merge_zmm_64bit<vtype>(zmm_t31);
    zmm[31] = bitonic_merge_zmm_64bit<vtype>(zmm_t32);
}

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE void sort_8_64bit(type_t *arr, int32_t N) {
    typename vtype::opmask_t load_mask = (0x01 << N) - 0x01;
    typename vtype::zmm_t zmm =
        vtype::mask_loadu(vtype::zmm_max(), load_mask, arr);
    vtype::mask_storeu(arr, load_mask, sort_zmm_64bit<vtype>(zmm));
}

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE void sort_16_64bit(type_t *arr, int32_t N) {
    if (N <= 8) {
        sort_8_64bit<vtype>(arr, N);
        return;
    }
    using zmm_t = typename vtype::zmm_t;
    zmm_t zmm1 = vtype::loadu(arr);
    typename vtype::opmask_t load_mask = (0x01 << (N - 8)) - 0x01;
    zmm_t zmm2 = vtype::mask_loadu(vtype::zmm_max(), load_mask, arr + 8);
    zmm1 = sort_zmm_64bit<vtype>(zmm1);
    zmm2 = sort_zmm_64bit<vtype>(zmm2);
    bitonic_merge_two_zmm_64bit<vtype>(zmm1, zmm2);
    vtype::storeu(arr, zmm1);
    vtype::mask_storeu(arr + 8, load_mask, zmm2);
}

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE void sort_32_64bit(type_t *arr, int32_t N) {
    if (N <= 16) {
        sort_16_64bit<vtype>(arr, N);
        return;
    }
    using zmm_t = typename vtype::zmm_t;
    using opmask_t = typename vtype::opmask_t;
    zmm_t zmm[4];
    zmm[0] = vtype::loadu(arr);
    zmm[1] = vtype::loadu(arr + 8);
    opmask_t load_mask1 = 0xFF, load_mask2 = 0xFF;
    uint64_t combined_mask = (0x1ull << (N - 16)) - 0x1ull;
    load_mask1 = (combined_mask)&0xFF;
    load_mask2 = (combined_mask >> 8) & 0xFF;
    zmm[2] = vtype::mask_loadu(vtype::zmm_max(), load_mask1, arr + 16);
    zmm[3] = vtype::mask_loadu(vtype::zmm_max(), load_mask2, arr + 24);
    zmm[0] = sort_zmm_64bit<vtype>(zmm[0]);
    zmm[1] = sort_zmm_64bit<vtype>(zmm[1]);
    zmm[2] = sort_zmm_64bit<vtype>(zmm[2]);
    zmm[3] = sort_zmm_64bit<vtype>(zmm[3]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[0], zmm[1]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[2], zmm[3]);
    bitonic_merge_four_zmm_64bit<vtype>(zmm);
    vtype::storeu(arr, zmm[0]);
    vtype::storeu(arr + 8, zmm[1]);
    vtype::mask_storeu(arr + 16, load_mask1, zmm[2]);
    vtype::mask_storeu(arr + 24, load_mask2, zmm[3]);
}

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE void sort_64_64bit(type_t *arr, int32_t N) {
    if (N <= 32) {
        sort_32_64bit<vtype>(arr, N);
        return;
    }
    using zmm_t = typename vtype::zmm_t;
    using opmask_t = typename vtype::opmask_t;
    zmm_t zmm[8];
    zmm[0] = vtype::loadu(arr);
    zmm[1] = vtype::loadu(arr + 8);
    zmm[2] = vtype::loadu(arr + 16);
    zmm[3] = vtype::loadu(arr + 24);
    zmm[0] = sort_zmm_64bit<vtype>(zmm[0]);
    zmm[1] = sort_zmm_64bit<vtype>(zmm[1]);
    zmm[2] = sort_zmm_64bit<vtype>(zmm[2]);
    zmm[3] = sort_zmm_64bit<vtype>(zmm[3]);
    opmask_t load_mask1 = 0xFF, load_mask2 = 0xFF;
    opmask_t load_mask3 = 0xFF, load_mask4 = 0xFF;
    // N-32 >= 1
    uint64_t combined_mask = (0x1ull << (N - 32)) - 0x1ull;
    load_mask1 = (combined_mask)&0xFF;
    load_mask2 = (combined_mask >> 8) & 0xFF;
    load_mask3 = (combined_mask >> 16) & 0xFF;
    load_mask4 = (combined_mask >> 24) & 0xFF;
    zmm[4] = vtype::mask_loadu(vtype::zmm_max(), load_mask1, arr + 32);
    zmm[5] = vtype::mask_loadu(vtype::zmm_max(), load_mask2, arr + 40);
    zmm[6] = vtype::mask_loadu(vtype::zmm_max(), load_mask3, arr + 48);
    zmm[7] = vtype::mask_loadu(vtype::zmm_max(), load_mask4, arr + 56);
    zmm[4] = sort_zmm_64bit<vtype>(zmm[4]);
    zmm[5] = sort_zmm_64bit<vtype>(zmm[5]);
    zmm[6] = sort_zmm_64bit<vtype>(zmm[6]);
    zmm[7] = sort_zmm_64bit<vtype>(zmm[7]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[0], zmm[1]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[2], zmm[3]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[4], zmm[5]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[6], zmm[7]);
    bitonic_merge_four_zmm_64bit<vtype>(zmm);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 4);
    bitonic_merge_eight_zmm_64bit<vtype>(zmm);
    vtype::storeu(arr, zmm[0]);
    vtype::storeu(arr + 8, zmm[1]);
    vtype::storeu(arr + 16, zmm[2]);
    vtype::storeu(arr + 24, zmm[3]);
    vtype::mask_storeu(arr + 32, load_mask1, zmm[4]);
    vtype::mask_storeu(arr + 40, load_mask2, zmm[5]);
    vtype::mask_storeu(arr + 48, load_mask3, zmm[6]);
    vtype::mask_storeu(arr + 56, load_mask4, zmm[7]);
}

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE void sort_128_64bit(type_t *arr, int32_t N) {
    if (N <= 64) {
        sort_64_64bit<vtype>(arr, N);
        return;
    }
    using zmm_t = typename vtype::zmm_t;
    using opmask_t = typename vtype::opmask_t;
    zmm_t zmm[16];
    zmm[0] = vtype::loadu(arr);
    zmm[1] = vtype::loadu(arr + 8);
    zmm[2] = vtype::loadu(arr + 16);
    zmm[3] = vtype::loadu(arr + 24);
    zmm[4] = vtype::loadu(arr + 32);
    zmm[5] = vtype::loadu(arr + 40);
    zmm[6] = vtype::loadu(arr + 48);
    zmm[7] = vtype::loadu(arr + 56);
    zmm[0] = sort_zmm_64bit<vtype>(zmm[0]);
    zmm[1] = sort_zmm_64bit<vtype>(zmm[1]);
    zmm[2] = sort_zmm_64bit<vtype>(zmm[2]);
    zmm[3] = sort_zmm_64bit<vtype>(zmm[3]);
    zmm[4] = sort_zmm_64bit<vtype>(zmm[4]);
    zmm[5] = sort_zmm_64bit<vtype>(zmm[5]);
    zmm[6] = sort_zmm_64bit<vtype>(zmm[6]);
    zmm[7] = sort_zmm_64bit<vtype>(zmm[7]);
    opmask_t load_mask1 = 0xFF, load_mask2 = 0xFF;
    opmask_t load_mask3 = 0xFF, load_mask4 = 0xFF;
    opmask_t load_mask5 = 0xFF, load_mask6 = 0xFF;
    opmask_t load_mask7 = 0xFF, load_mask8 = 0xFF;
    if (N != 128) {
        uint64_t combined_mask = (0x1ull << (N - 64)) - 0x1ull;
        load_mask1 = (combined_mask)&0xFF;
        load_mask2 = (combined_mask >> 8) & 0xFF;
        load_mask3 = (combined_mask >> 16) & 0xFF;
        load_mask4 = (combined_mask >> 24) & 0xFF;
        load_mask5 = (combined_mask >> 32) & 0xFF;
        load_mask6 = (combined_mask >> 40) & 0xFF;
        load_mask7 = (combined_mask >> 48) & 0xFF;
        load_mask8 = (combined_mask >> 56) & 0xFF;
    }
    zmm[8] = vtype::mask_loadu(vtype::zmm_max(), load_mask1, arr + 64);
    zmm[9] = vtype::mask_loadu(vtype::zmm_max(), load_mask2, arr + 72);
    zmm[10] = vtype::mask_loadu(vtype::zmm_max(), load_mask3, arr + 80);
    zmm[11] = vtype::mask_loadu(vtype::zmm_max(), load_mask4, arr + 88);
    zmm[12] = vtype::mask_loadu(vtype::zmm_max(), load_mask5, arr + 96);
    zmm[13] = vtype::mask_loadu(vtype::zmm_max(), load_mask6, arr + 104);
    zmm[14] = vtype::mask_loadu(vtype::zmm_max(), load_mask7, arr + 112);
    zmm[15] = vtype::mask_loadu(vtype::zmm_max(), load_mask8, arr + 120);
    zmm[8] = sort_zmm_64bit<vtype>(zmm[8]);
    zmm[9] = sort_zmm_64bit<vtype>(zmm[9]);
    zmm[10] = sort_zmm_64bit<vtype>(zmm[10]);
    zmm[11] = sort_zmm_64bit<vtype>(zmm[11]);
    zmm[12] = sort_zmm_64bit<vtype>(zmm[12]);
    zmm[13] = sort_zmm_64bit<vtype>(zmm[13]);
    zmm[14] = sort_zmm_64bit<vtype>(zmm[14]);
    zmm[15] = sort_zmm_64bit<vtype>(zmm[15]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[0], zmm[1]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[2], zmm[3]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[4], zmm[5]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[6], zmm[7]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[8], zmm[9]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[10], zmm[11]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[12], zmm[13]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[14], zmm[15]);
    bitonic_merge_four_zmm_64bit<vtype>(zmm);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 4);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 8);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 12);
    bitonic_merge_eight_zmm_64bit<vtype>(zmm);
    bitonic_merge_eight_zmm_64bit<vtype>(zmm + 8);
    bitonic_merge_sixteen_zmm_64bit<vtype>(zmm);
    vtype::storeu(arr, zmm[0]);
    vtype::storeu(arr + 8, zmm[1]);
    vtype::storeu(arr + 16, zmm[2]);
    vtype::storeu(arr + 24, zmm[3]);
    vtype::storeu(arr + 32, zmm[4]);
    vtype::storeu(arr + 40, zmm[5]);
    vtype::storeu(arr + 48, zmm[6]);
    vtype::storeu(arr + 56, zmm[7]);
    vtype::mask_storeu(arr + 64, load_mask1, zmm[8]);
    vtype::mask_storeu(arr + 72, load_mask2, zmm[9]);
    vtype::mask_storeu(arr + 80, load_mask3, zmm[10]);
    vtype::mask_storeu(arr + 88, load_mask4, zmm[11]);
    vtype::mask_storeu(arr + 96, load_mask5, zmm[12]);
    vtype::mask_storeu(arr + 104, load_mask6, zmm[13]);
    vtype::mask_storeu(arr + 112, load_mask7, zmm[14]);
    vtype::mask_storeu(arr + 120, load_mask8, zmm[15]);
}

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE void sort_256_64bit(type_t *arr, int32_t N) {
    if (N <= 128) {
        sort_128_64bit<vtype>(arr, N);
        return;
    }
    using zmm_t = typename vtype::zmm_t;
    using opmask_t = typename vtype::opmask_t;
    zmm_t zmm[32];
    zmm[0] = vtype::loadu(arr);
    zmm[1] = vtype::loadu(arr + 8);
    zmm[2] = vtype::loadu(arr + 16);
    zmm[3] = vtype::loadu(arr + 24);
    zmm[4] = vtype::loadu(arr + 32);
    zmm[5] = vtype::loadu(arr + 40);
    zmm[6] = vtype::loadu(arr + 48);
    zmm[7] = vtype::loadu(arr + 56);
    zmm[8] = vtype::loadu(arr + 64);
    zmm[9] = vtype::loadu(arr + 72);
    zmm[10] = vtype::loadu(arr + 80);
    zmm[11] = vtype::loadu(arr + 88);
    zmm[12] = vtype::loadu(arr + 96);
    zmm[13] = vtype::loadu(arr + 104);
    zmm[14] = vtype::loadu(arr + 112);
    zmm[15] = vtype::loadu(arr + 120);
    zmm[0] = sort_zmm_64bit<vtype>(zmm[0]);
    zmm[1] = sort_zmm_64bit<vtype>(zmm[1]);
    zmm[2] = sort_zmm_64bit<vtype>(zmm[2]);
    zmm[3] = sort_zmm_64bit<vtype>(zmm[3]);
    zmm[4] = sort_zmm_64bit<vtype>(zmm[4]);
    zmm[5] = sort_zmm_64bit<vtype>(zmm[5]);
    zmm[6] = sort_zmm_64bit<vtype>(zmm[6]);
    zmm[7] = sort_zmm_64bit<vtype>(zmm[7]);
    zmm[8] = sort_zmm_64bit<vtype>(zmm[8]);
    zmm[9] = sort_zmm_64bit<vtype>(zmm[9]);
    zmm[10] = sort_zmm_64bit<vtype>(zmm[10]);
    zmm[11] = sort_zmm_64bit<vtype>(zmm[11]);
    zmm[12] = sort_zmm_64bit<vtype>(zmm[12]);
    zmm[13] = sort_zmm_64bit<vtype>(zmm[13]);
    zmm[14] = sort_zmm_64bit<vtype>(zmm[14]);
    zmm[15] = sort_zmm_64bit<vtype>(zmm[15]);
    opmask_t load_mask1 = 0xFF, load_mask2 = 0xFF;
    opmask_t load_mask3 = 0xFF, load_mask4 = 0xFF;
    opmask_t load_mask5 = 0xFF, load_mask6 = 0xFF;
    opmask_t load_mask7 = 0xFF, load_mask8 = 0xFF;
    opmask_t load_mask9 = 0xFF, load_mask10 = 0xFF;
    opmask_t load_mask11 = 0xFF, load_mask12 = 0xFF;
    opmask_t load_mask13 = 0xFF, load_mask14 = 0xFF;
    opmask_t load_mask15 = 0xFF, load_mask16 = 0xFF;
    if (N != 256) {
        uint64_t combined_mask;
        if (N < 192) {
            combined_mask = (0x1ull << (N - 128)) - 0x1ull;
            load_mask1 = (combined_mask)&0xFF;
            load_mask2 = (combined_mask >> 8) & 0xFF;
            load_mask3 = (combined_mask >> 16) & 0xFF;
            load_mask4 = (combined_mask >> 24) & 0xFF;
            load_mask5 = (combined_mask >> 32) & 0xFF;
            load_mask6 = (combined_mask >> 40) & 0xFF;
            load_mask7 = (combined_mask >> 48) & 0xFF;
            load_mask8 = (combined_mask >> 56) & 0xFF;
            load_mask9 = 0x00;
            load_mask10 = 0x0;
            load_mask11 = 0x00;
            load_mask12 = 0x00;
            load_mask13 = 0x00;
            load_mask14 = 0x00;
            load_mask15 = 0x00;
            load_mask16 = 0x00;
        } else {
            combined_mask = (0x1ull << (N - 192)) - 0x1ull;
            load_mask9 = (combined_mask)&0xFF;
            load_mask10 = (combined_mask >> 8) & 0xFF;
            load_mask11 = (combined_mask >> 16) & 0xFF;
            load_mask12 = (combined_mask >> 24) & 0xFF;
            load_mask13 = (combined_mask >> 32) & 0xFF;
            load_mask14 = (combined_mask >> 40) & 0xFF;
            load_mask15 = (combined_mask >> 48) & 0xFF;
            load_mask16 = (combined_mask >> 56) & 0xFF;
        }
    }
    zmm[16] = vtype::mask_loadu(vtype::zmm_max(), load_mask1, arr + 128);
    zmm[17] = vtype::mask_loadu(vtype::zmm_max(), load_mask2, arr + 136);
    zmm[18] = vtype::mask_loadu(vtype::zmm_max(), load_mask3, arr + 144);
    zmm[19] = vtype::mask_loadu(vtype::zmm_max(), load_mask4, arr + 152);
    zmm[20] = vtype::mask_loadu(vtype::zmm_max(), load_mask5, arr + 160);
    zmm[21] = vtype::mask_loadu(vtype::zmm_max(), load_mask6, arr + 168);
    zmm[22] = vtype::mask_loadu(vtype::zmm_max(), load_mask7, arr + 176);
    zmm[23] = vtype::mask_loadu(vtype::zmm_max(), load_mask8, arr + 184);
    if (N < 192) {
        zmm[24] = vtype::zmm_max();
        zmm[25] = vtype::zmm_max();
        zmm[26] = vtype::zmm_max();
        zmm[27] = vtype::zmm_max();
        zmm[28] = vtype::zmm_max();
        zmm[29] = vtype::zmm_max();
        zmm[30] = vtype::zmm_max();
        zmm[31] = vtype::zmm_max();
    } else {
        zmm[24] = vtype::mask_loadu(vtype::zmm_max(), load_mask9, arr + 192);
        zmm[25] = vtype::mask_loadu(vtype::zmm_max(), load_mask10, arr + 200);
        zmm[26] = vtype::mask_loadu(vtype::zmm_max(), load_mask11, arr + 208);
        zmm[27] = vtype::mask_loadu(vtype::zmm_max(), load_mask12, arr + 216);
        zmm[28] = vtype::mask_loadu(vtype::zmm_max(), load_mask13, arr + 224);
        zmm[29] = vtype::mask_loadu(vtype::zmm_max(), load_mask14, arr + 232);
        zmm[30] = vtype::mask_loadu(vtype::zmm_max(), load_mask15, arr + 240);
        zmm[31] = vtype::mask_loadu(vtype::zmm_max(), load_mask16, arr + 248);
    }
    zmm[16] = sort_zmm_64bit<vtype>(zmm[16]);
    zmm[17] = sort_zmm_64bit<vtype>(zmm[17]);
    zmm[18] = sort_zmm_64bit<vtype>(zmm[18]);
    zmm[19] = sort_zmm_64bit<vtype>(zmm[19]);
    zmm[20] = sort_zmm_64bit<vtype>(zmm[20]);
    zmm[21] = sort_zmm_64bit<vtype>(zmm[21]);
    zmm[22] = sort_zmm_64bit<vtype>(zmm[22]);
    zmm[23] = sort_zmm_64bit<vtype>(zmm[23]);
    zmm[24] = sort_zmm_64bit<vtype>(zmm[24]);
    zmm[25] = sort_zmm_64bit<vtype>(zmm[25]);
    zmm[26] = sort_zmm_64bit<vtype>(zmm[26]);
    zmm[27] = sort_zmm_64bit<vtype>(zmm[27]);
    zmm[28] = sort_zmm_64bit<vtype>(zmm[28]);
    zmm[29] = sort_zmm_64bit<vtype>(zmm[29]);
    zmm[30] = sort_zmm_64bit<vtype>(zmm[30]);
    zmm[31] = sort_zmm_64bit<vtype>(zmm[31]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[0], zmm[1]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[2], zmm[3]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[4], zmm[5]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[6], zmm[7]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[8], zmm[9]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[10], zmm[11]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[12], zmm[13]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[14], zmm[15]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[16], zmm[17]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[18], zmm[19]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[20], zmm[21]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[22], zmm[23]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[24], zmm[25]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[26], zmm[27]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[28], zmm[29]);
    bitonic_merge_two_zmm_64bit<vtype>(zmm[30], zmm[31]);
    bitonic_merge_four_zmm_64bit<vtype>(zmm);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 4);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 8);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 12);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 16);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 20);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 24);
    bitonic_merge_four_zmm_64bit<vtype>(zmm + 28);
    bitonic_merge_eight_zmm_64bit<vtype>(zmm);
    bitonic_merge_eight_zmm_64bit<vtype>(zmm + 8);
    bitonic_merge_eight_zmm_64bit<vtype>(zmm + 16);
    bitonic_merge_eight_zmm_64bit<vtype>(zmm + 24);
    bitonic_merge_sixteen_zmm_64bit<vtype>(zmm);
    bitonic_merge_sixteen_zmm_64bit<vtype>(zmm + 16);
    bitonic_merge_32_zmm_64bit<vtype>(zmm);
    vtype::storeu(arr, zmm[0]);
    vtype::storeu(arr + 8, zmm[1]);
    vtype::storeu(arr + 16, zmm[2]);
    vtype::storeu(arr + 24, zmm[3]);
    vtype::storeu(arr + 32, zmm[4]);
    vtype::storeu(arr + 40, zmm[5]);
    vtype::storeu(arr + 48, zmm[6]);
    vtype::storeu(arr + 56, zmm[7]);
    vtype::storeu(arr + 64, zmm[8]);
    vtype::storeu(arr + 72, zmm[9]);
    vtype::storeu(arr + 80, zmm[10]);
    vtype::storeu(arr + 88, zmm[11]);
    vtype::storeu(arr + 96, zmm[12]);
    vtype::storeu(arr + 104, zmm[13]);
    vtype::storeu(arr + 112, zmm[14]);
    vtype::storeu(arr + 120, zmm[15]);
    vtype::mask_storeu(arr + 128, load_mask1, zmm[16]);
    vtype::mask_storeu(arr + 136, load_mask2, zmm[17]);
    vtype::mask_storeu(arr + 144, load_mask3, zmm[18]);
    vtype::mask_storeu(arr + 152, load_mask4, zmm[19]);
    vtype::mask_storeu(arr + 160, load_mask5, zmm[20]);
    vtype::mask_storeu(arr + 168, load_mask6, zmm[21]);
    vtype::mask_storeu(arr + 176, load_mask7, zmm[22]);
    vtype::mask_storeu(arr + 184, load_mask8, zmm[23]);
    if (N > 192) {
        vtype::mask_storeu(arr + 192, load_mask9, zmm[24]);
        vtype::mask_storeu(arr + 200, load_mask10, zmm[25]);
        vtype::mask_storeu(arr + 208, load_mask11, zmm[26]);
        vtype::mask_storeu(arr + 216, load_mask12, zmm[27]);
        vtype::mask_storeu(arr + 224, load_mask13, zmm[28]);
        vtype::mask_storeu(arr + 232, load_mask14, zmm[29]);
        vtype::mask_storeu(arr + 240, load_mask15, zmm[30]);
        vtype::mask_storeu(arr + 248, load_mask16, zmm[31]);
    }
}

template <typename vtype, typename type_t>
static void qsort_64bit_(type_t *arr, int64_t left, int64_t right,
                         int64_t max_iters) {
    /*
     * Resort to std::sort if quicksort isnt making any progress
     */
    if (max_iters <= 0) {
        std::sort(arr + left, arr + right + 1);
        return;
    }
    /*
     * Base case: use bitonic networks to sort arrays <= 128
     */
    if (right + 1 - left <= 256) {
        sort_256_64bit<vtype>(arr + left, (int32_t)(right + 1 - left));
        return;
    }

    type_t pivot = get_pivot_scalar<type_t>(arr, left, right);
    type_t smallest = vtype::type_max();
    type_t biggest = vtype::type_min();
    int64_t pivot_index = partition_avx512_unrolled<vtype, 8>(
        arr, left, right + 1, pivot, &smallest, &biggest, false);
    if (pivot != smallest)
        qsort_64bit_<vtype>(arr, left, pivot_index - 1, max_iters - 1);
    if (pivot != biggest)
        qsort_64bit_<vtype>(arr, pivot_index, right, max_iters - 1);
}

template <>
void inline avx512_qsort<int64_t>(int64_t *arr, int64_t fromIndex, int64_t toIndex) {
    int64_t arrsize = toIndex - fromIndex;
    if (arrsize > 1) {
        qsort_64bit_<zmm_vector<int64_t>, int64_t>(arr, fromIndex, toIndex - 1,
                                                   2 * (int64_t)log2(arrsize));
    }
}

template <>
void inline avx512_qsort<double>(double *arr, int64_t fromIndex, int64_t toIndex) {
    int64_t arrsize = toIndex - fromIndex;
    if (arrsize > 1) {
        qsort_64bit_<zmm_vector<double>, double>(arr, fromIndex, toIndex - 1,
                                                 2 * (int64_t)log2(arrsize));
    }
}

#endif  // AVX512_QSORT_64BIT
