/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OBJARRAYKLASS_INLINE_HPP
#define SHARE_VM_OOPS_OBJARRAYKLASS_INLINE_HPP

#include "memory/memRegion.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/klass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/macros.hpp"

template <bool nv, typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements_specialized(objArrayOop a, OopClosureType* closure) {
  T* p         = (T*)a->base();
  T* const end = p + a->length();

  for (;p < end; p++) {
    Devirtualizer<nv>::do_oop(closure, p);
  }
}

template <bool nv, typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements_specialized_bounded(
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
    Devirtualizer<nv>::do_oop(closure, p);
  }
}

template <bool nv, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements(objArrayOop a, OopClosureType* closure) {
  if (UseCompressedOops) {
    oop_oop_iterate_elements_specialized<nv, narrowOop>(a, closure);
  } else {
    oop_oop_iterate_elements_specialized<nv, oop>(a, closure);
  }
}

template <bool nv, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements_bounded(objArrayOop a, OopClosureType* closure, MemRegion mr) {
  if (UseCompressedOops) {
    oop_oop_iterate_elements_specialized_bounded<nv, narrowOop>(a, closure, mr.start(), mr.end());
  } else {
    oop_oop_iterate_elements_specialized_bounded<nv, oop>(a, closure, mr.start(), mr.end());
  }
}

template <bool nv, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate(oop obj, OopClosureType* closure) {
  assert (obj->is_array(), "obj must be array");
  objArrayOop a = objArrayOop(obj);

  if (Devirtualizer<nv>::do_metadata(closure)) {
    Devirtualizer<nv>::do_klass(closure, obj->klass());
  }

  oop_oop_iterate_elements<nv>(a, closure);
}

template <bool nv, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  assert(obj->is_array(), "obj must be array");
  objArrayOop a  = objArrayOop(obj);

  if (Devirtualizer<nv>::do_metadata(closure)) {
    Devirtualizer<nv>::do_klass(closure, a->klass());
  }

  oop_oop_iterate_elements_bounded<nv>(a, closure, mr);
}

template <bool nv, typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_range_specialized(objArrayOop a, OopClosureType* closure, int start, int end) {
  T* low = start == 0 ? cast_from_oop<T*>(a) : a->obj_at_addr<T>(start);
  T* high = (T*)a->base() + end;

  oop_oop_iterate_elements_specialized_bounded<nv, T>(a, closure, low, high);
}

// Like oop_oop_iterate but only iterates over a specified range and only used
// for objArrayOops.
template <bool nv, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_range(oop obj, OopClosureType* closure, int start, int end) {
  assert(obj->is_array(), "obj must be array");
  objArrayOop a  = objArrayOop(obj);

  if (UseCompressedOops) {
    oop_oop_iterate_range_specialized<nv, narrowOop>(a, closure, start, end);
  } else {
    oop_oop_iterate_range_specialized<nv, oop>(a, closure, start, end);
  }
}

#define ALL_OBJ_ARRAY_KLASS_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)    \
  OOP_OOP_ITERATE_DEFN(             ObjArrayKlass, OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN_BOUNDED(     ObjArrayKlass, OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN_RANGE(       ObjArrayKlass, OopClosureType, nv_suffix)  \
  OOP_OOP_ITERATE_DEFN_NO_BACKWARDS(ObjArrayKlass, OopClosureType, nv_suffix)

#endif // SHARE_VM_OOPS_OBJARRAYKLASS_INLINE_HPP
