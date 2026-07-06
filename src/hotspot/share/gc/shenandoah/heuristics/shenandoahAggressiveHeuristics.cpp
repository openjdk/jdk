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


#include "gc/shenandoah/heuristics/shenandoahAggressiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/os.hpp"

ShenandoahAggressiveHeuristics::ShenandoahAggressiveHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info) {
  // Do not shortcut evacuation
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahImmediateThreshold, 100);

  // Aggressive evacuates everything, so it needs as much evac space as it can get
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahEvacReserveOverflow);
}

void ShenandoahAggressiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                           RegionData* data, size_t size,
                                                                           size_t free) {
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
    if (r->garbage() > 0) {
      cset->add_region(r);
    }
  }
}

bool ShenandoahAggressiveHeuristics::should_start_gc() {
  log_trigger("Start next cycle immediately");
  accept_trigger();
  return true;
}

bool ShenandoahAggressiveHeuristics::should_unload_classes() {
  if (!can_unload_classes()) return false;
  if (has_metaspace_oom()) return true;
  // Randomly unload classes with 50% chance.
  return (os::random() & 1) == 1;
}
