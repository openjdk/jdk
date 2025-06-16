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
 */

#ifndef SHARE_GC_Z_ZPAGEAGE_INLINE_HPP
#define SHARE_GC_Z_ZPAGEAGE_INLINE_HPP

#include "gc/z/zPageAge.hpp"

#include "utilities/checkedCast.hpp"

#include <type_traits>

inline uint untype(ZPageAge age) {
  return static_cast<uint>(age);
}

inline ZPageAge to_zpageage(uint age) {
  assert(age < ZPageAgeCount, "Invalid age");
  return static_cast<ZPageAge>(age);
}

inline ZPageAge operator+(ZPageAge age, size_t size) {
  const auto size_value = checked_cast<std::underlying_type_t<ZPageAge>>(size);
  return to_zpageage(untype(age) + size_value);
}

inline ZPageAge operator-(ZPageAge age, size_t size) {
  const auto size_value = checked_cast<std::underlying_type_t<ZPageAge>>(size);
  return to_zpageage(untype(age) - size_value);
}

#endif // SHARE_GC_Z_ZPAGEAGE_INLINE_HPP
