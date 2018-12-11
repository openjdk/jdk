/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/prefetch.inline.hpp"

G1ParScanThreadState::G1ParScanThreadState(G1CollectedHeap* g1h,
                                           uint worker_id,
                                           size_t young_cset_length,
                                           size_t optional_cset_length)
  : _g1h(g1h),
    _refs(g1h->task_queue(worker_id)),
    _dcq(&g1h->dirty_card_queue_set()),
    _ct(g1h->card_table()),
    _closures(NULL),
    _plab_allocator(NULL),
    _age_table(false),
    _tenuring_threshold(g1h->g1_policy()->tenuring_threshold()),
    _scanner(g1h, this),
    _worker_id(worker_id),
    _stack_trim_upper_threshold(GCDrainStackTargetSize * 2 + 1),
    _stack_trim_lower_threshold(GCDrainStackTargetSize),
    _trim_ticks(),
    _old_gen_is_full(false),
    _num_optional_regions(optional_cset_length)
{
  // we allocate G1YoungSurvRateNumRegions plus one entries, since
  // we "sacrifice" entry 0 to keep track of surviving bytes for
  // non-young regions (where the age is -1)
  // We also add a few elements at the beginning and at the end in
  // an attempt to eliminate cache contention
  size_t real_length = 1 + young_cset_length;
  size_t array_length = PADDING_ELEM_NUM +
                      real_length +
                      PADDING_ELEM_NUM;
  _surviving_young_words_base = NEW_C_HEAP_ARRAY(size_t, array_length, mtGC);
  if (_surviving_young_words_base == NULL)
    vm_exit_out_of_memory(array_length * sizeof(size_t), OOM_MALLOC_ERROR,
                          "Not enough space for young surv histo.");
  _surviving_young_words = _surviving_young_words_base + PADDING_ELEM_NUM;
  memset(_surviving_young_words, 0, real_length * sizeof(size_t));

  _plab_allocator = new G1PLABAllocator(_g1h->allocator());

  _dest[InCSetState::NotInCSet]    = InCSetState::NotInCSet;
  // The dest for Young is used when the objects are aged enough to
  // need to be moved to the next space.
  _dest[InCSetState::Young]        = InCSetState::Old;
  _dest[InCSetState::Old]          = InCSetState::Old;

  _closures = G1EvacuationRootClosures::create_root_closures(this, _g1h);

  _oops_into_optional_regions = new G1OopStarChunkedList[_num_optional_regions];
}

// Pass locally gathered statistics to global state.
void G1ParScanThreadState::flush(size_t* surviving_young_words) {
  _dcq.flush();
  // Update allocation statistics.
  _plab_allocator->flush_and_retire_stats();
  _g1h->g1_policy()->record_age_table(&_age_table);

  uint length = _g1h->collection_set()->young_region_length();
  for (uint region_index = 0; region_index < length; region_index++) {
    surviving_young_words[region_index] += _surviving_young_words[region_index];
  }
}

G1ParScanThreadState::~G1ParScanThreadState() {
  delete _plab_allocator;
  delete _closures;
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_base);
  delete[] _oops_into_optional_regions;
}

void G1ParScanThreadState::waste(size_t& wasted, size_t& undo_wasted) {
  _plab_allocator->waste(wasted, undo_wasted);
}

#ifdef ASSERT
bool G1ParScanThreadState::verify_ref(narrowOop* ref) const {
  assert(ref != NULL, "invariant");
  assert(UseCompressedOops, "sanity");
  assert(!has_partial_array_mask(ref), "ref=" PTR_FORMAT, p2i(ref));
  oop p = RawAccess<>::oop_load(ref);
  assert(_g1h->is_in_g1_reserved(p),
         "ref=" PTR_FORMAT " p=" PTR_FORMAT, p2i(ref), p2i(p));
  return true;
}

