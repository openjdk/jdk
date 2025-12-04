/*
 * Copyright (c) 2021, 2023, Intel Corporation. All rights reserved.
 * Copyright (c) 2021 Serge Sans Paille. All rights reserved.
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

#ifndef AARCH64_SVE_PIVOT_SELECTION_HPP
#define AARCH64_SVE_PIVOT_SELECTION_HPP

#include <algorithm>
#include "sve-config.hpp"

/* <TODO> The current pivot selection method follows median-of-three method.
 * Possible improvements could be the usage of sorting network (Compare and exchange sorting)
 * for larger arrays.
 */

template <typename vtype, typename type_t>
static inline type_t get_pivot_blocks(type_t* arr, const arrsize_t left, const arrsize_t right) {
  const arrsize_t len = right - left;
  if (len < 64) return arr[left];

  const arrsize_t mid = left + (len / 2);
  const type_t a = arr[left];
  const type_t b = arr[mid];
  const type_t c = arr[right - 1];

  const type_t min_ab = std::min(a, b);
  const type_t max_ab = std::max(a, b);

  return std::min(max_ab, std::max(min_ab, c));
}

#endif // AARCH64_SVE_PIVOT_SELECTION_HPP
