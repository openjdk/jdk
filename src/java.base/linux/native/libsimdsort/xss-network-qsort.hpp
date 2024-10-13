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

#ifndef XSS_NETWORK_QSORT
#define XSS_NETWORK_QSORT

#include "xss-common-qsort.h"
#include "xss-optimal-networks.hpp"

template <typename vtype, int numVecs, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void bitonic_sort_n_vec(reg_t *regs) {
    if constexpr (numVecs == 1) {
        UNUSED(regs);
        return;
    } else if constexpr (numVecs == 2) {
        COEX<vtype>(regs[0], regs[1]);
    } else if constexpr (numVecs == 4) {
        optimal_sort_4<vtype>(regs);
    } else if constexpr (numVecs == 8) {
        optimal_sort_8<vtype>(regs);
    } else if constexpr (numVecs == 16) {
        optimal_sort_16<vtype>(regs);
    } else if constexpr (numVecs == 32) {
        optimal_sort_32<vtype>(regs);
    } else {
        static_assert(numVecs == -1, "should not reach here");
    }
}

/*
 * Swizzle ops explained:
 * swap_n<scale>: swap neighbouring blocks of size <scale/2> within block of
 * size <scale> reg i        = [7,6,5,4,3,2,1,0] swap_n<2>:   =
 * [[6,7],[4,5],[2,3],[0,1]] swap_n<4>:   = [[5,4,7,6],[1,0,3,2]] swap_n<8>:   =
 * [[3,2,1,0,7,6,5,4]] reverse_n<scale>: reverse elements within block of size
 * <scale> reg i        = [7,6,5,4,3,2,1,0] rev_n<2>:    =
 * [[6,7],[4,5],[2,3],[0,1]] rev_n<4>:    = [[4,5,6,7],[0,1,2,3]] rev_n<8>:    =
 * [[0,1,2,3,4,5,6,7]] merge_n<scale>: merge blocks of <scale/2> elements from
 * two regs reg b,a      = [a,a,a,a,a,a,a,a], [b,b,b,b,b,b,b,b] merge_n<2>   =
 * [a,b,a,b,a,b,a,b] merge_n<4>   = [a,a,b,b,a,a,b,b] merge_n<8>   =
 * [a,a,a,a,b,b,b,b]
 */

template <typename vtype, int numVecs, int scale, bool first = true>
X86_SIMD_SORT_FINLINE void internal_merge_n_vec(typename vtype::reg_t *reg) {
    using reg_t = typename vtype::reg_t;
    using swizzle = typename vtype::swizzle_ops;
    if constexpr (scale <= 1) {
        UNUSED(reg);
        return;
    } else {
        if constexpr (first) {
            // Use reverse then merge
            X86_SIMD_SORT_UNROLL_LOOP(64)
            for (int i = 0; i < numVecs; i++) {
                reg_t &v = reg[i];
                reg_t rev = swizzle::template reverse_n<vtype, scale>(v);
                COEX<vtype>(rev, v);
                v = swizzle::template merge_n<vtype, scale>(v, rev);
            }
        } else {
            // Use swap then merge
            X86_SIMD_SORT_UNROLL_LOOP(64)
            for (int i = 0; i < numVecs; i++) {
                reg_t &v = reg[i];
                reg_t swap = swizzle::template swap_n<vtype, scale>(v);
                COEX<vtype>(swap, v);
                v = swizzle::template merge_n<vtype, scale>(v, swap);
            }
        }
        internal_merge_n_vec<vtype, numVecs, scale / 2, false>(reg);
    }
}

template <typename vtype, int numVecs, int scale,
          typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void merge_substep_n_vec(reg_t *regs) {
    using swizzle = typename vtype::swizzle_ops;
    if constexpr (numVecs <= 1) {
        UNUSED(regs);
        return;
    }

    // Reverse upper half of vectors
    X86_SIMD_SORT_UNROLL_LOOP(64)
    for (int i = numVecs / 2; i < numVecs; i++) {
        regs[i] = swizzle::template reverse_n<vtype, scale>(regs[i]);
    }
    // Do compare exchanges
    X86_SIMD_SORT_UNROLL_LOOP(64)
    for (int i = 0; i < numVecs / 2; i++) {
        COEX<vtype>(regs[i], regs[numVecs - 1 - i]);
    }

    merge_substep_n_vec<vtype, numVecs / 2, scale>(regs);
    merge_substep_n_vec<vtype, numVecs / 2, scale>(regs + numVecs / 2);
}

