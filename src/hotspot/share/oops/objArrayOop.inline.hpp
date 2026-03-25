/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OBJARRAYOOP_INLINE_HPP
#define SHARE_OOPS_OBJARRAYOOP_INLINE_HPP

#include "oops/objArrayOop.hpp"

#include "oops/access.hpp"
#include "oops/arrayOop.hpp"
#include "oops/flatArrayOop.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/refArrayKlass.inline.hpp"
#include "runtime/globals.hpp"

inline HeapWord* objArrayOopDesc::base() const { return (HeapWord*) arrayOopDesc::base(T_OBJECT); }

template <class T> T* objArrayOopDesc::obj_at_addr(int index) const {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  return &((T*)base())[index];
}

inline oop objArrayOopDesc::obj_at(int index, TRAPS) const {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  if (is_flatArray()) {
    return ((const flatArrayOopDesc*)this)->obj_at(index, CHECK_NULL);
  } else {
    return ((const refArrayOopDesc*)this)->obj_at(index);
  }
}

inline bool objArrayOopDesc::obj_at_is_null(int index) const {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  if (is_flatArray()) {
    return ((const flatArrayOopDesc*)this)->obj_at_is_null(index);
  } else {
    return ((const refArrayOopDesc*)this)->obj_at(index) == nullptr;
  }
}

inline void objArrayOopDesc::obj_at_put(int index, oop value) {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  if (is_flatArray()) {
    ((flatArrayOopDesc*)this)->obj_at_put(index, value);
  } else {
    ((refArrayOopDesc*)this)->obj_at_put(index, value);
  }
}

inline void objArrayOopDesc::obj_at_put(int index, oop value, TRAPS) {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  if (is_flatArray()) {
    ((flatArrayOopDesc*)this)->obj_at_put(index, value, CHECK);
  } else {
    ((refArrayOopDesc*)this)->obj_at_put(index, value, CHECK);
  }
}

template <typename OopClosureType>
void objArrayOopDesc::oop_iterate_elements_range(OopClosureType* blk, int start, int end) {
  if (is_flatArray()) {
    ((flatArrayOopDesc*)this)->oop_iterate_elements_range(blk, start, end);
  } else {
    ((refArrayOopDesc*)this)->oop_iterate_elements_range(blk, start, end);
  }
}

#endif // SHARE_OOPS_OBJARRAYOOP_INLINE_HPP
