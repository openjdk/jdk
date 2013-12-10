/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/oopFactory.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/cmsOopClosures.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/parNew/parOopClosures.inline.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.inline.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#endif // INCLUDE_ALL_GCS

int InstanceMirrorKlass::_offset_of_static_fields = 0;

#ifdef ASSERT
template <class T> void assert_is_in(T *p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop o = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(Universe::heap()->is_in(o), "should be in heap");
  }
}
template <class T> void assert_is_in_closed_subset(T *p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop o = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(Universe::heap()->is_in_closed_subset(o), "should be in closed");
  }
}
template <class T> void assert_is_in_reserved(T *p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop o = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(Universe::heap()->is_in_reserved(o), "should be in reserved");
  }
}
template <class T> void assert_nothing(T *p) {}

#else
template <class T> void assert_is_in(T *p) {}
template <class T> void assert_is_in_closed_subset(T *p) {}
template <class T> void assert_is_in_reserved(T *p) {}
template <class T> void assert_nothing(T *p) {}
#endif // ASSERT

#define InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE( \
  T, start_p, count, do_oop,                         \
  assert_fn)                                         \
{                                                    \
  T* p         = (T*)(start_p);                      \
  T* const end = p + (count);                        \
  while (p < end) {                                  \
    (assert_fn)(p);                                  \
    do_oop;                                          \
    ++p;                                             \
  }                                                  \
}

#define InstanceMirrorKlass_SPECIALIZED_BOUNDED_OOP_ITERATE( \
  T, start_p, count, low, high,                              \
  do_oop, assert_fn)                                         \
{                                                            \
  T* const l = (T*)(low);                                    \
  T* const h = (T*)(high);                                   \
  assert(mask_bits((intptr_t)l, sizeof(T)-1) == 0 &&         \
         mask_bits((intptr_t)h, sizeof(T)-1) == 0,           \
         "bounded region must be properly aligned");         \
  T* p       = (T*)(start_p);                                \
  T* end     = p + (count);                                  \
  if (p < l) p = l;                                          \
  if (end > h) end = h;                                      \
  while (p < end) {                                          \
    (assert_fn)(p);                                          \
    do_oop;                                                  \
    ++p;                                                     \
  }                                                          \
}


#define InstanceMirrorKlass_OOP_ITERATE(start_p, count,    \
                                  do_oop, assert_fn)       \
{                                                          \
  if (UseCompressedOops) {                                 \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE(narrowOop, \
      start_p, count,                                      \
      do_oop, assert_fn)                                   \
  } else {                                                 \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE(oop,       \
      start_p, count,                                      \
      do_oop, assert_fn)                                   \
  }                                                        \
}

// The following macros call specialized macros, passing either oop or
// narrowOop as the specialization type.  These test the UseCompressedOops
// flag.
#define InstanceMirrorKlass_BOUNDED_OOP_ITERATE(start_p, count, low, high, \
                                          do_oop, assert_fn)               \
{                                                                          \
  if (UseCompressedOops) {                                                 \
    InstanceMirrorKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(narrowOop,         \
      start_p, count,                                                      \
      low, high,                                                           \
      do_oop, assert_fn)                                                   \
  } else {                                                                 \
    InstanceMirrorKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(oop,               \
      start_p, count,                                                      \
      low, high,                                                           \
      do_oop, assert_fn)                                                   \
  }                                                                        \
}


void InstanceMirrorKlass::oop_follow_contents(oop obj) {
  InstanceKlass::oop_follow_contents(obj);

  // Follow the klass field in the mirror.
  Klass* klass = java_lang_Class::as_Klass(obj);
  if (klass != NULL) {
    // An anonymous class doesn't have its own class loader, so the call
    // to follow_klass will mark and push its java mirror instead of the
    // class loader. When handling the java mirror for an anonymous class
    // we need to make sure its class loader data is claimed, this is done
    // by calling follow_class_loader explicitly. For non-anonymous classes
    // the call to follow_class_loader is made when the class loader itself
    // is handled.
    if (klass->oop_is_instance() && InstanceKlass::cast(klass)->is_anonymous()) {
      MarkSweep::follow_class_loader(klass->class_loader_data());
    } else {
      MarkSweep::follow_klass(klass);
    }
  } else {
    // If klass is NULL then this a mirror for a primitive type.
    // We don't have to follow them, since they are handled as strong
    // roots in Universe::oops_do.
    assert(java_lang_Class::is_primitive(obj), "Sanity check");
  }

  InstanceMirrorKlass_OOP_ITERATE(                                                    \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),        \
    MarkSweep::mark_and_push(p),                                                      \
    assert_is_in_closed_subset)
}

