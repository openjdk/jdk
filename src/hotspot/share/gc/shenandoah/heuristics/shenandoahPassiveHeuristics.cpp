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


#include "gc/shenandoah/heuristics/shenandoahPassiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

ShenandoahPassiveHeuristics::ShenandoahPassiveHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info) {}

bool ShenandoahPassiveHeuristics::should_start_gc() {
  // Never do concurrent GCs.
  decline_trigger();
  return false;
}

bool ShenandoahPassiveHeuristics::should_unload_classes() {
  // Always unload classes, if we can.
  return can_unload_classes();
}

bool ShenandoahPassiveHeuristics::should_degenerate_cycle() {
  // Always fail to Degenerated GC, if enabled
  return ShenandoahDegeneratedGC;
}

size_t ShenandoahPassiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                          RegionData* data, size_t size,
                                                                          size_t actual_free) {
  assert(ShenandoahDegeneratedGC, "This path is only taken for Degenerated GC");

  // Do not select too large CSet that would overflow the available free space.
  // Take at least the entire evacuation reserve, and be free to overflow to free space.
  size_t max_capacity = _space_info->max_capacity();
  size_t available = MAX2(max_capacity / 100 * ShenandoahEvacReserve, actual_free);
  size_t max_cset  = (size_t)(available / ShenandoahEvacWaste);

  log_info(gc, ergo)("CSet Selection. Actual Free: %zu%s, Max CSet: %zu%s",
                     byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free),
                     byte_size_in_proper_unit(max_cset),    proper_unit_for_byte_size(max_cset));

  size_t threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  size_t live_cset = 0;
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
    size_t new_cset = live_cset + r->get_live_data_bytes();
    if (new_cset < max_cset && r->garbage() > threshold) {
      live_cset = new_cset;
      cset->add_region(r);
    }
  }
  return 0;
}
