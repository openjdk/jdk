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

#include "gc/shenandoah/heuristics/shenandoahStaticHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

ShenandoahStaticHeuristics::ShenandoahStaticHeuristics() : ShenandoahHeuristics() {
  // Static heuristics may degrade to continuous if live data is larger
  // than free threshold. ShenandoahAllocationThreshold is supposed to break this,
  // but it only works if it is non-zero.
  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahAllocationThreshold, 1);

  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);

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

ShenandoahStaticHeuristics::~ShenandoahStaticHeuristics() {}

bool ShenandoahStaticHeuristics::should_start_normal_gc() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  size_t capacity = heap->capacity();
  size_t available = heap->free_set()->available();
  size_t threshold_available = (capacity * ShenandoahFreeThreshold) / 100;

  if (available < threshold_available) {
    log_info(gc)("Trigger: Free (" SIZE_FORMAT "M) is below free threshold (" SIZE_FORMAT "M)",
                 available / M, threshold_available / M);
    return true;
  }
  return ShenandoahHeuristics::should_start_normal_gc();
}

void ShenandoahStaticHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                       RegionData* data, size_t size,
                                                                       size_t free) {
  size_t threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx]._region;
    if (r->garbage() > threshold) {
      cset->add_region(r);
    }
  }
}

const char* ShenandoahStaticHeuristics::name() {
  return "static";
}

bool ShenandoahStaticHeuristics::is_diagnostic() {
  return false;
}

bool ShenandoahStaticHeuristics::is_experimental() {
  return false;
}
