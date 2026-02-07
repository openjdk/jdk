/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_INTEGERCAST_HPP
#define SHARE_UTILITIES_INTEGERCAST_HPP

#include "cppstdlib/limits.hpp"
#include "cppstdlib/type_traits.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

#include <stdint.h>

// Tests whether all values for the From type are within the range of values
// for the To Type.  From and To must be integral types.  This is used by
// integer_cast to test for tautological conversions.
template<typename From, typename To,
         ENABLE_IF(std::is_integral_v<From>),
         ENABLE_IF(std::is_integral_v<To>)>
constexpr bool is_always_integer_convertible() {
  if constexpr (std::is_signed_v<To> == std::is_signed_v<From>) {
    // signed => signed or unsigned => unsigned.
    return sizeof(To) >= sizeof(From);
  } else if constexpr (std::is_signed_v<From>) {
    // signed => unsigned is never tautological, because of negative values.
    return false;
  } else {
    // unsigned => signed.
    return sizeof(To) > sizeof(From);
  }
}

// Tests whether the value of from is within the range of values for the To
// type.  To and From must be integral types.  This is used by integer_cast
// to test whether the conversion should be performed.
template<typename To, typename From,
         ENABLE_IF(std::is_integral_v<To>),
         ENABLE_IF(std::is_integral_v<From>)>
constexpr bool is_integer_convertible(From from) {
  if constexpr (is_always_integer_convertible<From, To>()) {
    // This clause simplifies direct calls.  It isn't needed by
    // integer_cast, where a tautological call is discarded.
    return true;
  } else if constexpr (std::is_unsigned_v<From>) {
    using U = std::make_unsigned_t<To>;
    // unsigned => signed or unsigned => unsigned.
    // Convert To::max to corresponding unsigned for compare.
    return from <= static_cast<U>(std::numeric_limits<To>::max());
  } else if constexpr (std::is_signed_v<To>) {
    // signed => signed.  Range check with one comparison.
    using U = std::make_unsigned_t<From>;
    U ufrom = static_cast<U>(from);
    constexpr U umin = static_cast<U>(std::numeric_limits<To>::min());
    constexpr U umax = static_cast<U>(std::numeric_limits<To>::max());
    // The "usual" single-compare range check formulation would be
    //   (U)(from - min) <= (U)(max - min)
    // but that has UB overflows (both actual and potential).
    // Converting to U earlier is equivalent but avoids UB overflows.
    return (ufrom - umin) <= (umax - umin);
  } else {
    // signed => unsigned.  Range check with one comparison.
    if constexpr (sizeof(To) < sizeof(From)) { // Avoid tautological compare.
      using U = std::make_unsigned_t<From>;
      // Negative from converts to large value that exceeds To's max.
      return static_cast<U>(from) <= std::numeric_limits<To>::max();
    } else {
      return from >= 0;
    }
  }
}

// Convert the from value to the To type, after a debug-only check that the
// value of from is within the range of values for the To type.  To and From
// must be integral types.
//
// permit_tautology determines the behavior when a conversion will always
// succeed because the range of values for the From type is enclosed by the
// range of values for the To type (is_always_integer_convertible<From, To>()
// is true).  If true, the conversion will be performed as requested. If
// false, a compile-time error is produced.  The default is false for 64bit
// platforms, true for 32bit platforms.
//
// Unnecessary integer_casts make code harder to understand.  Hence the
// compile-time failure for tautological conversions, to alert that a code
// change is making a integer_cast unnecessary.  This can be suppressed on a
// per-call basis, because there are cases where a conversion might only
// sometimes be tautological.  For example, the types involved may vary by
// platform.  Another case is if the operation is in a template with dependent
// types, with the operation only being tautological for some instantiations.
// Suppressing the tautology check is an alternative to possibly complex
// metaprogramming to only perform the integer_cast when necessary.
//
// Despite that, for 32bit platforms the default is to not reject unnecessary
// integer_casts.  This is because 64bit platforms are the primary target, and
// are likely to require conversions in some places.  However, some of those
// conversions will be tautological on 32bit platforms, such as size_t => uint.
template<typename To,
         bool permit_tautology = LP64_ONLY(false) NOT_LP64(true),
         typename From,
         ENABLE_IF(std::is_integral_v<To>),
         ENABLE_IF(std::is_integral_v<From>)>
constexpr To integer_cast(From from) {
  if constexpr (is_always_integer_convertible<From, To>()) {
    static_assert(permit_tautology, "tautological integer_cast");
  } else {
#ifdef ASSERT
    if (!is_integer_convertible<To>(from)) {
      if constexpr (std::is_signed_v<From>) {
        fatal("integer_cast failed: %jd", static_cast<intmax_t>(from));
      } else {
        fatal("integer_cast failed: %ju", static_cast<uintmax_t>(from));
      }
    }
#endif // ASSERT
  }
  return static_cast<To>(from);
}

// Convert an enumerator to an integral value via static_cast, after a
// debug-only check that the value is within the range for the destination
// type.  This is mostly for compatibility with old code.  Class scoped enums
// were used to work around ancient compilers that didn't implement class
// scoped static integral constants properly, and HotSpot code still has many
// examples of this.  For others it might be sufficient to provide an explicit
// underlying type and either permit implicit conversions or use
// PrimitiveConversion::cast.
template<typename To, typename From,
         ENABLE_IF(std::is_integral_v<To>),
         ENABLE_IF(std::is_enum_v<From>)>
constexpr To integer_cast(From from) {
  using U = std::underlying_type_t<From>;
  return integer_cast<To, true /* permit_tautology */>(static_cast<U>(from));
}

#endif // SHARE_UTILITIES_INTEGERCAST_HPP
