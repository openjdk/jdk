/*
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
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
#include "gc/g1/g1ConcurrentMarkBitMap.inline.hpp"
#include "gc/g1/g1HeapRegionChunk.hpp"
#include "gc/g1/heapRegion.hpp"

G1HeapRegionChunk::G1HeapRegionChunk(HeapRegion* region, uint chunk_idx, uint chunk_size, const G1CMBitMap* const bitmap) :
  _chunk_size(chunk_size),
  _region(region),
  _chunk_idx(chunk_idx),
  _bitmap(bitmap) {

  HeapWord* top = _region->top();
  HeapWord* bottom = _region->bottom();
  _start = MIN2(top, bottom + _chunk_idx * _chunk_size);
  _limit = MIN2(top, bottom + (_chunk_idx + 1) * _chunk_size);
  _first_obj_in_chunk = _bitmap->get_next_marked_addr(_start, _limit);
  _next_obj_in_region = _bitmap->get_next_marked_addr(_limit, top);
  // There is marked obj in this chunk
  bool marked_obj_in_this_chunk = _start <= _first_obj_in_chunk && _first_obj_in_chunk < _limit;
  _include_first_obj_in_region = marked_obj_in_this_chunk
                                 && _bitmap->get_next_marked_addr(bottom, _limit) >= _start;
  _include_last_obj_in_region = marked_obj_in_this_chunk
                                && _bitmap->get_next_marked_addr(_limit, top) == top;
}

G1HeapRegionChunksClaimer::G1HeapRegionChunksClaimer(uint region_idx, bool region_ready) :
  _chunk_num(G1YoungGCEvacFailureInjector::evacuation_failure_heap_region_chunk_num()),
  _chunk_size(static_cast<uint>(G1HeapRegionSize / _chunk_num)),
  _region_idx(region_idx),
  _chunks(mtGC) {
  _chunks.resize(_chunk_num);
}

bool G1HeapRegionChunksClaimer::claim_chunk(uint chunk_idx) {
  return _chunks.par_set_bit(chunk_idx);
}

G1ScanChunksInHeapRegionClosure::G1ScanChunksInHeapRegionClosure(G1HeapRegionChunksClaimer** chunk_claimers,
                                                                 G1HeapRegionChunkClosure* closure,
                                                                 uint worker_id) :
  _chunk_claimers(chunk_claimers),
  _closure(closure),
  _worker_id(worker_id),
  _bitmap(G1CollectedHeap::heap()->concurrent_mark()->prev_mark_bitmap()) {
}

bool G1ScanChunksInHeapRegionClosure::do_heap_region(HeapRegion* r) {
  G1GCPhaseTimes* phase_times = G1CollectedHeap::heap()->phase_times();
  G1HeapRegionChunksClaimer* claimer = _chunk_claimers[r->hrm_index()];

  uint total_workers = G1CollectedHeap::heap()->workers()->active_workers();
  const uint start_pos = _worker_id * claimer->chunk_num() / total_workers;
  uint chunk_idx = start_pos;

  while (true) {
    if (claimer->claim_chunk(chunk_idx)) {
      Ticks start2 = Ticks::now();
      G1HeapRegionChunk chunk(r, chunk_idx, claimer->chunk_size(), _bitmap);
      phase_times->record_or_add_time_secs(G1GCPhaseTimes::PrepareChunks, _worker_id, (Ticks::now() - start2).seconds());

      if (chunk.empty()) {
        phase_times->record_or_add_thread_work_item(G1GCPhaseTimes::RemoveSelfForwardsInChunks, _worker_id, 1, G1GCPhaseTimes::RemoveSelfForwardEmptyChunksNum);
        continue;
      }
      phase_times->record_or_add_thread_work_item(G1GCPhaseTimes::RemoveSelfForwardsInChunks, _worker_id, 1, G1GCPhaseTimes::RemoveSelfForwardChunksNum);
      Ticks start = Ticks::now();
      _closure->do_heap_region_chunk(&chunk);
      phase_times->record_or_add_time_secs(G1GCPhaseTimes::RemoveSelfForwardsInChunks, _worker_id, (Ticks::now() - start).seconds());
    }

    if (++chunk_idx == claimer->chunk_num()) {
      chunk_idx = 0;
    }
    if (chunk_idx == start_pos) break;
  }
  return false;
}