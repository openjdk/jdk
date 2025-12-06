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

#ifndef AARCH64_SVE_CONFIG_HPP
#define AARCH64_SVE_CONFIG_HPP

#include <cstddef>
#include <cstdint>
#include <limits>
#include "simdsort-support.hpp"

#define SIMD_SORT_INFINITYF std::numeric_limits<float>::infinity()
#define SIMD_SORT_MAX_INT32 std::numeric_limits<int32_t>::max()
#define SIMD_SORT_MIN_INT32 std::numeric_limits<int32_t>::min()

#if defined(__GNUC__)
  #define SVE_SORT_INLINE  static inline
  #define SVE_SORT_FINLINE static inline __attribute__((always_inline))
#else
  #define SVE_SORT_INLINE  static
  #define SVE_SORT_FINLINE static
#endif

#ifndef DLL_PUBLIC
  #define DLL_PUBLIC __attribute__((visibility("default")))
#endif

using arrsize_t = std::size_t;

#ifndef OET_SORT_THRESHOLD
  #define OET_SORT_THRESHOLD 8
#endif

#endif // AARCH64_SVE_CONFIG_HPP
