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

#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegionClosures.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

ShenandoahYoungGeneration::ShenandoahYoungGeneration(uint max_queues) :
  ShenandoahGeneration(YOUNG, max_queues),
  _old_gen_task_queues(nullptr) {
  assert(type() == ShenandoahGenerationType::YOUNG, "OO sanity");
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

bool ShenandoahYoungGeneration::contains(ShenandoahAffiliation affiliation) const {
  return affiliation == YOUNG_GENERATION;
}

bool ShenandoahYoungGeneration::contains(ShenandoahHeapRegion* region) const {
  return region->is_young();
}

void ShenandoahYoungGeneration::parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  // Just iterate over the young generation here.
  ShenandoahIncludeRegionClosure<YOUNG_GENERATION> young_regions_cl(cl);
  ShenandoahHeap::heap()->parallel_heap_region_iterate(&young_regions_cl);
}

void ShenandoahYoungGeneration::heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahIncludeRegionClosure<YOUNG_GENERATION> young_regions_cl(cl);
  ShenandoahHeap::heap()->heap_region_iterate(&young_regions_cl);
}

void ShenandoahYoungGeneration::parallel_heap_region_iterate_free(ShenandoahHeapRegionClosure* cl) {
  // Iterate over everything that is not old.
  ShenandoahExcludeRegionClosure<OLD_GENERATION> exclude_cl(cl);
  ShenandoahHeap::heap()->parallel_heap_region_iterate(&exclude_cl);
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
  _young_heuristics = new ShenandoahYoungHeuristics(this);
  _heuristics = _young_heuristics;
  _heuristics->set_guaranteed_gc_interval(ShenandoahGuaranteedYoungGCInterval);
  confirm_heuristics_mode();
  return _heuristics;
}

size_t ShenandoahYoungGeneration::used() const {
  return _free_set->young_used();
}

size_t ShenandoahYoungGeneration::bytes_allocated_since_gc_start() const {
  assert(ShenandoahHeap::heap()->mode()->is_generational(), "Young implies generational");
  return _free_set->get_bytes_allocated_since_gc_start();
}

size_t ShenandoahYoungGeneration::get_affiliated_region_count() const {
  return _free_set->young_affiliated_regions();
}

size_t ShenandoahYoungGeneration::get_humongous_waste() const {
  return _free_set->humongous_waste_in_mutator();
}

size_t ShenandoahYoungGeneration::used_regions() const {
  return _free_set->young_affiliated_regions();
}

size_t ShenandoahYoungGeneration::used_regions_size() const {
  size_t used_regions = _free_set->young_affiliated_regions();
  return used_regions * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahYoungGeneration::max_capacity() const {
  size_t total_regions = _free_set->total_young_regions();
  return total_regions * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahYoungGeneration::free_unaffiliated_regions() const {
  return _free_set->young_unaffiliated_regions();
}

size_t ShenandoahYoungGeneration::available() const {
  // The collector reserve may eat into what the mutator is allowed to use. Make sure we are looking
  // at what is available to the mutator when reporting how much memory is available.
  size_t available = this->ShenandoahGeneration::available();
  return MIN2(available, ShenandoahHeap::heap()->free_set()->available());
}

size_t ShenandoahYoungGeneration::soft_mutator_available() const {
  size_t available = this->ShenandoahGeneration::soft_mutator_available();
  return MIN2(available, ShenandoahHeap::heap()->free_set()->available());
}

void ShenandoahYoungGeneration::prepare_gc() {

  ShenandoahGeneration::prepare_gc();

  assert(type() == YOUNG, "Error?");
  // Clear any stale/partial local census data before the start of a
  // new marking cycle
  ShenandoahGenerationalHeap::heap()->age_census()->reset_local();
}
