/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1COLLECTEDHEAP_INLINE_HPP
#define SHARE_GC_G1_G1COLLECTEDHEAP_INLINE_HPP

#include "gc/g1/g1CollectedHeap.hpp"

#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1EvacFailureRegions.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/heapRegionSet.inline.hpp"
#include "gc/shared/markBitMap.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "oops/stackChunkOop.hpp"
#include "runtime/atomic.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "utilities/bitMap.inline.hpp"

inline bool G1STWIsAliveClosure::do_object_b(oop p) {
  // An object is reachable if it is outside the collection set,
  // or is inside and copied.
  return !_g1h->is_in_cset(p) || p->is_forwarded();
}

inline JavaThread* const* G1JavaThreadsListClaimer::claim(uint& count) {
  count = 0;
  if (Atomic::load(&_cur_claim) >= _list.length()) {
    return nullptr;
  }
  uint claim = Atomic::fetch_then_add(&_cur_claim, _claim_step);
  if (claim >= _list.length()) {
    return nullptr;
  }
  count = MIN2(_list.length() - claim, _claim_step);
  return _list.list()->threads() + claim;
}

inline void G1JavaThreadsListClaimer::apply(ThreadClosure* cl) {
  JavaThread* const* list;
  uint count;

  while ((list = claim(count)) != nullptr) {
    for (uint i = 0; i < count; i++) {
      cl->do_thread(list[i]);
    }
  }
}

G1GCPhaseTimes* G1CollectedHeap::phase_times() const {
  return _policy->phase_times();
}

G1EvacStats* G1CollectedHeap::alloc_buffer_stats(G1HeapRegionAttr dest) {
  switch (dest.type()) {
    case G1HeapRegionAttr::Young:
      return &_survivor_evac_stats;
    case G1HeapRegionAttr::Old:
      return &_old_evac_stats;
    default:
      ShouldNotReachHere();
      return nullptr; // Keep some compilers happy
  }
}

size_t G1CollectedHeap::desired_plab_sz(G1HeapRegionAttr dest) {
  size_t gclab_word_size = alloc_buffer_stats(dest)->desired_plab_size(workers()->active_workers());
  return clamp_plab_size(gclab_word_size);
}

inline size_t G1CollectedHeap::clamp_plab_size(size_t value) const {
  return clamp(value, PLAB::min_size(), _humongous_object_threshold_in_words);
}

// Inline functions for G1CollectedHeap

// Return the region with the given index. It assumes the index is valid.
inline HeapRegion* G1CollectedHeap::region_at(uint index) const { return _hrm.at(index); }

// Return the region with the given index, or null if unmapped. It assumes the index is valid.
inline HeapRegion* G1CollectedHeap::region_at_or_null(uint index) const { return _hrm.at_or_null(index); }

template <typename Func>
inline void G1CollectedHeap::humongous_obj_regions_iterate(HeapRegion* start, const Func& f) {
  assert(start->is_starts_humongous(), "must be");

  do {
    HeapRegion* next = _hrm.next_region_in_humongous(start);
    f(start);
    start = next;
  } while (start != nullptr);
}