bool G1ParScanThreadState::verify_ref(oop* ref) const {
  assert(ref != NULL, "invariant");
  if (has_partial_array_mask(ref)) {
    // Must be in the collection set--it's already been copied.
    oop p = clear_partial_array_mask(ref);
    assert(_g1h->is_in_cset(p),
           "ref=" PTR_FORMAT " p=" PTR_FORMAT, p2i(ref), p2i(p));
  } else {
    oop p = RawAccess<>::oop_load(ref);
    assert(_g1h->is_in_g1_reserved(p),
           "ref=" PTR_FORMAT " p=" PTR_FORMAT, p2i(ref), p2i(p));
  }
  return true;
}

bool G1ParScanThreadState::verify_task(StarTask ref) const {
  if (ref.is_narrow()) {
    return verify_ref((narrowOop*) ref);
  } else {
    return verify_ref((oop*) ref);
  }
}
#endif // ASSERT

void G1ParScanThreadState::trim_queue() {
  StarTask ref;
  do {
    // Fully drain the queue.
    trim_queue_to_threshold(0);
  } while (!_refs->is_empty());
}

HeapWord* G1ParScanThreadState::allocate_in_next_plab(InCSetState const state,
                                                      InCSetState* dest,
                                                      size_t word_sz,
                                                      bool previous_plab_refill_failed) {
  assert(state.is_in_cset_or_humongous(), "Unexpected state: " CSETSTATE_FORMAT, state.value());
  assert(dest->is_in_cset_or_humongous(), "Unexpected dest: " CSETSTATE_FORMAT, dest->value());

  // Right now we only have two types of regions (young / old) so
  // let's keep the logic here simple. We can generalize it when necessary.
  if (dest->is_young()) {
    bool plab_refill_in_old_failed = false;
    HeapWord* const obj_ptr = _plab_allocator->allocate(InCSetState::Old,
                                                        word_sz,
                                                        &plab_refill_in_old_failed);
    // Make sure that we won't attempt to copy any other objects out
    // of a survivor region (given that apparently we cannot allocate
    // any new ones) to avoid coming into this slow path again and again.
    // Only consider failed PLAB refill here: failed inline allocations are
    // typically large, so not indicative of remaining space.
    if (previous_plab_refill_failed) {
      _tenuring_threshold = 0;
    }

    if (obj_ptr != NULL) {
      dest->set_old();
    } else {
      // We just failed to allocate in old gen. The same idea as explained above
      // for making survivor gen unavailable for allocation applies for old gen.
      _old_gen_is_full = plab_refill_in_old_failed;
    }
    return obj_ptr;
  } else {
    _old_gen_is_full = previous_plab_refill_failed;
    assert(dest->is_old(), "Unexpected dest: " CSETSTATE_FORMAT, dest->value());
    // no other space to try.
    return NULL;
  }
}

InCSetState G1ParScanThreadState::next_state(InCSetState const state, markOop const m, uint& age) {
  if (state.is_young()) {
    age = !m->has_displaced_mark_helper() ? m->age()
                                          : m->displaced_mark_helper()->age();
    if (age < _tenuring_threshold) {
      return state;
    }
  }
  return dest(state);
}

void G1ParScanThreadState::report_promotion_event(InCSetState const dest_state,
                                                  oop const old, size_t word_sz, uint age,
                                                  HeapWord * const obj_ptr) const {
  PLAB* alloc_buf = _plab_allocator->alloc_buffer(dest_state);
  if (alloc_buf->contains(obj_ptr)) {
    _g1h->_gc_tracer_stw->report_promotion_in_new_plab_event(old->klass(), word_sz * HeapWordSize, age,
                                                             dest_state.value() == InCSetState::Old,
                                                             alloc_buf->word_sz() * HeapWordSize);
  } else {
    _g1h->_gc_tracer_stw->report_promotion_outside_plab_event(old->klass(), word_sz * HeapWordSize, age,
                                                              dest_state.value() == InCSetState::Old);
  }
}

