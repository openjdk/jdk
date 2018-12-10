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

#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/heuristics/shenandoahCompactHeuristics.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

ShenandoahCompactHeuristics::ShenandoahCompactHeuristics() : ShenandoahHeuristics() {
  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahUncommit);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahAlwaysClearSoftRefs);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahAllocationThreshold,  10);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahImmediateThreshold,   100);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahUncommitDelay,        1000);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahGuaranteedGCInterval, 30000);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahGarbageThreshold,     10);

  // Final configuration checks
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahSATBBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahReadBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahWriteBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahStoreValReadBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahKeepAliveBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCASBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahAcmpBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCloneBarrier);
}

bool ShenandoahCompactHeuristics::should_start_normal_gc() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  size_t available = heap->free_set()->available();
  size_t threshold_bytes_allocated = heap->capacity() * ShenandoahAllocationThreshold / 100;
  size_t min_threshold = ShenandoahMinFreeThreshold * heap->capacity() / 100;

  if (available < min_threshold) {
    log_info(gc)("Trigger: Free (" SIZE_FORMAT "M) is below minimum threshold (" SIZE_FORMAT "M)",
                 available / M, min_threshold / M);
    return true;
  }

  if (available < threshold_bytes_allocated) {
    log_info(gc)("Trigger: Free (" SIZE_FORMAT "M) is lower than allocated recently (" SIZE_FORMAT "M)",
                 available / M, threshold_bytes_allocated / M);
    return true;
  }

  size_t bytes_allocated = heap->bytes_allocated_since_gc_start();
  if (bytes_allocated > threshold_bytes_allocated) {
    log_info(gc)("Trigger: Allocated since last cycle (" SIZE_FORMAT "M) is larger than allocation threshold (" SIZE_FORMAT "M)",
                 bytes_allocated / M, threshold_bytes_allocated / M);
    return true;
  }

  return ShenandoahHeuristics::should_start_normal_gc();
}

void ShenandoahCompactHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                        RegionData* data, size_t size,
                                                                        size_t actual_free) {
  // Do not select too large CSet that would overflow the available free space
  size_t max_cset = actual_free * 3 / 4;

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

const char* ShenandoahCompactHeuristics::name() {
  return "compact";
}

bool ShenandoahCompactHeuristics::is_diagnostic() {
  return false;
}

bool ShenandoahCompactHeuristics::is_experimental() {
  return false;
}
