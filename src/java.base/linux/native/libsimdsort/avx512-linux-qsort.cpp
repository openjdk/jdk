/*
 * Copyright (c) 2023 Intel Corporation. All rights reserved.
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
#include "simdsort-support.hpp"
#ifdef __SIMDSORT_SUPPORTED_LINUX

#pragma GCC target("avx512dq", "avx512f")
#include "avx512-32bit-qsort.hpp"
#include "avx512-64bit-qsort.hpp"
#include "classfile_constants.h"


#define DLL_PUBLIC __attribute__((visibility("default")))
#define INSERTION_SORT_THRESHOLD_32BIT 16
#define INSERTION_SORT_THRESHOLD_64BIT 20

extern "C" {

    DLL_PUBLIC void avx512_sort(void *array, int elem_type, int32_t from_index, int32_t to_index) {
        switch(elem_type) {
            case JVM_T_INT:
                avx512_fast_sort((int32_t*)array, from_index, to_index, INSERTION_SORT_THRESHOLD_32BIT);
                break;
            case JVM_T_LONG:
                avx512_fast_sort((int64_t*)array, from_index, to_index, INSERTION_SORT_THRESHOLD_64BIT);
                break;
            case JVM_T_FLOAT:
                avx512_fast_sort((float*)array, from_index, to_index, INSERTION_SORT_THRESHOLD_32BIT);
                break;
            case JVM_T_DOUBLE:
                avx512_fast_sort((double*)array, from_index, to_index, INSERTION_SORT_THRESHOLD_64BIT);
                break;
            default:
                assert(false, "Unexpected type");
        }
    }

    DLL_PUBLIC void avx512_partition(void *array, int elem_type, int32_t from_index, int32_t to_index, int32_t *pivot_indices, int32_t index_pivot1, int32_t index_pivot2) {
        switch(elem_type) {
            case JVM_T_INT:
                avx512_fast_partition((int32_t*)array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
                break;
            case JVM_T_LONG:
                avx512_fast_partition((int64_t*)array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
                break;
            case JVM_T_FLOAT:
                avx512_fast_partition((float*)array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
                break;
            case JVM_T_DOUBLE:
                avx512_fast_partition((double*)array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
                break;
            default:
                assert(false, "Unexpected type");
        }
    }

}

#endif
