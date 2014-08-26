/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/heapRegionManager.inline.hpp"
#include "gc_implementation/parNew/parOopClosures.inline.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.inline.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#endif // INCLUDE_ALL_GCS

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

template <class T>
void specialized_oop_follow_contents(InstanceRefKlass* ref, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  T heap_oop = oopDesc::load_heap_oop(referent_addr);
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("InstanceRefKlass::oop_follow_contents " INTPTR_FORMAT, (void *)obj);
    }
  )
  if (!oopDesc::is_null(heap_oop)) {
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (!referent->is_gc_marked() &&
        MarkSweep::ref_processor()->discover_reference(obj, ref->reference_type())) {
      // reference was discovered, referent will be traversed later
      ref->InstanceKlass::oop_follow_contents(obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL enqueued " INTPTR_FORMAT, (void *)obj);
        }
      )
      return;
    } else {
      // treat referent as normal oop
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL normal " INTPTR_FORMAT, (void *)obj);
        }
      )
      MarkSweep::mark_and_push(referent_addr);
    }
  }
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  if (ReferenceProcessor::pending_list_uses_discovered_field()) {
    // Treat discovered as normal oop, if ref is not "active",
    // i.e. if next is non-NULL.
    T  next_oop = oopDesc::load_heap_oop(next_addr);
    if (!oopDesc::is_null(next_oop)) { // i.e. ref is not "active"
      T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("   Process discovered as normal "
                                 INTPTR_FORMAT, discovered_addr);
        }
      )
      MarkSweep::mark_and_push(discovered_addr);
    }
  } else {
#ifdef ASSERT
    // In the case of older JDKs which do not use the discovered
    // field for the pending list, an inactive ref (next != NULL)
    // must always have a NULL discovered field.
    oop next = oopDesc::load_decode_heap_oop(next_addr);
    oop discovered = java_lang_ref_Reference::discovered(obj);
    assert(oopDesc::is_null(next) || oopDesc::is_null(discovered),
           err_msg("Found an inactive reference " PTR_FORMAT " with a non-NULL discovered field",
                   (oopDesc*)obj));
#endif
  }
  // treat next as normal oop.  next is a link in the reference queue.
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("   Process next as normal " INTPTR_FORMAT, next_addr);
    }
  )
  MarkSweep::mark_and_push(next_addr);
  ref->InstanceKlass::oop_follow_contents(obj);
}

void InstanceRefKlass::oop_follow_contents(oop obj) {
  if (UseCompressedOops) {
    specialized_oop_follow_contents<narrowOop>(this, obj);
  } else {
    specialized_oop_follow_contents<oop>(this, obj);
  }
}

#if INCLUDE_ALL_GCS
template <class T>
void specialized_oop_follow_contents(InstanceRefKlass* ref,
                                     ParCompactionManager* cm,
                                     oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  T heap_oop = oopDesc::load_heap_oop(referent_addr);
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("InstanceRefKlass::oop_follow_contents " INTPTR_FORMAT, (void *)obj);
    }
  )
  if (!oopDesc::is_null(heap_oop)) {
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (PSParallelCompact::mark_bitmap()->is_unmarked(referent) &&
        PSParallelCompact::ref_processor()->
          discover_reference(obj, ref->reference_type())) {
      // reference already enqueued, referent will be traversed later
      ref->InstanceKlass::oop_follow_contents(cm, obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL enqueued " INTPTR_FORMAT, (void *)obj);
        }
      )
      return;
    } else {
      // treat referent as normal oop
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL normal " INTPTR_FORMAT, (void *)obj);
        }
      )
      PSParallelCompact::mark_and_push(cm, referent_addr);
    }
  }
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  if (ReferenceProcessor::pending_list_uses_discovered_field()) {
    // Treat discovered as normal oop, if ref is not "active",
    // i.e. if next is non-NULL.
    T  next_oop = oopDesc::load_heap_oop(next_addr);
    if (!oopDesc::is_null(next_oop)) { // i.e. ref is not "active"
      T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("   Process discovered as normal "
                                 INTPTR_FORMAT, discovered_addr);
        }
      )
      PSParallelCompact::mark_and_push(cm, discovered_addr);
    }
  } else {
#ifdef ASSERT
    // In the case of older JDKs which do not use the discovered
    // field for the pending list, an inactive ref (next != NULL)
    // must always have a NULL discovered field.
    T next = oopDesc::load_heap_oop(next_addr);
    oop discovered = java_lang_ref_Reference::discovered(obj);
    assert(oopDesc::is_null(next) || oopDesc::is_null(discovered),
           err_msg("Found an inactive reference " PTR_FORMAT " with a non-NULL discovered field",
                   (oopDesc*)obj));
#endif
  }
  PSParallelCompact::mark_and_push(cm, next_addr);
  ref->InstanceKlass::oop_follow_contents(cm, obj);
}

