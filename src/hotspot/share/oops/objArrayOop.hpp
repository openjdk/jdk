/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OBJARRAYOOP_HPP
#define SHARE_OOPS_OBJARRAYOOP_HPP

#include "oops/arrayOop.hpp"
#include "utilities/align.hpp"

#include <type_traits>

class Klass;

// An objArrayOop is an array containing oops.
// Evaluating "String arg[10]" will create an objArrayOop.

class objArrayOopDesc : public arrayOopDesc {
  friend class ArchiveHeapWriter;
  friend class ObjArrayKlass;
  friend class Runtime1;
  friend class psPromotionManager;
  friend class CSetMarkWordClosure;
  friend class Continuation;
  template <typename T>
  friend class RawOopWriter;

  template <class T> T* obj_at_addr(int index) const;

  template <class T>
  static ptrdiff_t obj_at_offset(int index) {
    return base_offset_in_bytes() + sizeof(T) * index;
  }

 public:
  // Returns the offset of the first element.
  static int base_offset_in_bytes() {
    return arrayOopDesc::base_offset_in_bytes(T_OBJECT);
  }

  // base is the address following the header.
  HeapWord* base() const;

  // Accessing
  oop obj_at(int index) const;

  void obj_at_put(int index, oop value);

  oop replace_if_null(int index, oop exchange_value);

  // Sizing
  size_t object_size()        { return object_size(length()); }

  static size_t object_size(int length) {
    // This returns the object size in HeapWords.
    size_t asz = (size_t)length * heapOopSize;
    size_t size_words = heap_word_size(base_offset_in_bytes() + asz);
    size_t osz = align_object_size(size_words);
    assert(osz < max_jint, "no overflow");
    return osz;
  }

  Klass* element_klass();

public:
  // special iterators for index ranges, returns size of object
  template <typename OopClosureType>
  void oop_iterate_range(OopClosureType* blk, int start, int end);
};

// See similar requirement for oopDesc.
static_assert(std::is_trivially_default_constructible<objArrayOopDesc>::value, "required");

#endif // SHARE_OOPS_OBJARRAYOOP_HPP