oop G1ParScanThreadState::copy_to_survivor_space(InCSetState const state,
                                                 oop const old,
                                                 markOop const old_mark) {
  const size_t word_sz = old->size();
  HeapRegion* const from_region = _g1h->heap_region_containing(old);
  // +1 to make the -1 indexes valid...
  const int young_index = from_region->young_index_in_cset()+1;
  assert( (from_region->is_young() && young_index >  0) ||
         (!from_region->is_young() && young_index == 0), "invariant" );

  uint age = 0;
  InCSetState dest_state = next_state(state, old_mark, age);
  // The second clause is to prevent premature evacuation failure in case there
  // is still space in survivor, but old gen is full.
  if (_old_gen_is_full && dest_state.is_old()) {
    return handle_evacuation_failure_par(old, old_mark);
  }
  HeapWord* obj_ptr = _plab_allocator->plab_allocate(dest_state, word_sz);

  // PLAB allocations should succeed most of the time, so we'll
  // normally check against NULL once and that's it.
  if (obj_ptr == NULL) {
    bool plab_refill_failed = false;
    obj_ptr = _plab_allocator->allocate_direct_or_new_plab(dest_state, word_sz, &plab_refill_failed);
    if (obj_ptr == NULL) {
      obj_ptr = allocate_in_next_plab(state, &dest_state, word_sz, plab_refill_failed);
      if (obj_ptr == NULL) {
        // This will either forward-to-self, or detect that someone else has
        // installed a forwarding pointer.
        return handle_evacuation_failure_par(old, old_mark);
      }
    }
    if (_g1h->_gc_tracer_stw->should_report_promotion_events()) {
      // The events are checked individually as part of the actual commit
      report_promotion_event(dest_state, old, word_sz, age, obj_ptr);
    }
  }

  assert(obj_ptr != NULL, "when we get here, allocation should have succeeded");
  assert(_g1h->is_in_reserved(obj_ptr), "Allocated memory should be in the heap");

#ifndef PRODUCT
  // Should this evacuation fail?
  if (_g1h->evacuation_should_fail()) {
    // Doing this after all the allocation attempts also tests the
    // undo_allocation() method too.
    _plab_allocator->undo_allocation(dest_state, obj_ptr, word_sz);
    return handle_evacuation_failure_par(old, old_mark);
  }
#endif // !PRODUCT

  // We're going to allocate linearly, so might as well prefetch ahead.
  Prefetch::write(obj_ptr, PrefetchCopyIntervalInBytes);

  const oop obj = oop(obj_ptr);
  const oop forward_ptr = old->forward_to_atomic(obj, old_mark, memory_order_relaxed);
  if (forward_ptr == NULL) {
    Copy::aligned_disjoint_words((HeapWord*) old, obj_ptr, word_sz);

    if (dest_state.is_young()) {
      if (age < markOopDesc::max_age) {
        age++;
      }
      if (old_mark->has_displaced_mark_helper()) {
        // In this case, we have to install the mark word first,
        // otherwise obj looks to be forwarded (the old mark word,
        // which contains the forward pointer, was copied)
        obj->set_mark_raw(old_mark);
        markOop new_mark = old_mark->displaced_mark_helper()->set_age(age);
        old_mark->set_displaced_mark_helper(new_mark);
      } else {
        obj->set_mark_raw(old_mark->set_age(age));
      }
      _age_table.add(age, word_sz);
    } else {
      obj->set_mark_raw(old_mark);
    }

    if (G1StringDedup::is_enabled()) {
      const bool is_from_young = state.is_young();
      const bool is_to_young = dest_state.is_young();
      assert(is_from_young == _g1h->heap_region_containing(old)->is_young(),
             "sanity");
      assert(is_to_young == _g1h->heap_region_containing(obj)->is_young(),
             "sanity");
      G1StringDedup::enqueue_from_evacuation(is_from_young,
                                             is_to_young,
                                             _worker_id,
                                             obj);
    }

    _surviving_young_words[young_index] += word_sz;

    if (obj->is_objArray() && arrayOop(obj)->length() >= ParGCArrayScanChunk) {
      // We keep track of the next start index in the length field of
      // the to-space object. The actual length can be found in the
      // length field of the from-space object.
      arrayOop(obj)->set_length(0);
      oop* old_p = set_partial_array_mask(old);
      do_oop_partial_array(old_p);
    } else {
      G1ScanInYoungSetter x(&_scanner, dest_state.is_young());
      obj->oop_iterate_backwards(&_scanner);
    }
    return obj;
  } else {
    _plab_allocator->undo_allocation(dest_state, obj_ptr, word_sz);
    return forward_ptr;
  }
}