inline uint G1CollectedHeap::addr_to_region(const void* addr) const {
  assert(is_in_reserved(addr),
         "Cannot calculate region index for address " PTR_FORMAT " that is outside of the heap [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(addr), p2i(reserved().start()), p2i(reserved().end()));
  return (uint)(pointer_delta(addr, reserved().start(), sizeof(uint8_t)) >> HeapRegion::LogOfHRGrainBytes);
}

inline HeapWord* G1CollectedHeap::bottom_addr_for_region(uint index) const {
  return _hrm.reserved().start() + index * HeapRegion::GrainWords;
}


inline HeapRegion* G1CollectedHeap::heap_region_containing(const void* addr) const {
  uint const region_idx = addr_to_region(addr);
  return region_at(region_idx);
}

inline HeapRegion* G1CollectedHeap::heap_region_containing_or_null(const void* addr) const {
  uint const region_idx = addr_to_region(addr);
  return region_at_or_null(region_idx);
}

inline void G1CollectedHeap::old_set_add(HeapRegion* hr) {
  _old_set.add(hr);
}

inline void G1CollectedHeap::old_set_remove(HeapRegion* hr) {
  _old_set.remove(hr);
}

// It dirties the cards that cover the block so that the post
// write barrier never queues anything when updating objects on this
// block. It is assumed (and in fact we assert) that the block
// belongs to a young region.
inline void
G1CollectedHeap::dirty_young_block(HeapWord* start, size_t word_size) {
  assert_heap_not_locked();

  // Assign the containing region to containing_hr so that we don't
  // have to keep calling heap_region_containing() in the
  // asserts below.
  DEBUG_ONLY(HeapRegion* containing_hr = heap_region_containing(start);)
  assert(word_size > 0, "pre-condition");
  assert(containing_hr->is_in(start), "it should contain start");
  assert(containing_hr->is_young(), "it should be young");
  assert(!containing_hr->is_humongous(), "it should not be humongous");

  HeapWord* end = start + word_size;
  assert(containing_hr->is_in(end - 1), "it should also contain end - 1");

  MemRegion mr(start, end);
  card_table()->g1_mark_as_young(mr);
}

inline G1ScannerTasksQueueSet* G1CollectedHeap::task_queues() const {
  return _task_queues;
}

inline G1ScannerTasksQueue* G1CollectedHeap::task_queue(uint i) const {
  return _task_queues->queue(i);
}

inline bool G1CollectedHeap::is_marked(oop obj) const {
  return _cm->mark_bitmap()->is_marked(obj);
}

inline bool G1CollectedHeap::is_in_cset(oop obj) const {
  return is_in_cset(cast_from_oop<HeapWord*>(obj));
}

inline bool G1CollectedHeap::is_in_cset(HeapWord* addr) const {
  return _region_attr.is_in_cset(addr);
}

bool G1CollectedHeap::is_in_cset(const HeapRegion* hr) const {
  return _region_attr.is_in_cset(hr);
}

bool G1CollectedHeap::is_in_cset_or_humongous_candidate(const oop obj) {
  return _region_attr.is_in_cset_or_humongous_candidate(cast_from_oop<HeapWord*>(obj));
}

G1HeapRegionAttr G1CollectedHeap::region_attr(const void* addr) const {
  return _region_attr.at((HeapWord*)addr);
}

G1HeapRegionAttr G1CollectedHeap::region_attr(uint idx) const {
  return _region_attr.get_by_index(idx);
}

void G1CollectedHeap::register_humongous_candidate_region_with_region_attr(uint index) {
  _region_attr.set_humongous_candidate(index);
}

void G1CollectedHeap::register_new_survivor_region_with_region_attr(HeapRegion* r) {
  _region_attr.set_new_survivor_region(r->hrm_index());
}

void G1CollectedHeap::register_region_with_region_attr(HeapRegion* r) {
  _region_attr.set_remset_is_tracked(r->hrm_index(), r->rem_set()->is_tracked());
}

void G1CollectedHeap::register_old_region_with_region_attr(HeapRegion* r) {
  _region_attr.set_in_old(r->hrm_index(), r->rem_set()->is_tracked());
  _rem_set->exclude_region_from_scan(r->hrm_index());
}

void G1CollectedHeap::register_optional_region_with_region_attr(HeapRegion* r) {
  _region_attr.set_optional(r->hrm_index(), r->rem_set()->is_tracked());
}

inline bool G1CollectedHeap::is_in_young(const oop obj) const {
  if (obj == nullptr) {
    return false;
  }
  return heap_region_containing(obj)->is_young();
}

inline bool G1CollectedHeap::requires_barriers(stackChunkOop obj) const {
  assert(obj != nullptr, "");
  return !heap_region_containing(obj)->is_young(); // is_in_young does an unnecessary null check
}

inline bool G1CollectedHeap::is_obj_filler(const oop obj) {
  Klass* k = obj->klass_raw();
  return k == Universe::fillerArrayKlassObj() || k == vmClasses::FillerObject_klass();
}

inline bool G1CollectedHeap::is_obj_dead(const oop obj, const HeapRegion* hr) const {
  if (hr->is_in_parsable_area(obj)) {
    // This object is in the parsable part of the heap, live unless scrubbed.
    return is_obj_filler(obj);
  } else {
    // From Remark until a region has been concurrently scrubbed, parts of the
    // region is not guaranteed to be parsable. Use the bitmap for liveness.
    return !concurrent_mark()->mark_bitmap()->is_marked(obj);
  }
}

inline bool G1CollectedHeap::is_obj_dead(const oop obj) const {
  assert(obj != nullptr, "precondition");

  return is_obj_dead(obj, heap_region_containing(obj));
}

inline bool G1CollectedHeap::is_obj_dead_full(const oop obj, const HeapRegion* hr) const {
   return !is_marked(obj);
}

inline bool G1CollectedHeap::is_obj_dead_full(const oop obj) const {
    return is_obj_dead_full(obj, heap_region_containing(obj));
}

inline bool G1CollectedHeap::is_humongous_reclaim_candidate(uint region) {
  return _region_attr.is_humongous_candidate(region);
}

inline void G1CollectedHeap::set_humongous_is_live(oop obj) {
  uint region = addr_to_region(obj);
  // Reset the entry in the region attribute table so that subsequent
  // references to the same humongous object do not go into the slow path
  // again. This is racy, as multiple threads may at the same time enter here,
  // but this is benign because the transition is unidirectional, from
  // humongous-candidate to not, and the write, in evacuation, is
  // separated from the read, in post-evacuation.
  if (_region_attr.is_humongous_candidate(region)) {
    _region_attr.clear_humongous_candidate(region);
  }
}

inline bool G1CollectedHeap::is_collection_set_candidate(const HeapRegion* r) const {
  const G1CollectionSetCandidates* candidates = collection_set()->candidates();
  return candidates->contains(r);
}

#endif // SHARE_GC_G1_G1COLLECTEDHEAP_INLINE_HPP
