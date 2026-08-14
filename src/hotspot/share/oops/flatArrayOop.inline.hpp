/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_FLATARRAYOOP_INLINE_HPP
#define SHARE_VM_OOPS_FLATARRAYOOP_INLINE_HPP

#include "oops/flatArrayOop.hpp"

#include "classfile/vmSymbols.hpp"
#include "oops/access.inline.hpp"
#include "oops/flatArrayKlass.hpp"
#include "oops/inlineKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/valuePayload.inline.hpp"
#include "runtime/globals.hpp"

inline FlatArrayKlass* flatArrayOopDesc::klass() const {
  Klass* k = oopDesc::klass();
  return FlatArrayKlass::cast(k);
}

inline void* flatArrayOopDesc::base() const { return arrayOopDesc::base(T_FLAT_ELEMENT); }

inline size_t flatArrayOopDesc::base_offset_in_bytes() {
  return static_cast<size_t>(arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT));
}

inline void* flatArrayOopDesc::value_at_addr(int index, jint lh) const {
  assert(is_within_bounds(index), "index out of bounds");

  address array_base = (address) base();
  size_t offset = value_offset_from_base(index, lh);
  address addr = array_base + offset;
  assert(addr >= array_base, "must be");
  return (void*) addr;
}

inline size_t flatArrayOopDesc::value_offset(int index, jint lh) const {
  assert(is_within_bounds(index), "index out of bounds");
  return base_offset_in_bytes() + value_offset_from_base(index, lh);
}

inline size_t flatArrayOopDesc::value_offset_from_base(int index, jint lh) const {
  assert(is_within_bounds(index), "index out of bounds");
  return static_cast<size_t>(index) << Klass::layout_helper_log2_element_size(lh);
}

inline int flatArrayOopDesc::object_size(int lh) const {
  return object_size(lh, length());
}

inline oop flatArrayOopDesc::obj_at(int index, TRAPS) const {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  FlatArrayPayload payload(flatArrayOop(const_cast<flatArrayOopDesc*>(this)), index);
  return payload.read(THREAD);
}

inline bool flatArrayOopDesc::obj_at_is_null(int index) const {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  FlatArrayPayload payload(flatArrayOop(const_cast<flatArrayOopDesc*>(this)), index);
  return payload.is_payload_null();
}

inline jboolean flatArrayOopDesc::null_marker_of_obj_at(int index) const {
  EXCEPTION_MARK;
  return null_marker_of_obj_at(index, THREAD);
}

inline jboolean flatArrayOopDesc::null_marker_of_obj_at(int index, TRAPS) const {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  FlatArrayKlass* fak = klass();
  InlineKlass* vk = fak->element_klass();
  char* this_oop = (char*) (oopDesc*) this;
  char* val = (char*) value_at_addr(index, fak->layout_helper());
  ptrdiff_t offset = val - this_oop + (ptrdiff_t)vk->null_marker_offset_in_payload();
  return bool_field(offset);
}

inline void flatArrayOopDesc::obj_at_put(int index, oop value) {
  EXCEPTION_MARK;                                 // What if the caller is not a Java Thread?
  obj_at_put(index, value, THREAD);
}

inline void flatArrayOopDesc::obj_at_put(int index, oop value, TRAPS) {
  assert(is_within_bounds(index), "index %d out of bounds %d", index, length());
  FlatArrayKlass* fak = klass();
  InlineKlass* vk = fak->element_klass();
  if (value != nullptr) {
    if (value->klass() != vk) {
      THROW(vmSymbols::java_lang_ArrayStoreException());
    }
  } else if(fak->is_null_free_array_klass()) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Cannot store null in a null-restricted array");
  }

  FlatArrayPayload payload(flatArrayOop(this), index, fak);
  // The value and klass has already been checked for null compatibility.
  payload.write_without_nullability_check(inlineOop(value));
}

template <typename OopClosureType>
void flatArrayOopDesc::oop_iterate_elements_range(OopClosureType* blk, int start, int end) {
  if (UseCompressedOops) {
    klass()->oop_oop_iterate_elements_range<narrowOop>(this, blk, start, end);
  } else {
    klass()->oop_oop_iterate_elements_range<oop>(this, blk, start, end);
  }
}

#endif // SHARE_VM_OOPS_FLATARRAYOOP_INLINE_HPP
