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
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/prefetch.hpp"
#include "utilities/bitMap.inline.hpp"


class PhaseTimesStat {
  static constexpr G1GCPhaseTimes::GCParPhases phase_name =
    G1GCPhaseTimes::RemoveSelfForwards;

  G1GCPhaseTimes* _phase_times;
  uint _worker_id;
  Ticks _start;

public:
  PhaseTimesStat(G1GCPhaseTimes* phase_times, uint worker_id) :
    _phase_times(phase_times),
    _worker_id(worker_id),
    _start(Ticks::now()) { }

  ~PhaseTimesStat() {
    _phase_times->record_or_add_time_secs(phase_name,
                                          _worker_id,
                                          (Ticks::now() - _start).seconds());
  }

  void register_empty_chunk() {
    _phase_times->record_or_add_thread_work_item(phase_name,
                                                 _worker_id,
                                                 1,
                                                 G1GCPhaseTimes::RemoveSelfForwardEmptyChunksNum);
  }

  void register_nonempty_chunk() {
    _phase_times->record_or_add_thread_work_item(phase_name,
                                                 _worker_id,
                                                 1,
                                                 G1GCPhaseTimes::RemoveSelfForwardChunksNum);
  }

  void register_objects_count_and_size(size_t num_marked_obj, size_t marked_words) {
    _phase_times->record_or_add_thread_work_item(phase_name,
                                                 _worker_id,
                                                 num_marked_obj,
                                                 G1GCPhaseTimes::RemoveSelfForwardObjectsNum);

    size_t marked_bytes = marked_words * HeapWordSize;
    _phase_times->record_or_add_thread_work_item(phase_name,
                                                 _worker_id,
                                                 marked_bytes,
                                                 G1GCPhaseTimes::RemoveSelfForwardObjectsBytes);
  }
};

// Fill the memory area from start to end with filler objects, and update the BOT
// accordingly. Since we clear and use the bitmap for marking objects that failed
// evacuation, there is no other work to be done there.
static size_t zap_dead_objects(HeapRegion* hr, HeapWord* start, HeapWord* end) {
  assert(start <= end, "precondition");
  if (start == end) {
    return 0;
  }

  hr->fill_range_with_dead_objects(start, end);
  return pointer_delta(end, start);
}

static void update_garbage_words_in_hr(HeapRegion* hr, size_t garbage_words) {
  if (garbage_words != 0) {
    hr->note_self_forward_chunk_done(garbage_words * HeapWordSize);
  }
}

static void prefetch_obj(HeapWord* obj_addr) {
  Prefetch::write(obj_addr, PrefetchScanIntervalInBytes);
}

void G1RemoveSelfForwardsTask::process_chunk(uint worker_id,
                                             uint chunk_idx) {
  PhaseTimesStat stat(_g1h->phase_times(), worker_id);

  G1CMBitMap* bitmap = _cm->mark_bitmap();
  const uint region_idx = _evac_failure_regions->get_region_idx(chunk_idx / _num_chunks_per_region);
  HeapRegion* hr = _g1h->region_at(region_idx);

  HeapWord* hr_bottom = hr->bottom();
  HeapWord* hr_top = hr->top();
  HeapWord* chunk_start = hr_bottom + (chunk_idx % _num_chunks_per_region) * _chunk_size;

  assert(chunk_start < hr->end(), "inv");
  if (chunk_start >= hr_top) {
    return;
  }

  HeapWord* chunk_end = MIN2(chunk_start + _chunk_size, hr_top);
  HeapWord* first_marked_addr = bitmap->get_next_marked_addr(chunk_start, hr_top);

  size_t garbage_words = 0;

  if (chunk_start == hr_bottom) {
    // This is the bottom-most chunk in this region; zap [bottom, first_marked_addr).
    garbage_words += zap_dead_objects(hr, hr_bottom, first_marked_addr);
  }

  if (first_marked_addr >= chunk_end) {
    stat.register_empty_chunk();
    update_garbage_words_in_hr(hr, garbage_words);
    return;
  }

  stat.register_nonempty_chunk();

  size_t num_marked_objs = 0;
  size_t marked_words = 0;

  HeapWord* obj_addr = first_marked_addr;
  assert(chunk_start <= obj_addr && obj_addr < chunk_end,
         "object " PTR_FORMAT " must be within chunk [" PTR_FORMAT ", " PTR_FORMAT "[",
         p2i(obj_addr), p2i(chunk_start), p2i(chunk_end));
  do {
    assert(bitmap->is_marked(obj_addr), "inv");
    prefetch_obj(obj_addr);

    oop obj = cast_to_oop(obj_addr);
    const size_t obj_size = obj->size();
    HeapWord* const obj_end_addr = obj_addr + obj_size;

    {
      // Process marked object.
      assert(obj->is_forwarded() && obj->forwardee() == obj, "must be self-forwarded");
      obj->init_mark();
      hr->update_bot_for_block(obj_addr, obj_end_addr);

      // Statistics
      num_marked_objs++;
      marked_words += obj_size;
    }

    assert(obj_end_addr <= hr_top, "inv");
    // Use hr_top as the limit so that we zap dead ranges up to the next
    // marked obj or hr_top.
    HeapWord* next_marked_obj_addr = bitmap->get_next_marked_addr(obj_end_addr, hr_top);
    garbage_words += zap_dead_objects(hr, obj_end_addr, next_marked_obj_addr);
    obj_addr = next_marked_obj_addr;
  } while (obj_addr < chunk_end);

  assert(marked_words > 0 && num_marked_objs > 0, "inv");

  stat.register_objects_count_and_size(num_marked_objs, marked_words);

  update_garbage_words_in_hr(hr, garbage_words);
}

G1RemoveSelfForwardsTask::G1RemoveSelfForwardsTask(G1EvacFailureRegions* evac_failure_regions) :
  WorkerTask("G1 Remove Self-forwarding Pointers"),
  _g1h(G1CollectedHeap::heap()),
  _cm(_g1h->concurrent_mark()),
  _evac_failure_regions(evac_failure_regions),
  _chunk_bitmap(mtGC) {

  _num_evac_fail_regions = _evac_failure_regions->num_regions_failed_evacuation();
  _num_chunks_per_region = G1CollectedHeap::get_chunks_per_region();

  _chunk_size = static_cast<uint>(HeapRegion::GrainWords / _num_chunks_per_region);

  log_debug(gc, ergo)("Initializing removing self forwards with %u chunks per region",
                      _num_chunks_per_region);

  _chunk_bitmap.resize(_num_chunks_per_region * _num_evac_fail_regions);
}

void G1RemoveSelfForwardsTask::work(uint worker_id) {
  const uint total_workers = G1CollectedHeap::heap()->workers()->active_workers();
  const uint total_chunks = _num_chunks_per_region * _num_evac_fail_regions;
  const uint start_chunk_idx = worker_id * total_chunks / total_workers;

  for (uint i = 0; i < total_chunks; i++) {
    const uint chunk_idx = (start_chunk_idx + i) % total_chunks;
    if (claim_chunk(chunk_idx)) {
      process_chunk(worker_id, chunk_idx);
    }
  }
}
