/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGC.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "utilities/events.hpp"

class ShenandoahConcurrentCoalesceAndFillTask : public AbstractGangTask {
private:
  uint _nworkers;
  ShenandoahHeapRegion** _coalesce_and_fill_region_array;
  uint _coalesce_and_fill_region_count;

public:
  ShenandoahConcurrentCoalesceAndFillTask(uint nworkers,
                                          ShenandoahHeapRegion** coalesce_and_fill_region_array, uint region_count) :
    AbstractGangTask("Shenandoah Concurrent Coalesce and Fill"),
    _nworkers(nworkers),
    _coalesce_and_fill_region_array(coalesce_and_fill_region_array),
    _coalesce_and_fill_region_count(region_count) {
  }

  void work(uint worker_id) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();

    for (uint region_idx = worker_id; region_idx < _coalesce_and_fill_region_count; region_idx += _nworkers) {
      ShenandoahHeapRegion* r = _coalesce_and_fill_region_array[region_idx];
      if (!r->is_humongous())
        r->oop_fill_and_coalesce();
      else {
        // there's only one object in this region and it's not garbage, so no need to coalesce or fill
      }
    }
  }
};


ShenandoahOldGC::ShenandoahOldGC(ShenandoahGeneration* generation, ShenandoahSharedFlag& allow_preemption) :
    ShenandoahConcurrentGC(generation, false), _allow_preemption(allow_preemption) {
  _coalesce_and_fill_region_array = NEW_C_HEAP_ARRAY(ShenandoahHeapRegion*, ShenandoahHeap::heap()->num_regions(), mtGC);
}

void ShenandoahOldGC::entry_old_evacuations() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahOldHeuristics* old_heuristics = heap->old_heuristics();
  entry_coalesce_and_fill();
  old_heuristics->start_old_evacuations();
}


// Final mark for old-gen is different than for young or old, so we
// override the implementation.
void ShenandoahOldGC::op_final_mark() {

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(!heap->has_forwarded_objects(), "No forwarded objects on this path");

  if (ShenandoahVerify) {
    heap->verifier()->verify_roots_no_forwarded();
  }

  if (!heap->cancelled_gc()) {
    assert(_mark.generation()->generation_mode() == OLD, "Generation of Old-Gen GC should be OLD");
    _mark.finish_mark();
    assert(!heap->cancelled_gc(), "STW mark cannot OOM");

    // Believe notifying JVMTI that the tagmap table will need cleaning is not relevant following old-gen mark
    // so commenting out for now:
    //   JvmtiTagMap::set_needs_cleaning();

    {
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::choose_cset);
      ShenandoahHeapLocker locker(heap->lock());
      // Old-gen choose_collection_set() does not directly manipulate heap->collection_set() so no need to clear it.
      _generation->heuristics()->choose_collection_set(nullptr, nullptr);
    }

    // Believe verification following old-gen concurrent mark needs to be different than verification following
    // young-gen concurrent mark, so am commenting this out for now:
    //   if (ShenandoahVerify) {
    //     heap->verifier()->verify_after_concmark();
    //   }

    if (VerifyAfterGC) {
      Universe::verify();
    }
  }
}

bool ShenandoahOldGC::collect(GCCause::Cause cause) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Continue concurrent mark, do not reset regions, do not mark roots, do not collect $200.
  _allow_preemption.set();
  entry_mark();
  _allow_preemption.unset();
  if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_mark)) return false;

  // Complete marking under STW
  vmop_entry_final_mark();

  entry_old_evacuations();

  // We aren't dealing with old generation evacuation yet. Our heuristic
  // should not have built a cset in final mark.
  assert(!heap->is_evacuation_in_progress(), "Old gen evacuations are not supported");

  // Concurrent stack processing
  if (heap->is_evacuation_in_progress()) {
    entry_thread_roots();
  }

  // Process weak roots that might still point to regions that would be broken by cleanup
  if (heap->is_concurrent_weak_root_in_progress()) {
    entry_weak_refs();
    entry_weak_roots();
  }

  // Final mark might have reclaimed some immediate garbage, kick cleanup to reclaim
  // the space. This would be the last action if there is nothing to evacuate.
  entry_cleanup_early();

  {
    ShenandoahHeapLocker locker(heap->lock());
    heap->free_set()->log_status();
  }

  // Perform concurrent class unloading
  if (heap->unload_classes() &&
      heap->is_concurrent_weak_root_in_progress()) {
    entry_class_unloading();
  }

  // Processing strong roots
  // This may be skipped if there is nothing to update/evacuate.
  // If so, strong_root_in_progress would be unset.
  if (heap->is_concurrent_strong_root_in_progress()) {
    entry_strong_roots();
  }

  entry_rendezvous_roots();
  return true;
}

void ShenandoahOldGC::entry_coalesce_and_fill_message(char *buf, size_t len) const {
  // ShenandoahHeap* const heap = ShenandoahHeap::heap();
  jio_snprintf(buf, len, "Coalescing and filling (%s)", _generation->name());
}

void ShenandoahOldGC::op_coalesce_and_fill() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  WorkGang* workers = heap->workers();
  uint nworkers = workers->active_workers();

  assert(_generation->generation_mode() == OLD, "Only old-GC does coalesce and fill");

  ShenandoahOldHeuristics* old_heuristics = heap->old_heuristics();
  uint coalesce_and_fill_regions_count = old_heuristics->old_coalesce_and_fill_candidates();
  assert(coalesce_and_fill_regions_count <= heap->num_regions(), "Sanity");
  old_heuristics->get_coalesce_and_fill_candidates(_coalesce_and_fill_region_array);
  ShenandoahConcurrentCoalesceAndFillTask task(nworkers, _coalesce_and_fill_region_array, coalesce_and_fill_regions_count);


  // TODO:  We need to implement preemption of coalesce and fill.  If young-gen wants to run while we're working on this,
  // we should preempt this code and then resume it after young-gen has finished.  This requires that we "remember" the state
  // of each worker thread so it can be resumed where it left off.  Note that some worker threads may have processed more regions
  // than others at the time of preemption.

  workers->run_task(&task);
}

void ShenandoahOldGC::entry_coalesce_and_fill() {
  char msg[1024];
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  entry_coalesce_and_fill_message(msg, sizeof(msg));
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::coalesce_and_fill);

  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  EventMark em("%s", msg);
  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_marking(),
                              "concurrent coalesce and fill");

  op_coalesce_and_fill();
}
