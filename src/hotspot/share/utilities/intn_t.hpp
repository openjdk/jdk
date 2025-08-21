/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_INTN_T_HPP
#define SHARE_UTILITIES_INTN_T_HPP

#include "utilities/count_leading_zeros.hpp"

#include <limits>

template <unsigned int nbits>
class uintn_t;

// This class represents a signed integer type with the width of exactly nbits
// bits. Conceptually, nbits == 8 gives a type equivalent to int8_t,
// nbits == 16 gives a type equivalent to int16_t, and so on. This class may be
// used to verify the correctness of an algorithm that is supposed to be
// applicable to all fixed-width integral types. With small nbits, it makes it
// possible to perform an exhaustive test that exercises the algorithm with all
// possible input values.
// Implementation-wise, this class currently only supports 0 < nbits <= 8. Also
// note that this class is implemented so that overflows in alrithmetic
// operations are well-defined and wrap-around.
template <unsigned int nbits>
class intn_t {
  static_assert(0 < nbits && nbits <= 8, "should not be larger than char");

private:
  // Only the lowest nbits bits are important, operations should act as if it
  // sign extends the lowest nbits to an int, performs the calculation on ints,
  // then truncates the result to nbits. In practice, we do not need to
  // truncate the result, as the lowest nbits will be sign extended in the next
  // operations. We can also sign extends the operands sparingly, for example,
  // addition or subtraction do not need this sign extension, and we can add or
  // subtract the value of _v directly. This is because the lowest nbits bits
  // of a sum or a difference only depends on the lowest nbits bits of the
  // operands.
  uint _v;

  constexpr static uint _mask = (1 << nbits) - 1;

  friend class uintn_t<nbits>;

public:
  explicit constexpr intn_t(int v) : _v(v) {}
  constexpr intn_t() : _v(0) {}
  constexpr intn_t(const intn_t&) = default;
  constexpr intn_t& operator=(const intn_t&) = default;
  explicit constexpr intn_t(uintn_t<nbits> v);

  // Sign extension
  explicit constexpr operator int() const {
    int shift = 32 - nbits;
    return int(_v << shift) >> shift;
  }

  constexpr static int min = std::numeric_limits<unsigned int>::max() << (nbits - 1);
  constexpr static int max = (1 << (nbits - 1)) - 1;
  static_assert(min < max, "");

  constexpr bool operator==(intn_t o) const { return (_v & _mask) == (o._v & _mask); }
  constexpr bool operator<(intn_t o) const { return int(*this) < int(o); }
  constexpr bool operator>(intn_t o) const { return int(*this) > int(o); }
  constexpr bool operator<=(intn_t o) const { return int(*this) <= int(o); }
  constexpr bool operator>=(intn_t o) const { return int(*this) >= int(o); }
};

template <unsigned int nbits>
unsigned count_leading_zeros(uintn_t<nbits>);

// The unsigned version of intn_t<nbits>
template <unsigned int nbits>
class uintn_t {
  static_assert(0 < nbits && nbits <= 8, "should not be larger than char");

private:
  // Similar to intn_t<nbits>, the difference is that the operation should act
  // as if it zero extends the lowest nbits bits of the operands.
  uint _v;

  constexpr static uint _mask = (1 << nbits) - 1;

  friend class intn_t<nbits>;

  friend unsigned count_leading_zeros<nbits>(uintn_t<nbits>);

public:
  explicit constexpr uintn_t(int v) : _v(v) {}
  constexpr uintn_t() : _v(0) {}
  constexpr uintn_t(const uintn_t&) = default;
  constexpr uintn_t& operator=(const uintn_t&) = default;
  explicit constexpr uintn_t(intn_t<nbits> v) : _v(v._v) {}

  // Zero extension
  explicit constexpr operator uint() const { return _v & _mask; }

  constexpr static int min = 0;
  constexpr static int max = _mask;
  static_assert(min < max, "");

  constexpr bool operator==(uintn_t o) const { return (_v & _mask) == (o._v & _mask); }
  constexpr bool operator!=(uintn_t o) const { return (_v & _mask) != (o._v & _mask); }
  constexpr bool operator<(uintn_t o) const { return (_v & _mask) < (o._v & _mask); }
  constexpr bool operator>(uintn_t o) const { return (_v & _mask) > (o._v & _mask); }
  constexpr bool operator<=(uintn_t o) const { return (_v & _mask) <= (o._v & _mask); }
  constexpr bool operator>=(uintn_t o) const { return (_v & _mask) >= (o._v & _mask); }
  constexpr uintn_t operator+(uintn_t o) const { return uintn_t(_v + o._v); }
  constexpr uintn_t operator-(uintn_t o) const { return uintn_t(_v - o._v); }
  constexpr uintn_t operator&(uintn_t o) const { return uintn_t(_v & o._v); }
  constexpr uintn_t operator|(uintn_t o) const { return uintn_t(_v | o._v); }
  constexpr uintn_t operator^(uintn_t o) const { return uintn_t(_v ^ o._v); }
  constexpr uintn_t operator>>(unsigned int s) const { return uintn_t((_v & _mask) >> s); }
  constexpr uintn_t operator<<(unsigned int s) const { return uintn_t(_v << s); }
  constexpr uintn_t operator~() const { return uintn_t(~_v); }
  constexpr uintn_t operator-() const { return uintn_t(-_v); }
  constexpr uintn_t& operator|=(uintn_t o) { _v |= o._v; return *this; }
};

template <unsigned int nbits>
constexpr intn_t<nbits>::intn_t(uintn_t<nbits> v) : _v(v._v) {}

namespace std {

template <unsigned int nbits>
class numeric_limits<intn_t<nbits>> {
public:
  constexpr static intn_t<nbits> min() { return intn_t<nbits>(intn_t<nbits>::min); }
  constexpr static intn_t<nbits> max() { return intn_t<nbits>(intn_t<nbits>::max); }
};

template <unsigned int nbits>
class numeric_limits<uintn_t<nbits>> {
public:
  constexpr static uintn_t<nbits> min() { return uintn_t<nbits>(uintn_t<nbits>::min); }
  constexpr static uintn_t<nbits> max() { return uintn_t<nbits>(uintn_t<nbits>::max); }
};

} // namespace std

template <unsigned int nbits>
inline unsigned count_leading_zeros(uintn_t<nbits> v) {
  return count_leading_zeros<unsigned int>(v._v & uintn_t<nbits>::_mask) - (32 - nbits);
}

#endif // SHARE_UTILITIES_INTN_T_HPP
