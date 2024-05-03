/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1Allocator.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1EvacFailureRegions.inline.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/g1Trace.hpp"
#include "gc/g1/g1YoungGCAllocationFailureInjector.inline.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/partialArrayTaskStepper.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/prefetch.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// In fastdebug builds the code size can get out of hand, potentially
// tripping over compiler limits (which may be bugs, but nevertheless
// need to be taken into consideration).  A side benefit of limiting
// inlining is that we get more call frames that might aid debugging.
// And the fastdebug compile time for this file is much reduced.
// Explicit NOINLINE to block ATTRIBUTE_FLATTENing.
#define MAYBE_INLINE_EVACUATION NOT_DEBUG(inline) DEBUG_ONLY(NOINLINE)

G1ParScanThreadState::G1ParScanThreadState(G1CollectedHeap* g1h,
                                           G1RedirtyCardsQueueSet* rdcqs,
                                           PreservedMarks* preserved_marks,
                                           uint worker_id,
                                           uint num_workers,
                                           G1CollectionSet* collection_set,
                                           G1EvacFailureRegions* evac_failure_regions)
  : _g1h(g1h),
    _task_queue(g1h->task_queue(worker_id)),
    _rdc_local_qset(rdcqs),
    _ct(g1h->card_table()),
    _closures(nullptr),
    _plab_allocator(nullptr),
    _age_table(false),
    _tenuring_threshold(g1h->policy()->tenuring_threshold()),
    _scanner(g1h, this),
    _worker_id(worker_id),
    _last_enqueued_card(SIZE_MAX),
    _stack_trim_upper_threshold(GCDrainStackTargetSize * 2 + 1),
    _stack_trim_lower_threshold(GCDrainStackTargetSize),
    _trim_ticks(),
    _surviving_young_words_base(nullptr),
    _surviving_young_words(nullptr),
    _surviving_words_length(collection_set->young_region_length() + 1),
    _old_gen_is_full(false),
    _partial_objarray_chunk_size(ParGCArrayScanChunk),
    _partial_array_stepper(num_workers),
    _string_dedup_requests(),
    _max_num_optional_regions(collection_set->optional_region_length()),
    _numa(g1h->numa()),
    _obj_alloc_stat(nullptr),
    ALLOCATION_FAILURE_INJECTOR_ONLY(_allocation_failure_inject_counter(0) COMMA)
    _preserved_marks(preserved_marks),
    _evacuation_failed_info(),
    _evac_failure_regions(evac_failure_regions),
    _evac_failure_enqueued_cards(0)
{
  // We allocate number of young gen regions in the collection set plus one
  // entries, since entry 0 keeps track of surviving bytes for non-young regions.
  // We also add a few elements at the beginning and at the end in
  // an attempt to eliminate cache contention
  const size_t padding_elem_num = (DEFAULT_PADDING_SIZE / sizeof(size_t));
  size_t array_length = padding_elem_num + _surviving_words_length + padding_elem_num;

  _surviving_young_words_base = NEW_C_HEAP_ARRAY(size_t, array_length, mtGC);
  _surviving_young_words = _surviving_young_words_base + padding_elem_num;
  memset(_surviving_young_words, 0, _surviving_words_length * sizeof(size_t));

  _plab_allocator = new G1PLABAllocator(_g1h->allocator());

  _closures = G1EvacuationRootClosures::create_root_closures(_g1h,
                                                             this,
                                                             collection_set->only_contains_young_regions());

  _oops_into_optional_regions = new G1OopStarChunkedList[_max_num_optional_regions];

  initialize_numa_stats();
}

size_t G1ParScanThreadState::flush_stats(size_t* surviving_young_words, uint num_workers, BufferNodeList* rdc_buffers) {
  *rdc_buffers = _rdc_local_qset.flush();
  flush_numa_stats();
  // Update allocation statistics.
  _plab_allocator->flush_and_retire_stats(num_workers);
  _g1h->policy()->record_age_table(&_age_table);

  if (_evacuation_failed_info.has_failed()) {
    _g1h->gc_tracer_stw()->report_evacuation_failed(_evacuation_failed_info);
  }

  size_t sum = 0;
  for (uint i = 0; i < _surviving_words_length; i++) {
    surviving_young_words[i] += _surviving_young_words[i];
    sum += _surviving_young_words[i];
  }
  return sum;
}

