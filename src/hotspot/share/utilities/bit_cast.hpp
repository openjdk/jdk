/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

// C++14 compatible implementation of std::bit_cast introduced in C++20.

#include "metaprogramming/enableIf.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include <cstring>
#include <memory>
#include <type_traits>

#if defined(__cpp_lib_bit_cast) && __cpp_lib_bit_cast >= 201806L
#include <bit>
#endif

template <typename To, typename From,
          ENABLE_IF(sizeof(To) == sizeof(From) &&
                    std::is_integral<From>::value &&
                    std::is_integral<To>::value)>
constexpr To bit_cast(const From& from) {
  return static_cast<To>(from);
}

template <typename To, typename From,
          ENABLE_IF(sizeof(To) == sizeof(From) &&
                    std::is_integral<From>::value &&
                    std::is_enum<To>::value)>
constexpr To bit_cast(const From& from) {
  return static_cast<To>(static_cast<std::underlying_type_t<To>>(from));
}

template <typename To, typename From,
          ENABLE_IF(sizeof(To) == sizeof(From) &&
                    std::is_enum<From>::value &&
                    std::is_integral<To>::value)>
constexpr To bit_cast(const From& from) {
  return static_cast<To>(static_cast<std::underlying_type_t<From>>(from));
}

template <typename To, typename From,
          ENABLE_IF(sizeof(To) == sizeof(From) &&
                    std::is_enum<From>::value &&
                    std::is_enum<To>::value)>
constexpr To bit_cast(const From& from) {
  return static_cast<To>(static_cast<std::underlying_type_t<To>>(
      static_cast<std::underlying_type_t<From>>(from)));
}

template <typename To, typename From,
          ENABLE_IF(sizeof(To) == sizeof(From) &&
                    (std::is_pointer<From>::value || std::is_pointer<To>::value))>
ALWAYSINLINE To bit_cast(const From& from) {
#if defined(__cpp_lib_bit_cast) && __cpp_lib_bit_cast >= 201806L
  // Once Hotspot moves to at least C++20 all usages should be moved to
  // std::bit_cast directly and this wrapper removed.
  return std::bit_cast<To>(from);
#elif HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  return reinterpret_cast<To>(from);
#endif
}

template <typename To, typename From,
          ENABLE_IF(sizeof(To) == sizeof(From) &&
                    (std::is_floating_point<From>::value || std::is_floating_point<To>::value))>
ALWAYSINLINE To bit_cast(const From& from) {
#if defined(__cpp_lib_bit_cast) && __cpp_lib_bit_cast >= 201806L
  // Once Hotspot moves to at least C++20 all usages should be moved to
  // std::bit_cast directly and this wrapper removed.
  return std::bit_cast<To>(from);
#elif HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  union {
    From from;
    To to;
  } converter;
  converter.from = from;
  return converter.to;
#endif
}

template <typename To, typename From,
          ENABLE_IF(sizeof(To) == sizeof(From) &&
                    std::is_trivially_copyable<To>::value &&
                    std::is_trivially_copyable<From>::value &&
                    std::is_default_constructible<To>::value &&
                    !std::is_integral<From>::value &&
                    !std::is_enum<From>::value &&
                    !std::is_pointer<From>::value &&
                    !std::is_floating_point<From>::value &&
                    !std::is_integral<To>::value &&
                    !std::is_enum<To>::value &&
                    !std::is_pointer<To>::value &&
                    !std::is_floating_point<To>::value)>
ALWAYSINLINE To bit_cast(const From& from) {
#if defined(__cpp_lib_bit_cast) && __cpp_lib_bit_cast >= 201806L
  // Once Hotspot moves to at least C++20 all usages should be moved to
  // std::bit_cast directly and this wrapper removed.
  return std::bit_cast<To>(from);
#elif HAS_BUILTIN(__builtin_bit_cast)
  return __builtin_bit_cast(To, from);
#else
  To to;
#if HAS_BUILTIN(__builtin_memcpy_inline)
  __builtin_memcpy_inline(std::addressof(to), std::addressof(from), sizeof(To));
#elif HAS_BUILTIN(__builtin_memcpy)
  __builtin_memcpy(std::addressof(to), std::addressof(from), sizeof(To));
#else
  std::memcpy(std::addressof(to), std::addressof(from), sizeof(To));
#endif
  return to;
#endif
}

#endif  // SHARE_UTILITIES_BIT_CAST_HPP
