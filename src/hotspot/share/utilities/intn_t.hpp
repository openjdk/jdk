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

template <unsigned int n>
class uintn_t;

template <unsigned int n>
class intn_t {
  static_assert(n > 0 && n <= 8, "should not be larger than char");

private:
  uint _v;

  constexpr static uint _mask = (1 << n) - 1;

  friend class uintn_t<n>;

public:
  explicit constexpr intn_t(int v) : _v(v) {}
  constexpr intn_t() : _v(0) {}
  constexpr intn_t(const intn_t&) = default;
  explicit constexpr intn_t(uintn_t<n> v);

  operator int() const {
    int shift = 32 - n;
    return int(_v << shift) >> shift;
  }

  constexpr static int min = std::numeric_limits<unsigned int>::max() << (n - 1);
  constexpr static int max = (1 << (n - 1)) - 1;
  static_assert(min < max, "");

  constexpr bool operator==(intn_t o) const { return (_v & _mask) == (o._v & _mask); }
  constexpr bool operator<(intn_t o) const {
    // Shift the highest bit of intn_t to the highest bit of the int representation
    int shift = 32 - n;
    return int(_v << shift) < int(o._v << shift);
  }
  constexpr bool operator>(intn_t o) const { return o < *this; }
  constexpr bool operator<=(intn_t o) const { return !(o < *this); }
  constexpr bool operator>=(intn_t o) const { return !(*this < o); }
};

template <unsigned int n>
class uintn_t {
  static_assert(n > 0 && n <= 8, "should not be larger than char");

private:
  uint _v;

  constexpr static uint _mask = (1 << n) - 1;

  friend class intn_t<n>;

  template <class T>
  friend unsigned count_leading_zeros(T);

public:
  explicit constexpr uintn_t(int v) : _v(v) {}
  constexpr uintn_t() : _v(0) {}
  constexpr uintn_t(const uintn_t&) = default;
  explicit constexpr uintn_t(intn_t<n> v) : _v(v._v) {}
  operator uint() const { return _v & _mask; }

  constexpr static int min = 0;
  constexpr static int max = _mask;
  static_assert(min < max, "");

  constexpr bool operator==(uintn_t o) const { return (_v & _mask) == (o._v & _mask); }
  constexpr bool operator!=(uintn_t o) const { return !(*this == o); }
  constexpr bool operator<(uintn_t o) const { return (_v & _mask) < (o._v & _mask); }
  constexpr bool operator>(uintn_t o) const { return o < *this; }
  constexpr bool operator<=(uintn_t o) const { return !(o < *this); }
  constexpr bool operator>=(uintn_t o) const { return !(*this < o); }
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

template <unsigned int n>
constexpr intn_t<n>::intn_t(uintn_t<n> v) : _v(v._v) {}

namespace std {

template <unsigned int n>
class numeric_limits<intn_t<n>> {
public:
  constexpr static intn_t<n> min() { return intn_t<n>(intn_t<n>::min); }
  constexpr static intn_t<n> max() { return intn_t<n>(intn_t<n>::max); }
};

template <unsigned int n>
class numeric_limits<uintn_t<n>> {
public:
  constexpr static uintn_t<n> min() { return uintn_t<n>(uintn_t<n>::min); }
  constexpr static uintn_t<n> max() { return uintn_t<n>(uintn_t<n>::max); }
};

}

template <>
inline unsigned count_leading_zeros<uintn_t<1>>(uintn_t<1> v) {
  return count_leading_zeros<unsigned int>(v._v & uintn_t<1>::_mask) - (32 - 1);
}

template <>
inline unsigned count_leading_zeros<uintn_t<2>>(uintn_t<2> v) {
  return count_leading_zeros<unsigned int>(v._v & uintn_t<2>::_mask) - (32 - 2);
}

template <>
inline unsigned count_leading_zeros<uintn_t<3>>(uintn_t<3> v) {
  return count_leading_zeros<unsigned int>(v._v & uintn_t<3>::_mask) - (32 - 3);
}

template <>
inline unsigned count_leading_zeros<uintn_t<4>>(uintn_t<4> v) {
  return count_leading_zeros<unsigned int>(v._v & uintn_t<4>::_mask) - (32 - 4);
}

#endif // SHARE_UTILITIES_INTN_T_HPP