G1ParScanThreadState::~G1ParScanThreadState() {
  delete _plab_allocator;
  delete _closures;
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_base);
  delete[] _oops_into_optional_regions;
  FREE_C_HEAP_ARRAY(size_t, _obj_alloc_stat);
}

size_t G1ParScanThreadState::lab_waste_words() const {
  return _plab_allocator->waste();
}

size_t G1ParScanThreadState::lab_undo_waste_words() const {
  return _plab_allocator->undo_waste();
}

size_t G1ParScanThreadState::evac_failure_enqueued_cards() const {
  return _evac_failure_enqueued_cards;
}

#ifdef ASSERT
void G1ParScanThreadState::verify_task(narrowOop* task) const {
  assert(task != nullptr, "invariant");
  assert(UseCompressedOops, "sanity");
  oop p = RawAccess<>::oop_load(task);
  assert(_g1h->is_in_reserved(p),
         "task=" PTR_FORMAT " p=" PTR_FORMAT, p2i(task), p2i(p));
}

void G1ParScanThreadState::verify_task(oop* task) const {
  assert(task != nullptr, "invariant");
  oop p = RawAccess<>::oop_load(task);
  assert(_g1h->is_in_reserved(p),
         "task=" PTR_FORMAT " p=" PTR_FORMAT, p2i(task), p2i(p));
}

void G1ParScanThreadState::verify_task(PartialArrayScanTask task) const {
  // Must be in the collection set--it's already been copied.
  oop p = task.to_source_array();
  assert(_g1h->is_in_cset(p), "p=" PTR_FORMAT, p2i(p));
}

void G1ParScanThreadState::verify_task(ScannerTask task) const {
  if (task.is_narrow_oop_ptr()) {
    verify_task(task.to_narrow_oop_ptr());
  } else if (task.is_oop_ptr()) {
    verify_task(task.to_oop_ptr());
  } else if (task.is_partial_array_task()) {
    verify_task(task.to_partial_array_task());
  } else {
    ShouldNotReachHere();
  }
}
#endif // ASSERT

template <class T>
MAYBE_INLINE_EVACUATION
void G1ParScanThreadState::do_oop_evac(T* p) {
  // Reference should not be null here as such are never pushed to the task queue.
  oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);

  // Although we never intentionally push references outside of the collection
  // set, due to (benign) races in the claim mechanism during RSet scanning more
  // than one thread might claim the same card. So the same card may be
  // processed multiple times, and so we might get references into old gen here.
  // So we need to redo this check.
  const G1HeapRegionAttr region_attr = _g1h->region_attr(obj);
  // References pushed onto the work stack should never point to a humongous region
  // as they are not added to the collection set due to above precondition.
  assert(!region_attr.is_humongous_candidate(),
         "Obj " PTR_FORMAT " should not refer to humongous region %u from " PTR_FORMAT,
         p2i(obj), _g1h->addr_to_region(obj), p2i(p));

  if (!region_attr.is_in_cset()) {
    // In this case somebody else already did all the work.
    return;
  }

  markWord m = obj->mark();
  if (m.is_forwarded()) {
    obj = m.forwardee();
  } else {
    obj = do_copy_to_survivor_space(region_attr, obj, m);
  }
  RawAccess<IS_NOT_NULL>::oop_store(p, obj);

  write_ref_field_post(p, obj);
}

MAYBE_INLINE_EVACUATION
void G1ParScanThreadState::do_partial_array(PartialArrayScanTask task) {
  oop from_obj = task.to_source_array();

  assert(_g1h->is_in_reserved(from_obj), "must be in heap.");
  assert(from_obj->is_objArray(), "must be obj array");
  assert(from_obj->is_forwarded(), "must be forwarded");

  oop to_obj = from_obj->forwardee();
  assert(from_obj != to_obj, "should not be chunking self-forwarded objects");
  assert(to_obj->is_objArray(), "must be obj array");
  objArrayOop to_array = objArrayOop(to_obj);

  PartialArrayTaskStepper::Step step
    = _partial_array_stepper.next(objArrayOop(from_obj),
                                  to_array,
                                  _partial_objarray_chunk_size);
  for (uint i = 0; i < step._ncreate; ++i) {
    push_on_queue(ScannerTask(PartialArrayScanTask(from_obj)));
  }

  G1HeapRegionAttr dest_attr = _g1h->region_attr(to_array);
  G1SkipCardEnqueueSetter x(&_scanner, dest_attr.is_new_survivor());
  // Process claimed task.  The length of to_array is not correct, but
  // fortunately the iteration ignores the length field and just relies
  // on start/end.
  to_array->oop_iterate_range(&_scanner,
                              step._index,
                              step._index + _partial_objarray_chunk_size);
}

