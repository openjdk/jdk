/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"

ShenandoahYoungGeneration::ShenandoahYoungGeneration(uint max_queues, size_t max_capacity, size_t soft_max_capacity) :
  ShenandoahGeneration(YOUNG, max_queues, max_capacity, soft_max_capacity),
  _old_gen_task_queues(nullptr) {
}

const char* ShenandoahYoungGeneration::name() const {
  return "YOUNG";
}

void ShenandoahYoungGeneration::set_concurrent_mark_in_progress(bool in_progress) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->set_concurrent_young_mark_in_progress(in_progress);
  if (_old_gen_task_queues != nullptr && in_progress) {
    // This is not a bug. When the young generation marking is complete,
    // the old generation marking is still in progress.
    heap->set_concurrent_old_mark_in_progress(in_progress);
  }
}

class ShenandoahPromoteTenuredRegionsTask : public AbstractGangTask {
private:
  ShenandoahRegionIterator* _regions;
public:
  volatile size_t _promoted;

  ShenandoahPromoteTenuredRegionsTask(ShenandoahRegionIterator* regions) :
    AbstractGangTask("Shenandoah Promote Tenured Regions"),
    _regions(regions),
    _promoted(0) {
  }

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahHeapRegion* r = _regions->next();
    while (r != NULL) {
      if (r->is_young() && r->age() >= InitialTenuringThreshold && ((r->is_regular() && !r->has_young_lab_flag()) || r->is_humongous_start())) {
        // The thread that first encounters a humongous start region promotes the associated humonogous continuations,
        // so we do not process humongous continuations directly.  Below, we rely on promote() to promote related
        // continuation regions when encountering a homongous start.
        size_t promoted = r->promote(false);
        Atomic::add(&_promoted, promoted);
      }
      r = _regions->next();
    }
  }
};

void ShenandoahYoungGeneration::promote_tenured_regions() {
  ShenandoahHeap::heap()->set_young_lab_region_flags();
  ShenandoahRegionIterator regions;
  ShenandoahPromoteTenuredRegionsTask task(&regions);
  ShenandoahHeap::heap()->workers()->run_task(&task);
  log_info(gc)("Promoted " SIZE_FORMAT " regions.", task._promoted);
}

void ShenandoahYoungGeneration::promote_all_regions() {
  // This only happens on a full stw collect. No allocations can happen here.
  shenandoah_assert_safepoint();

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  for (size_t index = 0; index < heap->num_regions(); index++) {
    ShenandoahHeapRegion* r = heap->get_region(index);
    if (r->is_young()) {
      r->promote(true);
    }
  }
  assert(_affiliated_region_count == 0, "young generation must not have affiliated regions after reset");
  _used = 0;
}

bool ShenandoahYoungGeneration::contains(ShenandoahHeapRegion* region) const {
  // TODO: why not test for equals YOUNG_GENERATION?  As written, returns true for regions that are FREE
  return region->affiliation() != OLD_GENERATION;
}

void ShenandoahYoungGeneration::parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  // Just iterate over the young generation here.
  ShenandoahGenerationRegionClosure<YOUNG> young_regions(cl);
  ShenandoahHeap::heap()->parallel_heap_region_iterate(&young_regions);
}

void ShenandoahYoungGeneration::heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahGenerationRegionClosure<YOUNG> young_regions(cl);
  ShenandoahHeap::heap()->heap_region_iterate(&young_regions);
}

bool ShenandoahYoungGeneration::is_concurrent_mark_in_progress() {
  return ShenandoahHeap::heap()->is_concurrent_young_mark_in_progress();
}

void ShenandoahYoungGeneration::reserve_task_queues(uint workers) {
  ShenandoahGeneration::reserve_task_queues(workers);
  if (_old_gen_task_queues != NULL) {
    _old_gen_task_queues->reserve(workers);
  }
}
