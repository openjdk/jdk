/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/specialized_oop_closures.hpp"
#include "oops/instanceKlass.hpp"
#include "utilities/macros.hpp"

class ClassFileParser;

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
 private:
  InstanceRefKlass(const ClassFileParser& parser) : InstanceKlass(parser, InstanceKlass::_misc_kind_reference) {}

 public:
  InstanceRefKlass() { assert(DumpSharedSpaces || UseSharedSpaces, "only for CDS"); }

  // GC specific object visitors
  //
  // Mark Sweep
  int  oop_ms_adjust_pointers(oop obj);
#if INCLUDE_ALL_GCS
  // Parallel Scavenge
  void oop_ps_push_contents(  oop obj, PSPromotionManager* pm);
  // Parallel Compact
  void oop_pc_follow_contents(oop obj, ParCompactionManager* cm);
  void oop_pc_update_pointers(oop obj);
#endif

  // Oop fields (and metadata) iterators
  //  [nv = true]  Use non-virtual calls to do_oop_nv.
  //  [nv = false] Use virtual calls to do_oop.
  //
  // The InstanceRefKlass iterators also support reference processing.


  // Forward iteration
private:
  // Iterate over all oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate(oop obj, OopClosureType* closure);

  // Reverse iteration
#if INCLUDE_ALL_GCS
  // Iterate over all oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_reverse(oop obj, OopClosureType* closure);
#endif // INCLUDE_ALL_GCS

  // Bounded range iteration
  // Iterate over all oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr);

  // Reference processing part of the iterators.

  // Specialized for [T = oop] or [T = narrowOop].
  template <bool nv, typename T, class OopClosureType, class Contains>
  inline void oop_oop_iterate_ref_processing_specialized(oop obj, OopClosureType* closure, Contains& contains);

  // Only perform reference processing if the referent object is within mr.
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_ref_processing_bounded(oop obj, OopClosureType* closure, MemRegion mr);

  // Reference processing
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_ref_processing(oop obj, OopClosureType* closure);


 public:

  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL)

#if INCLUDE_ALL_GCS
  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL_BACKWARDS)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL_BACKWARDS)
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