MAYBE_INLINE_EVACUATION
void G1ParScanThreadState::start_partial_objarray(G1HeapRegionAttr dest_attr,
                                                  oop from_obj,
                                                  oop to_obj) {
  assert(from_obj->is_objArray(), "precondition");
  assert(from_obj->is_forwarded(), "precondition");
  assert(from_obj->forwardee() == to_obj, "precondition");
  assert(from_obj != to_obj, "should not be scanning self-forwarded objects");
  assert(to_obj->is_objArray(), "precondition");

  objArrayOop to_array = objArrayOop(to_obj);

  PartialArrayTaskStepper::Step step
    = _partial_array_stepper.start(objArrayOop(from_obj),
                                   to_array,
                                   _partial_objarray_chunk_size);

  // Push any needed partial scan tasks.  Pushed before processing the
  // initial chunk to allow other workers to steal while we're processing.
  for (uint i = 0; i < step._ncreate; ++i) {
    push_on_queue(ScannerTask(PartialArrayScanTask(from_obj)));
  }

  // Skip the card enqueue iff the object (to_array) is in survivor region.
  // However, HeapRegion::is_survivor() is too expensive here.
  // Instead, we use dest_attr.is_young() because the two values are always
  // equal: successfully allocated young regions must be survivor regions.
  assert(dest_attr.is_young() == _g1h->heap_region_containing(to_array)->is_survivor(), "must be");
  G1SkipCardEnqueueSetter x(&_scanner, dest_attr.is_young());
  // Process the initial chunk.  No need to process the type in the
  // klass, as it will already be handled by processing the built-in
  // module. The length of to_array is not correct, but fortunately
  // the iteration ignores that length field and relies on start/end.
  to_array->oop_iterate_range(&_scanner, 0, step._index);
}

MAYBE_INLINE_EVACUATION
void G1ParScanThreadState::dispatch_task(ScannerTask task) {
  verify_task(task);
  if (task.is_narrow_oop_ptr()) {
    do_oop_evac(task.to_narrow_oop_ptr());
  } else if (task.is_oop_ptr()) {
    do_oop_evac(task.to_oop_ptr());
  } else {
    do_partial_array(task.to_partial_array_task());
  }
}

// Process tasks until overflow queue is empty and local queue
// contains no more than threshold entries.  NOINLINE to prevent
// inlining into steal_and_trim_queue.
ATTRIBUTE_FLATTEN NOINLINE
void G1ParScanThreadState::trim_queue_to_threshold(uint threshold) {
  ScannerTask task;
  do {
    while (_task_queue->pop_overflow(task)) {
      if (!_task_queue->try_push_to_taskqueue(task)) {
        dispatch_task(task);
      }
    }
    while (_task_queue->pop_local(task, threshold)) {
      dispatch_task(task);
    }
  } while (!_task_queue->overflow_empty());
}

ATTRIBUTE_FLATTEN
void G1ParScanThreadState::steal_and_trim_queue(G1ScannerTasksQueueSet* task_queues) {
  ScannerTask stolen_task;
  while (task_queues->steal(_worker_id, stolen_task)) {
    dispatch_task(stolen_task);
    // Processing stolen task may have added tasks to our queue.
    trim_queue();
  }
}

HeapWord* G1ParScanThreadState::allocate_in_next_plab(G1HeapRegionAttr* dest,
                                                      size_t word_sz,
                                                      bool previous_plab_refill_failed,
                                                      uint node_index) {

  assert(dest->is_in_cset_or_humongous_candidate(), "Unexpected dest: %s region attr", dest->get_type_str());

  // Right now we only have two types of regions (young / old) so
  // let's keep the logic here simple. We can generalize it when necessary.
  if (dest->is_young()) {
    bool plab_refill_in_old_failed = false;
    HeapWord* const obj_ptr = _plab_allocator->allocate(G1HeapRegionAttr::Old,
                                                        word_sz,
                                                        &plab_refill_in_old_failed,
                                                        node_index);
    // Make sure that we won't attempt to copy any other objects out
    // of a survivor region (given that apparently we cannot allocate
    // any new ones) to avoid coming into this slow path again and again.
    // Only consider failed PLAB refill here: failed inline allocations are
    // typically large, so not indicative of remaining space.
    if (previous_plab_refill_failed) {
      _tenuring_threshold = 0;
    }

    if (obj_ptr != nullptr) {
      dest->set_old();
    } else {
      // We just failed to allocate in old gen. The same idea as explained above
      // for making survivor gen unavailable for allocation applies for old gen.
      _old_gen_is_full = plab_refill_in_old_failed;
    }
    return obj_ptr;
  } else {
    _old_gen_is_full = previous_plab_refill_failed;
    assert(dest->is_old(), "Unexpected dest region attr: %s", dest->get_type_str());
    // no other space to try.
    return nullptr;
  }
}

