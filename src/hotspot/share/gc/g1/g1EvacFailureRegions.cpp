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
#include "gc/g1/g1EvacFailureRegions.inline.hpp"
#include "gc/g1/g1HeapRegionChunk.hpp"
#include "gc/g1/heapRegion.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

G1EvacFailureRegions::G1EvacFailureRegions() :
  _regions_failed_evacuation(mtGC),
  _evac_failure_regions(nullptr),
  _chunk_claimers(nullptr),
  _evac_failure_regions_cur_length(0),
  _max_regions(0) {
  _live_stats = NEW_C_HEAP_ARRAY(G1RegionMarkStats, G1CollectedHeap::heap()->max_regions(), mtGC);
  for (uint j = 0; j < G1CollectedHeap::heap()->max_regions(); j++) {
    _live_stats[j].clear();
  }
}

G1EvacFailureRegions::~G1EvacFailureRegions() {
  assert(_evac_failure_regions == nullptr, "not cleaned up");
  assert(_chunk_claimers == nullptr, "not cleaned up");
  FREE_C_HEAP_ARRAY(uint, _live_stats);
  _live_stats = nullptr;
}

void G1EvacFailureRegions::pre_collection(uint max_regions) {
  Atomic::store(&_evac_failure_regions_cur_length, 0u);
  _max_regions = max_regions;
  _regions_failed_evacuation.resize(_max_regions);
  _evac_failure_regions = NEW_C_HEAP_ARRAY(uint, _max_regions, mtGC);
  _chunk_claimers = NEW_C_HEAP_ARRAY(G1HeapRegionChunksClaimer*, _max_regions, mtGC);
}

void G1EvacFailureRegions::post_collection() {
  _regions_failed_evacuation.resize(0);

  for (uint i = 0; i < _evac_failure_regions_cur_length; i++) {
    FREE_C_HEAP_OBJ(_chunk_claimers[_evac_failure_regions[i]]);
  }
  FREE_C_HEAP_ARRAY(uint, _chunk_claimers);
  _chunk_claimers = nullptr;

  FREE_C_HEAP_ARRAY(uint, _evac_failure_regions);
  _evac_failure_regions = nullptr;
  _max_regions = 0; // To have any record() attempt fail in the future.
}

void G1EvacFailureRegions::par_iterate(HeapRegionClosure* closure,
                                       HeapRegionClaimer* _hrclaimer,
                                       uint worker_id) const {
  G1CollectedHeap::heap()->par_iterate_regions_array(closure,
                                                     _hrclaimer,
                                                     _evac_failure_regions,
                                                     Atomic::load(&_evac_failure_regions_cur_length),
                                                     worker_id);
}

void G1EvacFailureRegions::par_iterate_chunks_in_regions(HeapRegionClosure* prepare_region_closure,
                                                         G1HeapRegionChunkClosure* chunk_closure,
                                                         uint worker_id) const {
  G1ScanChunksInHeapRegionClosure closure(_chunk_claimers, prepare_region_closure, chunk_closure, worker_id);

  G1CollectedHeap::heap()->par_iterate_regions_array(&closure,
                                                     nullptr, // pass null, so every worker thread go through every region.
                                                     _evac_failure_regions,
                                                     Atomic::load(&_evac_failure_regions_cur_length),
                                                     worker_id);
}

bool G1EvacFailureRegions::contains(uint region_idx) const {
  assert(region_idx < _max_regions, "must be");
  return _regions_failed_evacuation.par_at(region_idx, memory_order_relaxed);
}
