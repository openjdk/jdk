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
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

ShenandoahStaticHeuristics::ShenandoahStaticHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info) {
  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);
}

ShenandoahStaticHeuristics::~ShenandoahStaticHeuristics() {}

bool ShenandoahStaticHeuristics::should_start_gc() {
  size_t max_capacity = _space_info->max_capacity();
  size_t capacity = ShenandoahHeap::heap()->soft_max_capacity();
  size_t available = _space_info->available();

  // Make sure the code below treats available without the soft tail.
  size_t soft_tail = max_capacity - capacity;
  available = (available > soft_tail) ? (available - soft_tail) : 0;

  size_t threshold_available = capacity / 100 * ShenandoahMinFreeThreshold;

  if (available < threshold_available) {
    log_trigger("Free (%zu%s) is below minimum threshold (%zu%s)",
                 byte_size_in_proper_unit(available),           proper_unit_for_byte_size(available),
                 byte_size_in_proper_unit(threshold_available), proper_unit_for_byte_size(threshold_available));
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
