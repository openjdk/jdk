/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_COUNTTRAILINGZEROS_HPP
#define SHARE_VM_UTILITIES_COUNTTRAILINGZEROS_HPP

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// unsigned count_trailing_zeros(uintx x)
// Return the number of trailing zeros in x, e.g. the zero-based index
// of the least significant set bit in x.
// Precondition: x != 0.

// Dispatch on toolchain to select implementation.

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc)

inline unsigned count_trailing_zeros(uintx x) {
  STATIC_ASSERT(sizeof(unsigned long) == sizeof(uintx));
  assert(x != 0, "precondition");
  return __builtin_ctzl(x);
}

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

#include <intrin.h>

#ifdef _LP64
#pragma intrinsic(_BitScanForward64)
#else
#pragma intrinsic(_BitScanForward)
#endif

inline unsigned count_trailing_zeros(uintx x) {
  assert(x != 0, "precondition");
  unsigned long index;
#ifdef _LP64
  _BitScanForward64(&index, x);
#else
  _BitScanForward(&index, x);
#endif
  return index;
}

/*****************************************************************************
 * IBM XL C/C++
 *****************************************************************************/
#elif defined(TARGET_COMPILER_xlc)

#include <builtins.h>

inline unsigned count_trailing_zeros(uintx x) {
  assert(x != 0, "precondition");
#ifdef _LP64
  return __cnttz8(x);
#else
  return __cnttz4(x);
#endif
}

/*****************************************************************************
 * Oracle Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_sparcWorks)

// No compiler built-in / intrinsic, so use inline assembler.

#include "utilities/macros.hpp"

#include OS_CPU_HEADER(count_trailing_zeros)

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else
#error Unknown TARGET_COMPILER

#endif // Toolchain dispatch

#endif // include guard
