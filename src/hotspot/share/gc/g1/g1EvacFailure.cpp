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

class PhaseTimesStat {
  static constexpr G1GCPhaseTimes::GCParPhases phase_name =
    G1GCPhaseTimes::RemoveSelfForwardsInChunks;

  G1GCPhaseTimes* _phase_times;
  uint _worker_id;
  Ticks _start;
public:
  PhaseTimesStat(G1GCPhaseTimes* phase_times, uint worker_id) :
    _phase_times(phase_times),
    _worker_id(worker_id) {}

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

  void register_objects_size(size_t marked_words) {
    auto marked_bytes = marked_words * BytesPerWord;
    _phase_times->record_or_add_thread_work_item(phase_name,
                                                 _worker_id,
                                                 marked_bytes,
                                                 G1GCPhaseTimes::RemoveSelfForwardObjectsBytes);
  }

  void register_objects_count(size_t num_marked_obj) {
    _phase_times->record_or_add_thread_work_item(phase_name,
                                                  _worker_id,
                                                  num_marked_obj,
                                                  G1GCPhaseTimes::RemoveSelfForwardObjectsNum);
  }
};

// Fill the memory area from start to end with filler objects, and update the BOT
// accordingly. Since we clear and use the prev bitmap for marking objects that
// failed evacuation, there is no work to be done there.
static void zap_dead_objects(HeapRegion* hr, HeapWord* start, HeapWord* end) {
  assert(start <= end, "precondition");
  if (start == end) {
    return;
  }

  size_t gap_size = pointer_delta(end, start);
  if (gap_size >= CollectedHeap::min_fill_size()) {
    CollectedHeap::fill_with_objects(start, gap_size);

    HeapWord* end_first_obj = start + cast_to_oop(start)->size();
    hr->update_bot_for_block(start, end_first_obj);
    // Fill_with_objects() may have created multiple (i.e. two)
    // objects, as the max_fill_size() is half a region.
    // After updating the BOT for the first object, also update the
    // BOT for the second object to make the BOT complete.
    if (end_first_obj != end) {
      hr->update_bot_for_block(end_first_obj, end);
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
}

static void prefetch_obj(HeapWord* obj_addr) {
  Prefetch::write(obj_addr, PrefetchScanIntervalInBytes);
}

// Caches the currently accumulated number of live/marked words found in this heap region.
// Avoids direct (frequent) atomic operations on the HeapRegion's marked words.
class G1ParRemoveSelfForwardPtrsTask::RegionMarkedWordsCache {
  G1CollectedHeap* _g1h;
  const uint _uninitialized_idx;
  uint _region_idx;
  size_t _marked_words;

  void note_self_forwarding_removal_end_par() {
    _g1h->region_at(_region_idx)->note_self_forwarding_removal_end_par(_marked_words * BytesPerWord);
  }

  void flush() {
    if (_region_idx != _uninitialized_idx) {
      note_self_forwarding_removal_end_par();
    }
  }
public:
  RegionMarkedWordsCache(G1CollectedHeap* g1h):
    _g1h(g1h),
    _uninitialized_idx(_g1h->max_regions()),
    _region_idx(_uninitialized_idx),
    _marked_words(0) { }

  ~RegionMarkedWordsCache() {
    flush();
  }

  void add(uint region_idx, size_t marked_words) {
    if (_region_idx == _uninitialized_idx) {
      _region_idx = region_idx;
      _marked_words = marked_words;
    } else if (_region_idx == region_idx) {
      _marked_words += marked_words;
    } else {
      note_self_forwarding_removal_end_par();
      _region_idx = region_idx;
      _marked_words = marked_words;
    }
  }
};

void G1ParRemoveSelfForwardPtrsTask::process_chunk(uint worker_id,
                                                   uint chunk_idx,
                                                   RegionMarkedWordsCache* cache) {
  PhaseTimesStat stat{_g1h->phase_times(), worker_id};

  G1CMBitMap* bitmap = _g1h->concurrent_mark()->mark_bitmap();
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

  if (chunk_start == hr_bottom) {
    // first chunk in this region; zap [bottom, first_marked_addr)
    zap_dead_objects(hr, hr_bottom, first_marked_addr);
  }

  if (first_marked_addr >= chunk_end) {
    stat.register_empty_chunk();
    return;
  }

  stat.register_nonempty_chunk();
  size_t num_marked_objs = 0;
  size_t marked_words = 0;

  HeapWord* obj_addr = first_marked_addr;
  assert(chunk_start <= obj_addr && obj_addr < chunk_end, "inv");
  do {
    assert(bitmap->is_marked(obj_addr), "inv");
    prefetch_obj(obj_addr);
    oop obj = cast_to_oop(obj_addr);
    const size_t obj_size = obj->size();
    HeapWord* const obj_end_addr = obj_addr + obj_size;

    {
      // Process marked obj
      assert(obj->is_forwarded() && obj->forwardee() == obj, "inv");
      if (_during_concurrent_start) {
        _g1h->concurrent_mark()->mark_in_bitmap(worker_id, obj);
      }
      obj->init_mark();
      hr->update_bot_for_block(obj_addr, obj_end_addr);

      // stat
      num_marked_objs++;
      marked_words += obj_size;
    }

    assert(obj_end_addr <= hr_top, "inv");
    // Use hr_top as the limit so that we zap dead ranges up to the next
    // marked obj or hr_top
    auto next_marked_obj_addr = bitmap->get_next_marked_addr(obj_end_addr,
                                                                hr_top);
    zap_dead_objects(hr, obj_end_addr, next_marked_obj_addr);
    obj_addr = next_marked_obj_addr;
  } while (obj_addr < chunk_end);

  assert(marked_words > 0 && num_marked_objs > 0, "inv");

  stat.register_objects_size(marked_words);
  stat.register_objects_count(num_marked_objs);

  cache->add(region_idx, marked_words);
}

G1ParRemoveSelfForwardPtrsTask::G1ParRemoveSelfForwardPtrsTask(G1EvacFailureRegions* evac_failure_regions) :
  WorkerTask("G1 Remove Self-forwarding Pointers"),
  _g1h(G1CollectedHeap::heap()),
  _during_concurrent_start(_g1h->collector_state()->in_concurrent_start_gc()),
  _evac_failure_regions(evac_failure_regions),
  _chunk_bitmap() {}

void G1ParRemoveSelfForwardPtrsTask::work(uint worker_id) {
  const uint total_workers = G1CollectedHeap::heap()->workers()->active_workers();
  const uint total_chunks = _num_chunks_per_region * _num_evac_fail_regions;
  const uint start_chunk_idx = worker_id * total_chunks / total_workers;

  RegionMarkedWordsCache region_marked_words_cache{_g1h};

  for (uint i = 0; i < total_chunks; i++) {
    const uint chunk_idx = (start_chunk_idx + i) % total_chunks;
    if (claim_chunk(chunk_idx)) {
      process_chunk(worker_id, chunk_idx, &region_marked_words_cache);
    }
  }
}
