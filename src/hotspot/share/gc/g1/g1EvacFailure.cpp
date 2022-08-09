/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"

class RemoveSelfForwardPtrObjClosure {
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

  // Handle the marked objects in the region. These are self-forwarded objects
  // that need to be kept live. We need to update the remembered sets of these
  // objects. Further update the BOT and marks.
  // We can coalesce and overwrite the remaining heap contents with dummy objects
  // as they have either been dead or evacuated (which are unreferenced now, i.e.
  // dead too) already.
  size_t apply(oop obj) {
    HeapWord* obj_addr = cast_from_oop<HeapWord*>(obj);
    size_t obj_size = obj->size();
    assert(_last_forwarded_object_end <= obj_addr, "should iterate in ascending address order");
    assert(_hr->is_in(obj_addr), "sanity");

    // The object failed to move.
    assert(obj->is_forwarded() && obj->forwardee() == obj, "sanity");

    zap_dead_objects(_last_forwarded_object_end, obj_addr);

    assert(_cm->is_marked_in_bitmap(obj), "should be correctly marked");
    if (_during_concurrent_start) {
      // If the evacuation failure occurs during concurrent start we should do
      // any additional necessary per-object actions.
      _cm->add_to_liveness(_worker_id, obj, obj_size);
    }

    _marked_words += obj_size;
    // Reset the markWord
    obj->init_mark();

    HeapWord* obj_end = obj_addr + obj_size;
    _last_forwarded_object_end = obj_end;
    _hr->update_bot_for_block(obj_addr, obj_end);
    return obj_size;
  }

  // Fill the memory area from start to end with filler objects, and update the BOT
  // accordingly.
  void zap_dead_objects(HeapWord* start, HeapWord* end) {
    if (start == end) {
      return;
    }

    _hr->fill_range_with_dead_objects(start, end);
  }

  void zap_remainder() {
    zap_dead_objects(_last_forwarded_object_end, _hr->top());
  }
};

class RemoveSelfForwardPtrHRClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  uint _worker_id;

  G1EvacFailureRegions* _evac_failure_regions;

  G1GCPhaseTimes* _phase_times;

public:
  RemoveSelfForwardPtrHRClosure(uint worker_id,
                                G1EvacFailureRegions* evac_failure_regions) :
    _g1h(G1CollectedHeap::heap()),
    _worker_id(worker_id),
    _evac_failure_regions(evac_failure_regions),
    _phase_times(G1CollectedHeap::heap()->phase_times()) {
  }

  size_t remove_self_forward_ptr_by_walking_hr(HeapRegion* hr,
                                               bool during_concurrent_start) {
    RemoveSelfForwardPtrObjClosure rspc(hr,
                                        during_concurrent_start,
                                        _worker_id);

    // All objects that failed evacuation has been marked in the bitmap.
    // Use the bitmap to apply the above closure to all failing objects.
    G1CMBitMap* bitmap = _g1h->concurrent_mark()->mark_bitmap();
    hr->apply_to_marked_objects(bitmap, &rspc);
    // Need to zap the remainder area of the processed region.
    rspc.zap_remainder();
    // Now clear all the marks to be ready for a new marking cyle.
    if (!during_concurrent_start) {
      assert(hr->top_at_mark_start() == hr->bottom(), "TAMS must be bottom to make all objects look live");
      _g1h->clear_bitmap_for_region(hr);
    } else {
      assert(hr->top_at_mark_start() == hr->top(), "TAMS must be top for bitmap to have any value");
      // Keep the bits.
    }
    // We never evacuate Old (non-humongous, non-archive) regions during scrubbing
    // (only afterwards); other regions (young, humongous, archive) never need
    // scrubbing, so the following must hold.
    assert(hr->parsable_bottom() == hr->bottom(), "PB must be bottom to make the whole area parsable");

    return rspc.marked_bytes();
  }

  bool do_heap_region(HeapRegion *hr) {
    assert(!hr->is_pinned(), "Unexpected pinned region at index %u", hr->hrm_index());
    assert(hr->in_collection_set(), "bad CS");
    assert(_evac_failure_regions->contains(hr->hrm_index()), "precondition");

    hr->clear_index_in_opt_cset();

    bool during_concurrent_start = _g1h->collector_state()->in_concurrent_start_gc();

    hr->note_self_forwarding_removal_start(during_concurrent_start);

    _phase_times->record_or_add_thread_work_item(G1GCPhaseTimes::RestoreRetainedRegions,
                                                   _worker_id,
                                                   1,
                                                   G1GCPhaseTimes::RestoreRetainedRegionsNum);

    size_t live_bytes = remove_self_forward_ptr_by_walking_hr(hr, during_concurrent_start);

    hr->rem_set()->clean_code_roots(hr);
    hr->rem_set()->clear_locked(true);

    hr->note_self_forwarding_removal_end(live_bytes);

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
