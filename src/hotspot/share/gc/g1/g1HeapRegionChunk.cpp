/*
 * Copyright (c) 2022, Huawei Technologies Co. Ltd. All rights reserved.
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
}

bool G1ScanChunksInHeapRegions::claim_chunk(uint chunk_id) {
  return _chunks.par_set_bit(chunk_id);
}

void G1ScanChunksInHeapRegions::process_chunk(G1HeapRegionChunkClosure* chunk_closure, uint chunk_id, uint worker_id) {
  G1CollectedHeap* glh = G1CollectedHeap::heap();
  G1GCPhaseTimes* p = glh->phase_times();

  // Prepare and analyze assigned chunk.
  Ticks chunk_prepare_start = Ticks::now();
  uint region_idx = _evac_failure_regions[chunk_id / _chunks_per_region];
  G1HeapRegionChunk chunk(glh->region_at(region_idx), chunk_id % _chunks_per_region, _chunk_size, _bitmap);
  p->record_or_add_time_secs(G1GCPhaseTimes::PrepareChunks, worker_id, (Ticks::now() - chunk_prepare_start).seconds());

  if (chunk.empty()) {
    p->record_or_add_thread_work_item(G1GCPhaseTimes::RemoveSelfForwardsInChunks, worker_id, 1, G1GCPhaseTimes::RemoveSelfForwardEmptyChunksNum);
    return;
  }
  p->record_or_add_thread_work_item(G1GCPhaseTimes::RemoveSelfForwardsInChunks, worker_id, 1, G1GCPhaseTimes::RemoveSelfForwardChunksNum);

  // Process the chunk.
  Ticks start = Ticks::now();
  chunk_closure->do_heap_region_chunk(&chunk);
  p->record_or_add_time_secs(G1GCPhaseTimes::RemoveSelfForwardsInChunks, worker_id, (Ticks::now() - start).seconds());
}

G1ScanChunksInHeapRegions::G1ScanChunksInHeapRegions() :
  _bitmap(G1CollectedHeap::heap()->concurrent_mark()->prev_mark_bitmap()),
  _chunks(mtGC) { }

void G1ScanChunksInHeapRegions::initialize(const uint* evac_failure_regions, uint evac_failure_regions_length, uint num_workers) {
  _evac_failure_regions = evac_failure_regions;

  _chunks_per_region = next_power_of_2(num_workers * G1RemoveSelfForwardPtrsThreadLoadFactor / evac_failure_regions_length);
  _chunk_size = static_cast<uint>(G1HeapRegionSize / _chunks_per_region);
  log_debug(gc, ergo)("Initializing removing self forwards with %u chunks per region given %u workers", _chunks_per_region, num_workers);

  _chunks.resize(_chunks_per_region * evac_failure_regions_length);
}

void G1ScanChunksInHeapRegions::par_iterate_chunks_in_regions(G1HeapRegionChunkClosure* chunk_closure, uint worker_id) {
  const uint total_workers = G1CollectedHeap::heap()->workers()->active_workers();
  const uint start_chunk_id = worker_id * static_cast<uint>(_chunks.size()) / total_workers;
  for (uint i = 0; i < _chunks.size(); i++) {
    const uint chunk_id = (start_chunk_id + i) % _chunks.size();
    if (claim_chunk(chunk_id)) {
      process_chunk(chunk_closure, chunk_id, worker_id);
    }
  }
}
