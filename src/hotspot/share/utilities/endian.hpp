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

#ifndef SHARE_UTILITIES_ENDIAN_HPP
#define SHARE_UTILITIES_ENDIAN_HPP

#include "memory/allStatic.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/byteswap.hpp"
#include "utilities/unalignedAccess.hpp"

class Endian final : public AllStatic {
public:
  enum Order {
    LITTLE,
    BIG,
    JAVA = BIG,
    NATIVE =
#ifdef VM_LITTLE_ENDIAN
    LITTLE
#else
    BIG
#endif
  };

  // Returns true, if the byte ordering used by Java is different from
  // the native byte ordering of the underlying machine.
  static constexpr bool is_Java_byte_ordering_different() {
    return NATIVE != JAVA;
  }

  template <Order From, Order To>
  struct Converter;
};

template <Endian::Order To>
class Endianness final : public AllStatic {
 private:
  template <typename T, ENABLE_IF(std::is_integral<T>::value)>
  static inline T to_native(T x) {
    return Endian::Converter<To, Endian::NATIVE>{}(x);
  }

  template <typename T, ENABLE_IF(std::is_integral<T>::value)>
  static inline T from_native(T x) {
    return Endian::Converter<Endian::NATIVE, To>{}(x);
  }

 public:
  template <typename T, ENABLE_IF(std::is_integral<T>::value)>
  static inline T load(const T* p) {
    return to_native(*p);
  }

  template <typename T, ENABLE_IF(std::is_integral<T>::value)>
  static inline void store(T* p, T x) {
    *p = from_native(x);
  }

  template <typename T, ENABLE_IF(std::is_integral<T>::value)>
  static inline T load_unaligned(const void* p) {
    return to_native(UnalignedAccess::load<T>(p));
  }

  template <typename T, ENABLE_IF(std::is_integral<T>::value)>
  static inline void store_unaligned(void* p, T x) {
    UnalignedAccess::store(p, from_native(x));
  }
};

// Utility for loading and storing 8-bit, 16-bit, 32-bit, and 64-bit integers in big endian. If the
// native endianness is little, then the integers are byteswapped before storing and after loading.
// That is, all integers passed are expected to be in native endianness for storing and are returned
// in native endianness when loading.
using BigEndian = Endianness<Endian::BIG>;

using JavaEndian = Endianness<Endian::JAVA>;

static_assert(std::is_same<BigEndian, JavaEndian>::value, "BigEndian and JavaEndian are different");

// Utility for loading and storing 8-bit, 16-bit, 32-bit, and 64-bit integers in little endian. If
// the native endianness is big, then the integers are byteswapped before storing and after loading.
// That is, all integers passed are expected to be in native endianness for storing and are returned
// in native endianness when loading.
using LittleEndian = Endianness<Endian::LITTLE>;

template <Endian::Order From, Endian::Order To>
struct Endian::Converter {
  template <typename T>
  inline T operator()(T x) const {
    return byteswap(x);
  }
};

template <>
struct Endian::Converter<Endian::LITTLE, Endian::LITTLE> {
  template <typename T>
  inline constexpr T operator()(T x) const {
    return x;
  }
};

template <>
struct Endian::Converter<Endian::BIG, Endian::BIG> {
  template <typename T>
  inline constexpr T operator()(T x) const {
    return x;
  }
};

#endif // SHARE_UTILITIES_ENDIAN_HPP
