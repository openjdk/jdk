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


#include "gc/shenandoah/heuristics/shenandoahStaticHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

ShenandoahStaticHeuristics::ShenandoahStaticHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info) {
  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);
}

bool ShenandoahStaticHeuristics::should_start_gc() {
  size_t capacity = ShenandoahHeap::heap()->soft_max_capacity();
  size_t available = _space_info->soft_mutator_available();
  size_t allocated = _space_info->bytes_allocated_since_gc_start();

  log_debug(gc, ergo)("should_start_gc calculation: available: " PROPERFMT ", soft_max_capacity: "  PROPERFMT ", "
                "allocated_since_gc_start: "  PROPERFMT,
                PROPERFMTARGS(available), PROPERFMTARGS(capacity), PROPERFMTARGS(allocated));

  size_t threshold_available = capacity / 100 * ShenandoahMinFreeThreshold;

  if (available < threshold_available) {
    log_trigger("Free (Soft) (" PROPERFMT ") is below minimum threshold (" PROPERFMT ")",
                 PROPERFMTARGS(available), PROPERFMTARGS(threshold_available));
    accept_trigger();
    return true;
  }
  return ShenandoahHeuristics::should_start_gc();
}

void ShenandoahStaticHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                       RegionData* data, size_t size,
                                                                       size_t free) {
  size_t threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
    if (r->garbage() > threshold) {
      cset->add_region(r);
    }
  }
}
