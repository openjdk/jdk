/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_CHECKEDCAST_HPP
#define SHARE_UTILITIES_CHECKEDCAST_HPP

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#include <limits>
#include <type_traits>

// Implementation support for checked_cast.
//
// Because of details of C++ conversion rules, including undefined or
// implementation-defined behavior in some cases, along with the possibility
// of tautological comparison warnings from some compilers, the implementation
// is not as simple as doing the obvious range checks.
class CheckedCastImpl {
  template<typename To, typename From, bool permit_tautology>
  struct CheckSameSign;

  template<typename To, typename From, bool permit_tautology,
           bool is_narrowing = (sizeof(To) <= sizeof(From))>
  struct CheckToSigned;

  template<typename To, typename From, bool permit_tautology,
           bool is_narrowing = (sizeof(To) < sizeof(From))>
  struct CheckFromSigned;

  template<typename To, typename From>
  static constexpr bool check_range(From from) {
    To to_max = std::numeric_limits<To>::max();
    return from <= static_cast<From>(to_max);
  }

  template<typename To, typename From, bool permit_tautology>
  struct Dispatcher {
    static const bool to_signed = std::is_signed<To>::value;
    static const bool from_signed = std::is_signed<From>::value;
    using SameSign = CheckSameSign<To, From, permit_tautology>;
    using ToSigned = CheckToSigned<To, From, permit_tautology>;
    using FromSigned = CheckFromSigned<To, From, permit_tautology>;
    using type =
      std::conditional_t<(to_signed == from_signed),
                         SameSign,
                         std::conditional_t<to_signed, ToSigned, FromSigned>>;
  };

public:
  template<typename To, typename From>
  static constexpr bool is_tautology() {
    using Dispatcher = Dispatcher<To, From, true>;
    using Checker = typename Dispatcher::type;
    return Checker::is_tautology;
  }

  template<typename To, bool permit_tautology, typename From>
  static constexpr bool check(From from) {
    using Dispatcher = Dispatcher<To, From, permit_tautology>;
    using Checker = typename Dispatcher::type;
    return Checker()(from);
  }
};

// If both types are signed or both are unsigned, only a narrowing conversion
// can lose information.  We check for such loss via a round-trip conversion.
// C++14 4.7 defines the result for unsigned types, and implementation-defined
// for signed types.  All supported implementations do the "obvious" discard
// of high-order bits when narrowing signed values, and C++20 requires that
// behavior.  This approach avoids any additional complexity to avoid
// potential tautological comparisons.
template<typename To, typename From, bool permit_tautology>
struct CheckedCastImpl::CheckSameSign {
  static const bool is_narrowing = sizeof(To) < sizeof(From);
  static const bool is_tautology = !is_narrowing;
  static_assert(permit_tautology || is_narrowing, "tautological checked_cast");
  constexpr bool operator()(From from) const {
    return !is_narrowing || (static_cast<From>(static_cast<To>(from)) == from);
  }
};

// Conversion from unsigned to signed is okay if value <= To's max.
template<typename To, typename From, bool permit_tautology>
struct CheckedCastImpl::CheckToSigned<To, From, permit_tautology, true /* is_narrowing */> {
  static const bool is_tautology = false;
  constexpr bool operator()(From from) const {
    return check_range<To>(from);
  }
};

// Avoid tautological comparison when not narrowing.
template<typename To, typename From, bool permit_tautology>
struct CheckedCastImpl::CheckToSigned<To, From, permit_tautology, false /* is_narrowing */> {
  static const bool is_tautology = true;
  static_assert(permit_tautology, "tautological checked_cast");
  constexpr bool operator()(From from) const {
    return true;
  }
};

// Conversion from signed to unsigned is okay when 0 <= value <= To's max.
template<typename To, typename From, bool permit_tautology>
struct CheckedCastImpl::CheckFromSigned<To, From, permit_tautology, true /* is_narrowing */> {
  static const bool is_tautology = false;
  constexpr bool operator()(From from) const {
    return (from >= 0) && check_range<To>(from);
  }
};

// Avoid tautological comparison when not narrowing.
template<typename To, typename From, bool permit_tautology>
struct CheckedCastImpl::CheckFromSigned<To, From, permit_tautology, false /* is_narrowing */> {
  static const bool is_tautology = false;
  constexpr bool operator()(From from) const {
    return (from >= 0);
  }
};

/**
 * Convert an integral value to another integral type via static_cast, after a
 * debug-only check that the value is within the range for the destination
 * type.
 *
 * * To is the desired result type, which must be integral.
 *
 * * From is the type of the argument, which must be integral.
 *
 * * permit_tautology determines the behavior when a conversion will
 * always succeed because the range of values for the From type is enclosed by
 * the range of values for the To type.  If true, the conversion will be
 * performed as requested.  If false, a compile-time error is produced.  The
 * default is false for 64bit platforms, true for 32bit platforms.
 *
 * * from is the value to be converted.
 *
 * Unnecessary checked_casts make code harder to understand.  Hence the
 * compile-time failure for tautological conversions, to alert that a code
 * change is making a checked_cast unnecessary.  This can be suppressed on a
 * per-call basis, because there are cases where a conversion might only
 * sometimes be tautological.  For example, the types involved may vary by
 * platform.  Another case is if the operation is in a template with dependent
 * types, with the operation only being tautological for some instantiations.
 * Suppressing the tautology check is an alternative to possibly complex
 * metaprogramming to only perform the checked_cast when necessary.
 *
 * Despite that, for 32bit platforms the default is to not reject unnecessary
 * checked_casts.  This is because 64bit platforms are the primary target, and
 * are likely to require conversions in some places.  However, some of those
 * conversions will be tautological on 32bit platforms.
 */
template<typename To,
         bool permit_tautology = LP64_ONLY(false) NOT_LP64(true),
         typename From,
         ENABLE_IF(std::is_integral<To>::value),
         ENABLE_IF(std::is_integral<From>::value)>
constexpr To checked_cast(From from) {
  assert((CheckedCastImpl::check<To, permit_tautology>(from)), "checked_cast failed");
  return static_cast<To>(from);
}

/**
 * Convert an enumerator to an integral value via static_cast, after a
 * debug-only check that the value is within the range for the destination
 * type.  This is mostly for compatibility with old code.  Class scoped enums
 * were used to work around ancient compilers that didn't implement class
 * scoped static integral constants properly, and HotSpot code still has many
 * examples of this.  For others it might be sufficient to provide an explicit
 * underlying type and either permit implicit conversions or use
 * PrimitiveConversion::cast.
 */
template<typename To, typename From,
         ENABLE_IF(std::is_integral<To>::value),
         ENABLE_IF(std::is_enum<From>::value)>
constexpr To checked_cast(From from) {
  using U = std::underlying_type_t<From>;
  return checked_cast<To, true /* permit_tautology */>(static_cast<U>(from));
}

#endif // SHARE_UTILITIES_CHECKEDCAST_HPP