#if INCLUDE_ALL_GCS
void InstanceMirrorKlass::oop_follow_contents(ParCompactionManager* cm,
                                              oop obj) {
  InstanceKlass::oop_follow_contents(cm, obj);

  // Follow the klass field in the mirror.
  Klass* klass = java_lang_Class::as_Klass(obj);
  if (klass != NULL) {
    // An anonymous class doesn't have its own class loader, so the call
    // to follow_klass will mark and push its java mirror instead of the
    // class loader. When handling the java mirror for an anonymous class
    // we need to make sure its class loader data is claimed, this is done
    // by calling follow_class_loader explicitly. For non-anonymous classes
    // the call to follow_class_loader is made when the class loader itself
    // is handled.
    if (klass->oop_is_instance() && InstanceKlass::cast(klass)->is_anonymous()) {
      PSParallelCompact::follow_class_loader(cm, klass->class_loader_data());
    } else {
      PSParallelCompact::follow_klass(cm, klass);
    }
  } else {
    // If klass is NULL then this a mirror for a primitive type.
    // We don't have to follow them, since they are handled as strong
    // roots in Universe::oops_do.
    assert(java_lang_Class::is_primitive(obj), "Sanity check");
  }

  InstanceMirrorKlass_OOP_ITERATE(                                                    \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),        \
    PSParallelCompact::mark_and_push(cm, p),                                          \
    assert_is_in)
}
#endif // INCLUDE_ALL_GCS

int InstanceMirrorKlass::oop_adjust_pointers(oop obj) {
  int size = oop_size(obj);
  InstanceKlass::oop_adjust_pointers(obj);

  InstanceMirrorKlass_OOP_ITERATE(                                                    \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),        \
    MarkSweep::adjust_pointer(p),                                                     \
    assert_nothing)
  return size;
}

#define InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(T, nv_suffix)                \
  InstanceMirrorKlass_OOP_ITERATE(                                                    \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),        \
      (closure)->do_oop##nv_suffix(p),                                                \
    assert_is_in_closed_subset)                                                       \
  return oop_size(obj);                                                               \

#define InstanceMirrorKlass_BOUNDED_SPECIALIZED_OOP_ITERATE(T, nv_suffix, mr)         \
  InstanceMirrorKlass_BOUNDED_OOP_ITERATE(                                            \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),        \
    mr.start(), mr.end(),                                                             \
      (closure)->do_oop##nv_suffix(p),                                                \
    assert_is_in_closed_subset)                                                       \
  return oop_size(obj);                                                               \


#define if_do_metadata_checked(closure, nv_suffix)                    \
  /* Make sure the non-virtual and the virtual versions match. */     \
  assert(closure->do_metadata##nv_suffix() == closure->do_metadata(), \
      "Inconsistency in do_metadata");                                \
  if (closure->do_metadata##nv_suffix())

// Macro to define InstanceMirrorKlass::oop_oop_iterate for virtual/nonvirtual for
// all closures.  Macros calling macros above for each oop size.

#define InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)           \
                                                                                      \
int InstanceMirrorKlass::                                                             \
oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) {                        \
  /* Get size before changing pointers */                                             \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);      \
                                                                                      \
  InstanceKlass::oop_oop_iterate##nv_suffix(obj, closure);                            \
                                                                                      \
  if_do_metadata_checked(closure, nv_suffix) {                                        \
    Klass* klass = java_lang_Class::as_Klass(obj);                                    \
    /* We'll get NULL for primitive mirrors. */                                       \
    if (klass != NULL) {                                                              \
      closure->do_klass##nv_suffix(klass);                                            \
    }                                                                                 \
  }                                                                                   \
                                                                                      \
  if (UseCompressedOops) {                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(narrowOop, nv_suffix);           \
  } else {                                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(oop, nv_suffix);                 \
  }                                                                                   \
}

