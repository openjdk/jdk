/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_interface/collectedHeap.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/preserveException.hpp"
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

template <class T>
static void specialized_oop_follow_contents(instanceRefKlass* ref, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  T heap_oop = oopDesc::load_heap_oop(referent_addr);
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("instanceRefKlass::oop_follow_contents " INTPTR_FORMAT, obj);
    }
  )
  if (!oopDesc::is_null(heap_oop)) {
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (!referent->is_gc_marked() &&
        MarkSweep::ref_processor()->
          discover_reference(obj, ref->reference_type())) {
      // reference already enqueued, referent will be traversed later
      ref->instanceKlass::oop_follow_contents(obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL enqueued " INTPTR_FORMAT, obj);
        }
      )
      return;
    } else {
      // treat referent as normal oop
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL normal " INTPTR_FORMAT, obj);
        }
      )
      MarkSweep::mark_and_push(referent_addr);
    }
  }
  // treat next as normal oop.  next is a link in the pending list.
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("   Process next as normal " INTPTR_FORMAT, next_addr);
    }
  )
  MarkSweep::mark_and_push(next_addr);
  ref->instanceKlass::oop_follow_contents(obj);
}

void instanceRefKlass::oop_follow_contents(oop obj) {
  if (UseCompressedOops) {
    specialized_oop_follow_contents<narrowOop>(this, obj);
  } else {
    specialized_oop_follow_contents<oop>(this, obj);
  }
}

#ifndef SERIALGC
template <class T>
void specialized_oop_follow_contents(instanceRefKlass* ref,
                                     ParCompactionManager* cm,
                                     oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  T heap_oop = oopDesc::load_heap_oop(referent_addr);
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("instanceRefKlass::oop_follow_contents " INTPTR_FORMAT, obj);
    }
  )
  if (!oopDesc::is_null(heap_oop)) {
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (PSParallelCompact::mark_bitmap()->is_unmarked(referent) &&
        PSParallelCompact::ref_processor()->
          discover_reference(obj, ref->reference_type())) {
      // reference already enqueued, referent will be traversed later
      ref->instanceKlass::oop_follow_contents(cm, obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL enqueued " INTPTR_FORMAT, obj);
        }
      )
      return;
    } else {
      // treat referent as normal oop
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL normal " INTPTR_FORMAT, obj);
        }
      )
      PSParallelCompact::mark_and_push(cm, referent_addr);
    }
  }
  // treat next as normal oop.  next is a link in the pending list.
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("   Process next as normal " INTPTR_FORMAT, next_addr);
    }
  )
  PSParallelCompact::mark_and_push(cm, next_addr);
  ref->instanceKlass::oop_follow_contents(cm, obj);
}

void instanceRefKlass::oop_follow_contents(ParCompactionManager* cm,
                                           oop obj) {
  if (UseCompressedOops) {
    specialized_oop_follow_contents<narrowOop>(this, cm, obj);
  } else {
    specialized_oop_follow_contents<oop>(this, cm, obj);
  }
}
#endif // SERIALGC

#ifdef ASSERT
template <class T> void trace_reference_gc(const char *s, oop obj,
                                           T* referent_addr,
                                           T* next_addr,
                                           T* discovered_addr) {
  if(TraceReferenceGC && PrintGCDetails) {
    gclog_or_tty->print_cr("%s obj " INTPTR_FORMAT, s, (address)obj);
    gclog_or_tty->print_cr("     referent_addr/* " INTPTR_FORMAT " / "
         INTPTR_FORMAT, referent_addr,
         referent_addr ?
           (address)oopDesc::load_decode_heap_oop(referent_addr) : NULL);
    gclog_or_tty->print_cr("     next_addr/* " INTPTR_FORMAT " / "
         INTPTR_FORMAT, next_addr,
         next_addr ? (address)oopDesc::load_decode_heap_oop(next_addr) : NULL);
    gclog_or_tty->print_cr("     discovered_addr/* " INTPTR_FORMAT " / "
         INTPTR_FORMAT, discovered_addr,
         discovered_addr ?
           (address)oopDesc::load_decode_heap_oop(discovered_addr) : NULL);
  }
}
#endif