G1HeapRegionAttr G1ParScanThreadState::next_region_attr(G1HeapRegionAttr const region_attr, markWord const m, uint& age) {
  assert(region_attr.is_young() || region_attr.is_old(), "must be either Young or Old");

  if (region_attr.is_young()) {
    age = !m.has_displaced_mark_helper() ? m.age()
                                         : m.displaced_mark_helper().age();
    if (age < _tenuring_threshold) {
      return region_attr;
    }
  }
  // young-to-old (promotion) or old-to-old; destination is old in both cases.
  return G1HeapRegionAttr::Old;
}

void G1ParScanThreadState::report_promotion_event(G1HeapRegionAttr const dest_attr,
                                                  oop const old, size_t word_sz, uint age,
                                                  HeapWord * const obj_ptr, uint node_index) const {
  PLAB* alloc_buf = _plab_allocator->alloc_buffer(dest_attr, node_index);
  if (alloc_buf->contains(obj_ptr)) {
    _g1h->gc_tracer_stw()->report_promotion_in_new_plab_event(old->klass(), word_sz * HeapWordSize, age,
                                                              dest_attr.type() == G1HeapRegionAttr::Old,
                                                              alloc_buf->word_sz() * HeapWordSize);
  } else {
    _g1h->gc_tracer_stw()->report_promotion_outside_plab_event(old->klass(), word_sz * HeapWordSize, age,
                                                               dest_attr.type() == G1HeapRegionAttr::Old);
  }
}

NOINLINE
HeapWord* G1ParScanThreadState::allocate_copy_slow(G1HeapRegionAttr* dest_attr,
                                                   oop old,
                                                   size_t word_sz,
                                                   uint age,
                                                   uint node_index) {
  HeapWord* obj_ptr = nullptr;
  // Try slow-path allocation unless we're allocating old and old is already full.
  if (!(dest_attr->is_old() && _old_gen_is_full)) {
    bool plab_refill_failed = false;
    obj_ptr = _plab_allocator->allocate_direct_or_new_plab(*dest_attr,
                                                           word_sz,
                                                           &plab_refill_failed,
                                                           node_index);
    if (obj_ptr == nullptr) {
      obj_ptr = allocate_in_next_plab(dest_attr,
                                      word_sz,
                                      plab_refill_failed,
                                      node_index);
    }
  }
  if (obj_ptr != nullptr) {
    update_numa_stats(node_index);
    if (_g1h->gc_tracer_stw()->should_report_promotion_events()) {
      // The events are checked individually as part of the actual commit
      report_promotion_event(*dest_attr, old, word_sz, age, obj_ptr, node_index);
    }
  }
  return obj_ptr;
}

#if ALLOCATION_FAILURE_INJECTOR
bool G1ParScanThreadState::inject_allocation_failure(uint region_idx) {
  return _g1h->allocation_failure_injector()->allocation_should_fail(_allocation_failure_inject_counter, region_idx);
}
#endif

NOINLINE
void G1ParScanThreadState::undo_allocation(G1HeapRegionAttr dest_attr,
                                           HeapWord* obj_ptr,
                                           size_t word_sz,
                                           uint node_index) {
  _plab_allocator->undo_allocation(dest_attr, obj_ptr, word_sz, node_index);
}

void G1ParScanThreadState::update_bot_after_copying(oop obj, size_t word_sz) {
  HeapWord* obj_start = cast_from_oop<HeapWord*>(obj);
  HeapRegion* region = _g1h->heap_region_containing(obj_start);
  region->update_bot_for_block(obj_start, obj_start + word_sz);
}

