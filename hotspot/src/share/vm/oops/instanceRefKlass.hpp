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

#ifndef SHARE_VM_OOPS_INSTANCEREFKLASS_HPP
#define SHARE_VM_OOPS_INSTANCEREFKLASS_HPP

#include "oops/instanceKlass.hpp"
#include "utilities/macros.hpp"

// An InstanceRefKlass is a specialized InstanceKlass for Java
// classes that are subclasses of java/lang/ref/Reference.
//
// These classes are used to implement soft/weak/final/phantom
// references and finalization, and need special treatment by the
// garbage collector.
//
// During GC discovered reference objects are added (chained) to one
// of the four lists below, depending on the type of reference.
// The linked occurs through the next field in class java/lang/ref/Reference.
//
// Afterwards, the discovered references are processed in decreasing
// order of reachability. Reference objects eligible for notification
// are linked to the static pending_list in class java/lang/ref/Reference,
// and the pending list lock object in the same class is notified.


class InstanceRefKlass: public InstanceKlass {
  friend class InstanceKlass;

  // Constructor
  InstanceRefKlass(int vtable_len, int itable_len, int static_field_size, int nonstatic_oop_map_size, ReferenceType rt, AccessFlags access_flags, bool is_anonymous)
    : InstanceKlass(vtable_len, itable_len, static_field_size, nonstatic_oop_map_size, rt, access_flags, is_anonymous) {}

 public:
  InstanceRefKlass() { assert(DumpSharedSpaces || UseSharedSpaces, "only for CDS"); }
  // Type testing
  bool oop_is_instanceRef() const             { return true; }

  // Casting from Klass*
  static InstanceRefKlass* cast(Klass* k) {
    assert(k->oop_is_instanceRef(), "cast to InstanceRefKlass");
    return (InstanceRefKlass*) k;
  }

  // Garbage collection
  int  oop_adjust_pointers(oop obj);
  void oop_follow_contents(oop obj);

  // Parallel Scavenge and Parallel Old
  PARALLEL_GC_DECLS

  int oop_oop_iterate(oop obj, ExtendedOopClosure* blk) {
    return oop_oop_iterate_v(obj, blk);
  }
  int oop_oop_iterate_m(oop obj, ExtendedOopClosure* blk, MemRegion mr) {
    return oop_oop_iterate_v_m(obj, blk, mr);
  }

#define InstanceRefKlass_OOP_OOP_ITERATE_DECL(OopClosureType, nv_suffix)                \
  int oop_oop_iterate##nv_suffix(oop obj, OopClosureType* blk);                         \
  int oop_oop_iterate##nv_suffix##_m(oop obj, OopClosureType* blk, MemRegion mr);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_DECL)

#if INCLUDE_ALL_GCS
#define InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DECL(OopClosureType, nv_suffix)      \
  int oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* blk);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DECL)
#endif // INCLUDE_ALL_GCS

  static void release_and_notify_pending_list_lock(BasicLock *pending_list_basic_lock);
  static void acquire_pending_list_lock(BasicLock *pending_list_basic_lock);
  static bool owns_pending_list_lock(JavaThread* thread);

  // Update non-static oop maps so 'referent', 'nextPending' and
  // 'discovered' will look like non-oops
  static void update_nonstatic_oop_maps(Klass* k);

 public:
  // Verification
  void oop_verify_on(oop obj, outputStream* st);
};

#endif // SHARE_VM_OOPS_INSTANCEREFKLASS_HPP