template <class T> void specialized_oop_adjust_pointers(instanceRefKlass *ref, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  MarkSweep::adjust_pointer(referent_addr);
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  MarkSweep::adjust_pointer(next_addr);
  T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
  MarkSweep::adjust_pointer(discovered_addr);
  debug_only(trace_reference_gc("instanceRefKlass::oop_adjust_pointers", obj,
                                referent_addr, next_addr, discovered_addr);)
}

int instanceRefKlass::oop_adjust_pointers(oop obj) {
  int size = size_helper();
  instanceKlass::oop_adjust_pointers(obj);

  if (UseCompressedOops) {
    specialized_oop_adjust_pointers<narrowOop>(this, obj);
  } else {
    specialized_oop_adjust_pointers<oop>(this, obj);
  }
  return size;
}

#define InstanceRefKlass_SPECIALIZED_OOP_ITERATE(T, nv_suffix, contains)        \
  if (closure->apply_to_weak_ref_discovered_field()) {                          \
    T* disc_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);           \
    closure->do_oop##nv_suffix(disc_addr);                                      \
  }                                                                             \
                                                                                \
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);           \
  T heap_oop = oopDesc::load_heap_oop(referent_addr);                           \
  if (!oopDesc::is_null(heap_oop) && contains(referent_addr)) {                 \
    ReferenceProcessor* rp = closure->_ref_processor;                           \
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);                 \
    if (!referent->is_gc_marked() && (rp != NULL) &&                            \
        rp->discover_reference(obj, reference_type())) {                        \
      return size;                                                              \
    } else {                                                                    \
      /* treat referent as normal oop */                                        \
      SpecializationStats::record_do_oop_call##nv_suffix(SpecializationStats::irk);\
      closure->do_oop##nv_suffix(referent_addr);                                \
    }                                                                           \
  }                                                                             \
  /* treat next as normal oop */                                                \
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);                   \
  if (contains(next_addr)) {                                                    \
    SpecializationStats::record_do_oop_call##nv_suffix(SpecializationStats::irk); \
    closure->do_oop##nv_suffix(next_addr);                                      \
  }                                                                             \
  return size;                                                                  \


template <class T> bool contains(T *t) { return true; }

// Macro to define instanceRefKlass::oop_oop_iterate for virtual/nonvirtual for
// all closures.  Macros calling macros above for each oop size.

#define InstanceRefKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)        \
                                                                                \
int instanceRefKlass::                                                          \
oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) {                  \
  /* Get size before changing pointers */                                       \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
                                                                                \
  int size = instanceKlass::oop_oop_iterate##nv_suffix(obj, closure);           \
                                                                                \
  if (UseCompressedOops) {                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, contains);   \
  } else {                                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, contains);         \
  }                                                                             \
}

#ifndef SERIALGC
#define InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix) \
                                                                                \
int instanceRefKlass::                                                          \
oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure) {        \
  /* Get size before changing pointers */                                       \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
                                                                                \
  int size = instanceKlass::oop_oop_iterate_backwards##nv_suffix(obj, closure); \
                                                                                \
  if (UseCompressedOops) {                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, contains);   \
  } else {                                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, contains);         \
  }                                                                             \
}
#endif // !SERIALGC


#define InstanceRefKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix)      \
                                                                                \
int instanceRefKlass::                                                          \
oop_oop_iterate##nv_suffix##_m(oop obj,                                         \
                               OopClosureType* closure,                         \
                               MemRegion mr) {                                  \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
                                                                                \
  int size = instanceKlass::oop_oop_iterate##nv_suffix##_m(obj, closure, mr);   \
  if (UseCompressedOops) {                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, mr.contains); \
  } else {                                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, mr.contains);      \
  }                                                                             \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_DEFN)
