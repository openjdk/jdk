/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSPROMOTIONMANAGER_INLINE_HPP
#define SHARE_GC_PARALLEL_PSPROMOTIONMANAGER_INLINE_HPP

#include "gc/parallel/psPromotionManager.hpp"

#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/parMarkBitMap.inline.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psPromotionLAB.inline.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/parallel/psStringDedup.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/prefetch.inline.hpp"
#include "utilities/copy.hpp"

inline PSPromotionManager* PSPromotionManager::manager_array(uint index) {
  assert(_manager_array != nullptr, "access of null manager_array");
  assert(index < ParallelGCThreads, "out of range manager_array access");
  return &_manager_array[index];
}

template <class T>
ALWAYSINLINE void PSPromotionManager::claim_or_forward_depth(T* p) {
  assert(ParallelScavengeHeap::heap()->is_in(p), "pointer outside heap");
  T heap_oop = RawAccess<>::oop_load(p);
  if (PSScavenge::is_obj_in_young(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    assert(!PSScavenge::is_obj_in_to_space(obj), "revisiting object?");
    Prefetch::write(obj->base_addr(), oopDesc::mark_offset_in_bytes());
    claimed_stack_depth()->push(ScannerTask(p));
  }
}

inline void PSPromotionManager::promotion_trace_event(oop new_obj, Klass* klass,
                                                      size_t obj_size,
                                                      uint age, bool tenured,
                                                      const PSPromotionLAB* lab) {
  // Skip if memory allocation failed
  if (new_obj != nullptr) {
    const ParallelScavengeTracer* gc_tracer = PSScavenge::gc_tracer();

    if (lab != nullptr) {
      // Promotion of object through newly allocated PLAB
      if (gc_tracer->should_report_promotion_in_new_plab_event()) {
        size_t obj_bytes = obj_size * HeapWordSize;
        size_t lab_size = lab->capacity();
        gc_tracer->report_promotion_in_new_plab_event(klass, obj_bytes,
                                                      age, tenured, lab_size);
      }
    } else {
      // Promotion of object directly to heap
      if (gc_tracer->should_report_promotion_outside_plab_event()) {
        size_t obj_bytes = obj_size * HeapWordSize;
        gc_tracer->report_promotion_outside_plab_event(klass, obj_bytes,
                                                       age, tenured);
      }
    }
  }
}

class PSPushContentsClosure: public BasicOopIterateClosure {
  PSPromotionManager* _pm;
 public:
  PSPushContentsClosure(PSPromotionManager* pm) : BasicOopIterateClosure(PSScavenge::reference_processor()), _pm(pm) {}

  template <typename T> void do_oop_work(T* p) {
    _pm->claim_or_forward_depth(p);
  }

  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
};

//
// This closure specialization will override the one that is defined in
// instanceRefKlass.inline.cpp. It swaps the order of oop_oop_iterate and
// oop_oop_iterate_ref_processing. Unfortunately G1 and Parallel behaves
// significantly better (especially in the Derby benchmark) using opposite
// order of these function calls.
//
template <>
inline void InstanceRefKlass::oop_oop_iterate_reverse<oop, PSPushContentsClosure>(oop obj, PSPushContentsClosure* closure) {
  oop_oop_iterate_ref_processing<oop>(obj, closure);
  InstanceKlass::oop_oop_iterate_reverse<oop>(obj, closure);
}

template <>
inline void InstanceRefKlass::oop_oop_iterate_reverse<narrowOop, PSPushContentsClosure>(oop obj, PSPushContentsClosure* closure) {
  oop_oop_iterate_ref_processing<narrowOop>(obj, closure);
  InstanceKlass::oop_oop_iterate_reverse<narrowOop>(obj, closure);
}

inline void PSPromotionManager::push_contents(oop obj) {
  if (!obj->klass()->is_typeArray_klass()) {
    PSPushContentsClosure pcc(this);
    obj->oop_iterate_backwards(&pcc);
  }
}

inline void PSPromotionManager::push_contents_bounded(oop obj, HeapWord* left, HeapWord* right) {
  PSPushContentsClosure pcc(this);
  obj->oop_iterate(&pcc, MemRegion(left, right));
}

template<bool promote_immediately>
inline oop PSPromotionManager::copy_to_survivor_space(oop o) {
  assert(PSScavenge::is_obj_in_young(o), "precondition");
  assert(!PSScavenge::is_obj_in_to_space(o), "precondition");

  // NOTE! We must be very careful with any methods that access the mark
  // in o. There may be multiple threads racing on it, and it may be forwarded
  // at any time.
  markWord m = o->mark();
  if (!m.is_forwarded()) {
    return copy_unmarked_to_survivor_space<promote_immediately>(o, m);
  } else {
    // Return the already installed forwardee.
    return o->forwardee(m);
  }
}

inline HeapWord* PSPromotionManager::allocate_in_young_gen(Klass* klass,
                                                           size_t obj_size,
                                                           uint age) {
  HeapWord* result = _young_lab.allocate(obj_size);
  if (result != nullptr) {
    return result;
  }
  if (_young_gen_is_full) {
    return nullptr;
  }
  // Do we allocate directly, or flush and refill?
  if (obj_size > (YoungPLABSize / 2)) {
    // Allocate this object directly
    result = young_space()->cas_allocate(obj_size);
    promotion_trace_event(cast_to_oop(result), klass, obj_size, age, false, nullptr);
  } else {
    // Flush and fill
    _young_lab.flush();

    HeapWord* lab_base = young_space()->cas_allocate(YoungPLABSize);
    if (lab_base != nullptr) {
      _young_lab.initialize(MemRegion(lab_base, YoungPLABSize));
      // Try the young lab allocation again.
      result = _young_lab.allocate(obj_size);
      promotion_trace_event(cast_to_oop(result), klass, obj_size, age, false, &_young_lab);
    } else {
      _young_gen_is_full = true;
    }
  }
  if (result == nullptr && !_young_gen_is_full && !_young_gen_has_alloc_failure) {
    _young_gen_has_alloc_failure = true;
  }
  return result;
}

inline HeapWord* PSPromotionManager::allocate_in_old_gen(Klass* klass,
                                                         size_t obj_size,
                                                         uint age) {
#ifndef PRODUCT
  if (ParallelScavengeHeap::heap()->promotion_should_fail()) {
    return nullptr;
  }
#endif  // #ifndef PRODUCT

  HeapWord* result = _old_lab.allocate(obj_size);
  if (result != nullptr) {
    return result;
  }
  if (_old_gen_is_full) {
    return nullptr;
  }
  // Do we allocate directly, or flush and refill?
  if (obj_size > (OldPLABSize / 2)) {
    // Allocate this object directly
    result = old_gen()->allocate(obj_size);
    promotion_trace_event(cast_to_oop(result), klass, obj_size, age, true, nullptr);
  } else {
    // Flush and fill
    _old_lab.flush();

    HeapWord* lab_base = old_gen()->allocate(OldPLABSize);
    if (lab_base != nullptr) {
      _old_lab.initialize(MemRegion(lab_base, OldPLABSize));
      // Try the old lab allocation again.
      result = _old_lab.allocate(obj_size);
      promotion_trace_event(cast_to_oop(result), klass, obj_size, age, true, &_old_lab);
    }
  }
  if (result == nullptr) {
    _old_gen_is_full = true;
  }
  return result;
}

//
// This method is pretty bulky. It would be nice to split it up
// into smaller submethods, but we need to be careful not to hurt
// performance.
//
template<bool promote_immediately>
inline oop PSPromotionManager::copy_unmarked_to_survivor_space(oop o,
                                                               markWord test_mark) {
  HeapWord* new_obj_addr = nullptr;
  bool new_obj_is_tenured = false;

  // NOTE: With compact headers, it is not safe to load the Klass* from old, because
  // that would access the mark-word, that might change at any time by concurrent
  // workers.
  // This mark word would refer to a forwardee, which may not yet have completed
  // copying. Therefore we must load the Klass* from the mark-word that we already
  // loaded. This is safe, because we only enter here if not yet forwarded.
  assert(!test_mark.is_forwarded(), "precondition");
  Klass* klass = UseCompactObjectHeaders
      ? test_mark.klass()
      : o->klass();

  size_t new_obj_size = o->size_given_klass(klass);

  // Find the objects age, MT safe.
  uint age = (test_mark.has_displaced_mark_helper() /* o->has_displaced_mark() */) ?
      test_mark.displaced_mark_helper().age() : test_mark.age();

  if (!promote_immediately) {
    // Try allocating obj in to-space (unless too old)
    if (age < PSScavenge::tenuring_threshold()) {
      new_obj_addr = allocate_in_young_gen(klass, new_obj_size, age);
    }
  }

  // Otherwise try allocating obj tenured
  if (new_obj_addr == nullptr) {
    new_obj_addr = allocate_in_old_gen(klass, new_obj_size, age);
    if (new_obj_addr == nullptr) {
      return oop_promotion_failed(o, test_mark);
    }
    new_obj_is_tenured = true;
  }

  assert(new_obj_addr != nullptr, "allocation should have succeeded");

  // Copy obj
  Copy::aligned_disjoint_words(cast_from_oop<HeapWord*>(o), new_obj_addr, new_obj_size);

  // Now we have to CAS in the header.
  // Because the forwarding is done with memory_order_relaxed there is no
  // ordering with the above copy.  Clients that get the forwardee must not
  // examine its contents without other synchronization, since the contents
  // may not be up to date for them.
  oop forwardee = o->forward_to_atomic(cast_to_oop(new_obj_addr), test_mark, memory_order_relaxed);
  if (forwardee == nullptr) {  // forwardee is null when forwarding is successful
    // We won any races, we "own" this object.
    oop new_obj = cast_to_oop(new_obj_addr);
    assert(new_obj == o->forwardee(), "Sanity");

    // Increment age if obj still in new generation. Now that
    // we're dealing with a markWord that cannot change, it is
    // okay to use the non mt safe oop methods.
    if (!new_obj_is_tenured) {
      new_obj->incr_age();
      assert(young_space()->contains(new_obj), "Attempt to push non-promoted obj");
    }

    ContinuationGCSupport::transform_stack_chunk(new_obj);

    // Do the size comparison first with new_obj_size, which we
    // already have. Hopefully, only a few objects are larger than
    // _min_array_size_for_chunking, and most of them will be arrays.
    // So, the objArray test would be very infrequent.
    if (new_obj_size > _min_array_size_for_chunking &&
        klass->is_objArray_klass()) {
      push_objArray(o, new_obj);
    } else {
      // we'll just push its contents
      push_contents(new_obj);

      if (StringDedup::is_enabled_string(klass) &&
          psStringDedup::is_candidate_from_evacuation(new_obj, new_obj_is_tenured)) {
        _string_dedup_requests.add(o);
      }
    }
    return new_obj;
  } else {
    // We lost, someone else "owns" this object.
    assert(o->is_forwarded(), "Object must be forwarded if the cas failed.");
    assert(o->forwardee() == forwardee, "invariant");

    if (new_obj_is_tenured) {
      _old_lab.unallocate_object(new_obj_addr, new_obj_size);
    } else {
      _young_lab.unallocate_object(new_obj_addr, new_obj_size);
    }
    return forwardee;
  }
}

// Attempt to "claim" oop at p via CAS, push the new obj if successful
template <bool promote_immediately, class T>
inline void PSPromotionManager::copy_and_push_safe_barrier(T* p) {
  assert(ParallelScavengeHeap::heap()->is_in_reserved(p), "precondition");

  oop o = RawAccess<IS_NOT_NULL>::oop_load(p);
  oop new_obj = copy_to_survivor_space<promote_immediately>(o);
  RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);

  if (!PSScavenge::is_obj_in_young((HeapWord*)p) &&
       PSScavenge::is_obj_in_young(new_obj)) {
    PSScavenge::card_table()->inline_write_ref_field_gc(p);
  }
}

inline void PSPromotionManager::process_popped_location_depth(ScannerTask task,
                                                              bool stolen) {
  if (task.is_partial_array_state()) {
    process_array_chunk(task.to_partial_array_state(), stolen);
  } else {
    if (task.is_narrow_oop_ptr()) {
      assert(UseCompressedOops, "Error");
      copy_and_push_safe_barrier</*promote_immediately=*/false>(task.to_narrow_oop_ptr());
    } else {
      copy_and_push_safe_barrier</*promote_immediately=*/false>(task.to_oop_ptr());
    }
  }
}

inline bool PSPromotionManager::steal_depth(int queue_num, ScannerTask& t) {
  return stack_array_depth()->steal(queue_num, t);
}

#endif // SHARE_GC_PARALLEL_PSPROMOTIONMANAGER_INLINE_HPP