// Private inline function, for direct internal use and providing the
// implementation of the public not-inline function.
MAYBE_INLINE_EVACUATION
oop G1ParScanThreadState::do_copy_to_survivor_space(G1HeapRegionAttr const region_attr,
                                                    oop const old,
                                                    markWord const old_mark) {
  assert(region_attr.is_in_cset(),
         "Unexpected region attr type: %s", region_attr.get_type_str());

  // Get the klass once.  We'll need it again later, and this avoids
  // re-decoding when it's compressed.
  Klass* klass = old->klass();
  const size_t word_sz = old->size_given_klass(klass);

  // JNI only allows pinning of typeArrays, so we only need to keep those in place.
  if (region_attr.is_pinned() && klass->is_typeArray_klass()) {
    return handle_evacuation_failure_par(old, old_mark, word_sz, true /* cause_pinned */);
  }

  uint age = 0;
  G1HeapRegionAttr dest_attr = next_region_attr(region_attr, old_mark, age);
  HeapRegion* const from_region = _g1h->heap_region_containing(old);
  uint node_index = from_region->node_index();

  HeapWord* obj_ptr = _plab_allocator->plab_allocate(dest_attr, word_sz, node_index);

  // PLAB allocations should succeed most of the time, so we'll
  // normally check against null once and that's it.
  if (obj_ptr == nullptr) {
    obj_ptr = allocate_copy_slow(&dest_attr, old, word_sz, age, node_index);
    if (obj_ptr == nullptr) {
      // This will either forward-to-self, or detect that someone else has
      // installed a forwarding pointer.
      return handle_evacuation_failure_par(old, old_mark, word_sz, false /* cause_pinned */);
    }
  }

  assert(obj_ptr != nullptr, "when we get here, allocation should have succeeded");
  assert(_g1h->is_in_reserved(obj_ptr), "Allocated memory should be in the heap");

  // Should this evacuation fail?
  if (inject_allocation_failure(from_region->hrm_index())) {
    // Doing this after all the allocation attempts also tests the
    // undo_allocation() method too.
    undo_allocation(dest_attr, obj_ptr, word_sz, node_index);
    return handle_evacuation_failure_par(old, old_mark, word_sz, false /* cause_pinned */);
  }

  // We're going to allocate linearly, so might as well prefetch ahead.
  Prefetch::write(obj_ptr, PrefetchCopyIntervalInBytes);
  Copy::aligned_disjoint_words(cast_from_oop<HeapWord*>(old), obj_ptr, word_sz);

  const oop obj = cast_to_oop(obj_ptr);
  // Because the forwarding is done with memory_order_relaxed there is no
  // ordering with the above copy.  Clients that get the forwardee must not
  // examine its contents without other synchronization, since the contents
  // may not be up to date for them.
  const oop forward_ptr = old->forward_to_atomic(obj, old_mark, memory_order_relaxed);
  if (forward_ptr == nullptr) {

    {
      const uint young_index = from_region->young_index_in_cset();
      assert((from_region->is_young() && young_index >  0) ||
             (!from_region->is_young() && young_index == 0), "invariant" );
      _surviving_young_words[young_index] += word_sz;
    }

    if (dest_attr.is_young()) {
      if (age < markWord::max_age) {
        age++;
        obj->incr_age();
      }
      _age_table.add(age, word_sz);
    } else {
      update_bot_after_copying(obj, word_sz);
    }

    // Most objects are not arrays, so do one array check rather than
    // checking for each array category for each object.
    if (klass->is_array_klass()) {
      if (klass->is_objArray_klass()) {
        start_partial_objarray(dest_attr, old, obj);
      } else {
        // Nothing needs to be done for typeArrays.  Body doesn't contain
        // any oops to scan, and the type in the klass will already be handled
        // by processing the built-in module.
        assert(klass->is_typeArray_klass(), "invariant");
      }
      return obj;
    }

    ContinuationGCSupport::transform_stack_chunk(obj);

    // Check for deduplicating young Strings.
    if (G1StringDedup::is_candidate_from_evacuation(klass,
                                                    region_attr,
                                                    dest_attr,
                                                    age)) {
      // Record old; request adds a new weak reference, which reference
      // processing expects to refer to a from-space object.
      _string_dedup_requests.add(old);
    }

    // Skip the card enqueue iff the object (obj) is in survivor region.
    // However, HeapRegion::is_survivor() is too expensive here.
    // Instead, we use dest_attr.is_young() because the two values are always
    // equal: successfully allocated young regions must be survivor regions.
    assert(dest_attr.is_young() == _g1h->heap_region_containing(obj)->is_survivor(), "must be");
    G1SkipCardEnqueueSetter x(&_scanner, dest_attr.is_young());
    obj->oop_iterate_backwards(&_scanner, klass);
    return obj;
  } else {
    _plab_allocator->undo_allocation(dest_attr, obj_ptr, word_sz, node_index);
    return forward_ptr;
  }
}

