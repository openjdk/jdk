/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/permGen.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#ifndef SERIALGC
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/parNew/parOopClosures.inline.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.inline.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#endif

int instanceMirrorKlass::_offset_of_static_fields = 0;

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


void instanceMirrorKlass::oop_follow_contents(oop obj) {
  instanceKlass::oop_follow_contents(obj);
  InstanceMirrorKlass_OOP_ITERATE(                                                    \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),        \
    MarkSweep::mark_and_push(p),                                                      \
    assert_is_in_closed_subset)
}

#ifndef SERIALGC
void instanceMirrorKlass::oop_follow_contents(ParCompactionManager* cm,
                                              oop obj) {
  instanceKlass::oop_follow_contents(cm, obj);
  InstanceMirrorKlass_OOP_ITERATE(                                                    \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),        \
    PSParallelCompact::mark_and_push(cm, p),                                          \
    assert_is_in)
}
#endif // SERIALGC

int instanceMirrorKlass::oop_adjust_pointers(oop obj) {
  int size = oop_size(obj);
  instanceKlass::oop_adjust_pointers(obj);
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


// Macro to define instanceMirrorKlass::oop_oop_iterate for virtual/nonvirtual for
// all closures.  Macros calling macros above for each oop size.

#define InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)           \
                                                                                      \
int instanceMirrorKlass::                                                             \
oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) {                        \
  /* Get size before changing pointers */                                             \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);      \
                                                                                      \
  instanceKlass::oop_oop_iterate##nv_suffix(obj, closure);                            \
                                                                                      \
  if (UseCompressedOops) {                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(narrowOop, nv_suffix);           \
  } else {                                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(oop, nv_suffix);                 \
  }                                                                                   \
}

#ifndef SERIALGC
#define InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix) \
                                                                                      \
int instanceMirrorKlass::                                                             \
oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure) {              \
  /* Get size before changing pointers */                                             \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);      \
                                                                                      \
  instanceKlass::oop_oop_iterate_backwards##nv_suffix(obj, closure);                  \
                                                                                      \
  if (UseCompressedOops) {                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(narrowOop, nv_suffix);           \
  } else {                                                                            \
    InstanceMirrorKlass_SPECIALIZED_OOP_ITERATE_DEFN(oop, nv_suffix);                 \
  }                                                                                   \
}
#endif // !SERIALGC


#define InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix)         \
                                                                                      \
int instanceMirrorKlass::                                                             \
oop_oop_iterate##nv_suffix##_m(oop obj,                                               \
                               OopClosureType* closure,                               \
                               MemRegion mr) {                                        \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);      \
                                                                                      \
  instanceKlass::oop_oop_iterate##nv_suffix##_m(obj, closure, mr);                    \
  if (UseCompressedOops) {                                                            \
    InstanceMirrorKlass_BOUNDED_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, mr);    \
  } else {                                                                            \
    InstanceMirrorKlass_BOUNDED_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, mr);          \
  }                                                                                   \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN)
#ifndef SERIALGC
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
#endif // SERIALGC
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_DEFN_m)

#ifndef SERIALGC
void instanceMirrorKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  instanceKlass::oop_push_contents(pm, obj);
  InstanceMirrorKlass_OOP_ITERATE(                                            \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),\
    if (PSScavenge::should_scavenge(p)) {                                     \
      pm->claim_or_forward_depth(p);                                          \
    },                                                                        \
    assert_nothing )
}

int instanceMirrorKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  instanceKlass::oop_update_pointers(cm, obj);
  InstanceMirrorKlass_OOP_ITERATE(                                            \
    start_of_static_fields(obj), java_lang_Class::static_oop_field_count(obj),\
    PSParallelCompact::adjust_pointer(p),                                     \
    assert_nothing)
  return oop_size(obj);
}
#endif // SERIALGC

int instanceMirrorKlass::instance_size(KlassHandle k) {
  if (k() != NULL && k->oop_is_instance()) {
    return align_object_size(size_helper() + instanceKlass::cast(k())->static_field_size());
  }
  return size_helper();
}

instanceOop instanceMirrorKlass::allocate_instance(KlassHandle k, TRAPS) {
  // Query before forming handle.
  int size = instance_size(k);
  KlassHandle h_k(THREAD, as_klassOop());
  instanceOop i;

  if (JavaObjectsInPerm) {
    i = (instanceOop) CollectedHeap::permanent_obj_allocate(h_k, size, CHECK_NULL);
  } else {
    assert(ScavengeRootsInCode > 0, "must be");
    i = (instanceOop) CollectedHeap::obj_allocate(h_k, size, CHECK_NULL);
  }

  return i;
}

int instanceMirrorKlass::oop_size(oop obj) const {
  return java_lang_Class::oop_size(obj);
}

int instanceMirrorKlass::compute_static_oop_field_count(oop obj) {
  klassOop k = java_lang_Class::as_klassOop(obj);
  if (k != NULL && k->klass_part()->oop_is_instance()) {
    return instanceKlass::cast(k)->static_oop_field_count();
  }
  return 0;
}
