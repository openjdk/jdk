/*
 * Copyright (c) 2023, 2024, Google and/or its affiliates. All rights reserved.
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

#include "metaprogramming/enableIf.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cstddef>
#include <cstdint>
#include <type_traits>

template <typename T, size_t N = sizeof(T)>
struct ByteswapImpl;

// T byteswap<T>(T)
//
// Reverses the bytes for the value of the integer type T. Partially compatible with std::byteswap
// introduced in C++23.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
inline T byteswap(T x) {
  using U = std::make_unsigned_t<T>;
  return static_cast<T>(ByteswapImpl<U>{}(static_cast<U>(x)));
}

// We support 8-bit integer types to be compatible with C++23's std::byteswap.
template <typename T>
struct ByteswapImpl<T, 1> {
  inline constexpr T operator()(T x) const {
    return x;
  }
};

/*****************************************************************************
 * Fallback
 *****************************************************************************/

template <typename T, size_t N = sizeof(T)>
struct ByteswapFallbackImpl;

template <typename T>
struct ByteswapFallbackImpl<T, 2> {
  inline constexpr uint16_t operator()(uint16_t x) const {
    return (((x & UINT16_C(0x00ff)) << 8) | ((x & UINT16_C(0xff00)) >> 8));
  }
};

template <typename T>
struct ByteswapFallbackImpl<T, 4> {
  inline constexpr uint32_t operator()(uint32_t x) const {
    return (((x & UINT32_C(0x000000ff)) << 24) | ((x & UINT32_C(0x0000ff00)) << 8) |
            ((x & UINT32_C(0x00ff0000)) >> 8)  | ((x & UINT32_C(0xff000000)) >> 24));
  }
};

template <typename T>
struct ByteswapFallbackImpl<T, 8> {
  inline constexpr uint64_t operator()(uint64_t x) const {
    return (((x & UINT64_C(0x00000000000000ff)) << 56) | ((x & UINT64_C(0x000000000000ff00)) << 40) |
            ((x & UINT64_C(0x0000000000ff0000)) << 24) | ((x & UINT64_C(0x00000000ff000000)) << 8) |
            ((x & UINT64_C(0x000000ff00000000)) >> 8)  | ((x & UINT64_C(0x0000ff0000000000)) >> 24) |
            ((x & UINT64_C(0x00ff000000000000)) >> 40) | ((x & UINT64_C(0xff00000000000000)) >> 56));
  }
};

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc)

#if defined(__clang__) || defined(ASSERT)

// Unlike GCC, Clang is willing to inline the generic implementation of __builtin_bswap when
// architecture support is unavailable in -O2. This ensures we avoid the function call to libgcc.
// Clang is able to recognize the fallback implementation as byteswapping, but not on every
// architecture unlike GCC. This suggests the optimization pass for GCC that recognizes byteswapping
// is architecture agnostic, while for Clang it is not.

template <typename T>
struct ByteswapImpl<T, 2> {
  inline constexpr uint16_t operator()(uint16_t x) const {
    return __builtin_bswap16(x);
  }
};

template <typename T>
struct ByteswapImpl<T, 4> {
  inline constexpr uint32_t operator()(uint32_t x) const {
    return __builtin_bswap32(x);
  }
};

template <typename T>
struct ByteswapImpl<T, 8> {
  inline constexpr uint64_t operator()(uint64_t x) const {
    return __builtin_bswap64(x);
  }
};

#else

// We do not use __builtin_bswap and friends for GCC in release builds. Unfortunately on
// architectures that do not have a byteswap instruction (i.e. RISC-V), GCC emits a function call to
// libgcc regardless of optimization options, even when the generic implementation is, for example,
// less than 20 instructions. GCC is however able to recognize the fallback as byteswapping
// regardless of architecture and appropriately replaces the code in -O2 with the appropriate
// architecture-specific byteswap instruction, if available. If it is not available, GCC emits the
// exact same implementation that underpins its __builtin_bswap in libgcc as there is really only
// one way to implement it, as we have in fallback.

template <typename T, size_t N>
struct ByteswapImpl : public ByteswapFallbackImpl<T, N> {};

#endif

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

#include <cstdlib>

#pragma intrinsic(_byteswap_ushort)
#pragma intrinsic(_byteswap_ulong)
#pragma intrinsic(_byteswap_uint64)

template <typename T>
struct ByteswapImpl<T, 2> {
  inline unsigned short operator()(unsigned short x) const {
    return _byteswap_ushort(x);
  }
};

template <typename T>
struct ByteswapImpl<T, 4> {
  inline unsigned long operator()(unsigned long x) const {
    return _byteswap_ulong(x);
  }
};

template <typename T>
struct ByteswapImpl<T, 8> {
  inline unsigned __int64 operator()(unsigned __int64 x) const {
    return _byteswap_uint64(x);
  }
};

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else

#error Unknown toolchain.

#endif

#endif // SHARE_UTILITIES_BYTESWAP_HPP