void InstanceRefKlass::oop_follow_contents(ParCompactionManager* cm,
                                           oop obj) {
  if (UseCompressedOops) {
    specialized_oop_follow_contents<narrowOop>(this, cm, obj);
  } else {
    specialized_oop_follow_contents<oop>(this, cm, obj);
  }
}
#endif // INCLUDE_ALL_GCS

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

template <class T> void specialized_oop_adjust_pointers(InstanceRefKlass *ref, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  MarkSweep::adjust_pointer(referent_addr);
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  MarkSweep::adjust_pointer(next_addr);
  T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
  MarkSweep::adjust_pointer(discovered_addr);
  debug_only(trace_reference_gc("InstanceRefKlass::oop_adjust_pointers", obj,
                                referent_addr, next_addr, discovered_addr);)
}

int InstanceRefKlass::oop_adjust_pointers(oop obj) {
  int size = size_helper();
  InstanceKlass::oop_adjust_pointers(obj);

  if (UseCompressedOops) {
    specialized_oop_adjust_pointers<narrowOop>(this, obj);
  } else {
    specialized_oop_adjust_pointers<oop>(this, obj);
  }
  return size;
}

#define InstanceRefKlass_SPECIALIZED_OOP_ITERATE(T, nv_suffix, contains)        \
  T* disc_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);             \
  if (closure->apply_to_weak_ref_discovered_field()) {                          \
    closure->do_oop##nv_suffix(disc_addr);                                      \
  }                                                                             \
                                                                                \
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);           \
  T heap_oop = oopDesc::load_heap_oop(referent_addr);                           \
  ReferenceProcessor* rp = closure->_ref_processor;                             \
  if (!oopDesc::is_null(heap_oop)) {                                            \
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);                 \
    if (!referent->is_gc_marked() && (rp != NULL) &&                            \
        rp->discover_reference(obj, reference_type())) {                        \
      return size;                                                              \
    } else if (contains(referent_addr)) {                                       \
      /* treat referent as normal oop */                                        \
      SpecializationStats::record_do_oop_call##nv_suffix(SpecializationStats::irk);\
      closure->do_oop##nv_suffix(referent_addr);                                \
    }                                                                           \
  }                                                                             \
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);                   \
  if (ReferenceProcessor::pending_list_uses_discovered_field()) {               \
    T next_oop  = oopDesc::load_heap_oop(next_addr);                            \
    /* Treat discovered as normal oop, if ref is not "active" (next non-NULL) */\
    if (!oopDesc::is_null(next_oop) && contains(disc_addr)) {                   \
        /* i.e. ref is not "active" */                                          \
      debug_only(                                                               \
        if(TraceReferenceGC && PrintGCDetails) {                                \
          gclog_or_tty->print_cr("   Process discovered as normal "             \
                                 INTPTR_FORMAT, disc_addr);                     \
        }                                                                       \
      )                                                                         \
      SpecializationStats::record_do_oop_call##nv_suffix(SpecializationStats::irk);\
      closure->do_oop##nv_suffix(disc_addr);                                    \
    }                                                                           \
  } else {                                                                      \
    /* In the case of older JDKs which do not use the discovered field for  */  \
    /* the pending list, an inactive ref (next != NULL) must always have a  */  \
    /* NULL discovered field. */                                                \
    debug_only(                                                                 \
      T next_oop = oopDesc::load_heap_oop(next_addr);                           \
      T disc_oop = oopDesc::load_heap_oop(disc_addr);                           \
      assert(oopDesc::is_null(next_oop) || oopDesc::is_null(disc_oop),          \
           err_msg("Found an inactive reference " PTR_FORMAT " with a non-NULL" \
                   "discovered field", (oopDesc*)obj));                                   \
    )                                                                           \
  }                                                                             \
  /* treat next as normal oop */                                                \
  if (contains(next_addr)) {                                                    \
    SpecializationStats::record_do_oop_call##nv_suffix(SpecializationStats::irk); \
    closure->do_oop##nv_suffix(next_addr);                                      \
  }                                                                             \
  return size;                                                                  \


