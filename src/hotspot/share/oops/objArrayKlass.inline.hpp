/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OBJARRAYKLASS_INLINE_HPP
#define SHARE_OOPS_OBJARRAYKLASS_INLINE_HPP

#include "oops/objArrayKlass.hpp"

#include "memory/memRegion.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayOop.inline.hpp"
#include "oops/klass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/devirtualizer.inline.hpp"
#include "utilities/macros.hpp"

template <typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements(objArrayOop a, OopClosureType* closure) {
  T* p         = (T*)a->base();
  T* const end = p + a->length();

  for (;p < end; p++) {
    Devirtualizer::do_oop(closure, p);
  }
}

template <typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements_bounded(
    objArrayOop a, OopClosureType* closure, void* low, void* high) {

  T* const l = (T*)low;
  T* const h = (T*)high;

  T* p   = (T*)a->base();
  T* end = p + a->length();

  if (p < l) {
    p = l;
  }
  if (end > h) {
    end = h;
  }

  for (;p < end; ++p) {
    Devirtualizer::do_oop(closure, p);
  }
}

template <typename T, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate(oop obj, OopClosureType* closure, KlassLUTEntry klute) {
  assert(obj->is_array(), "obj must be array");
  objArrayOop a = objArrayOop(obj);

  if (Devirtualizer::do_metadata(closure)) {
    Devirtualizer::do_klass(closure, obj->klass());
  }

  oop_oop_iterate_elements<T>(a, closure);
}

template <typename T, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr, KlassLUTEntry klute) {
  assert(obj->is_array(), "obj must be array");
  objArrayOop a  = objArrayOop(obj);

  if (Devirtualizer::do_metadata(closure)) {
    Devirtualizer::do_klass(closure, a->klass()); // Todo: why not "this" ??
  }

  oop_oop_iterate_elements_bounded<T>(a, closure, mr.start(), mr.end());
}

template <typename T, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate_reverse(oop obj, OopClosureType* closure, KlassLUTEntry klute) {
  // No reverse implementation ATM.
  oop_oop_iterate<T>(obj, closure, klute);
}

// Like oop_oop_iterate but only iterates over a specified range and only used
// for objArrayOops.
template <HeaderMode mode, typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_range(objArrayOop a, OopClosureType* closure, int start, int end) {
  assert(start <= end, "Sanity");

  assert(start >= 0, "Sanity");

  const int len = a->length_nobranches<mode>();
  if (len < end) {
    end = len;
  }

  T* const b = (T*)a->base_nobranches<mode, T>();
  for (int i = start; i < end; i++) {
    Devirtualizer::do_oop(closure, b + i);
  }
}

DEFINE_EXACT_CAST_FUNCTIONS(ObjArrayKlass)
DEFINE_NARROW_KLASS_UTILITY_FUNCTIONS(ObjArrayKlass)

#endif // SHARE_OOPS_OBJARRAYKLASS_INLINE_HPP
