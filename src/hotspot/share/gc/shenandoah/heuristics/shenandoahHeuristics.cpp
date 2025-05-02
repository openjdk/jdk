/*
 * Copyright (c) 2018, 2020, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/quickSort.hpp"

// sort by decreasing garbage (so most garbage comes first)
int ShenandoahHeuristics::compare_by_garbage(RegionData a, RegionData b) {
  if (a.get_garbage() > b.get_garbage()) {
    return -1;
  } else if (a.get_garbage() < b.get_garbage()) {
    return 1;
  } else {
    return 0;
  }
}

ShenandoahHeuristics::ShenandoahHeuristics(ShenandoahSpaceInfo* space_info) :
  _start_gc_is_pending(false),
  _declined_trigger_count(0),
  _most_recent_declined_trigger_count(0),
  _space_info(space_info),
  _region_data(nullptr),
  _guaranteed_gc_interval(0),
  _cycle_start(os::elapsedTime()),
  _last_cycle_end(0),
  _gc_times_learned(0),
  _gc_time_penalties(0),
  _gc_cycle_time_history(new TruncatedSeq(Moving_Average_Samples, ShenandoahAdaptiveDecayFactor)),
  _metaspace_oom()
{
  size_t num_regions = ShenandoahHeap::heap()->num_regions();
  assert(num_regions > 0, "Sanity");

  _region_data = NEW_C_HEAP_ARRAY(RegionData, num_regions, mtGC);
  for (size_t i = 0; i < num_regions; i++) {
    _region_data[i].clear();
  }
}

ShenandoahHeuristics::~ShenandoahHeuristics() {
  FREE_C_HEAP_ARRAY(RegionGarbage, _region_data);
}

void ShenandoahHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set) {
  assert(collection_set->is_empty(), "Must be empty");

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Check all pinned regions have updated status before choosing the collection set.
  heap->assert_pinned_region_status();

  // Step 1. Build up the region candidates we care about, rejecting losers and accepting winners right away.

  size_t num_regions = heap->num_regions();

  RegionData* candidates = _region_data;

  size_t cand_idx = 0;

  size_t total_garbage = 0;

  size_t immediate_garbage = 0;
  size_t immediate_regions = 0;

  size_t free = 0;
  size_t free_regions = 0;

  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);

    size_t garbage = region->garbage();
    total_garbage += garbage;

    if (region->is_empty()) {
      free_regions++;
      free += ShenandoahHeapRegion::region_size_bytes();
    } else if (region->is_regular()) {
      if (!region->has_live()) {
        // We can recycle it right away and put it in the free set.
        immediate_regions++;
        immediate_garbage += garbage;
        region->make_trash_immediate();
      } else {
        // This is our candidate for later consideration.
        candidates[cand_idx].set_region_and_garbage(region, garbage);
        cand_idx++;
      }
    } else if (region->is_humongous_start()) {
      // Reclaim humongous regions here, and count them as the immediate garbage
#ifdef ASSERT
      bool reg_live = region->has_live();
      bool bm_live = heap->gc_generation()->complete_marking_context()->is_marked(cast_to_oop(region->bottom()));
      assert(reg_live == bm_live,
             "Humongous liveness and marks should agree. Region live: %s; Bitmap live: %s; Region Live Words: %zu",
             BOOL_TO_STR(reg_live), BOOL_TO_STR(bm_live), region->get_live_data_words());
#endif
      if (!region->has_live()) {
        heap->trash_humongous_region_at(region);

        // Count only the start. Continuations would be counted on "trash" path
        immediate_regions++;
        immediate_garbage += garbage;
      }
    } else if (region->is_trash()) {
      // Count in just trashed collection set, during coalesced CM-with-UR
      immediate_regions++;
      immediate_garbage += garbage;
    }
  }

  // Step 2. Look back at garbage statistics, and decide if we want to collect anything,
  // given the amount of immediately reclaimable garbage. If we do, figure out the collection set.

  assert (immediate_garbage <= total_garbage,
          "Cannot have more immediate garbage than total garbage: %zu%s vs %zu%s",
          byte_size_in_proper_unit(immediate_garbage), proper_unit_for_byte_size(immediate_garbage),
          byte_size_in_proper_unit(total_garbage),     proper_unit_for_byte_size(total_garbage));

  size_t immediate_percent = (total_garbage == 0) ? 0 : (immediate_garbage * 100 / total_garbage);

  if (immediate_percent <= ShenandoahImmediateThreshold) {
    choose_collection_set_from_regiondata(collection_set, candidates, cand_idx, immediate_garbage + free);
  }

  size_t cset_percent = (total_garbage == 0) ? 0 : (collection_set->garbage() * 100 / total_garbage);
  size_t collectable_garbage = collection_set->garbage() + immediate_garbage;
  size_t collectable_garbage_percent = (total_garbage == 0) ? 0 : (collectable_garbage * 100 / total_garbage);

  log_info(gc, ergo)("Collectable Garbage: %zu%s (%zu%%), "
                     "Immediate: %zu%s (%zu%%), %zu regions, "
                     "CSet: %zu%s (%zu%%), %zu regions",

                     byte_size_in_proper_unit(collectable_garbage),
                     proper_unit_for_byte_size(collectable_garbage),
                     collectable_garbage_percent,

                     byte_size_in_proper_unit(immediate_garbage),
                     proper_unit_for_byte_size(immediate_garbage),
                     immediate_percent,
                     immediate_regions,

                     byte_size_in_proper_unit(collection_set->garbage()),
                     proper_unit_for_byte_size(collection_set->garbage()),
                     cset_percent,
                     collection_set->count());
}

void ShenandoahHeuristics::record_cycle_start() {
  _cycle_start = os::elapsedTime();
}

void ShenandoahHeuristics::record_cycle_end() {
  _last_cycle_end = os::elapsedTime();
}

bool ShenandoahHeuristics::should_start_gc() {
  if (_start_gc_is_pending) {
    log_trigger("GC start is already pending");
    return true;
  }
  // Perform GC to cleanup metaspace
  if (has_metaspace_oom()) {
    // Some of vmTestbase/metaspace tests depend on following line to count GC cycles
    log_trigger("%s", GCCause::to_string(GCCause::_metadata_GC_threshold));
    accept_trigger();
    return true;
  }

  if (_guaranteed_gc_interval > 0) {
    double last_time_ms = (os::elapsedTime() - _last_cycle_end) * 1000;
    if (last_time_ms > _guaranteed_gc_interval) {
      log_trigger("Time since last GC (%.0f ms) is larger than guaranteed interval (%zu ms)",
                   last_time_ms, _guaranteed_gc_interval);
      accept_trigger();
      return true;
    }
  }
  decline_trigger();
  return false;
}

bool ShenandoahHeuristics::should_degenerate_cycle() {
  return ShenandoahHeap::heap()->shenandoah_policy()->consecutive_degenerated_gc_count() <= ShenandoahFullGCThreshold;
}

void ShenandoahHeuristics::adjust_penalty(intx step) {
  assert(0 <= _gc_time_penalties && _gc_time_penalties <= 100,
         "In range before adjustment: %zd", _gc_time_penalties);

  if ((_most_recent_declined_trigger_count <= Penalty_Free_Declinations) && (step > 0)) {
    // Don't penalize if heuristics are not responsible for a negative outcome.  Allow Penalty_Free_Declinations following
    // previous GC for self calibration without penalty.
    step = 0;
  }

  intx new_val = _gc_time_penalties + step;
  if (new_val < 0) {
    new_val = 0;
  }
  if (new_val > 100) {
    new_val = 100;
  }
  _gc_time_penalties = new_val;

  assert(0 <= _gc_time_penalties && _gc_time_penalties <= 100,
         "In range after adjustment: %zd", _gc_time_penalties);
}

void ShenandoahHeuristics::log_trigger(const char* fmt, ...) {
  LogTarget(Info, gc) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    ls.print_raw("Trigger", 7);
    if (ShenandoahHeap::heap()->mode()->is_generational()) {
      ls.print(" (%s)", _space_info->name());
    }
    ls.print_raw(": ", 2);
    va_list va;
    va_start(va, fmt);
    ls.vprint(fmt, va);
    va_end(va);
    ls.cr();
  }
}

void ShenandoahHeuristics::record_success_concurrent() {
  _gc_cycle_time_history->add(elapsed_cycle_time());
  _gc_times_learned++;

  adjust_penalty(Concurrent_Adjust);
}

void ShenandoahHeuristics::record_success_degenerated() {
  adjust_penalty(Degenerated_Penalty);
}

void ShenandoahHeuristics::record_success_full() {
  adjust_penalty(Full_Penalty);
}

void ShenandoahHeuristics::record_allocation_failure_gc() {
  // Do nothing.
}

void ShenandoahHeuristics::record_requested_gc() {
  // Assume users call System.gc() when external state changes significantly,
  // which forces us to re-learn the GC timings and allocation rates.
  _gc_times_learned = 0;
}

bool ShenandoahHeuristics::can_unload_classes() {
  return ClassUnloading;
}

bool ShenandoahHeuristics::should_unload_classes() {
  if (!can_unload_classes()) return false;
  if (has_metaspace_oom()) return true;
  return ClassUnloadingWithConcurrentMark;
}

void ShenandoahHeuristics::initialize() {
  // Nothing to do by default.
}

double ShenandoahHeuristics::elapsed_cycle_time() const {
  return os::elapsedTime() - _cycle_start;
}
