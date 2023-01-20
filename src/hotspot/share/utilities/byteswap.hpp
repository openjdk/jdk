/*
 * Copyright (c) 2023, Google and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_BYTESWAP_HPP
#define SHARE_UTILITIES_BYTESWAP_HPP

// Byte swapping for 8-bit, 16-bit, 32-bit, and 64-bit integers.

// byteswap<T>()
//
// Reverses the bytes for the value of the integer type T. Partially compatible with std::byteswap
// introduced in C++23.

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include <cstddef>
#include <cstdint>
#include <type_traits>

template <typename T>
struct CanByteswapImpl final
    : public std::integral_constant<bool, (std::is_integral<T>::value && sizeof(T) <= 8)> {};

template <typename T, size_t N = sizeof(T)>
struct ByteswapImpl;

template <typename T, ENABLE_IF(CanByteswapImpl<T>::value)>
ALWAYSINLINE T byteswap(T x) {
  using U = std::make_unsigned_t<T>;
  STATIC_ASSERT(sizeof(T) == sizeof(U));
  return static_cast<T>(ByteswapImpl<U>{}(static_cast<U>(x)));
}

/*****************************************************************************
 * Implementation
 *****************************************************************************/

// We support 8-bit integer types to be compatible with C++23's std::byteswap.
template <typename T>
struct ByteswapImpl<T, 1> final {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == 1);

  ALWAYSINLINE T operator()(T x) const {
    return x;
  }
};

/*****************************************************************************
 * Fallback
 *****************************************************************************/

template <typename T, size_t N = sizeof(T)>
struct ByteswapFallbackImpl;

// We support 8-bit integer types to be compatible with C++23's std::byteswap.
template <typename T>
struct ByteswapFallbackImpl<T, 1> {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == 1);

  ALWAYSINLINE T operator()(T x) const {
    return x;
  }
};

template <typename T>
struct ByteswapFallbackImpl<T, 2> {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == 2);

  ALWAYSINLINE uint16_t operator()(uint16_t x) const {
    return (((x & UINT16_C(0x00ff)) << 8) | ((x & UINT16_C(0xff00)) >> 8));
  }
};

template <typename T>
struct ByteswapFallbackImpl<T, 4> {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == 4);

  ALWAYSINLINE uint32_t operator()(uint32_t x) const {
    return (((x & UINT32_C(0x000000ff)) << 24) | ((x & UINT32_C(0x0000ff00)) << 8) |
            ((x & UINT32_C(0x00ff0000)) >> 8) | ((x & UINT32_C(0xff000000)) >> 24));
  }
};

template <typename T>
struct ByteswapFallbackImpl<T, 8> {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == 8);

  ALWAYSINLINE uint64_t operator()(uint64_t x) const {
    return (((x & UINT64_C(0x00000000000000ff)) << 56) | ((x & UINT64_C(0x000000000000ff00)) << 40) |
            ((x & UINT64_C(0x0000000000ff0000)) << 24) | ((x & UINT64_C(0x00000000ff000000)) << 8) |
            ((x & UINT64_C(0x000000ff00000000)) >> 8) | ((x & UINT64_C(0x0000ff0000000000)) >> 24) |
            ((x & UINT64_C(0x00ff000000000000)) >> 40) | ((x & UINT64_C(0xff00000000000000)) >> 56));
  }
};

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc)

#if defined(__clang__)

// Unlike GCC, Clang is willing to inline the generic implementation of __builtin_bswap when
// architecture support is unavailable in -O2. This ensures we avoid the function call to libgcc.
// Clang is able to recognize the fallback implementation as byteswapping, but not on every
// architecture unlike GCC. This suggests the optimization pass for GCC that recognizes byteswapping
// is architecture agnostic, while for Clang it is not.

template <typename T>
struct ByteswapImpl<T, 2> final {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == sizeof(uint16_t));

  ALWAYSINLINE T operator()(T x) const {
    return static_cast<T>(__builtin_bswap16(static_cast<uint16_t>(x)));
  }
};

template <typename T>
struct ByteswapImpl<T, 4> final {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == sizeof(uint32_t));

  ALWAYSINLINE T operator()(T x) const {
    return static_cast<T>(__builtin_bswap32(static_cast<uint32_t>(x)));
  }
};

template <typename T>
struct ByteswapImpl<T, 8> final {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(T) == sizeof(uint64_t));

  ALWAYSINLINE T operator()(T x) const {
    return static_cast<T>(__builtin_bswap64(static_cast<uint64_t>(x)));
  }
};

#else

// We do not use __builtin_bswap and friends for GCC. Unfortunately on architectures that do not
// have a byteswap instruction (i.e. RISC-V), GCC emits a function call to libgcc regardless of
// optimization options, even when the generic implementation is, for example, less than 20
// instructions. GCC is however able to recognize the fallback as byteswapping regardless of
// architecture and appropriately replaces the code in -O2 with the appropriate
// architecture-specific byteswap instruction, if available. If it is not available, GCC emits the
// exact same implementation that underpins its __builtin_bswap in libgcc as there is really only
// one way to implement it, as we have in fallback.

template <typename T, size_t N>
struct ByteswapImpl final : public ByteswapFallbackImpl<T, N> {};

#endif

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

#include <stdlib.h>

#pragma intrinsic(_byteswap_ushort)
#pragma intrinsic(_byteswap_ulong)
#pragma intrinsic(_byteswap_uint64)

template <typename T>
struct ByteswapImpl<T, 2> final {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(unsigned short) == sizeof(2));
  STATIC_ASSERT(sizeof(T) == sizeof(unsigned short));

  ALWAYSINLINE T operator()(T x) const {
    return static_cast<T>(_byteswap_ushort(static_cast<unsigned short>(x)));
  }
};

template <typename T>
struct ByteswapImpl<T, 4> final {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(unsigned long) == sizeof(4));
  STATIC_ASSERT(sizeof(T) == sizeof(unsigned long));

  ALWAYSINLINE T operator()(T x) const {
    return static_cast<T>(_byteswap_ulong(static_cast<unsigned long>(x)));
  }
};

template <typename T>
struct ByteswapImpl<T, 8> final {
  STATIC_ASSERT(CanByteswapImpl<T>::value);
  STATIC_ASSERT(sizeof(unsigned __int64) == sizeof(8));
  STATIC_ASSERT(sizeof(T) == sizeof(unsigned __int64));

  ALWAYSINLINE T operator()(T x) const {
    return static_cast<T>(_byteswap_uint64(static_cast<unsigned __int64>(x)));
  }
};

/*****************************************************************************
 * IBM XL C/C++
 *****************************************************************************/
#elif defined(TARGET_COMPILER_xlc)

// To our knowledge XL C/C++ does not have a compiler intrinsic for byteswapping.

template <typename T, size_t N>
struct ByteswapImpl final : public ByteswapFallbackImpl<T, N> {};

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else

#error Unknown toolchain.

#endif

#endif // SHARE_UTILITIES_BYTESWAP_HPP