// Public not-inline entry point.
ATTRIBUTE_FLATTEN
oop G1ParScanThreadState::copy_to_survivor_space(G1HeapRegionAttr region_attr,
                                                 oop old,
                                                 markWord old_mark) {
  return do_copy_to_survivor_space(region_attr, old, old_mark);
}

G1ParScanThreadState* G1ParScanThreadStateSet::state_for_worker(uint worker_id) {
  assert(worker_id < _num_workers, "out of bounds access");
  if (_states[worker_id] == nullptr) {
    _states[worker_id] =
      new G1ParScanThreadState(_g1h, rdcqs(),
                               _preserved_marks_set.get(worker_id),
                               worker_id,
                               _num_workers,
                               _collection_set,
                               _evac_failure_regions);
  }
  return _states[worker_id];
}

const size_t* G1ParScanThreadStateSet::surviving_young_words() const {
  assert(_flushed, "thread local state from the per thread states should have been flushed");
  return _surviving_young_words_total;
}

void G1ParScanThreadStateSet::flush_stats() {
  assert(!_flushed, "thread local state from the per thread states should be flushed once");
  for (uint worker_id = 0; worker_id < _num_workers; ++worker_id) {
    G1ParScanThreadState* pss = _states[worker_id];
    assert(pss != nullptr, "must be initialized");

    G1GCPhaseTimes* p = _g1h->phase_times();

    // Need to get the following two before the call to G1ParThreadScanState::flush()
    // because it resets the PLAB allocator where we get this info from.
    size_t lab_waste_bytes = pss->lab_waste_words() * HeapWordSize;
    size_t lab_undo_waste_bytes = pss->lab_undo_waste_words() * HeapWordSize;
    size_t copied_bytes = pss->flush_stats(_surviving_young_words_total, _num_workers, &_rdc_buffers[worker_id]) * HeapWordSize;
    size_t evac_fail_enqueued_cards = pss->evac_failure_enqueued_cards();

    p->record_or_add_thread_work_item(G1GCPhaseTimes::MergePSS, worker_id, copied_bytes, G1GCPhaseTimes::MergePSSCopiedBytes);
    p->record_or_add_thread_work_item(G1GCPhaseTimes::MergePSS, worker_id, lab_waste_bytes, G1GCPhaseTimes::MergePSSLABWasteBytes);
    p->record_or_add_thread_work_item(G1GCPhaseTimes::MergePSS, worker_id, lab_undo_waste_bytes, G1GCPhaseTimes::MergePSSLABUndoWasteBytes);
    p->record_or_add_thread_work_item(G1GCPhaseTimes::MergePSS, worker_id, evac_fail_enqueued_cards, G1GCPhaseTimes::MergePSSEvacFailExtra);

    delete pss;
    _states[worker_id] = nullptr;
  }

  G1DirtyCardQueueSet& dcq = G1BarrierSet::dirty_card_queue_set();
  dcq.merge_bufferlists(rdcqs());
  rdcqs()->verify_empty();

  _flushed = true;
}

void G1ParScanThreadStateSet::record_unused_optional_region(HeapRegion* hr) {
  for (uint worker_index = 0; worker_index < _num_workers; ++worker_index) {
    G1ParScanThreadState* pss = _states[worker_index];
    assert(pss != nullptr, "must be initialized");

    size_t used_memory = pss->oops_into_optional_region(hr)->used_memory();
    _g1h->phase_times()->record_or_add_thread_work_item(G1GCPhaseTimes::OptScanHR, worker_index, used_memory, G1GCPhaseTimes::ScanHRUsedMemory);
  }
}