template <class T> bool contains(T *t) { return true; }

// Macro to define InstanceRefKlass::oop_oop_iterate for virtual/nonvirtual for
// all closures.  Macros calling macros above for each oop size.

#define InstanceRefKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)        \
                                                                                \
int InstanceRefKlass::                                                          \
oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) {                  \
  /* Get size before changing pointers */                                       \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
                                                                                \
  int size = InstanceKlass::oop_oop_iterate##nv_suffix(obj, closure);           \
                                                                                \
  if (UseCompressedOops) {                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, contains);   \
  } else {                                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, contains);         \
  }                                                                             \
}

#if INCLUDE_ALL_GCS
#define InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix) \
                                                                                \
int InstanceRefKlass::                                                          \
oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure) {        \
  /* Get size before changing pointers */                                       \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
                                                                                \
  int size = InstanceKlass::oop_oop_iterate_backwards##nv_suffix(obj, closure); \
                                                                                \
  if (UseCompressedOops) {                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, contains);   \
  } else {                                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, contains);         \
  }                                                                             \
}
#endif // INCLUDE_ALL_GCS


#define InstanceRefKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix)      \
                                                                                \
int InstanceRefKlass::                                                          \
oop_oop_iterate##nv_suffix##_m(oop obj,                                         \
                               OopClosureType* closure,                         \
                               MemRegion mr) {                                  \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::irk);\
                                                                                \
  int size = InstanceKlass::oop_oop_iterate##nv_suffix##_m(obj, closure, mr);   \
  if (UseCompressedOops) {                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(narrowOop, nv_suffix, mr.contains); \
  } else {                                                                      \
    InstanceRefKlass_SPECIALIZED_OOP_ITERATE(oop, nv_suffix, mr.contains);      \
  }                                                                             \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_DEFN)
#if INCLUDE_ALL_GCS
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
#endif // INCLUDE_ALL_GCS
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceRefKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceRefKlass_OOP_OOP_ITERATE_DEFN_m)

#if INCLUDE_ALL_GCS
template <class T>
void specialized_oop_push_contents(InstanceRefKlass *ref,
                                   PSPromotionManager* pm, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  if (PSScavenge::should_scavenge(referent_addr)) {
    ReferenceProcessor* rp = PSScavenge::reference_processor();
    if (rp->discover_reference(obj, ref->reference_type())) {
      // reference already enqueued, referent and next will be traversed later
      ref->InstanceKlass::oop_push_contents(pm, obj);
      return;
    } else {
      // treat referent as normal oop
      pm->claim_or_forward_depth(referent_addr);
    }
  }
  // Treat discovered as normal oop, if ref is not "active",
  // i.e. if next is non-NULL.
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  if (ReferenceProcessor::pending_list_uses_discovered_field()) {
    T  next_oop = oopDesc::load_heap_oop(next_addr);
    if (!oopDesc::is_null(next_oop)) { // i.e. ref is not "active"
      T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("   Process discovered as normal "
                                 INTPTR_FORMAT, discovered_addr);
        }
      )
      if (PSScavenge::should_scavenge(discovered_addr)) {
        pm->claim_or_forward_depth(discovered_addr);
      }
    }
  } else {
#ifdef ASSERT
    // In the case of older JDKs which do not use the discovered
    // field for the pending list, an inactive ref (next != NULL)
    // must always have a NULL discovered field.
    oop next = oopDesc::load_decode_heap_oop(next_addr);
    oop discovered = java_lang_ref_Reference::discovered(obj);
    assert(oopDesc::is_null(next) || oopDesc::is_null(discovered),
           err_msg("Found an inactive reference " PTR_FORMAT " with a non-NULL discovered field",
                   (oopDesc*)obj));
#endif
  }

  // Treat next as normal oop;  next is a link in the reference queue.
  if (PSScavenge::should_scavenge(next_addr)) {
    pm->claim_or_forward_depth(next_addr);
  }
  ref->InstanceKlass::oop_push_contents(pm, obj);
}

void InstanceRefKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  if (UseCompressedOops) {
    specialized_oop_push_contents<narrowOop>(this, pm, obj);
  } else {
    specialized_oop_push_contents<oop>(this, pm, obj);
  }
}

template <class T>
void specialized_oop_update_pointers(InstanceRefKlass *ref,
                                    ParCompactionManager* cm, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  PSParallelCompact::adjust_pointer(referent_addr);
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  PSParallelCompact::adjust_pointer(next_addr);
  T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
  PSParallelCompact::adjust_pointer(discovered_addr);
  debug_only(trace_reference_gc("InstanceRefKlass::oop_update_ptrs", obj,
                                referent_addr, next_addr, discovered_addr);)
}

int InstanceRefKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  InstanceKlass::oop_update_pointers(cm, obj);
  if (UseCompressedOops) {
    specialized_oop_update_pointers<narrowOop>(this, cm, obj);
  } else {
    specialized_oop_update_pointers<oop>(this, cm, obj);
  }
  return size_helper();
}
#endif // INCLUDE_ALL_GCS

void InstanceRefKlass::update_nonstatic_oop_maps(Klass* k) {
  // Clear the nonstatic oop-map entries corresponding to referent
  // and nextPending field.  They are treated specially by the
  // garbage collector.
  // The discovered field is used only by the garbage collector
  // and is also treated specially.
  InstanceKlass* ik = InstanceKlass::cast(k);

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

void InstanceRefKlass::oop_verify_on(oop obj, outputStream* st) {
  InstanceKlass::oop_verify_on(obj, st);
  // Verify referent field
  oop referent = java_lang_ref_Reference::referent(obj);

  // We should make this general to all heaps
  GenCollectedHeap* gch = NULL;
  if (Universe::heap()->kind() == CollectedHeap::GenCollectedHeap)
    gch = GenCollectedHeap::heap();

  if (referent != NULL) {
    guarantee(referent->is_oop(), "referent field heap failed");
  }
  // Verify next field
  oop next = java_lang_ref_Reference::next(obj);
  if (next != NULL) {
    guarantee(next->is_oop(), "next field verify failed");
    guarantee(next->is_instanceRef(), "next field verify failed");
  }
}

bool InstanceRefKlass::owns_pending_list_lock(JavaThread* thread) {
  if (java_lang_ref_Reference::pending_list_lock() == NULL) return false;
  Handle h_lock(thread, java_lang_ref_Reference::pending_list_lock());
  return ObjectSynchronizer::current_thread_holds_lock(thread, h_lock);
}

void InstanceRefKlass::acquire_pending_list_lock(BasicLock *pending_list_basic_lock) {
  // we may enter this with pending exception set
  PRESERVE_EXCEPTION_MARK;  // exceptions are never thrown, needed for TRAPS argument

  // Create a HandleMark in case we retry a GC multiple times.
  // Each time we attempt the GC, we allocate the handle below
  // to hold the pending list lock. We want to free this handle.
  HandleMark hm;

  Handle h_lock(THREAD, java_lang_ref_Reference::pending_list_lock());
  ObjectSynchronizer::fast_enter(h_lock, pending_list_basic_lock, false, THREAD);
  assert(ObjectSynchronizer::current_thread_holds_lock(
           JavaThread::current(), h_lock),
         "Locking should have succeeded");
  if (HAS_PENDING_EXCEPTION) CLEAR_PENDING_EXCEPTION;
}

void InstanceRefKlass::release_and_notify_pending_list_lock(
  BasicLock *pending_list_basic_lock) {
  // we may enter this with pending exception set
  PRESERVE_EXCEPTION_MARK;  // exceptions are never thrown, needed for TRAPS argument

  // Create a HandleMark in case we retry a GC multiple times.
  // Each time we attempt the GC, we allocate the handle below
  // to hold the pending list lock. We want to free this handle.
  HandleMark hm;

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