G1ParScanThreadState* G1ParScanThreadStateSet::state_for_worker(uint worker_id) {
  assert(worker_id < _n_workers, "out of bounds access");
  if (_states[worker_id] == NULL) {
    _states[worker_id] =
      new G1ParScanThreadState(_g1h, worker_id, _young_cset_length, _optional_cset_length);
  }
  return _states[worker_id];
}

const size_t* G1ParScanThreadStateSet::surviving_young_words() const {
  assert(_flushed, "thread local state from the per thread states should have been flushed");
  return _surviving_young_words_total;
}

void G1ParScanThreadStateSet::flush() {
  assert(!_flushed, "thread local state from the per thread states should be flushed once");

  for (uint worker_index = 0; worker_index < _n_workers; ++worker_index) {
    G1ParScanThreadState* pss = _states[worker_index];

    if (pss == NULL) {
      continue;
    }

    pss->flush(_surviving_young_words_total);
    delete pss;
    _states[worker_index] = NULL;
  }
  _flushed = true;
}

void G1ParScanThreadStateSet::record_unused_optional_region(HeapRegion* hr) {
  for (uint worker_index = 0; worker_index < _n_workers; ++worker_index) {
    G1ParScanThreadState* pss = _states[worker_index];

    if (pss == NULL) {
      continue;
    }

    size_t used_memory = pss->oops_into_optional_region(hr)->used_memory();
    _g1h->g1_policy()->phase_times()->record_or_add_thread_work_item(G1GCPhaseTimes::OptScanRS, worker_index, used_memory, G1GCPhaseTimes::OptCSetUsedMemory);
  }
}

oop G1ParScanThreadState::handle_evacuation_failure_par(oop old, markOop m) {
  assert(_g1h->is_in_cset(old), "Object " PTR_FORMAT " should be in the CSet", p2i(old));

  oop forward_ptr = old->forward_to_atomic(old, m, memory_order_relaxed);
  if (forward_ptr == NULL) {
    // Forward-to-self succeeded. We are the "owner" of the object.
    HeapRegion* r = _g1h->heap_region_containing(old);

    if (!r->evacuation_failed()) {
      r->set_evacuation_failed(true);
     _g1h->hr_printer()->evac_failure(r);
    }

    _g1h->preserve_mark_during_evac_failure(_worker_id, old, m);

    G1ScanInYoungSetter x(&_scanner, r->is_young());
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
G1ParScanThreadStateSet::G1ParScanThreadStateSet(G1CollectedHeap* g1h,
                                                 uint n_workers,
                                                 size_t young_cset_length,
                                                 size_t optional_cset_length) :
    _g1h(g1h),
    _states(NEW_C_HEAP_ARRAY(G1ParScanThreadState*, n_workers, mtGC)),
    _surviving_young_words_total(NEW_C_HEAP_ARRAY(size_t, young_cset_length, mtGC)),
    _young_cset_length(young_cset_length),
    _optional_cset_length(optional_cset_length),
    _n_workers(n_workers),
    _flushed(false) {
  for (uint i = 0; i < n_workers; ++i) {
    _states[i] = NULL;
  }
  memset(_surviving_young_words_total, 0, young_cset_length * sizeof(size_t));
}

G1ParScanThreadStateSet::~G1ParScanThreadStateSet() {
  assert(_flushed, "thread local state from the per thread states should have been flushed");
  FREE_C_HEAP_ARRAY(G1ParScanThreadState*, _states);
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_total);
}
