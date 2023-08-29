/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"

ShenandoahYoungGeneration::ShenandoahYoungGeneration(uint max_queues, size_t max_capacity, size_t soft_max_capacity) :
  ShenandoahGeneration(YOUNG, max_queues, max_capacity, soft_max_capacity),
  _old_gen_task_queues(nullptr) {
}

void ShenandoahYoungGeneration::set_concurrent_mark_in_progress(bool in_progress) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->set_concurrent_young_mark_in_progress(in_progress);
  if (is_bootstrap_cycle() && in_progress && !heap->is_prepare_for_old_mark_in_progress()) {
    // This is not a bug. When the bootstrapping marking phase is complete,
    // the old generation marking is still in progress, unless it's not.
    // In the case that old-gen preparation for mixed evacuation has been
    // preempted, we do not want to set concurrent old mark to be in progress.
    heap->set_concurrent_old_mark_in_progress(in_progress);
  }
}

bool ShenandoahYoungGeneration::contains(ShenandoahHeapRegion* region) const {
  // TODO: why not test for equals YOUNG_GENERATION?  As written, returns true for regions that are FREE
  return !region->is_old();
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
  if (is_bootstrap_cycle()) {
    _old_gen_task_queues->reserve(workers);
  }
}

bool ShenandoahYoungGeneration::contains(oop obj) const {
  return ShenandoahHeap::heap()->is_in_young(obj);
}

ShenandoahHeuristics* ShenandoahYoungGeneration::initialize_heuristics(ShenandoahMode* gc_mode) {
  _heuristics = new ShenandoahYoungHeuristics(this);
  _heuristics->set_guaranteed_gc_interval(ShenandoahGuaranteedYoungGCInterval);
  confirm_heuristics_mode();
  return _heuristics;
}

size_t ShenandoahYoungGeneration::available() const {
  // The collector reserve may eat into what the mutator is allowed to use. Make sure we are looking
  // at what is available to the mutator when reporting how much memory is available.
  size_t available = this->ShenandoahGeneration::available();
  return MIN2(available, ShenandoahHeap::heap()->free_set()->available());
}

size_t ShenandoahYoungGeneration::soft_available() const {
  size_t available = this->ShenandoahGeneration::soft_available();
  return MIN2(available, ShenandoahHeap::heap()->free_set()->available());
}
