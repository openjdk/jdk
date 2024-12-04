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

#include "library_entries.h"

#include "avx512-32bit-qsort.hpp"
#include "avx512-64bit-qsort.hpp"

//#define DLL_PUBLIC __attribute__((visibility("default")))
#define DLL_PUBLIC
#define INSERTION_SORT_THRESHOLD_32BIT 16
#define INSERTION_SORT_THRESHOLD_64BIT 20

DLL_PUBLIC
void avx512_sort_int(int32_t* array, int32_t from_index, int32_t to_index) {
  simd_fast_sort<zmm_vector<int32_t>, int32_t>(array, from_index, to_index, INSERTION_SORT_THRESHOLD_32BIT);
}

DLL_PUBLIC
void avx512_sort_long(int64_t* array, int32_t from_index, int32_t to_index) {
  simd_fast_sort<zmm_vector<int64_t>, int64_t>(array, from_index, to_index, INSERTION_SORT_THRESHOLD_64BIT);
}

DLL_PUBLIC
void avx512_sort_float(float* array, int32_t from_index, int32_t to_index) {
  simd_fast_sort<zmm_vector<float>, float>(array, from_index, to_index, INSERTION_SORT_THRESHOLD_32BIT);
}

DLL_PUBLIC
void avx512_sort_double(double* array, int32_t from_index, int32_t to_index) {
  simd_fast_sort<zmm_vector<double>, double>(array, from_index, to_index, INSERTION_SORT_THRESHOLD_64BIT);
}


DLL_PUBLIC
void avx512_partition_int(int32_t* array, int32_t from_index, int32_t to_index,
                          int32_t *pivot_indices, int32_t index_pivot1, int32_t index_pivot2) {
  simd_fast_partition<zmm_vector<int32_t>, int32_t>(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
}

DLL_PUBLIC
void avx512_partition_long(int64_t* array, int32_t from_index, int32_t to_index,
                           int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2) {
  simd_fast_partition<zmm_vector<int64_t>, int64_t>(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
}

DLL_PUBLIC
void avx512_partition_float(float* array, int32_t from_index, int32_t to_index,
                            int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2) {
  simd_fast_partition<zmm_vector<float>, float>(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
}

DLL_PUBLIC
void avx512_partition_double(double* array, int32_t from_index, int32_t to_index,
                             int32_t* pivot_indices, int32_t index_pivot1, int32_t index_pivot2) {
  simd_fast_partition<zmm_vector<double>, double>(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
}
#endif
