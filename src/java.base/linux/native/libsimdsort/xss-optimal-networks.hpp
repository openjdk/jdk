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

// This implementation is based on x86-simd-sort(https://github.com/intel/x86-simd-sort) All of these sources
// files are generated from the optimal networks described in
// https://bertdobbelaere.github.io/sorting_networks.html

template <typename vtype, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void optimal_sort_4(reg_t *vecs) {
    COEX<vtype>(vecs[0], vecs[2]);
    COEX<vtype>(vecs[1], vecs[3]);

    COEX<vtype>(vecs[0], vecs[1]);
    COEX<vtype>(vecs[2], vecs[3]);

    COEX<vtype>(vecs[1], vecs[2]);
}

template <typename vtype, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void optimal_sort_8(reg_t *vecs) {
    COEX<vtype>(vecs[0], vecs[2]);
    COEX<vtype>(vecs[1], vecs[3]);
    COEX<vtype>(vecs[4], vecs[6]);
    COEX<vtype>(vecs[5], vecs[7]);

    COEX<vtype>(vecs[0], vecs[4]);
    COEX<vtype>(vecs[1], vecs[5]);
    COEX<vtype>(vecs[2], vecs[6]);
    COEX<vtype>(vecs[3], vecs[7]);

    COEX<vtype>(vecs[0], vecs[1]);
    COEX<vtype>(vecs[2], vecs[3]);
    COEX<vtype>(vecs[4], vecs[5]);
    COEX<vtype>(vecs[6], vecs[7]);

    COEX<vtype>(vecs[2], vecs[4]);
    COEX<vtype>(vecs[3], vecs[5]);

    COEX<vtype>(vecs[1], vecs[4]);
    COEX<vtype>(vecs[3], vecs[6]);

    COEX<vtype>(vecs[1], vecs[2]);
    COEX<vtype>(vecs[3], vecs[4]);
    COEX<vtype>(vecs[5], vecs[6]);
}

template <typename vtype, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void optimal_sort_16(reg_t *vecs) {
    COEX<vtype>(vecs[0], vecs[13]);
    COEX<vtype>(vecs[1], vecs[12]);
    COEX<vtype>(vecs[2], vecs[15]);
    COEX<vtype>(vecs[3], vecs[14]);
    COEX<vtype>(vecs[4], vecs[8]);
    COEX<vtype>(vecs[5], vecs[6]);
    COEX<vtype>(vecs[7], vecs[11]);
    COEX<vtype>(vecs[9], vecs[10]);

    COEX<vtype>(vecs[0], vecs[5]);
    COEX<vtype>(vecs[1], vecs[7]);
    COEX<vtype>(vecs[2], vecs[9]);
    COEX<vtype>(vecs[3], vecs[4]);
    COEX<vtype>(vecs[6], vecs[13]);
    COEX<vtype>(vecs[8], vecs[14]);
    COEX<vtype>(vecs[10], vecs[15]);
    COEX<vtype>(vecs[11], vecs[12]);

    COEX<vtype>(vecs[0], vecs[1]);
    COEX<vtype>(vecs[2], vecs[3]);
    COEX<vtype>(vecs[4], vecs[5]);
    COEX<vtype>(vecs[6], vecs[8]);
    COEX<vtype>(vecs[7], vecs[9]);
    COEX<vtype>(vecs[10], vecs[11]);
    COEX<vtype>(vecs[12], vecs[13]);
    COEX<vtype>(vecs[14], vecs[15]);

    COEX<vtype>(vecs[0], vecs[2]);
    COEX<vtype>(vecs[1], vecs[3]);
    COEX<vtype>(vecs[4], vecs[10]);
    COEX<vtype>(vecs[5], vecs[11]);
    COEX<vtype>(vecs[6], vecs[7]);
    COEX<vtype>(vecs[8], vecs[9]);
    COEX<vtype>(vecs[12], vecs[14]);
    COEX<vtype>(vecs[13], vecs[15]);

    COEX<vtype>(vecs[1], vecs[2]);
    COEX<vtype>(vecs[3], vecs[12]);
    COEX<vtype>(vecs[4], vecs[6]);
    COEX<vtype>(vecs[5], vecs[7]);
    COEX<vtype>(vecs[8], vecs[10]);
    COEX<vtype>(vecs[9], vecs[11]);
    COEX<vtype>(vecs[13], vecs[14]);

    COEX<vtype>(vecs[1], vecs[4]);
    COEX<vtype>(vecs[2], vecs[6]);
    COEX<vtype>(vecs[5], vecs[8]);
    COEX<vtype>(vecs[7], vecs[10]);
    COEX<vtype>(vecs[9], vecs[13]);
    COEX<vtype>(vecs[11], vecs[14]);

    COEX<vtype>(vecs[2], vecs[4]);
    COEX<vtype>(vecs[3], vecs[6]);
    COEX<vtype>(vecs[9], vecs[12]);
    COEX<vtype>(vecs[11], vecs[13]);

    COEX<vtype>(vecs[3], vecs[5]);
    COEX<vtype>(vecs[6], vecs[8]);
    COEX<vtype>(vecs[7], vecs[9]);
    COEX<vtype>(vecs[10], vecs[12]);

    COEX<vtype>(vecs[3], vecs[4]);
    COEX<vtype>(vecs[5], vecs[6]);
    COEX<vtype>(vecs[7], vecs[8]);
    COEX<vtype>(vecs[9], vecs[10]);
    COEX<vtype>(vecs[11], vecs[12]);

    COEX<vtype>(vecs[6], vecs[7]);
    COEX<vtype>(vecs[8], vecs[9]);
}

