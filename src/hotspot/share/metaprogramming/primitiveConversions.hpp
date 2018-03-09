/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_METAPROGRAMMING_PRIMITIVECONVERSIONS_HPP
#define SHARE_VM_METAPROGRAMMING_PRIMITIVECONVERSIONS_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/integralConstant.hpp"
#include "metaprogramming/isFloatingPoint.hpp"
#include "metaprogramming/isIntegral.hpp"
#include "metaprogramming/isRegisteredEnum.hpp"
#include "utilities/debug.hpp"

class PrimitiveConversions : public AllStatic {
public:
  // Return a value of type T with the same representation as x.
  //
  // T and U must be of the same size.
  //
  // At least one of T or U must be an integral type.  The other must
  // be an integral, floating point, or pointer type.
  template<typename T, typename U> static T cast(U x);

  // Support thin wrappers over primitive types.
  // If derived from TrueType, provides representational conversion
  // from T to some other type.  When true, must provide
  // - Value: typedef for T.
  // - Decayed: typedef for decayed type.
  // - static Decayed decay(T x): return value of type Decayed with
  //   the same representation as x.
  // - static T recover(Decayed x): return a value of type T with the
  //   same representation as x.
  template<typename T> struct Translate : public FalseType {};

private:

  template<typename T,
           typename U,
           bool same_size = sizeof(T) == sizeof(U),
           typename Enable = void>
  struct Cast;

  template<typename T, typename U> static T cast_using_union(U x);
};

// Return an object of type T with the same value representation as x.
//
// T and U must be of the same size.  It is expected that one of T and
// U is an integral type, and the other is an integral type, a
// (registered) enum type, or a floating point type
//
// This implementation uses the "union trick", which seems to be the
// best of a bad set of options.  Though technically undefined
// behavior, it is widely and well supported, producing good code.  In
// some cases, such as gcc, that support is explicitly documented.
//
// Using memcpy is the correct method, but some compilers produce
// wretched code for that method, even at maximal optimization levels.
//
// Using static_cast is only possible for integral and enum types, not
// for floating point types.  And for integral and enum conversions,
// static_cast has unspecified or implementation-defined behavior for
// some cases.  C++11 <type_traits> can be used to avoid most or all
// of those unspecified or implementation-defined issues, though that
// may require multi-step conversions.
//
// Using reinterpret_cast of references has undefined behavior for
// many cases, and there is much less empirical basis for its use, as
// compared to the union trick.
template<typename T, typename U>
inline T PrimitiveConversions::cast_using_union(U x) {
  STATIC_ASSERT(sizeof(T) == sizeof(U));
  union { T t; U u; };
  u = x;
  return t;
}

//////////////////////////////////////////////////////////////////////////////
// cast<T>(x)
//
// Cast<T, U, same_size, Enable>

// Give an informative error if the sizes differ.
template<typename T, typename U>
struct PrimitiveConversions::Cast<T, U, false> {
  STATIC_ASSERT(sizeof(T) == sizeof(U));
};

// Conversion between integral types.
template<typename T, typename U>
struct PrimitiveConversions::Cast<
  T, U, true,
  typename EnableIf<IsIntegral<T>::value && IsIntegral<U>::value>::type>
{
  T operator()(U x) const { return cast_using_union<T>(x); }
};

// Convert an enum or floating point value to an integer value.
template<typename T, typename U>
struct PrimitiveConversions::Cast<
  T, U, true,
  typename EnableIf<IsIntegral<T>::value &&
                    (IsRegisteredEnum<U>::value ||
                     IsFloatingPoint<U>::value)>::type>
{
  T operator()(U x) const { return cast_using_union<T>(x); }
};

// Convert an integer to an enum or floating point value.
template<typename T, typename U>
struct PrimitiveConversions::Cast<
  T, U, true,
  typename EnableIf<IsIntegral<U>::value &&
                    (IsRegisteredEnum<T>::value ||
                     IsFloatingPoint<T>::value)>::type>
{
  T operator()(U x) const { return cast_using_union<T>(x); }
};

// Convert a pointer to an integral value.
template<typename T, typename U>
struct PrimitiveConversions::Cast<
  T, U*, true,
  typename EnableIf<IsIntegral<T>::value>::type>
{
  T operator()(U* x) const { return reinterpret_cast<T>(x); }
};

// Convert an integral value to a pointer.
template<typename T, typename U>
struct PrimitiveConversions::Cast<
  T*, U, true,
  typename EnableIf<IsIntegral<U>::value>::type>
{
  T* operator()(U x) const { return reinterpret_cast<T*>(x); }
};

template<typename T, typename U>
inline T PrimitiveConversions::cast(U x) {
  return Cast<T, U>()(x);
}

// jfloat and jdouble translation to integral types

template<>
struct PrimitiveConversions::Translate<jdouble> : public TrueType {
  typedef double Value;
  typedef int64_t Decayed;

  static Decayed decay(Value x) { return PrimitiveConversions::cast<Decayed>(x); }
  static Value recover(Decayed x) { return PrimitiveConversions::cast<Value>(x); }
};

template<>
struct PrimitiveConversions::Translate<jfloat> : public TrueType {
  typedef float Value;
  typedef int32_t Decayed;

  static Decayed decay(Value x) { return PrimitiveConversions::cast<Decayed>(x); }
  static Value recover(Decayed x) { return PrimitiveConversions::cast<Value>(x); }
};

#endif // SHARE_VM_METAPROGRAMMING_PRIMITIVECONVERSIONS_HPP
