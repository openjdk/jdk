/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_BIT_CAST_HPP
#define SHARE_UTILITIES_BIT_CAST_HPP

// Convert between two types while preserving bit representation.

// To bit_cast<To>(From)
//
// Casts from type From to type To without changing the underlying bit representation. This is
// partially compatible with std::bit_cast introduced in C++20, but is more restrictive on the type
// of conversions allowed.

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

#include <cstdint>
#include <cstring>
#include <type_traits>

template <typename To, typename From>
using CanBitCastImpl = std::integral_constant<bool, (sizeof(To) == sizeof(From) &&
                                                     std::is_trivially_copyable<To>::value &&
                                                     std::is_trivially_copyable<From>::value)>;

// From and To are integrals of the same size. We can simply static_cast without changing the bit
// representation.
template <typename To, typename From,
          ENABLE_IF(CanBitCastImpl<To, From>::value &&
                    std::is_integral<To>::value &&
                    std::is_integral<From>::value)>
inline constexpr To bit_cast(const From& from) {
#if HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  return static_cast<To>(from);
#endif
}

// From is an integral and To is a enum. We can simply static_cast using the underlying type.
template <typename To, typename From,
          ENABLE_IF(CanBitCastImpl<To, From>::value &&
                    std::is_enum<To>::value &&
                    std::is_integral<From>::value)>
inline constexpr To bit_cast(const From& from) {
#if HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  return static_cast<To>(bit_cast<std::underlying_type_t<To>>(from));
#endif
}

// From is an enum and To is an integral. We can simply static_cast using the underlying type.
template <typename To, typename From,
          ENABLE_IF(CanBitCastImpl<To, From>::value &&
                    std::is_integral<To>::value &&
                    std::is_enum<From>::value)>
inline constexpr To bit_cast(const From& from) {
#if HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  return bit_cast<To>(static_cast<std::underlying_type_t<From>>(from));
#endif
}

// From is an enum and To is an enum. We can simply static_cast using the underlying type.
template <typename To, typename From,
          ENABLE_IF(CanBitCastImpl<To, From>::value &&
                    std::is_enum<To>::value &&
                    std::is_enum<From>::value)>
inline constexpr To bit_cast(const From& from) {
#if HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  return static_cast<To>(bit_cast<std::underlying_type_t<To>>(
      static_cast<std::underlying_type_t<From>>(from)));
#endif
}

// To is a pointer and From is uintptr_t/intptr_t.
template <typename To, typename From,
          ENABLE_IF(CanBitCastImpl<To, From>::value &&
                    std::is_pointer<To>::value &&
                    (std::is_same<From, uintptr_t>::value || std::is_same<From, intptr_t>::value))>
inline To bit_cast(const From& from) {
#if HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  return reinterpret_cast<To>(from);
#endif
}

// From is a pointer and To is uintptr_t/intptr_t.
template <typename To, typename From,
          ENABLE_IF(CanBitCastImpl<To, From>::value &&
                    std::is_pointer<From>::value &&
                    (std::is_same<To, uintptr_t>::value || std::is_same<To, intptr_t>::value))>
inline To bit_cast(const From& from) {
#if HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  return reinterpret_cast<To>(from);
#endif
}

// From or To is floating point.
template <typename To, typename From,
          ENABLE_IF(CanBitCastImpl<To, From>::value &&
                    (std::is_floating_point<To>::value || std::is_floating_point<From>::value))>
inline To bit_cast(const From& from) {
#if HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  // Use the union trick. The union trick is technically UB, but is
  // widely and well supported, producing good code. In some cases,
  // such as gcc, that support is explicitly documented. Using memcpy
  // is the correct method, but some compilers produce wretched code
  // for that method, even at maximal optimization levels. Neither
  // the union trick nor memcpy provides constexpr support.
  union {
    From from;
    To to;
  } converter = { from };
  return converter.to;
#endif
}

#endif  // SHARE_UTILITIES_BIT_CAST_HPP