template <typename vtype, typename reg_t = typename vtype::reg_t>
X86_SIMD_SORT_FINLINE void optimal_sort_32(reg_t *vecs) {
    COEX<vtype>(vecs[0], vecs[1]);
    COEX<vtype>(vecs[2], vecs[3]);
    COEX<vtype>(vecs[4], vecs[5]);
    COEX<vtype>(vecs[6], vecs[7]);
    COEX<vtype>(vecs[8], vecs[9]);
    COEX<vtype>(vecs[10], vecs[11]);
    COEX<vtype>(vecs[12], vecs[13]);
    COEX<vtype>(vecs[14], vecs[15]);
    COEX<vtype>(vecs[16], vecs[17]);
    COEX<vtype>(vecs[18], vecs[19]);
    COEX<vtype>(vecs[20], vecs[21]);
    COEX<vtype>(vecs[22], vecs[23]);
    COEX<vtype>(vecs[24], vecs[25]);
    COEX<vtype>(vecs[26], vecs[27]);
    COEX<vtype>(vecs[28], vecs[29]);
    COEX<vtype>(vecs[30], vecs[31]);

    COEX<vtype>(vecs[0], vecs[2]);
    COEX<vtype>(vecs[1], vecs[3]);
    COEX<vtype>(vecs[4], vecs[6]);
    COEX<vtype>(vecs[5], vecs[7]);
    COEX<vtype>(vecs[8], vecs[10]);
    COEX<vtype>(vecs[9], vecs[11]);
    COEX<vtype>(vecs[12], vecs[14]);
    COEX<vtype>(vecs[13], vecs[15]);
    COEX<vtype>(vecs[16], vecs[18]);
    COEX<vtype>(vecs[17], vecs[19]);
    COEX<vtype>(vecs[20], vecs[22]);
    COEX<vtype>(vecs[21], vecs[23]);
    COEX<vtype>(vecs[24], vecs[26]);
    COEX<vtype>(vecs[25], vecs[27]);
    COEX<vtype>(vecs[28], vecs[30]);
    COEX<vtype>(vecs[29], vecs[31]);

    COEX<vtype>(vecs[0], vecs[4]);
    COEX<vtype>(vecs[1], vecs[5]);
    COEX<vtype>(vecs[2], vecs[6]);
    COEX<vtype>(vecs[3], vecs[7]);
    COEX<vtype>(vecs[8], vecs[12]);
    COEX<vtype>(vecs[9], vecs[13]);
    COEX<vtype>(vecs[10], vecs[14]);
    COEX<vtype>(vecs[11], vecs[15]);
    COEX<vtype>(vecs[16], vecs[20]);
    COEX<vtype>(vecs[17], vecs[21]);
    COEX<vtype>(vecs[18], vecs[22]);
    COEX<vtype>(vecs[19], vecs[23]);
    COEX<vtype>(vecs[24], vecs[28]);
    COEX<vtype>(vecs[25], vecs[29]);
    COEX<vtype>(vecs[26], vecs[30]);
    COEX<vtype>(vecs[27], vecs[31]);

    COEX<vtype>(vecs[0], vecs[8]);
    COEX<vtype>(vecs[1], vecs[9]);
    COEX<vtype>(vecs[2], vecs[10]);
    COEX<vtype>(vecs[3], vecs[11]);
    COEX<vtype>(vecs[4], vecs[12]);
    COEX<vtype>(vecs[5], vecs[13]);
    COEX<vtype>(vecs[6], vecs[14]);
    COEX<vtype>(vecs[7], vecs[15]);
    COEX<vtype>(vecs[16], vecs[24]);
    COEX<vtype>(vecs[17], vecs[25]);
    COEX<vtype>(vecs[18], vecs[26]);
    COEX<vtype>(vecs[19], vecs[27]);
    COEX<vtype>(vecs[20], vecs[28]);
    COEX<vtype>(vecs[21], vecs[29]);
    COEX<vtype>(vecs[22], vecs[30]);
    COEX<vtype>(vecs[23], vecs[31]);

    COEX<vtype>(vecs[0], vecs[16]);
    COEX<vtype>(vecs[1], vecs[8]);
    COEX<vtype>(vecs[2], vecs[4]);
    COEX<vtype>(vecs[3], vecs[12]);
    COEX<vtype>(vecs[5], vecs[10]);
    COEX<vtype>(vecs[6], vecs[9]);
    COEX<vtype>(vecs[7], vecs[14]);
    COEX<vtype>(vecs[11], vecs[13]);
    COEX<vtype>(vecs[15], vecs[31]);
    COEX<vtype>(vecs[17], vecs[24]);
    COEX<vtype>(vecs[18], vecs[20]);
    COEX<vtype>(vecs[19], vecs[28]);
    COEX<vtype>(vecs[21], vecs[26]);
    COEX<vtype>(vecs[22], vecs[25]);
    COEX<vtype>(vecs[23], vecs[30]);
    COEX<vtype>(vecs[27], vecs[29]);

    COEX<vtype>(vecs[1], vecs[2]);
    COEX<vtype>(vecs[3], vecs[5]);
    COEX<vtype>(vecs[4], vecs[8]);
    COEX<vtype>(vecs[6], vecs[22]);
    COEX<vtype>(vecs[7], vecs[11]);
    COEX<vtype>(vecs[9], vecs[25]);
    COEX<vtype>(vecs[10], vecs[12]);
    COEX<vtype>(vecs[13], vecs[14]);
    COEX<vtype>(vecs[17], vecs[18]);
    COEX<vtype>(vecs[19], vecs[21]);
    COEX<vtype>(vecs[20], vecs[24]);
    COEX<vtype>(vecs[23], vecs[27]);
    COEX<vtype>(vecs[26], vecs[28]);
    COEX<vtype>(vecs[29], vecs[30]);

    COEX<vtype>(vecs[1], vecs[17]);
    COEX<vtype>(vecs[2], vecs[18]);
    COEX<vtype>(vecs[3], vecs[19]);
    COEX<vtype>(vecs[4], vecs[20]);
    COEX<vtype>(vecs[5], vecs[10]);
    COEX<vtype>(vecs[7], vecs[23]);
    COEX<vtype>(vecs[8], vecs[24]);
    COEX<vtype>(vecs[11], vecs[27]);
    COEX<vtype>(vecs[12], vecs[28]);
    COEX<vtype>(vecs[13], vecs[29]);
    COEX<vtype>(vecs[14], vecs[30]);
    COEX<vtype>(vecs[21], vecs[26]);

    COEX<vtype>(vecs[3], vecs[17]);
    COEX<vtype>(vecs[4], vecs[16]);
    COEX<vtype>(vecs[5], vecs[21]);
    COEX<vtype>(vecs[6], vecs[18]);
    COEX<vtype>(vecs[7], vecs[9]);
    COEX<vtype>(vecs[8], vecs[20]);
    COEX<vtype>(vecs[10], vecs[26]);
    COEX<vtype>(vecs[11], vecs[23]);
    COEX<vtype>(vecs[13], vecs[25]);
    COEX<vtype>(vecs[14], vecs[28]);
    COEX<vtype>(vecs[15], vecs[27]);
    COEX<vtype>(vecs[22], vecs[24]);

    COEX<vtype>(vecs[1], vecs[4]);
    COEX<vtype>(vecs[3], vecs[8]);
    COEX<vtype>(vecs[5], vecs[16]);
    COEX<vtype>(vecs[7], vecs[17]);
    COEX<vtype>(vecs[9], vecs[21]);
    COEX<vtype>(vecs[10], vecs[22]);
    COEX<vtype>(vecs[11], vecs[19]);
    COEX<vtype>(vecs[12], vecs[20]);
    COEX<vtype>(vecs[14], vecs[24]);
    COEX<vtype>(vecs[15], vecs[26]);
    COEX<vtype>(vecs[23], vecs[28]);
    COEX<vtype>(vecs[27], vecs[30]);

    COEX<vtype>(vecs[2], vecs[5]);
    COEX<vtype>(vecs[7], vecs[8]);
    COEX<vtype>(vecs[9], vecs[18]);
    COEX<vtype>(vecs[11], vecs[17]);
    COEX<vtype>(vecs[12], vecs[16]);
    COEX<vtype>(vecs[13], vecs[22]);
    COEX<vtype>(vecs[14], vecs[20]);
    COEX<vtype>(vecs[15], vecs[19]);
    COEX<vtype>(vecs[23], vecs[24]);
    COEX<vtype>(vecs[26], vecs[29]);

    COEX<vtype>(vecs[2], vecs[4]);
    COEX<vtype>(vecs[6], vecs[12]);
    COEX<vtype>(vecs[9], vecs[16]);
    COEX<vtype>(vecs[10], vecs[11]);
    COEX<vtype>(vecs[13], vecs[17]);
    COEX<vtype>(vecs[14], vecs[18]);
    COEX<vtype>(vecs[15], vecs[22]);
    COEX<vtype>(vecs[19], vecs[25]);
    COEX<vtype>(vecs[20], vecs[21]);
    COEX<vtype>(vecs[27], vecs[29]);

    COEX<vtype>(vecs[5], vecs[6]);
    COEX<vtype>(vecs[8], vecs[12]);
    COEX<vtype>(vecs[9], vecs[10]);
    COEX<vtype>(vecs[11], vecs[13]);
    COEX<vtype>(vecs[14], vecs[16]);
    COEX<vtype>(vecs[15], vecs[17]);
    COEX<vtype>(vecs[18], vecs[20]);
    COEX<vtype>(vecs[19], vecs[23]);
    COEX<vtype>(vecs[21], vecs[22]);
    COEX<vtype>(vecs[25], vecs[26]);

    COEX<vtype>(vecs[3], vecs[5]);
    COEX<vtype>(vecs[6], vecs[7]);
    COEX<vtype>(vecs[8], vecs[9]);
    COEX<vtype>(vecs[10], vecs[12]);
    COEX<vtype>(vecs[11], vecs[14]);
    COEX<vtype>(vecs[13], vecs[16]);
    COEX<vtype>(vecs[15], vecs[18]);
    COEX<vtype>(vecs[17], vecs[20]);
    COEX<vtype>(vecs[19], vecs[21]);
    COEX<vtype>(vecs[22], vecs[23]);
    COEX<vtype>(vecs[24], vecs[25]);
    COEX<vtype>(vecs[26], vecs[28]);

    COEX<vtype>(vecs[3], vecs[4]);
    COEX<vtype>(vecs[5], vecs[6]);
    COEX<vtype>(vecs[7], vecs[8]);
    COEX<vtype>(vecs[9], vecs[10]);
    COEX<vtype>(vecs[11], vecs[12]);
    COEX<vtype>(vecs[13], vecs[14]);
    COEX<vtype>(vecs[15], vecs[16]);
    COEX<vtype>(vecs[17], vecs[18]);
    COEX<vtype>(vecs[19], vecs[20]);
    COEX<vtype>(vecs[21], vecs[22]);
    COEX<vtype>(vecs[23], vecs[24]);
    COEX<vtype>(vecs[25], vecs[26]);
    COEX<vtype>(vecs[27], vecs[28]);
}
