/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahPassiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

ShenandoahPassiveHeuristics::ShenandoahPassiveHeuristics() : ShenandoahHeuristics() {
  // Do not allow concurrent cycles.
  FLAG_SET_DEFAULT(ExplicitGCInvokesConcurrent, false);
  FLAG_SET_DEFAULT(ShenandoahImplicitGCInvokesConcurrent, false);

  // Passive runs with max speed, reacts on allocation failure.
  FLAG_SET_DEFAULT(ShenandoahPacing, false);

  // No need for evacuation reserve with Full GC, only for Degenerated GC.
  if (!ShenandoahDegeneratedGC) {
    SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahEvacReserve, 0);
  }

  // Disable known barriers by default.
  SHENANDOAH_ERGO_DISABLE_FLAG(ShenandoahLoadRefBarrier);
  SHENANDOAH_ERGO_DISABLE_FLAG(ShenandoahSATBBarrier);
  SHENANDOAH_ERGO_DISABLE_FLAG(ShenandoahKeepAliveBarrier);
  SHENANDOAH_ERGO_DISABLE_FLAG(ShenandoahStoreValEnqueueBarrier);
  SHENANDOAH_ERGO_DISABLE_FLAG(ShenandoahCASBarrier);
  SHENANDOAH_ERGO_DISABLE_FLAG(ShenandoahCloneBarrier);

  // Final configuration checks
  // No barriers are required to run.
}

bool ShenandoahPassiveHeuristics::should_start_normal_gc() const {
  // Never do concurrent GCs.
  return false;
}

bool ShenandoahPassiveHeuristics::should_process_references() {
  // Always process references, if we can.
  return can_process_references();
}

bool ShenandoahPassiveHeuristics::should_unload_classes() {
  // Always unload classes, if we can.
  return can_unload_classes();
}

bool ShenandoahPassiveHeuristics::should_degenerate_cycle() {
  // Always fail to Degenerated GC, if enabled
  return ShenandoahDegeneratedGC;
}

void ShenandoahPassiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                        RegionData* data, size_t size,
                                                                        size_t actual_free) {
  assert(ShenandoahDegeneratedGC, "This path is only taken for Degenerated GC");

  // Do not select too large CSet that would overflow the available free space.
  // Take at least the entire evacuation reserve, and be free to overflow to free space.
  size_t capacity  = ShenandoahHeap::heap()->capacity();
  size_t available = MAX2(ShenandoahEvacReserve * capacity / 100, actual_free);
  size_t max_cset  = (size_t)(available / ShenandoahEvacWaste);

  log_info(gc, ergo)("CSet Selection. Actual Free: " SIZE_FORMAT "M, Max CSet: " SIZE_FORMAT "M",
                     actual_free / M, max_cset / M);

  size_t threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  size_t live_cset = 0;
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx]._region;
    size_t new_cset = live_cset + r->get_live_data_bytes();
    if (new_cset < max_cset && r->garbage() > threshold) {
      live_cset = new_cset;
      cset->add_region(r);
    }
  }
}

const char* ShenandoahPassiveHeuristics::name() {
  return "passive";
}

bool ShenandoahPassiveHeuristics::is_diagnostic() {
  return true;
}

bool ShenandoahPassiveHeuristics::is_experimental() {
  return false;
}
