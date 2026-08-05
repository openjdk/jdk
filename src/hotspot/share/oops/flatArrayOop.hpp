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

#ifndef SHARE_VM_OOPS_FLATARRAYOOP_HPP
#define SHARE_VM_OOPS_FLATARRAYOOP_HPP

#include "oops/klass.hpp"
#include "oops/objArrayOop.hpp"
#include "utilities/globalDefinitions.hpp"

class FlatArrayKlass;

// A flatArrayOop points to a flat array containing inline types (no indirection).
// It may include embedded oops in its elements.

class flatArrayOopDesc : public objArrayOopDesc {
 public:
  inline FlatArrayKlass* klass() const;

  void* base() const;
  static size_t base_offset_in_bytes();
  void* value_at_addr(int index, jint lh) const;
  size_t value_offset(int index, jint lh) const;
  size_t value_offset_from_base(int index, jint lh) const;

  // oop obj_at(...) must always allocate when reading a non-null flat element,
  // as such only obj_at(int, TRAPS) is provided. To null-check element without
  // allocating use obj_at_is_null(int);
  inline oop obj_at(int index) const = delete;
  inline oop obj_at(int index, TRAPS) const;
  inline bool obj_at_is_null(int index) const;
  inline jboolean null_marker_of_obj_at(int index) const;
  inline jboolean null_marker_of_obj_at(int index, TRAPS) const;
  inline void obj_at_put(int index, oop value);
  inline void obj_at_put(int index, oop value, TRAPS);

  // Sizing
  static size_t element_size(int lh, int nof_elements) {
    size_t sz = (size_t) nof_elements;
    return sz << Klass::layout_helper_log2_element_size(lh);
  }

  static int object_size(int lh, int length) {
    julong size_in_bytes = arrayOopDesc::base_offset_in_bytes(Klass::layout_helper_element_type(lh));
    size_in_bytes += element_size(lh, length);
    julong size_in_words = ((size_in_bytes + (HeapWordSize-1)) >> LogHeapWordSize);
    assert(size_in_words <= (julong)max_jint, "no overflow");
    return align_object_size((intptr_t)size_in_words);
  }

  int object_size(int lh) const;

  // Special iterators for an element index range.
  template <typename OopClosureType>
  void oop_iterate_elements_range(OopClosureType* blk, int start, int end);

};

// See similar requirement for oopDesc.
static_assert(std::is_trivially_default_constructible<flatArrayOopDesc>::value, "required");

#endif // SHARE_VM_OOPS_FLATARRAYOOP_HPP
