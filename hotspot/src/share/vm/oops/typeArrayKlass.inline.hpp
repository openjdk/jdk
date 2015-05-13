/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_TYPEARRAYKLASS_INLINE_HPP
#define SHARE_VM_OOPS_TYPEARRAYKLASS_INLINE_HPP

#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.hpp"

class ExtendedOopClosure;

inline int TypeArrayKlass::oop_oop_iterate_impl(oop obj, ExtendedOopClosure* closure) {
  assert(obj->is_typeArray(),"must be a type array");
  typeArrayOop t = typeArrayOop(obj);
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::TypeArrayKlass never moves.
  return t->object_size();
}

#define TypeArrayKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)  \
                                                                        \
int TypeArrayKlass::                                                    \
oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) {          \
  return oop_oop_iterate_impl(obj, closure);                            \
}

#if INCLUDE_ALL_GCS
#define TypeArrayKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix)  \
                                                                                  \
int TypeArrayKlass::                                                              \
oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure) {          \
  return oop_oop_iterate_impl(obj, closure);                                      \
}
#else
#define TypeArrayKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix)
#endif


#define TypeArrayKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix)          \
                                                                                  \
int TypeArrayKlass::                                                              \
oop_oop_iterate##nv_suffix##_m(oop obj, OopClosureType* closure, MemRegion mr) {  \
  return oop_oop_iterate_impl(obj, closure);                                      \
}

#define ALL_TYPE_ARRAY_KLASS_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)  \
  TypeArrayKlass_OOP_OOP_ITERATE_DEFN(          OopClosureType, nv_suffix)    \
  TypeArrayKlass_OOP_OOP_ITERATE_DEFN_m(        OopClosureType, nv_suffix)    \
  TypeArrayKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix)

#endif // SHARE_VM_OOPS_TYPEARRAYKLASS_INLINE_HPP
