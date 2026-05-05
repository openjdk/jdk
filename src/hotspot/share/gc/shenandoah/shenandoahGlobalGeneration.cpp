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

#include "gc/shenandoah/heuristics/shenandoahGlobalHeuristics.hpp"
#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"


const char* ShenandoahGlobalGeneration::name() const {
  return type() == NON_GEN ? "" : "Global";
}

size_t ShenandoahGlobalGeneration::max_capacity() const {
  size_t total_regions = _free_set->total_global_regions();
  return total_regions * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahGlobalGeneration::free_unaffiliated_regions() const {
  return _free_set->global_unaffiliated_regions();
}

size_t ShenandoahGlobalGeneration::used() const {
  return _free_set->global_used();
}

size_t ShenandoahGlobalGeneration::bytes_allocated_since_gc_start() const {
  return _free_set->get_bytes_allocated_since_gc_start();
}

size_t ShenandoahGlobalGeneration::get_affiliated_region_count() const {
  return _free_set->global_affiliated_regions();
}

size_t ShenandoahGlobalGeneration::get_humongous_waste() const {
  return _free_set->total_humongous_waste();
}


size_t ShenandoahGlobalGeneration::used_regions() const {
  return _free_set->global_affiliated_regions();
}

size_t ShenandoahGlobalGeneration::used_regions_size() const {
  size_t used_regions = _free_set->global_affiliated_regions();
  return used_regions * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahGlobalGeneration::available() const {
  // The collector reserve may eat into what the mutator is allowed to use. Make sure we are looking
  // at what is available to the mutator when reporting how much memory is available.
  size_t available = this->ShenandoahGeneration::available();
  return MIN2(available, ShenandoahHeap::heap()->free_set()->available());
}

void ShenandoahGlobalGeneration::set_concurrent_mark_in_progress(bool in_progress) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (in_progress && heap->mode()->is_generational()) {
    // Global collection has preempted an old generation mark. This is fine
    // because the global generation includes the old generation, but we
    // want the global collect to start from a clean slate and we don't want
    // any stale state in the old generation.
    assert(!heap->is_concurrent_old_mark_in_progress(), "Old cycle should not be running.");
  }

  heap->set_concurrent_young_mark_in_progress(in_progress);
}

bool ShenandoahGlobalGeneration::contains(ShenandoahAffiliation affiliation) const {
  return true;
}

bool ShenandoahGlobalGeneration::contains(ShenandoahHeapRegion* region) const {
  return true;
}

void ShenandoahGlobalGeneration::parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahHeap::heap()->parallel_heap_region_iterate(cl);
}

void ShenandoahGlobalGeneration::heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahHeap::heap()->heap_region_iterate(cl);
}

bool ShenandoahGlobalGeneration::is_concurrent_mark_in_progress() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  return heap->is_concurrent_mark_in_progress();
}

ShenandoahHeuristics* ShenandoahGlobalGeneration::initialize_heuristics(ShenandoahMode* gc_mode) {
  if (gc_mode->is_generational()) {
    _heuristics = new ShenandoahGlobalHeuristics(this);
  } else {
    _heuristics = gc_mode->initialize_heuristics(this);
  }

  _heuristics->set_guaranteed_gc_interval(ShenandoahGuaranteedGCInterval);
  confirm_heuristics_mode();
  return _heuristics;
}

void ShenandoahGlobalGeneration::set_mark_complete() {
  ShenandoahGeneration::set_mark_complete();
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
    heap->young_generation()->set_mark_complete();
    heap->old_generation()->set_mark_complete();
  }
}

void ShenandoahGlobalGeneration::set_mark_incomplete() {
  ShenandoahGeneration::set_mark_incomplete();
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
    heap->young_generation()->set_mark_incomplete();
    heap->old_generation()->set_mark_incomplete();
  }
}

void ShenandoahGlobalGeneration::prepare_gc() {
  ShenandoahGeneration::prepare_gc();

  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    assert(type() == GLOBAL, "Unexpected generation type");
    // Clear any stale/partial local census data before the start of a
    // new marking cycle
    ShenandoahGenerationalHeap::heap()->age_census()->reset_local();
  } else {
    assert(type() == NON_GEN, "Unexpected generation type");
  }
}