NOINLINE
oop G1ParScanThreadState::handle_evacuation_failure_par(oop old, markWord m, size_t word_sz, bool cause_pinned) {
  assert(_g1h->is_in_cset(old), "Object " PTR_FORMAT " should be in the CSet", p2i(old));

  oop forward_ptr = old->forward_to_atomic(old, m, memory_order_relaxed);
  if (forward_ptr == nullptr) {
    // Forward-to-self succeeded. We are the "owner" of the object.
    HeapRegion* r = _g1h->heap_region_containing(old);

    if (_evac_failure_regions->record(_worker_id, r->hrm_index(), cause_pinned)) {
      _g1h->hr_printer()->evac_failure(r);
    }

    // Mark the failing object in the marking bitmap and later use the bitmap to handle
    // evacuation failure recovery.
    _g1h->mark_evac_failure_object(_worker_id, old, word_sz);

    _preserved_marks->push_if_necessary(old, m);

    ContinuationGCSupport::transform_stack_chunk(old);

    _evacuation_failed_info.register_copy_failure(word_sz);

    // For iterating objects that failed evacuation currently we can reuse the
    // existing closure to scan evacuated objects; since we are iterating from a
    // collection set region (i.e. never a Survivor region), we always need to
    // gather cards for this case.
    G1SkipCardEnqueueSetter x(&_scanner, false /* skip_card_enqueue */);
    old->oop_iterate_backwards(&_scanner);

    return old;
  } else {
    // Forward-to-self failed. Either someone else managed to allocate
    // space for this object (old != forward_ptr) or they beat us in
    // self-forwarding it (old == forward_ptr).
    assert(old == forward_ptr || !_g1h->is_in_cset(forward_ptr),
           "Object " PTR_FORMAT " forwarded to: " PTR_FORMAT " "
           "should not be in the CSet",
           p2i(old), p2i(forward_ptr));
    return forward_ptr;
  }
}

void G1ParScanThreadState::initialize_numa_stats() {
  if (_numa->is_enabled()) {
    LogTarget(Info, gc, heap, numa) lt;

    if (lt.is_enabled()) {
      uint num_nodes = _numa->num_active_nodes();
      // Record only if there are multiple active nodes.
      _obj_alloc_stat = NEW_C_HEAP_ARRAY(size_t, num_nodes, mtGC);
      memset(_obj_alloc_stat, 0, sizeof(size_t) * num_nodes);
    }
  }
}

void G1ParScanThreadState::flush_numa_stats() {
  if (_obj_alloc_stat != nullptr) {
    uint node_index = _numa->index_of_current_thread();
    _numa->copy_statistics(G1NUMAStats::LocalObjProcessAtCopyToSurv, node_index, _obj_alloc_stat);
  }
}

void G1ParScanThreadState::update_numa_stats(uint node_index) {
  if (_obj_alloc_stat != nullptr) {
    _obj_alloc_stat[node_index]++;
  }
}

G1ParScanThreadStateSet::G1ParScanThreadStateSet(G1CollectedHeap* g1h,
                                                 uint num_workers,
                                                 G1CollectionSet* collection_set,
                                                 G1EvacFailureRegions* evac_failure_regions) :
    _g1h(g1h),
    _collection_set(collection_set),
    _rdcqs(G1BarrierSet::dirty_card_queue_set().allocator()),
    _preserved_marks_set(true /* in_c_heap */),
    _states(NEW_C_HEAP_ARRAY(G1ParScanThreadState*, num_workers, mtGC)),
    _rdc_buffers(NEW_C_HEAP_ARRAY(BufferNodeList, num_workers, mtGC)),
    _surviving_young_words_total(NEW_C_HEAP_ARRAY(size_t, collection_set->young_region_length() + 1, mtGC)),
    _num_workers(num_workers),
    _flushed(false),
    _evac_failure_regions(evac_failure_regions) {
  _preserved_marks_set.init(num_workers);
  for (uint i = 0; i < num_workers; ++i) {
    _states[i] = nullptr;
    _rdc_buffers[i] = BufferNodeList();
  }
  memset(_surviving_young_words_total, 0, (collection_set->young_region_length() + 1) * sizeof(size_t));
}

G1ParScanThreadStateSet::~G1ParScanThreadStateSet() {
  assert(_flushed, "thread local state from the per thread states should have been flushed");
  FREE_C_HEAP_ARRAY(G1ParScanThreadState*, _states);
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_total);
  FREE_C_HEAP_ARRAY(BufferNodeList, _rdc_buffers);
  _preserved_marks_set.reclaim();
}
