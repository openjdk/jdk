/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_ENUMITERATOR_HPP
#define SHARE_UTILITIES_ENUMITERATOR_HPP

#include <type_traits>
#include "utilities/debug.hpp"

// This is a type-safe iterator for enums. The enum T must have a continuous range from FIRST
// to (but not including) LIMIT.
//
// Examples:
//     enum class Color : int { RED, WHITE, BLUE, LIMIT };
//     typedef EnumIterator<Color, Color::RED, Color::LIMIT> ColorIterator;
//
//     for (Color c : ColorIterator())                 { /* iterates RED,   WHITE, BLUE */ }
//     for (Color c : ColorIterator(WHITE))            { /* iterates WHITE, BLUE        */ }
//     for (Color c : ColorIterator.with_limit(WHITE)) { /* iterates RED,   WHITE, BLUE */ }

template<typename T, T FIRST, T LIMIT>
class EnumIterator {
  static_assert(std::is_enum<T>::value, "expected an enum type");
  using Rep = std::underlying_type_t<T>;
  Rep _current;
  Rep _limit;
public:
  // Iterate from first upto (but not including) limit
  EnumIterator(T first = FIRST, T limit = LIMIT) :
    _current(static_cast<Rep>(first)),
    _limit  (static_cast<Rep>(limit)) {
    assert(_current >= static_cast<Rep>(FIRST) && _current <  static_cast<Rep>(LIMIT) &&
           _limit   >= static_cast<Rep>(FIRST) && _limit   <= static_cast<Rep>(LIMIT), "range check");
  }
  // Iterate from FIRST upto (but not including) limit
  static EnumIterator with_limit(T limit) {
    return EnumIterator(FIRST, limit);
  }
  bool is_end() const {
    return _current == static_cast<Rep>(_limit);
  }
  bool operator==(const EnumIterator& other) const {
    return _current == other._current;
  }
  bool operator!=(const EnumIterator& other) const {
    return _current != other._current;
  }
  T operator*() const {
    return static_cast<T>(_current);
  }
  EnumIterator& operator++() {
    assert(!is_end(), "at end");
    ++_current;
    return *this;
  }
  EnumIterator operator++(int) {
    assert(!is_end(), "at end");
    EnumIterator result = *this;
    ++_current;
    return result;
  }
  EnumIterator begin() const {
    return EnumIterator();
  }
  EnumIterator end() const {
    EnumIterator result;
    result._current = static_cast<Rep>(_limit);
    return result;
  }
};

#endif // SHARE_UTILITIES_ENUMITERATOR_HPP
