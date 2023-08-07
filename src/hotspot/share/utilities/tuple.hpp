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

#ifndef SHARE_UTILITIES_TUPLE_HPP
#define SHARE_UTILITIES_TUPLE_HPP

#include <type_traits>

template <class... Ts>
class Tuple;

template <>
class Tuple<> {};

template <class T, class... Ts>
class Tuple<T, Ts...> {
private:
  T _first;
  Tuple<Ts...> _remaining;

public:
  constexpr Tuple(const T& first, const Ts&... remaining) noexcept
    : _first(first), _remaining(remaining...) {}

  template <std::size_t I, std::enable_if_t<(I > 0), int> = 0>
  constexpr const auto& get() const noexcept {
    return _remaining.template get<I - 1>();
  };

  template <std::size_t I, std::enable_if_t<I == 0, int> = 0>
  constexpr const T& get() const noexcept {
    return _first;
  }
};

#endif // SHARE_UTILITIES_TUPLE_HPP
