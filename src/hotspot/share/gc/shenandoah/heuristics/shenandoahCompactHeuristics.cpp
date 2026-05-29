/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahCompactHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

ShenandoahCompactHeuristics::ShenandoahCompactHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info),
  _bytes_used_at_end_of_gc(0) {
  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahUncommit);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahAlwaysClearSoftRefs);
  SHENANDOAH_ERGO_ENABLE_FLAG(UseStringDeduplication);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahAllocationThreshold,  10);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahImmediateThreshold,   100);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahUncommitDelay,        1000);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahGuaranteedGCInterval, 30000);
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahGarbageThreshold,     10);
}

bool ShenandoahCompactHeuristics::should_start_gc() {
  const size_t used = _space_info->used();
  const size_t capacity = ShenandoahHeap::heap()->soft_max_capacity();
  const size_t available = _space_info->soft_mutator_available();
  const size_t bytes_allocated = used > _bytes_used_at_end_of_gc ? used - _bytes_used_at_end_of_gc : 0;

  log_debug(gc, ergo)("should_start_gc calculation: available: " PROPERFMT ", soft_max_capacity: "  PROPERFMT ", "
                "allocated_since_gc_start: "  PROPERFMT,
                PROPERFMTARGS(available), PROPERFMTARGS(capacity), PROPERFMTARGS(bytes_allocated));

  const size_t threshold_bytes_allocated = capacity / 100 * ShenandoahAllocationThreshold;
  const size_t min_threshold = capacity / 100 * ShenandoahMinFreeThreshold;

  if (available < min_threshold) {
    log_trigger("Free (Soft) (" PROPERFMT ") is below minimum threshold (" PROPERFMT ")",
                PROPERFMTARGS(available), PROPERFMTARGS(min_threshold));
    accept_trigger();
    return true;
  }

  if (bytes_allocated > threshold_bytes_allocated) {
    log_trigger("Allocated since last cycle started (" PROPERFMT ") is larger than allocation threshold (" PROPERFMT ")",
                PROPERFMTARGS(bytes_allocated), PROPERFMTARGS(threshold_bytes_allocated));
    accept_trigger();
    return true;
  }

  return ShenandoahHeuristics::should_start_gc();
}

void ShenandoahCompactHeuristics::record_cycle_end() {
  ShenandoahHeuristics::record_cycle_end();
  _bytes_used_at_end_of_gc = _space_info->used();
}

void ShenandoahCompactHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                        RegionData* data, size_t size,
                                                                        size_t actual_free) {
  // Do not select too large CSet that would overflow the available free space
  const size_t max_cset = actual_free * 3 / 4;

  log_info(gc, ergo)("CSet Selection. Actual Free: " PROPERFMT ", Max CSet: " PROPERFMT,
                     PROPERFMTARGS(actual_free), PROPERFMTARGS(max_cset));

  const size_t threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;
  size_t live_cset = 0;
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
    const size_t new_cset = live_cset + r->get_live_data_bytes();
    if (new_cset < max_cset && r->garbage() > threshold) {
      live_cset = new_cset;
      cset->add_region(r);
    }
  }
}
