/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1EvacFailure.hpp"
#include "gc/g1/g1EvacFailureRegions.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"

class RemoveSelfForwardPtrObjClosure: public ObjectClosure {
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;
  HeapRegion* _hr;
  size_t _marked_words;
  bool _during_concurrent_start;
  uint _worker_id;
  HeapWord* _last_forwarded_object_end;

public:
  RemoveSelfForwardPtrObjClosure(HeapRegion* hr,
                                 bool during_concurrent_start,
                                 uint worker_id) :
    _g1h(G1CollectedHeap::heap()),
    _cm(_g1h->concurrent_mark()),
    _hr(hr),
    _marked_words(0),
    _during_concurrent_start(during_concurrent_start),
    _worker_id(worker_id),
    _last_forwarded_object_end(hr->bottom()) { }

  size_t marked_bytes() { return _marked_words * HeapWordSize; }

  // Iterate over the live objects in the region to find self-forwarded objects
  // that need to be kept live. We need to update the remembered sets of these
  // objects. Further update the BOT and marks.
  // We can coalesce and overwrite the remaining heap contents with dummy objects
  // as they have either been dead or evacuated (which are unreferenced now, i.e.
  // dead too) already.
  void do_object(oop obj) {
    HeapWord* obj_addr = cast_from_oop<HeapWord*>(obj);
    assert(_last_forwarded_object_end <= obj_addr, "should iterate in ascending address order");
    assert(_hr->is_in(obj_addr), "sanity");

    // The object failed to move.
    assert(obj->is_forwarded() && obj->forwardee() == obj, "sanity");

    zap_dead_objects(_last_forwarded_object_end, obj_addr);
    // We consider all objects that we find self-forwarded to be
    // live. What we'll do is that we'll update the prev marking
    // info so that they are all under PTAMS and explicitly marked.
    if (!_cm->is_marked_in_prev_bitmap(obj)) {
      _cm->mark_in_prev_bitmap(obj);
    }
    if (_during_concurrent_start) {
      // For the next marking info we'll only mark the
      // self-forwarded objects explicitly if we are during
      // concurrent start (since, normally, we only mark objects pointed
      // to by roots if we succeed in copying them). By marking all
      // self-forwarded objects we ensure that we mark any that are
      // still pointed to be roots. During concurrent marking, and
      // after concurrent start, we don't need to mark any objects
      // explicitly and all objects in the CSet are considered
      // (implicitly) live. So, we won't mark them explicitly and
      // we'll leave them over NTAMS.
      _cm->mark_in_next_bitmap(_worker_id, _hr, obj);
    }
    size_t obj_size = obj->size();

    _marked_words += obj_size;
    PreservedMarks::init_forwarded_mark(obj);

    HeapWord* obj_end = obj_addr + obj_size;
    _last_forwarded_object_end = obj_end;
    _hr->alloc_block_in_bot(obj_addr, obj_end);
  }

  // Fill the memory area from start to end with filler objects, and update the BOT
  // and the mark bitmap accordingly.
  void zap_dead_objects(HeapWord* start, HeapWord* end) {
    if (start == end) {
      return;
    }

    size_t gap_size = pointer_delta(end, start);
    MemRegion mr(start, gap_size);
    if (gap_size >= CollectedHeap::min_fill_size()) {
      CollectedHeap::fill_with_objects(start, gap_size);

      HeapWord* end_first_obj = start + cast_to_oop(start)->size();
      _hr->alloc_block_in_bot(start, end_first_obj);
      // Fill_with_objects() may have created multiple (i.e. two)
      // objects, as the max_fill_size() is half a region.
      // After updating the BOT for the first object, also update the
      // BOT for the second object to make the BOT complete.
      if (end_first_obj != end) {
        _hr->alloc_block_in_bot(end_first_obj, end);
#ifdef ASSERT
        size_t size_second_obj = cast_to_oop(end_first_obj)->size();
        HeapWord* end_of_second_obj = end_first_obj + size_second_obj;
        assert(end == end_of_second_obj,
               "More than two objects were used to fill the area from " PTR_FORMAT " to " PTR_FORMAT ", "
               "second objects size " SIZE_FORMAT " ends at " PTR_FORMAT,
               p2i(start), p2i(end), size_second_obj, p2i(end_of_second_obj));
#endif
      }
    }
    _cm->clear_range_in_prev_bitmap(mr);
  }

  void zap_remainder() {
    zap_dead_objects(_last_forwarded_object_end, _hr->top());
  }
};

class RemoveSelfForwardPtrHRClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  uint _worker_id;

  G1EvacFailureRegions* _evac_failure_regions;

public:
  RemoveSelfForwardPtrHRClosure(uint worker_id,
                                G1EvacFailureRegions* evac_failure_regions) :
    _g1h(G1CollectedHeap::heap()),
    _worker_id(worker_id),
    _evac_failure_regions(evac_failure_regions) {
  }

  size_t remove_self_forward_ptr_by_walking_hr(HeapRegion* hr,
                                               bool during_concurrent_start) {
    RemoveSelfForwardPtrObjClosure rspc(hr,
                                        during_concurrent_start,
                                        _worker_id);
    // Iterates evac failure objs which are recorded during evacuation.
    hr->process_and_drop_evac_failure_objs(&rspc);
    // Need to zap the remainder area of the processed region.
    rspc.zap_remainder();

    return rspc.marked_bytes();
  }

  bool do_heap_region(HeapRegion *hr) {
    assert(!hr->is_pinned(), "Unexpected pinned region at index %u", hr->hrm_index());
    assert(hr->in_collection_set(), "bad CS");

    if (_evac_failure_regions->contains(hr->hrm_index())) {
      hr->clear_index_in_opt_cset();

      bool during_concurrent_start = _g1h->collector_state()->in_concurrent_start_gc();
      bool during_concurrent_mark = _g1h->collector_state()->mark_or_rebuild_in_progress();

      hr->note_self_forwarding_removal_start(during_concurrent_start,
                                             during_concurrent_mark);
      _g1h->verifier()->check_bitmaps("Self-Forwarding Ptr Removal", hr);

      hr->reset_bot();

      size_t live_bytes = remove_self_forward_ptr_by_walking_hr(hr, during_concurrent_start);

      hr->rem_set()->clean_strong_code_roots(hr);
      hr->rem_set()->clear_locked(true);

      hr->note_self_forwarding_removal_end(live_bytes);
    }
    return false;
  }
};

G1ParRemoveSelfForwardPtrsTask::G1ParRemoveSelfForwardPtrsTask(G1EvacFailureRegions* evac_failure_regions) :
  WorkerTask("G1 Remove Self-forwarding Pointers"),
  _g1h(G1CollectedHeap::heap()),
  _hrclaimer(_g1h->workers()->active_workers()),
  _evac_failure_regions(evac_failure_regions) { }

void G1ParRemoveSelfForwardPtrsTask::work(uint worker_id) {
  RemoveSelfForwardPtrHRClosure rsfp_cl(worker_id, _evac_failure_regions);

  // Iterate through all regions that failed evacuation during the entire collection.
  _evac_failure_regions->par_iterate(&rsfp_cl, &_hrclaimer, worker_id);
}

uint G1ParRemoveSelfForwardPtrsTask::num_failed_regions() const {
  return _evac_failure_regions->num_regions_failed_evacuation();
}
