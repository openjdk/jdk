/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_OOPS_FLATARRAYKLASS_INLINE_HPP
#define SHARE_VM_OOPS_FLATARRAYKLASS_INLINE_HPP

#include "oops/flatArrayKlass.hpp"

#include "memory/iterator.hpp"
#include "memory/memRegion.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/flatArrayOop.hpp"
#include "oops/flatArrayOop.inline.hpp"
#include "oops/inlineKlass.hpp"
#include "oops/inlineKlass.inline.hpp"
#include "oops/klass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/devirtualizer.inline.hpp"
#include "utilities/macros.hpp"

/*
 * Warning incomplete: requires embedded oops, not yet enabled, so consider this a "sketch-up" of oop iterators
 */

template <typename T, class OopClosureType>
void FlatArrayKlass::oop_oop_iterate_elements_specialized(flatArrayOop a,
                                                          OopClosureType* closure,
                                                          int start, int end) {
  precond(contains_oops());
  precond(start >= 0);
  assert(start <= end, "Invalid range [%d - %d)", start, end);
  assert(end <= a->length(), "Invalid range [%d - %d) for a.length: %d", start, end, a->length());

  const int shift = Klass::layout_helper_log2_element_size(layout_helper());
  const uintptr_t base = (uintptr_t) a->base();
  const uintptr_t start_addr = base + ((size_t)start << shift);
  const uintptr_t stop_addr = base + ((size_t)end << shift);

  oop_oop_iterate_elements_specialized_bounded<T>(a, closure, start_addr, stop_addr);
}

template <typename T, class OopClosureType>
void FlatArrayKlass::oop_oop_iterate_elements_specialized_bounded(flatArrayOop a,
                                                                  OopClosureType* closure,
                                                                  uintptr_t lo, uintptr_t hi) {
  assert(contains_oops(), "Nothing to iterate");

  const int shift = Klass::layout_helper_log2_element_size(layout_helper());
  const int addr_incr = 1 << shift;
  uintptr_t elem_addr = (uintptr_t)a->base();
  uintptr_t stop_addr = elem_addr + ((uintptr_t)a->length() << shift);
  const int oop_offset = element_klass()->payload_offset();

  if (elem_addr < lo) {
    uintptr_t diff = lo - elem_addr;
    elem_addr += (diff >> shift) << shift;
  }
  if (stop_addr > hi) {
    uintptr_t diff = stop_addr - hi;
    stop_addr -= (diff >> shift) << shift;
  }

  const uintptr_t end = stop_addr;
  while (elem_addr < end) {
    element_klass()->oop_iterate_specialized_bounded<T>((address)(elem_addr - oop_offset), closure, lo, hi);
    elem_addr += addr_incr;
  }
}

template <typename T, class OopClosureType>
void FlatArrayKlass::oop_oop_iterate_elements(flatArrayOop a, OopClosureType* closure) {
  if (contains_oops()) {
    oop_oop_iterate_elements_specialized<T>(a, closure, 0, a->length());
  }
}

template <typename T, typename OopClosureType>
void FlatArrayKlass::oop_oop_iterate(oop obj, OopClosureType* closure) {
  assert(obj->is_flatArray(), "must be a flat array");
  flatArrayOop a = flatArrayOop(obj);

  if (Devirtualizer::do_metadata(closure)) {
    Devirtualizer::do_klass(closure, obj->klass());
  }

  oop_oop_iterate_elements<T>(a, closure);
}

template <typename T, typename OopClosureType>
void FlatArrayKlass::oop_oop_iterate_reverse(oop obj, OopClosureType* closure) {
  // TODO
  oop_oop_iterate<T>(obj, closure);
}

template <typename T, class OopClosureType>
void FlatArrayKlass::oop_oop_iterate_elements_bounded(flatArrayOop a, OopClosureType* closure, MemRegion mr) {
  if (contains_oops()) {
    oop_oop_iterate_elements_specialized_bounded<T>(a, closure, (uintptr_t)mr.start(), (uintptr_t)mr.end());
  }
}

template <typename T, typename OopClosureType>
void FlatArrayKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  flatArrayOop a = flatArrayOop(obj);
  if (Devirtualizer::do_metadata(closure)) {
    Devirtualizer::do_klass(closure, a->klass());
  }
  oop_oop_iterate_elements_bounded<T>(a, closure, mr);
}

// Like oop_oop_iterate but only iterates over the specified range [start, end)
template <typename T, class OopClosureType>
void FlatArrayKlass::oop_oop_iterate_elements_range(flatArrayOop a, OopClosureType *closure, int start, int end) {
  if (contains_oops()) {
    oop_oop_iterate_elements_specialized<T>(a, closure, start, end);
  }
}

#endif // SHARE_VM_OOPS_FLATARRAYKLASS_INLINE_HPP
