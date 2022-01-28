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
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HeapRegionChunk.inline.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"

class RemoveSelfForwardPtrObjClosure {
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;
  HeapRegion* _hr;
  G1HeapRegionChunk* _chunk;
  size_t _marked_words;
  size_t _marked_objects;
  bool _during_concurrent_start;
  uint _worker_id;
  HeapWord* _last_forwarded_object_end;

public:
  RemoveSelfForwardPtrObjClosure(HeapRegion* hr,
                                 G1HeapRegionChunk* chunk,
                                 bool during_concurrent_start,
                                 uint worker_id) :
    _g1h(G1CollectedHeap::heap()),
    _cm(_g1h->concurrent_mark()),
    _hr(hr),
    _chunk(chunk),
    _marked_words(0),
    _marked_objects(0),
    _during_concurrent_start(during_concurrent_start),
    _worker_id(worker_id) {
    _last_forwarded_object_end = _chunk->include_first_obj_in_region() ?
                                 _hr->bottom() : _chunk->first_obj_in_chunk();
  }

  size_t marked_words() const { return _marked_words; }
  size_t marked_objects() const { return _marked_objects; }

  // Handle the marked objects in the region. These are self-forwarded objects
  // that need to be kept live. We need to update the remembered sets of these
  // objects. Further update the BOT and marks.
  // We can coalesce and overwrite the remaining heap contents with dummy objects
  // as they have either been dead or evacuated (which are unreferenced now, i.e.
  // dead too) already.
  size_t apply(oop obj) {
    HeapWord* obj_addr = cast_from_oop<HeapWord*>(obj);
    assert(_last_forwarded_object_end <= obj_addr, "should iterate in ascending address order");
    assert(_hr->is_in(obj_addr), "sanity");

    // The object failed to move.
    assert(obj->is_forwarded() && obj->forwardee() == obj, "sanity");

    zap_dead_objects(_last_forwarded_object_end, obj_addr);

    // Zapping clears the bitmap, make sure it didn't clear too much.
    assert(_cm->is_marked_in_prev_bitmap(obj), "should be correctly marked");
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
      _cm->mark_in_next_bitmap(_worker_id, obj);
    }
    size_t obj_size = obj->size();

    _marked_objects++;
    _marked_words += obj_size;
    PreservedMarks::init_forwarded_mark(obj);

    HeapWord* obj_end = obj_addr + obj_size;
    _last_forwarded_object_end = obj_end;
    _hr->update_bot_if_crossing_boundary(obj_addr, obj_size, false);
    return obj_size;
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

      size_t dummy_size = cast_to_oop(start)->size();
      HeapWord* end_first_obj = start + cast_to_oop(start)->size();
      _hr->update_bot_if_crossing_boundary(start, dummy_size, false);
      // Fill_with_objects() may have created multiple (i.e. two)
      // objects, as the max_fill_size() is half a region.
      // After updating the BOT for the first object, also update the
      // BOT for the second object to make the BOT complete.
      if (end_first_obj != end) {
        _hr->update_bot_if_crossing_boundary(end_first_obj, cast_to_oop(end_first_obj)->size(), false);
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
    zap_dead_objects(_last_forwarded_object_end, _chunk->next_obj_in_region());
    if (_chunk->include_last_obj_in_region()) {
      // As we have process the self forwardee in parallel,
      // it's necessary to update the bot threshold explicitly.
      _hr->update_bot_threshold();
    }
  }
};

class RemoveSelfForwardPtrHRChunkClosure : public G1HeapRegionChunkClosure {
  G1CollectedHeap* _g1h;
  size_t* _marked_words_in_regions;
  uint _worker_id;

  void remove_self_forward_ptr_by_walking_chunk(G1HeapRegionChunk* chunk,
                                                bool during_concurrent_start) {
    RemoveSelfForwardPtrObjClosure rspc(chunk->heap_region(),
                                        chunk,
                                        during_concurrent_start,
                                        _worker_id);

    // All objects that failed evacuation has been marked in the prev bitmap.
    // Use the bitmap to apply the above closure to all failing objects.
    chunk->apply_to_marked_objects(&rspc);
    _marked_words_in_regions[chunk->heap_region()->hrm_index()] += rspc.marked_words();
    // Need to zap the remainder area of the processed region.
    if (!chunk->empty()) {
      rspc.zap_remainder();
    }

    G1GCPhaseTimes* p = _g1h->phase_times();
    p->record_or_add_thread_work_item(G1GCPhaseTimes::RemoveSelfForwardsInChunks, _worker_id, rspc.marked_words(), G1GCPhaseTimes::RemoveSelfForwardObjectsBytes);
    p->record_or_add_thread_work_item(G1GCPhaseTimes::RemoveSelfForwardsInChunks, _worker_id, rspc.marked_objects(), G1GCPhaseTimes::RemoveSelfForwardObjectsNum);
  }

public:
  RemoveSelfForwardPtrHRChunkClosure(size_t* marked_words_in_regions,
                                     uint worker_id) :
    _g1h(G1CollectedHeap::heap()),
    _marked_words_in_regions(marked_words_in_regions),
    _worker_id(worker_id) {
  }

  void do_heap_region_chunk(G1HeapRegionChunk* chunk) override {
    bool during_concurrent_start = _g1h->collector_state()->in_concurrent_start_gc();
    remove_self_forward_ptr_by_walking_chunk(chunk, during_concurrent_start);
  }
};

G1ParRemoveSelfForwardPtrsTask::G1ParRemoveSelfForwardPtrsTask(G1EvacFailureRegions* evac_failure_regions) :
  WorkerTask("G1 Remove Self-forwarding Pointers"),
  _g1h(G1CollectedHeap::heap()),
  _hrclaimer(_g1h->workers()->active_workers()),
  _evac_failure_regions(evac_failure_regions) { }

void G1ParRemoveSelfForwardPtrsTask::work(uint worker_id) {

  // TODO: maybe only allocate and iterate through evacuation failed regions
  size_t marked_words_in_regions[_evac_failure_regions->max_regions()];
  memset(marked_words_in_regions, 0, sizeof marked_words_in_regions);
  RemoveSelfForwardPtrHRChunkClosure chunk_closure(marked_words_in_regions, worker_id);

  // Iterate through all chunks in regions that failed evacuation during the entire collection.
  _evac_failure_regions->par_iterate_chunks_in_regions(&chunk_closure, worker_id);

  // Sync marked words of regions to HeapRegion.
  for (uint idx = 0; idx < _evac_failure_regions->max_regions(); idx++) {
    if (marked_words_in_regions[idx] > 0) {
      HeapRegion* region = _g1h->region_at(idx);
      region->note_self_forwarding_removal_end_par(marked_words_in_regions[idx] * BytesPerWord);
    }
  }
}

uint G1ParRemoveSelfForwardPtrsTask::num_failed_regions() const {
  return _evac_failure_regions->num_regions_failed_evacuation();
}
