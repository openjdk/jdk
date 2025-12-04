/*
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

#ifndef AARCH64_SVE_OET_SORT_HPP
#define AARCH64_SVE_OET_SORT_HPP

#include "sve-config.hpp"
#include "sve-qsort.hpp"

template <typename vtype, typename type_t>
SVE_SORT_INLINE void sve_oet_sort(type_t* arr, arrsize_t from_index, arrsize_t to_index) {
    arrsize_t arr_num = to_index - from_index;
    const uint8_t numLanes = vtype::numlanes();

    for (int32_t i = 0; i < OET_SORT_THRESHOLD; i++) {
        // Odd-even pass: even i -> j starts at from_index
        //                odd i  -> j starts at from_index + 1
        int32_t j = from_index + i % 2;
        int32_t remaining = arr_num - (i % 2);

        while (remaining >= 2) {
            const int32_t vals_per_iteration = (remaining < (2 * numLanes)) ? remaining : 2 * numLanes;
            const int32_t num = vals_per_iteration / 2;
            vtype::oet_sort(&arr[j], num);

            j += vals_per_iteration;
            remaining -= vals_per_iteration;
        }
    }
}
#endif // AARCH64_SVE_OET_SORT_HPP
