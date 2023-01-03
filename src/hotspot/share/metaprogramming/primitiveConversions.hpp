/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_METAPROGRAMMING_PRIMITIVECONVERSIONS_HPP
#define SHARE_METAPROGRAMMING_PRIMITIVECONVERSIONS_HPP

#include "memory/allStatic.hpp"
#include "utilities/bit_cast.hpp"
#include "utilities/globalDefinitions.hpp"

#include <type_traits>

class PrimitiveConversions : public AllStatic {
public:
  // Support thin wrappers over primitive types and other conversions.
  // If derived from std::true_type, provides representational conversion
  // from T to some other type.  When true, must provide
  // - Value: typedef for T.
  // - Decayed: typedef for decayed type.
  // - static Decayed decay(T x): return value of type Decayed with
  //   the same value representation as x.
  // - static T recover(Decayed x): return a value of type T with the
  //   same value representation as x.
  template<typename T, typename Enable = void>
  struct Translate : public std::false_type {};
};

// Enum types translate to/from their underlying type.
template<typename T>
struct PrimitiveConversions::Translate<T, std::enable_if_t<std::is_enum<T>::value>>
  : public std::true_type
{
  using Value = T;
  using Decayed = std::underlying_type_t<T>;

  static constexpr Decayed decay(Value x) { return static_cast<Decayed>(x); }
  static constexpr Value recover(Decayed x) { return static_cast<Value>(x); }
};

// jfloat and jdouble translation to integral types

template<>
struct PrimitiveConversions::Translate<jdouble> : public std::true_type {
  typedef double Value;
  typedef int64_t Decayed;

  static Decayed decay(Value x) { return bit_cast<Decayed>(x); }
  static Value recover(Decayed x) { return bit_cast<Value>(x); }
};

template<>
struct PrimitiveConversions::Translate<jfloat> : public std::true_type {
  typedef float Value;
  typedef int32_t Decayed;

  static Decayed decay(Value x) { return bit_cast<Decayed>(x); }
  static Value recover(Decayed x) { return bit_cast<Value>(x); }
};

#endif // SHARE_METAPROGRAMMING_PRIMITIVECONVERSIONS_HPP