#ifndef SERIALGC
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
#endif // SERIALGC
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_DEFN_m)

#ifndef SERIALGC
template <class T>
void specialized_oop_push_contents(instanceRefKlass *ref,
                                   PSPromotionManager* pm, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  if (PSScavenge::should_scavenge(referent_addr)) {
    ReferenceProcessor* rp = PSScavenge::reference_processor();
    if (rp->discover_reference(obj, ref->reference_type())) {
      // reference already enqueued, referent and next will be traversed later
      ref->instanceKlass::oop_push_contents(pm, obj);
      return;
    } else {
      // treat referent as normal oop
      pm->claim_or_forward_depth(referent_addr);
    }
  }
  // treat next as normal oop
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  if (PSScavenge::should_scavenge(next_addr)) {
    pm->claim_or_forward_depth(next_addr);
  }
  ref->instanceKlass::oop_push_contents(pm, obj);
}

void instanceRefKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  if (UseCompressedOops) {
    specialized_oop_push_contents<narrowOop>(this, pm, obj);
  } else {
    specialized_oop_push_contents<oop>(this, pm, obj);
  }
}

template <class T>
void specialized_oop_update_pointers(instanceRefKlass *ref,
                                    ParCompactionManager* cm, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  PSParallelCompact::adjust_pointer(referent_addr);
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  PSParallelCompact::adjust_pointer(next_addr);
  T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
  PSParallelCompact::adjust_pointer(discovered_addr);
  debug_only(trace_reference_gc("instanceRefKlass::oop_update_ptrs", obj,
                                referent_addr, next_addr, discovered_addr);)
}

int instanceRefKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  instanceKlass::oop_update_pointers(cm, obj);
  if (UseCompressedOops) {
    specialized_oop_update_pointers<narrowOop>(this, cm, obj);
  } else {
    specialized_oop_update_pointers<oop>(this, cm, obj);
  }
  return size_helper();
}


template <class T> void
specialized_oop_update_pointers(ParCompactionManager* cm, oop obj,
                                HeapWord* beg_addr, HeapWord* end_addr) {
  T* p;
  T* referent_addr = p = (T*)java_lang_ref_Reference::referent_addr(obj);
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  T* next_addr = p = (T*)java_lang_ref_Reference::next_addr(obj);
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  T* discovered_addr = p = (T*)java_lang_ref_Reference::discovered_addr(obj);
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  debug_only(trace_reference_gc("instanceRefKlass::oop_update_ptrs", obj,
                                referent_addr, next_addr, discovered_addr);)
}

int
instanceRefKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                      HeapWord* beg_addr, HeapWord* end_addr) {
  instanceKlass::oop_update_pointers(cm, obj, beg_addr, end_addr);
  if (UseCompressedOops) {
    specialized_oop_update_pointers<narrowOop>(cm, obj, beg_addr, end_addr);
  } else {
    specialized_oop_update_pointers<oop>(cm, obj, beg_addr, end_addr);
  }
  return size_helper();
}
#endif // SERIALGC

void instanceRefKlass::update_nonstatic_oop_maps(klassOop k) {
  // Clear the nonstatic oop-map entries corresponding to referent
  // and nextPending field.  They are treated specially by the
  // garbage collector.
  // The discovered field is used only by the garbage collector
  // and is also treated specially.
  instanceKlass* ik = instanceKlass::cast(k);

  // Check that we have the right class
  debug_only(static bool first_time = true);
  assert(k == SystemDictionary::Reference_klass() && first_time,
         "Invalid update of maps");
  debug_only(first_time = false);
  assert(ik->nonstatic_oop_map_count() == 1, "just checking");

  OopMapBlock* map = ik->start_of_nonstatic_oop_maps();

  // Check that the current map is (2,4) - currently points at field with
  // offset 2 (words) and has 4 map entries.
  debug_only(int offset = java_lang_ref_Reference::referent_offset);
  debug_only(unsigned int count = ((java_lang_ref_Reference::discovered_offset -
    java_lang_ref_Reference::referent_offset)/heapOopSize) + 1);

  if (UseSharedSpaces) {
    assert(map->offset() == java_lang_ref_Reference::queue_offset &&
           map->count() == 1, "just checking");
  } else {
    assert(map->offset() == offset && map->count() == count,
           "just checking");

    // Update map to (3,1) - point to offset of 3 (words) with 1 map entry.
    map->set_offset(java_lang_ref_Reference::queue_offset);
    map->set_count(1);
  }
}


