/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahTraversalAggressiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "utilities/quickSort.hpp"

ShenandoahTraversalAggressiveHeuristics::ShenandoahTraversalAggressiveHeuristics() : ShenandoahHeuristics(),
  _last_cset_select(0) {
  // Do not shortcut evacuation
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahImmediateThreshold, 100);

  // Aggressive runs with max speed for allocation, to capture races against mutator
  SHENANDOAH_ERGO_DISABLE_FLAG(ShenandoahPacing);

  // Aggressive evacuates everything, so it needs as much evac space as it can get
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahEvacReserveOverflow);

  // If class unloading is globally enabled, aggressive does unloading even with
  // concurrent cycles.
  if (ClassUnloading) {
    SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahUnloadClassesFrequency, 1);
  }

}

bool ShenandoahTraversalAggressiveHeuristics::is_experimental() {
  return false;
}

bool ShenandoahTraversalAggressiveHeuristics::is_diagnostic() {
  return true;
}

const char* ShenandoahTraversalAggressiveHeuristics::name() {
  return "traversal-aggressive";
}

void ShenandoahTraversalAggressiveHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  ShenandoahTraversalGC* traversal_gc = heap->traversal_gc();

  ShenandoahHeapRegionSet* traversal_set = traversal_gc->traversal_set();
  traversal_set->clear();

  RegionData *data = get_region_data_cache(heap->num_regions());
  size_t cnt = 0;

  // About to choose the collection set, make sure we have pinned regions in correct state
  heap->assert_pinned_region_status();

  // Step 0. Prepare all regions
  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->used() > 0) {
      if (r->is_regular()) {
        data[cnt]._region = r;
        data[cnt]._garbage = r->garbage();
        data[cnt]._seqnum_last_alloc = r->seqnum_last_alloc_mutator();
        cnt++;
      }
      traversal_set->add_region(r);
    }
  }

  for (size_t i = 0; i < cnt; i++) {
    if (data[i]._seqnum_last_alloc > _last_cset_select) continue;

    ShenandoahHeapRegion* r = data[i]._region;
    assert (r->is_regular(), "should have been filtered before");

   if (r->garbage() > 0) {
      assert(!collection_set->is_in(r), "must not yet be in cset");
      collection_set->add_region(r);
    }
  }

  // Clear liveness data
  // TODO: Merge it with step 0, but save live data in RegionData before.
  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->used() > 0) {
      r->clear_live_data();
    }
  }

  collection_set->update_region_status();

  _last_cset_select = ShenandoahHeapRegion::seqnum_current_alloc();
}

void ShenandoahTraversalAggressiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* set,
                                                                                    RegionData* data, size_t data_size,
                                                                                    size_t free) {
  ShouldNotReachHere();
}

bool ShenandoahTraversalAggressiveHeuristics::should_start_gc() const {
  log_info(gc)("Trigger: Start next cycle immediately");
  return true;
}