template <typename vtype, int numVecs, int scale,
          typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void merge_step_n_vec(reg_t *regs) {
    // Do cross vector merges
    merge_substep_n_vec<vtype, numVecs, scale>(regs);

    // Do internal vector merges
    internal_merge_n_vec<vtype, numVecs, scale>(regs);
}

template <typename vtype, int numVecs, int numPer = 2,
          typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void merge_n_vec(reg_t *regs) {
    if constexpr (numPer > vtype::numlanes) {
        UNUSED(regs);
        return;
    } else {
        merge_step_n_vec<vtype, numVecs, numPer>(regs);
        merge_n_vec<vtype, numVecs, numPer * 2>(regs);
    }
}

template <typename vtype, int numVecs, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_INLINE void sort_n_vec(typename vtype::type_t *arr, int N) {
    static_assert(numVecs > 0, "numVecs should be > 0");
    if constexpr (numVecs > 1) {
        if (N * 2 <= numVecs * vtype::numlanes) {
            sort_n_vec<vtype, numVecs / 2>(arr, N);
            return;
        }
    }

    reg_t vecs[numVecs];

    // Generate masks for loading and storing
    typename vtype::opmask_t ioMasks[numVecs - numVecs / 2];
    X86_SIMD_SORT_UNROLL_LOOP(64)
    for (int i = numVecs / 2, j = 0; i < numVecs; i++, j++) {
        uint64_t num_to_read =
            std::min((uint64_t)std::max(0, N - i * vtype::numlanes),
                     (uint64_t)vtype::numlanes);
        ioMasks[j] = vtype::get_partial_loadmask(num_to_read);
    }

    // Unmasked part of the load
    X86_SIMD_SORT_UNROLL_LOOP(64)
    for (int i = 0; i < numVecs / 2; i++) {
        vecs[i] = vtype::loadu(arr + i * vtype::numlanes);
    }
    // Masked part of the load
    X86_SIMD_SORT_UNROLL_LOOP(64)
    for (int i = numVecs / 2, j = 0; i < numVecs; i++, j++) {
        vecs[i] = vtype::mask_loadu(vtype::zmm_max(), ioMasks[j],
                                    arr + i * vtype::numlanes);
    }

    /* Run the initial sorting network to sort the columns of the [numVecs x
     * num_lanes] matrix
     */
    bitonic_sort_n_vec<vtype, numVecs>(vecs);

    // Merge the vectors using bitonic merging networks
    merge_n_vec<vtype, numVecs>(vecs);

    // Unmasked part of the store
    X86_SIMD_SORT_UNROLL_LOOP(64)
    for (int i = 0; i < numVecs / 2; i++) {
        vtype::storeu(arr + i * vtype::numlanes, vecs[i]);
    }
    // Masked part of the store
    X86_SIMD_SORT_UNROLL_LOOP(64)
    for (int i = numVecs / 2, j = 0; i < numVecs; i++, j++) {
        vtype::mask_storeu(arr + i * vtype::numlanes, ioMasks[j], vecs[i]);
    }
}

template <typename vtype, int maxN>
X86_SIMD_SORT_INLINE void sort_n(typename vtype::type_t *arr, int N) {
    constexpr int numVecs = maxN / vtype::numlanes;
    constexpr bool isMultiple = (maxN == (vtype::numlanes * numVecs));
    constexpr bool powerOfTwo = (numVecs != 0 && !(numVecs & (numVecs - 1)));
    static_assert(powerOfTwo == true && isMultiple == true,
                  "maxN must be vtype::numlanes times a power of 2");

    sort_n_vec<vtype, numVecs>(arr, N);
}
#endif