#if INCLUDE_ALL_GCS
#define InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix) \
                                                                                      \
int InstanceMirrorKlass::                                                             \
oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure) {              \
  /* Get size before changing pointers */                                             \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);      \
                                                                                      \
  InstanceKlass::oop_oop_iterate_backwards##nv_suffix(obj, closure);                  \
                                                                                      \
  if (UseCompressedOops) {                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(narrowOop, nv_suffix);           \
  } else {                                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(oop, nv_suffix);                 \
  }                                                                                   \
}
#endif // INCLUDE_ALL_GCS


#define InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix)         \
                                                                                      \
int InstanceMirrorKlass::                                                             \
oop_oop_iterate##nv_suffix##_m(oop obj,                                               \
                               OopClosureType* closure,                               \
                               MemRegion mr) {                                        \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);      \
                                                                                      \
  InstanceKlass::oop_oop_iterate##nv_suffix##_m(obj, closure, mr);                    \
                                                                                      \
  if_do_metadata_checked(closure, nv_suffix) {                                        \
    if (mr.contains(obj)) {                                                           \
      Klass* klass = java_lang_Class::as_Klass(obj);                                  \
      /* We'll get NULL for primitive mirrors. */                                     \
      if (klass != NULL) {                                                            \
        closure->do_klass##nv_suffix(klass);                                          \
      }                                                                               \
    }                                                                                 \
  }                                                                                   \
                                                                                      \
  if (UseCompressedOops) {                                                            \
    InstanceMirrorKlass_BOUNDED_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, mr);    \
  } else {                                                                            \
    InstanceMirrorKlass_BOUNDED_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, mr);          \
  }                                                                                   \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN)
#if INCLUDE_ALL_GCS
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
#endif // INCLUDE_ALL_GCS
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN_m)

#if INCLUDE_ALL_GCS
void InstanceMirrorKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  // Note that we don't have to follow the mirror -> klass pointer, since all
  // klasses that are dirty will be scavenged when we iterate over the
  // ClassLoaderData objects.

  InstanceKlass::oop_push_contents(pm, obj);
  InstanceMirrorKlass_OOP_ITERATE(                                            \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),\
    if (PSScavenge::should_scavenge(p)) {                                     \
      pm->claim_or_forward_depth(p);                                          \
    },                                                                        \
    assert_nothing )
}

int InstanceMirrorKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  int size = oop_size(obj);
  InstanceKlass::oop_update_pointers(cm, obj);

  InstanceMirrorKlass_OOP_ITERATE(                                            \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),\
    PSParallelCompact::adjust_pointer(p),                                     \
    assert_nothing)
  return size;
}
#endif // INCLUDE_ALL_GCS

int InstanceMirrorKlass::instance_size(KlassHandle k) {
  if (k() != NULL && k->oop_is_instance()) {
    return align_object_size(size_helper() + InstanceKlass::cast(k())->static_field_size());
  }
  return size_helper();
}

instanceOop InstanceMirrorKlass::allocate_instance(KlassHandle k, TRAPS) {
  // Query before forming handle.
  int size = instance_size(k);
  KlassHandle h_k(THREAD, this);
  instanceOop i = (instanceOop) CollectedHeap::Class_obj_allocate(h_k, size, k, CHECK_NULL);
  return i;
}

int InstanceMirrorKlass::oop_size(oop obj) const {
  return java_lang_Class::oop_size(obj);
}

int InstanceMirrorKlass::compute_static_oop_field_count(oop obj) {
  Klass* k = java_lang_Class::as_Klass(obj);
  if (k != NULL && k->oop_is_instance()) {
    return InstanceKlass::cast(k)->static_oop_field_count();
  }
  return 0;
}
