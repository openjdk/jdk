/*
 * Copyright (c) 2023 Intel Corporation. All rights reserved.
 * Intel x86-simd-sort source code.
 *
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

#pragma GCC target("avx512dq", "avx512f")
#include "avx512-32bit-qsort.hpp"
#include "avx512-64bit-qsort.hpp"

#define DLL_PUBLIC __attribute__((visibility("default")))

extern "C" {

    DLL_PUBLIC void avx512_sort_int(int32_t *array_fromIndex, int64_t fromIndex,
                                    int64_t toIndex) {
        avx512_qsort<int32_t>(array_fromIndex, toIndex - fromIndex);
    }

    DLL_PUBLIC void avx512_sort_long(int64_t *array_fromIndex, int64_t fromIndex,
                                    int64_t toIndex) {
        avx512_qsort<int64_t>(array_fromIndex, toIndex - fromIndex);
    }

    DLL_PUBLIC void avx512_sort_float(float *array_fromIndex, int64_t fromIndex,
                                    int64_t toIndex) {
        avx512_qsort<float>(array_fromIndex, toIndex - fromIndex);
    }

    DLL_PUBLIC void avx512_sort_double(double *array_fromIndex, int64_t fromIndex,
                                    int64_t toIndex) {
        avx512_qsort<double>(array_fromIndex, toIndex - fromIndex);
    }

}
