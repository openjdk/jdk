/*
 * Copyright (c) 2021, 2022 Huawei Technologies Co. Ltd. All rights reserved.
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
  _chunks_in_regions(nullptr),
  _evac_failure_regions_cur_length(0) {
}

G1EvacFailureRegions::~G1EvacFailureRegions() {
  assert(_evac_failure_regions == nullptr, "not cleaned up");
  assert(_chunks_in_regions == nullptr, "not cleaned up");
}

void G1EvacFailureRegions::pre_collection(uint max_regions) {
  Atomic::store(&_evac_failure_regions_cur_length, 0u);
  _regions_failed_evacuation.resize(max_regions);
  _evac_failure_regions = NEW_C_HEAP_ARRAY(uint, max_regions, mtGC);
  _chunks_in_regions = new (NEW_C_HEAP_OBJ(G1ScanChunksInHeapRegions, mtGC)) G1ScanChunksInHeapRegions();
}

void G1EvacFailureRegions::post_collection() {
  _regions_failed_evacuation.resize(0);

  FREE_C_HEAP_OBJ(_chunks_in_regions);
  _chunks_in_regions = nullptr;

  FREE_C_HEAP_ARRAY(uint, _evac_failure_regions);
  _evac_failure_regions = nullptr;
}

bool G1EvacFailureRegions::contains(uint region_idx) const {
  return _regions_failed_evacuation.par_at(region_idx, memory_order_relaxed);
}

void G1EvacFailureRegions::par_iterate(HeapRegionClosure* closure,
                                       HeapRegionClaimer* hrclaimer,
                                       uint worker_id) const {
  G1CollectedHeap::heap()->par_iterate_regions_array(closure,
                                                     hrclaimer,
                                                     _evac_failure_regions,
                                                     Atomic::load(&_evac_failure_regions_cur_length),
                                                     worker_id);
}

void G1EvacFailureRegions::initialize_chunks(uint num_workers, const char* task_name) {
  _chunks_in_regions->initialize(_evac_failure_regions,
                                 Atomic::load(&_evac_failure_regions_cur_length),
                                 num_workers, task_name);
}

void G1EvacFailureRegions::par_iterate_chunks_in_regions(G1HeapRegionChunkClosure* chunk_closure,
                                                         uint worker_id) const {
  _chunks_in_regions->par_iterate_chunks_in_regions(chunk_closure, worker_id);
}

class PrepareEvacFailureRegionTask : public WorkerTask {
  G1EvacFailureRegions* _evac_failure_regions;
  uint _num_workers;
  HeapRegionClaimer _claimer;

  class PrepareEvacFailureRegionClosure : public HeapRegionClosure {
    const G1EvacFailureRegions* _evac_failure_regions;
    uint _worker_id;

    void prepare_region(uint region_idx, uint worker_id) {
      G1CollectedHeap* g1h = G1CollectedHeap::heap();
      G1GCPhaseTimes* p = g1h->phase_times();
      HeapRegion* hr = g1h->region_at(region_idx);
      assert(!hr->is_pinned(), "Unexpected pinned region at index %u", hr->hrm_index());
      assert(hr->in_collection_set(), "bad CS");
      assert(_evac_failure_regions->contains(hr->hrm_index()), "precondition");

      Ticks start = Ticks::now();

      hr->clear_index_in_opt_cset();

      bool during_concurrent_start = g1h->collector_state()->in_concurrent_start_gc();
      bool during_concurrent_mark = g1h->collector_state()->mark_or_rebuild_in_progress();

      hr->note_self_forwarding_removal_start(during_concurrent_start,
                                             during_concurrent_mark);

      p->record_or_add_thread_work_item(G1GCPhaseTimes::RestoreRetainedRegions,
                                        worker_id,
                                        1,
                                        G1GCPhaseTimes::RestoreRetainedRegionsNum);

      hr->rem_set()->clean_code_roots(hr);
      hr->rem_set()->clear_locked(true);

      p->record_or_add_time_secs(G1GCPhaseTimes::PrepareRetainedRegions, worker_id, (Ticks::now() - start).seconds());
    }

  public:
    PrepareEvacFailureRegionClosure(G1EvacFailureRegions* evac_failure_regions, uint worker_id) :
      _evac_failure_regions(evac_failure_regions),
      _worker_id(worker_id) { }

    bool do_heap_region(HeapRegion* r) override {
      assert(_evac_failure_regions->contains(r->hrm_index()), "precondition");
      prepare_region(r->hrm_index(), _worker_id);
      return false;
    }
  };

public:
  PrepareEvacFailureRegionTask(G1EvacFailureRegions* evac_failure_regions, uint num_workers) :
    WorkerTask("Prepare Evacuation Failure Region Task"),
    _evac_failure_regions(evac_failure_regions),
    _num_workers(num_workers),
    _claimer(_num_workers) {
  }

  void work(uint worker_id) override {
    PrepareEvacFailureRegionClosure closure(_evac_failure_regions, worker_id);
    _evac_failure_regions->par_iterate(&closure, &_claimer, worker_id);
  }
};

void G1EvacFailureRegions::prepare_regions() {
  WorkerThreads* workers = G1CollectedHeap::heap()->workers();
  uint num_workers = clamp(_evac_failure_regions_cur_length, 1u, workers->active_workers());
  PrepareEvacFailureRegionTask task(this, num_workers);
  workers->run_task(&task, num_workers);
}
