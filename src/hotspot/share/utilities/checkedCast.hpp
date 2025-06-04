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

#include "utilities/debug.hpp"

// In many places we've added C-style casts to silence compiler
// warnings, for example when truncating a size_t to an int when we
// know the size_t is a small struct. Such casts are risky because
// they effectively disable useful compiler warnings. We can make our
// lives safer with this function, which ensures that any cast is
// reversible without loss of information. It doesn't check
// everything: it isn't intended to make sure that pointer types are
// compatible, for example.
template <typename T2, typename T1>
constexpr T2 checked_cast(T1 thing) {
  T2 result = static_cast<T2>(thing);
  assert(static_cast<T1>(result) == thing, "must be");
  return result;
}

#endif // SHARE_UTILITIES_CHECKEDCAST_HPP

