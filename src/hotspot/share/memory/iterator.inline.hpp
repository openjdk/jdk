/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_ITERATOR_INLINE_HPP
#define SHARE_VM_MEMORY_ITERATOR_INLINE_HPP

#include "classfile/classLoaderData.hpp"
#include "memory/iterator.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/klass.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/instanceClassLoaderKlass.inline.hpp"
#include "oops/instanceRefKlass.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/typeArrayKlass.inline.hpp"
#include "utilities/debug.hpp"

inline void MetadataAwareOopClosure::do_cld_nv(ClassLoaderData* cld) {
  bool claim = true;  // Must claim the class loader data before processing.
  cld->oops_do(this, claim);
}

inline void MetadataAwareOopClosure::do_klass_nv(Klass* k) {
  ClassLoaderData* cld = k->class_loader_data();
  do_cld_nv(cld);
}

#ifdef ASSERT
// This verification is applied to all visited oops.
// The closures can turn is off by overriding should_verify_oops().
template <typename T>
void ExtendedOopClosure::verify(T* p) {
  if (should_verify_oops()) {
    T heap_oop = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(heap_oop)) {
      oop o = CompressedOops::decode_not_null(heap_oop);
      assert(Universe::heap()->is_in_closed_subset(o),
             "should be in closed *p " PTR_FORMAT " " PTR_FORMAT, p2i(p), p2i(o));
    }
  }
}
#endif

// Implementation of the non-virtual do_oop dispatch.

template <class OopClosureType, typename T>
inline void Devirtualizer<true>::do_oop(OopClosureType* closure, T* p) {
  debug_only(closure->verify(p));
  closure->do_oop_nv(p);
}
template <class OopClosureType>
inline void Devirtualizer<true>::do_klass(OopClosureType* closure, Klass* k) {
  closure->do_klass_nv(k);
}
template <class OopClosureType>
void Devirtualizer<true>::do_cld(OopClosureType* closure, ClassLoaderData* cld) {
  closure->do_cld_nv(cld);
}
template <class OopClosureType>
inline bool Devirtualizer<true>::do_metadata(OopClosureType* closure) {
  // Make sure the non-virtual and the virtual versions match.
  assert(closure->do_metadata_nv() == closure->do_metadata(), "Inconsistency in do_metadata");
  return closure->do_metadata_nv();
}

// Implementation of the virtual do_oop dispatch.

template <class OopClosureType, typename T>
void Devirtualizer<false>::do_oop(OopClosureType* closure, T* p) {
  debug_only(closure->verify(p));
  closure->do_oop(p);
}
template <class OopClosureType>
void Devirtualizer<false>::do_klass(OopClosureType* closure, Klass* k) {
  closure->do_klass(k);
}
template <class OopClosureType>
void Devirtualizer<false>::do_cld(OopClosureType* closure, ClassLoaderData* cld) {
  closure->do_cld(cld);
}
template <class OopClosureType>
bool Devirtualizer<false>::do_metadata(OopClosureType* closure) {
  return closure->do_metadata();
}

// The list of all "specializable" oop_oop_iterate function definitions.
#define ALL_KLASS_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)                  \
  ALL_INSTANCE_KLASS_OOP_OOP_ITERATE_DEFN(             OopClosureType, nv_suffix)  \
  ALL_INSTANCE_REF_KLASS_OOP_OOP_ITERATE_DEFN(         OopClosureType, nv_suffix)  \
  ALL_INSTANCE_MIRROR_KLASS_OOP_OOP_ITERATE_DEFN(      OopClosureType, nv_suffix)  \
  ALL_INSTANCE_CLASS_LOADER_KLASS_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)  \
  ALL_OBJ_ARRAY_KLASS_OOP_OOP_ITERATE_DEFN(            OopClosureType, nv_suffix)  \
  ALL_TYPE_ARRAY_KLASS_OOP_OOP_ITERATE_DEFN(           OopClosureType, nv_suffix)

#endif // SHARE_VM_MEMORY_ITERATOR_INLINE_HPP