// Verification

void instanceRefKlass::oop_verify_on(oop obj, outputStream* st) {
  instanceKlass::oop_verify_on(obj, st);
  // Verify referent field
  oop referent = java_lang_ref_Reference::referent(obj);

  // We should make this general to all heaps
  GenCollectedHeap* gch = NULL;
  if (Universe::heap()->kind() == CollectedHeap::GenCollectedHeap)
    gch = GenCollectedHeap::heap();

  if (referent != NULL) {
    guarantee(referent->is_oop(), "referent field heap failed");
    if (gch != NULL && !gch->is_in_youngest(obj)) {
      // We do a specific remembered set check here since the referent
      // field is not part of the oop mask and therefore skipped by the
      // regular verify code.
      if (UseCompressedOops) {
        narrowOop* referent_addr = (narrowOop*)java_lang_ref_Reference::referent_addr(obj);
        obj->verify_old_oop(referent_addr, true);
      } else {
        oop* referent_addr = (oop*)java_lang_ref_Reference::referent_addr(obj);
        obj->verify_old_oop(referent_addr, true);
      }
    }
  }
  // Verify next field
  oop next = java_lang_ref_Reference::next(obj);
  if (next != NULL) {
    guarantee(next->is_oop(), "next field verify failed");
    guarantee(next->is_instanceRef(), "next field verify failed");
    if (gch != NULL && !gch->is_in_youngest(obj)) {
      // We do a specific remembered set check here since the next field is
      // not part of the oop mask and therefore skipped by the regular
      // verify code.
      if (UseCompressedOops) {
        narrowOop* next_addr = (narrowOop*)java_lang_ref_Reference::next_addr(obj);
        obj->verify_old_oop(next_addr, true);
      } else {
        oop* next_addr = (oop*)java_lang_ref_Reference::next_addr(obj);
        obj->verify_old_oop(next_addr, true);
      }
    }
  }
}

void instanceRefKlass::acquire_pending_list_lock(BasicLock *pending_list_basic_lock) {
  // we may enter this with pending exception set
  PRESERVE_EXCEPTION_MARK;  // exceptions are never thrown, needed for TRAPS argument
  Handle h_lock(THREAD, java_lang_ref_Reference::pending_list_lock());
  ObjectSynchronizer::fast_enter(h_lock, pending_list_basic_lock, false, THREAD);
  assert(ObjectSynchronizer::current_thread_holds_lock(
           JavaThread::current(), h_lock),
         "Locking should have succeeded");
  if (HAS_PENDING_EXCEPTION) CLEAR_PENDING_EXCEPTION;
}

void instanceRefKlass::release_and_notify_pending_list_lock(
  BasicLock *pending_list_basic_lock) {
  // we may enter this with pending exception set
  PRESERVE_EXCEPTION_MARK;  // exceptions are never thrown, needed for TRAPS argument
  //
  Handle h_lock(THREAD, java_lang_ref_Reference::pending_list_lock());
  assert(ObjectSynchronizer::current_thread_holds_lock(
           JavaThread::current(), h_lock),
         "Lock should be held");
  // Notify waiters on pending lists lock if there is any reference.
  if (java_lang_ref_Reference::pending_list() != NULL) {
    ObjectSynchronizer::notifyall(h_lock, THREAD);
  }
  ObjectSynchronizer::fast_exit(h_lock(), pending_list_basic_lock, THREAD);
  if (HAS_PENDING_EXCEPTION) CLEAR_PENDING_EXCEPTION;
}
