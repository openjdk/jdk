/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OOPCAST_INLINE_HPP
#define SHARE_OOPS_OOPCAST_INLINE_HPP

#include "oops/oop.inline.hpp"
#include "oops/oopsHierarchy.hpp"

template<typename T>
static bool is_oop_type(oop theOop) {
  static_assert(sizeof(T) == 0, "No is_oop_type specialization found for this type");
  return false;
}
template<>
inline bool is_oop_type<instanceOop>(oop theOop) { return theOop->is_instance(); }
template<>
inline bool is_oop_type<arrayOop>(oop theOop) { return theOop->is_array(); }
template<>
inline bool is_oop_type<objArrayOop>(oop theOop) { return theOop->is_objArray(); }
template<>
inline bool is_oop_type<typeArrayOop>(oop theOop) { return theOop->is_typeArray(); }

template<typename R>
R oop_cast(oop theOop) {
  assert(is_oop_type<R>(theOop), "Invalid cast");
  return (R) theOop;
}

#endif // SHARE_OOPS_OOPCAST_INLINE_HPP
