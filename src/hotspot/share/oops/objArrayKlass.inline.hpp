/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/arrayOop.hpp"
#include "oops/klass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/devirtualizer.inline.hpp"
#include "utilities/macros.hpp"


inline ObjArrayKlass* ObjArrayKlass::default_ref_array_klass_acquire() const {
  return AtomicAccess::load_acquire(&_default_ref_array_klass);
}

inline void ObjArrayKlass::release_set_default_ref_array_klass(ObjArrayKlass* k) {
  AtomicAccess::release_store(&_default_ref_array_klass, k);
}

template <typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements(objArrayOop a, OopClosureType* closure) {
  ShouldNotReachHere();
}

template <typename T, class OopClosureType>
void ObjArrayKlass::oop_oop_iterate_elements_bounded(
    objArrayOop a, OopClosureType* closure, void* low, void* high) {
  ShouldNotReachHere();

}

template <typename T, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate(oop obj, OopClosureType* closure) {
  ShouldNotReachHere();
}

template <typename T, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate_reverse(oop obj, OopClosureType* closure) {
  ShouldNotReachHere();
}

template <typename T, typename OopClosureType>
void ObjArrayKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  ShouldNotReachHere();
}

#endif // SHARE_OOPS_OBJARRAYKLASS_INLINE_HPP
