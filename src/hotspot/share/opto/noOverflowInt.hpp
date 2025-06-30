/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_NOOVERFLOWINT_HPP
#define SHARE_OPTO_NOOVERFLOWINT_HPP

#include "utilities/ostream.hpp"

// Wrapper around jint, which detects overflow.
// If any operation overflows, then it returns a NaN.
class NoOverflowInt {
private:
  bool _is_NaN; // overflow, uninitialized, etc.
  jint _value;

public:
  // Default: NaN.
  constexpr NoOverflowInt() : _is_NaN(true), _value(0) {}

  // Create from jlong (or jint) -> NaN if overflows jint.
  constexpr explicit NoOverflowInt(jlong value) : _is_NaN(true), _value(0) {
    jint trunc = (jint)value;
    if ((jlong)trunc == value) {
      _is_NaN = false;
      _value = trunc;
    }
  }

  static constexpr NoOverflowInt make_NaN() { return NoOverflowInt(); }

  bool is_NaN() const { return _is_NaN; }
  jint value() const { assert(!is_NaN(), "NaN not allowed"); return _value; }
  bool is_zero() const { return !is_NaN() && value() == 0; }
  bool is_one() const { return !is_NaN() && value() == 1; }

  friend NoOverflowInt operator+(const NoOverflowInt& a, const NoOverflowInt& b) {
    if (a.is_NaN()) { return a; }
    if (b.is_NaN()) { return b; }
    return NoOverflowInt((jlong)a.value() + (jlong)b.value());
  }

  friend NoOverflowInt operator-(const NoOverflowInt& a, const NoOverflowInt& b) {
    if (a.is_NaN()) { return a; }
    if (b.is_NaN()) { return b; }
    return NoOverflowInt((jlong)a.value() - (jlong)b.value());
  }

  friend NoOverflowInt operator*(const NoOverflowInt& a, const NoOverflowInt& b) {
    if (a.is_NaN()) { return a; }
    if (b.is_NaN()) { return b; }
    return NoOverflowInt((jlong)a.value() * (jlong)b.value());
  }

  friend NoOverflowInt operator<<(const NoOverflowInt& a, const NoOverflowInt& b) {
    if (a.is_NaN()) { return a; }
    if (b.is_NaN()) { return b; }
    jint shift = b.value();
    if (shift < 0 || shift > 31) { return make_NaN(); }
    return NoOverflowInt((jlong)a.value() << shift);
  }

  friend bool operator==(const NoOverflowInt& a, const NoOverflowInt& b) {
    if (a.is_NaN()) { return false; }
    if (b.is_NaN()) { return false; }
    return a.value() == b.value();
  }

  NoOverflowInt abs() const {
    if (is_NaN()) { return *this; }
    if (value() >= 0) { return *this; }
    return NoOverflowInt(0) - *this;
  }

  bool is_multiple_of(const NoOverflowInt& other) const {
    NoOverflowInt a = this->abs();
    NoOverflowInt b = other.abs();
    if (a.is_NaN()) { return false; }
    if (b.is_NaN()) { return false; }
    if (b.is_zero()) { return false; }
    return a.value() % b.value() == 0;
  }

  // This "cmp" is used for sort only.
  // Note: the NaN semantics are different from floating arithmetic NaNs!
  // - Smaller non-NaN are before larger non-NaN.
  // - Any non-NaN are before NaN.
  // - NaN is equal to NaN.
  // Note: NaN indicate overflow, uninitialized, etc.
  static int cmp(const NoOverflowInt& a, const NoOverflowInt& b) {
    if (a.is_NaN()) {
      return b.is_NaN() ? 0 : 1;
    } else if (b.is_NaN()) {
      return -1;
    }
    if (a.value() < b.value()) { return -1; }
    if (a.value() > b.value()) { return  1; }
    return 0;
  }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    if (is_NaN()) {
      st->print("NaN");
    } else {
      st->print("%d", value());
    }
  }
#endif
};

#endif // SHARE_OPTO_NOOVERFLOWINT_HPP
